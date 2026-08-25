<?php
/*
 * KamiClient crash report endpoint.
 *
 * Takes the JSON that haven.error.ErrorHandler posts, sanity checks it, and
 * forwards it to a Discord webhook as an errorlog.txt attachment.
 *
 * The webhook URL lives HERE, on the server. It must never be compiled into
 * the client - a webhook URL is a bearer credential, so anyone who extracts it
 * can post to (and delete) the channel, and rotating it would mean shipping a
 * new build to everyone.
 *
 * Deploy: put this behind HTTPS, set $WEBHOOK below (or better, put it in an
 * env var), and point haven.errorurl at it in the client build.
 */

// The client compares the response Content-Type with an exact string match, so
// PHP must not append its usual ";charset=UTF-8" or the client ignores our reply
// and shows the user nothing. Must be set before any header() call.
ini_set('default_charset', '');

// ---------------------------------------------------------------- config

// Read from the environment (or a webhook.txt sitting next to this script, kept
// out of git). NEVER paste the URL in here - it is a bearer credential, and this
// file lives in the repo.
// The webhook URL is a bearer credential: whoever has it can post to the channel
// and delete the webhook. Keep it OUT of this file, and preferably out of the
// document root entirely. Checked in order of how well each survives a
// webserver misconfiguration:
//
//   1. Environment variable   - never on disk, nothing to serve. Best.
//   2. A file above the webroot - no URL maps to it, so Apache cannot serve it
//      even if PHP stops executing (broken deploy, disabled module, a stray
//      .htaccess). This is the one that matters.
//   3. Next to this script    - last resort. A .php holding the secret is only
//      safe while PHP is actually running; if it ever stops, the file is served
//      as plain text. Editor leftovers (config.php~, .swp) are served raw too.
$WEBHOOK = getenv('KAMI_REPORT_WEBHOOK') ?: null;
if(!$WEBHOOK) {
    // 2a. A PHP file that RETURNS the secret. Preferred on this host: fetched
    //     directly over HTTP it executes and emits nothing, so it does not leak
    //     even though it has to live inside the document root (PHP here cannot
    //     see above public_html, so "outside the webroot" is not an option).
    foreach([dirname(__DIR__) . '/kami-webhook.php',
             __DIR__ . '/kami-webhook.php'] as $f) {
        if(is_readable($f)) {
            $v = @include $f;
            if(is_string($v) && ($v = trim($v)) !== '') {
                $WEBHOOK = $v;
                break;
            }
        }
    }
    // 2b. Plain-text fallbacks, in descending order of safety.
    if(!$WEBHOOK) {
        foreach(['/etc/kamiclient/webhook',              // outside webroot, best
                 dirname(__DIR__) . '/.kami-webhook',    // dot-prefixed
                 dirname(__DIR__) . '/kami-webhook',
                 __DIR__ . '/.kami-webhook'] as $f) {
            if(is_readable($f) && ($v = trim(file_get_contents($f))) !== '') {
                $WEBHOOK = $v;
                break;
            }
        }
    }
}
// Only ever talk to Discord. Stops a tampered/garbage config from turning this
// endpoint into a relay that forwards user crash reports to somebody else.
if($WEBHOOK && !preg_match('~^https://(canary\.|ptb\.)?discord(app)?\.com/api/webhooks/~', $WEBHOOK)) {
    error_log('kami-report: refusing non-Discord webhook target');
    $WEBHOOK = null;
}
if(!$WEBHOOK) {
    error_log('kami-report: no webhook configured');
    accept('Report received.');
}
$MAX_BYTES = 262144;   // 256 KB. Anything bigger is not a real crash report.
$RATE_DIR  = sys_get_temp_dir() . '/kamireports';
$RATE_MAX  = 10;       // reports per IP...
$RATE_WIN  = 300;      // ...per this many seconds.

// ---------------------------------------------------------------- helpers

function bail($code, $msg) {
    // text/x-report-error makes the client raise ReportException and show $msg
    // to the user. Any other content type is silently accepted by the client.
    http_response_code($code);
    header('Content-Type: text/x-report-error');
    echo $msg;
    exit;
}

function accept($msg) {
    // text/x-report-info gets displayed in the client's error dialog.
    http_response_code(200);
    header('Content-Type: text/x-report-info');
    echo $msg;
    exit;
}

// ---------------------------------------------------------------- checks

if(($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST')
    bail(405, 'POST only');

// Reject on the declared length before reading anything into memory. The real
// enforcement should also be in nginx/apache (client_max_body_size); this is
// the backstop, not the front line.
$len = (int)($_SERVER['CONTENT_LENGTH'] ?? 0);
if($len <= 0 || $len > $MAX_BYTES)
    bail(413, 'Report too large');

$ctype = $_SERVER['CONTENT_TYPE'] ?? '';
if(stripos($ctype, 'application/json') === false)
    bail(415, 'Expected application/json');

// Rate limit per IP. Crash loops in a released build will hit this long before
// anyone malicious does - one user can otherwise send thousands of reports.
$ip = $_SERVER['REMOTE_ADDR'] ?? '0.0.0.0';
if(!is_dir($RATE_DIR)) @mkdir($RATE_DIR, 0700, true);
$slot = $RATE_DIR . '/' . hash('sha256', $ip);
$hits = [];
if(is_file($slot))
    $hits = array_filter(json_decode(@file_get_contents($slot), true) ?: [],
                         fn($t) => $t > time() - $RATE_WIN);
if(count($hits) >= $RATE_MAX)
    bail(429, 'Too many reports, please try again later');
$hits[] = time();
@file_put_contents($slot, json_encode($hits), LOCK_EX);

$raw = file_get_contents('php://input', false, null, 0, $MAX_BYTES + 1);
if($raw === false || strlen($raw) > $MAX_BYTES)
    bail(413, 'Report too large');

$rep = json_decode($raw, true);
if(!is_array($rep))
    bail(400, 'Malformed report');

// Shape check. Everything past here is attacker-controlled text: this endpoint
// is public and unauthenticated, so treat every string as hostile.
$trace = is_string($rep['trace'] ?? null) ? $rep['trace'] : null;
if($trace === null || $trace === '')
    bail(400, 'Report has no trace');

$exception = is_string($rep['exception'] ?? null) ? $rep['exception'] : 'unknown';
$message   = is_string($rep['message']   ?? null) ? $rep['message']   : '';

$props = [];
if(is_array($rep['props'] ?? null)) {
    foreach($rep['props'] as $k => $v) {
        if(!is_string($k) || !is_scalar($v)) continue;
        $props[substr($k, 0, 64)] = substr((string)$v, 0, 256);
    }
}

// ---------------------------------------------------------------- build

// Fingerprint on exception + first few frames, so a crash loop collapses into
// one recognisable id instead of a wall of identical reports.
$frames = [];
foreach(explode("\n", $trace) as $line) {
    $line = trim($line);
    if(str_starts_with($line, 'at ')) {
        $frames[] = $line;
        if(count($frames) >= 4) break;
    }
}
$fingerprint = substr(hash('sha256', $exception . "\n" . implode("\n", $frames)), 0, 12);

$file = "== KamiClient crash report ==\n"
      . 'fingerprint: ' . $fingerprint . "\n"
      . 'time:        ' . gmdate('Y-m-d H:i:s', (int)(($rep['time'] ?? time() * 1000) / 1000)) . " UTC\n"
      . 'exception:   ' . $exception . "\n"
      . 'message:     ' . $message . "\n\n";
foreach($props as $k => $v)
    $file .= sprintf("%-16s %s\n", $k . ':', $v);
$file .= "\n---- stack trace ----\n" . $trace . "\n";

// The summary line goes in the message body, the trace goes in the attachment.
// Strip everything that could ping the channel or break out of the code span -
// someone will eventually send a "stack trace" containing @everyone.
$safe = fn($s) => str_replace(['@', '`', "\n", "\r"], ['@.', "'", ' ', ' '], substr($s, 0, 180));
$summary = sprintf("`%s` **%s**%s",
                   $fingerprint,
                   $safe($exception),
                   $message !== '' ? ': ' . $safe($message) : '');

// ---------------------------------------------------------------- forward

$payload = json_encode([
    'content'          => $summary,
    // Belt and braces: even with the stripping above, tell Discord to parse no
    // mentions at all.
    'allowed_mentions' => ['parse' => []],
]);

$boundary = '----kami' . bin2hex(random_bytes(8));
$body  = "--$boundary\r\n"
       . "Content-Disposition: form-data; name=\"payload_json\"\r\n"
       . "Content-Type: application/json\r\n\r\n$payload\r\n"
       . "--$boundary\r\n"
       . "Content-Disposition: form-data; name=\"files[0]\"; filename=\"errorlog.txt\"\r\n"
       . "Content-Type: text/plain\r\n\r\n$file\r\n"
       . "--$boundary--\r\n";

$ch = curl_init($WEBHOOK);
curl_setopt_array($ch, [
    CURLOPT_POST           => true,
    CURLOPT_POSTFIELDS     => $body,
    CURLOPT_HTTPHEADER     => ["Content-Type: multipart/form-data; boundary=$boundary"],
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_TIMEOUT        => 10,
]);
$res  = curl_exec($ch);
$code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
curl_close($ch);

// Don't leak webhook failures to the user - they can't act on them, and the
// response text would expose the webhook's own error detail.
if($code < 200 || $code >= 300) {
    error_log("kami-report: discord returned $code: $res");
    accept('Report received.');
}

accept('Report received. Thanks! (id ' . $fingerprint . ')');
