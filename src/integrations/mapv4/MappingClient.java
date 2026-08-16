package integrations.mapv4;

import haven.*;
import haven.MapFile.Marker;
import haven.MCache.LoadingMap;
import me.ender.minimap.*;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;


/**
 * @author Vendan
 */
public class MappingClient {
    
    private ExecutorService gridsUploader = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    
    private static volatile MappingClient INSTANCE = null;
    
    private Glob glob;
    
    public static void init(Glob glob) {
	synchronized (MappingClient.class) {
	    if(INSTANCE == null) {
		INSTANCE = new MappingClient(glob);
	    } else {
		throw new IllegalStateException("MappingClient can only be initialized once!");
	    }
	}
    }
    
    public static void destroy() {
	synchronized (MappingClient.class) {
	    if(INSTANCE != null) {
		/* KamiClient: mark it dead BEFORE shutting the pools down.
		 * The marker and grid pipelines reschedule themselves, so a
		 * task already in flight would otherwise keep going and call
		 * execute() on a shut-down scheduler - which throws
		 * RejectedExecutionException and silently drops the upload.
		 * Tasks check dead() and bail out quietly instead. */
		INSTANCE.dead = true;
		INSTANCE.gridsUploader.shutdown();
		INSTANCE.scheduler.shutdown();
		INSTANCE = null;
	    }
	}
    }

    private volatile boolean dead = false;
    public boolean dead() {return dead;}

    /* KamiClient: every automap line goes through here so a player running the
     * bat file can just copy the console back. Tagged and timestamped because
     * these arrive interleaved from several pool threads. */
    /* KamiClient: the server's response body, truncated - it usually carries
     * the actual reason a request was rejected. */
    static String readbody(HttpURLConnection conn, boolean error) {
	try(InputStream in = error ? conn.getErrorStream() : conn.getInputStream()) {
	    if(in == null)
		return "<no body>";
	    ByteArrayOutputStream buf = new ByteArrayOutputStream();
	    byte[] chunk = new byte[4096];
	    int n;
	    while((n = in.read(chunk)) > 0 && buf.size() < 2048)
		buf.write(chunk, 0, n);
	    String s = new String(buf.toByteArray(), StandardCharsets.UTF_8).trim();
	    return s.isEmpty() ? "<empty body>" : (s.length() > 500 ? s.substring(0, 500) + "..." : s);
	} catch(Exception ex) {
	    return "<unreadable: " + ex + ">";
	}
    }

    public static void log(String fmt, Object... args) {
	System.out.printf("[automap %tT] %s%n", System.currentTimeMillis(),
			  (args.length == 0) ? fmt : String.format(fmt, args));
	System.out.flush();
    }

    /* automap failures used to go to stdout only, so an upload that never
     * happened looked exactly like one that did. Put it in front of the user -
     * they are the only one who can act on it. */
    private void warn(String msg) {
	log("ERROR: %s", msg);
	try {
	    GameUI gui = glob.sess.ui.gui;
	    if(gui != null)
		gui.error(msg);
	} catch(Exception ignored) {}
    }

    /* KamiClient: every submission to the pools goes through these, so a
     * shutdown race is a no-op rather than an exception. They report whether
     * the task was actually accepted. */
    private boolean submit(Runnable task) {
	String what = task.getClass().getSimpleName();
	if(dead) {
	    log("submit(%s) refused: mapping client already destroyed", what);
	    return false;
	}
	try {
	    scheduler.execute(task);
	    return true;
	} catch(RejectedExecutionException ex) {
	    log("submit(%s) REJECTED by scheduler: %s", what, ex);
	    return false;
	}
    }

    private boolean submit(Runnable task, long delay, TimeUnit unit) {
	String what = task.getClass().getSimpleName();
	if(dead) {
	    log("submit(%s, +%d%s) refused: mapping client already destroyed", what, delay, unit);
	    return false;
	}
	try {
	    scheduler.schedule(task, delay, unit);
	    return true;
	} catch(RejectedExecutionException ex) {
	    log("submit(%s, +%d%s) REJECTED by scheduler: %s", what, delay, unit, ex);
	    return false;
	}
    }
    
    public static boolean initialized() {return INSTANCE != null;}
    
    public static MappingClient getInstance() {
	synchronized (MappingClient.class) {
	    if(INSTANCE == null) {
		throw new IllegalStateException("MappingClient should be initialized first!");
	    }
	    return INSTANCE;
	}
    }
    
    private boolean trackingEnabled;
    
    /***
     * Enable tracking for this execution.  Must be called each time the client is started.
     * @param enabled
     */
    public void EnableTracking(boolean enabled) {
	trackingEnabled = enabled;
    }
    
    private boolean gridEnabled;
    
    /***
     * Enable grid data/image upload for this execution.  Must be called each time the client is started.
     * @param enabled
     */
    public void EnableGridUploads(boolean enabled) {
	gridEnabled = enabled;
    }
    
    private PositionUpdates pu = new PositionUpdates();
    
    private MappingClient(Glob glob) {
	this.glob = glob;
	scheduler.scheduleAtFixedRate(pu, 5L, 5L, TimeUnit.SECONDS);
    }
    
    private String endpoint;
    
    /***
     * Set mapping server endpoint.  Must be called each time the client is started.  Takes effect immediately.
     * @param endpoint
     */
    public void SetEndpoint(String endpoint) {
	this.endpoint = endpoint;
    }
    
    private String playerName;
    
    /***
     * Set the player name.  Typically called from Charlist.wdgmsg
     * @param name
     */
    public void SetPlayerName(String name) {
	playerName = name;
    }
    
    private String genus;
    
    /***
     * Set the world genus (version)
     * @param genus
     */
    public void setGenus(String genus) {
	this.genus = genus;
    }
    
    /***
     * Checks that the endpoint is functional and matches the version of this mapping client.
     * @return
     */
    public boolean CheckEndpoint() {
	try {
	    if (INSTANCE == null)
		return false;
	    String url = endpoint + "/checkVersion?version=4&genus=" + genus;
	    HttpURLConnection connection =
		(HttpURLConnection) new URL(url).openConnection();
	    connection.setRequestMethod("GET");
	    connection.setConnectTimeout(15000);
	    connection.setReadTimeout(15000);
	    int code = connection.getResponseCode();
	    if(code != 200)
		log("checkVersion body: %s", readbody(connection, true));
	    return code == 200;
	} catch (Exception ex) {
	    log("checkVersion FAILED for endpoint '%s': %s", endpoint, ex);
	    return false;
	}
    }
    
    private Coord2d playerCoord = new Coord2d(0,0);
    private long playerGridId = 0;
    
    /***
     * Track a gob at a location.  Typically called in Gob.move
     * Note, current implementation in gob is actually only tracking the player and nothing else
     * @param id
     * @param coordinates
     */
    public void Track(long id, Coord2d coordinates) {
	try {
	    playerCoord = coordinates;
	    MCache.Grid g = glob.map.getgrid(toGC(coordinates));
	    playerGridId = g.id;
	    pu.Track(id, coordinates, g.id, genus);
	} catch (Exception ex) {}
    }
    
    public void SetTimerToNearestRes(String inspectResult)
    {
	submit(new UploadInspectResult(playerGridId, playerCoord, inspectResult, genus));
    }
    
    private Coord lastGC = null;
    
    /***
     * Called when entering a new grid
     * @param gc Grid coordinates
     */
    public void EnterGrid(Coord gc) {
	lastGC = gc;
	submit(new GenerateGridUpdateTask(gc, genus));
    }
    
    /***
     * Called as you move around, automatically calculates if you have entered a new grid and calls EnterGrid accordingly.
     * @param c Normal coordinates
     */
    public void CheckGridCoord(Coord2d c) {
	Coord gc = toGC(c);
	if(lastGC == null || !gc.equals(lastGC)) {
	    EnterGrid(gc);
	}
    }
    
    private final Map<Long, MapRef> cache = new HashMap<Long, MapRef>();
    
    /***
     * Gets a MapRef (mapid, coordinate pair) for the players current location
     * @return Current grid MapRef
     */
    public MapRef GetMapRef() {
	try {
	    Gob player = glob.sess.ui.gui.map.player();
	    Coord gc = toGC(player.rc);
	    synchronized (cache) {
		long id = glob.map.getgrid(gc).id;
		MapRef mapRef = cache.get(id);
		if(mapRef == null) {
		    submit(new Locate(id));
		}
		return mapRef;
	    }
	} catch (Exception e) {}
	return null;
    }
    
    /***
     * Given a mapref, opens the map to the corresponding location
     * @param mapRef
     */
    public void OpenMap(MapRef mapRef) {
	try {
	    if(mapRef == null) {return;}
	    glob.sess.ui.wnd.toolkit().browse(new java.net.URI(
		String.format(endpoint + "/#/grid/%d/%d/%d/6", mapRef.mapID, mapRef.gc.x, mapRef.gc.y)));
	} catch (Exception ex) {}
    }
    
    private class Locate implements Runnable {
	long gridID;
	
	Locate(long gridID) {
	    this.gridID = gridID;
	}
	
	@Override
	public void run() {
	    try {
		final HttpURLConnection connection =
		    (HttpURLConnection) new URL(endpoint + "/locate?gridID=" + gridID).openConnection();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
		    String resp = reader.lines().collect(Collectors.joining());
		    String[] parts = resp.split(";");
		    if(parts.length == 3) {
			MapRef mr = new MapRef(Integer.valueOf(parts[0]), new Coord(Integer.valueOf(parts[1]), Integer.valueOf(parts[2])));
			synchronized (cache) {
			    cache.put(gridID, mr);
			}
		    }
		    
		} finally {
		    connection.disconnect();
		}
		
	    } catch (final Exception ex) {
		log("locate(gridID=%d) FAILED: %s", gridID, ex);
	    }
	}
    }
    
    /***
     * Process a mapfile to extract markers to upload
     * @param mapfile
     * @param uploadCheck
     */
    public void ProcessMap(MapFile mapfile, Predicate<Marker> uploadCheck) {
	log("ProcessMap called: endpoint=%s genus=%s player=%s gridUploads=%s tracking=%s",
	    endpoint, genus, playerName, gridEnabled, trackingEnabled);
	if(endpoint == null || endpoint.isEmpty()) {
	    warn("Automap: no endpoint configured, nothing will be uploaded.");
	    return;
	}
	if(!submit(new ExtractMapper(mapfile, uploadCheck, genus), 1, TimeUnit.SECONDS))
	    warn("Automap: could not start marker extraction.");
    }
    
    private class ExtractMapper implements Runnable {
	MapFile mapfile;
	Predicate<Marker> uploadCheck;
	int retries = 5;
	String genus;
	
	ExtractMapper(MapFile mapfile, Predicate<Marker> uploadCheck, String genus) {
	    this.mapfile = mapfile;
	    this.uploadCheck = uploadCheck;
	    this.genus = genus;
	}
	
	@Override
	public void run() {
	    if(mapfile.lock.readLock().tryLock()) {
		try {
		    List<MarkerData> markers = mapfile.markers.stream().map(m -> {
			Coord mgc = new Coord(Math.floorDiv(m.tc.x, 100), Math.floorDiv(m.tc.y, 100));
			Indir<MapFile.Grid> indirGrid = mapfile.segments.get(m.seg).grid(mgc);
			return new MarkerData(m, indirGrid);
		    }).collect(Collectors.toList());

		    if(!submit(new ProcessMapper(mapfile, markers, genus), 15, TimeUnit.SECONDS))
			warn("Automap: could not queue marker processing.");
		} catch (Exception ex)
		{
		    warn("Automap: error while collecting markers: " + ex);
		    ex.printStackTrace(System.out);
		}
		mapfile.lock.readLock().unlock();
	    } else {
		if(retries-- > 0) {
		    log("map file is busy, retrying extraction in 5s (%d retries left)", retries);
		    submit(this, 5, TimeUnit.SECONDS);
		} else {
		    /* KamiClient: this used to just stop. */
		    warn("Automap: gave up waiting for the map file - no markers uploaded.");
		}
	    }
	}
    }
    
    private class MarkerData {
	Marker m;
	Indir<MapFile.Grid> indirGrid;
	
	MarkerData(Marker m, Indir<MapFile.Grid> indirGrid) {
	    this.m = m;
	    this.indirGrid = indirGrid;
	}
    }
    
    private class ProcessMapper implements Runnable {
	MapFile mapfile;
	List<MarkerData> markers;
	String genus;
	
	ProcessMapper(MapFile mapfile, List<MarkerData> markers, String genus) {
	    this.mapfile = mapfile;
	    this.markers = markers;
	    this.genus = genus;
	}
	
	@Override
	public void run() {
	    try
	    {
		ArrayList<JSONObject> loadedMarkers = new ArrayList<>();
		if (markers.isEmpty()) {
		    return;
		}

		List<Color> uploadColors = new LinkedList<>();
		CFG.AUTOMAP_MARKERS.get().forEach(g -> {
		    uploadColors.add(g.col);
		});

		/* KamiClient: account for every marker that does NOT make it
		 * into the upload. "collected 148 / scheduling 125" gave no clue
		 * where the other 23 went. */
		int skipNoGrid = 0, skipColor = 0, skipOther = 0;
		for (int i = 0; i < markers.size(); i++) {
		    try {
			MarkerData md = markers.get(i);
			if (md.indirGrid.get() == null) {
			    skipNoGrid++;
			    continue;
			}

			if (md.m instanceof PMarker)
			    if (!uploadColors.contains(((PMarker) md.m).color)) {
				skipColor++;
				continue;
			    }

			Coord mgc = new Coord(Math.floorDiv(md.m.tc.x, 100), Math.floorDiv(md.m.tc.y, 100));
			long gridId = md.indirGrid.get().id;
			JSONObject o = new JSONObject();
			o.put("name", md.m.nm);
			o.put("genus", genus);
			o.put("gridID", String.valueOf(gridId));
			Coord gridOffset = md.m.tc.sub(mgc.mul(100));
			o.put("x", gridOffset.x);
			o.put("y", gridOffset.y);
			if(md.m instanceof SMarker) {
			    o.put("type", "shared");
			    try {
				/* KamiClient: .longValue(), NOT the UID itself.
				 *
				 * oid became a UID when ender markers were made to
				 * extend the vanilla ones. UID extends Number, so
				 * org.json writes it unquoted via toString() - and
				 * UID.toString() is HEX. That put a bare
				 * `id: 3f2a9c4b1d8e` in the body, which is not valid
				 * JSON, so the server rejected the whole request with
				 * a 400 during model binding - before any of its own
				 * code ran, which is why nothing was logged. */
				o.put("id", ((SMarker) md.m).oid.longValue());
			    } catch (Exception ex)
			    {
				o.put("id", 0);
			    }
			    o.put("image", ((SMarker) md.m).res.name);
			} else if(md.m instanceof PMarker) {
			    o.put("type", "player");
			    o.put("color", ((PMarker) md.m).color);
			}
			loadedMarkers.add(o);
		    } catch (Loading ex) {
			log("marker %d/%d still loading (%s), retrying the whole batch in 5s",
			    i + 1, markers.size(), ex.getMessage());
			submit(this, 5, TimeUnit.SECONDS);
			return;
		    } catch (Exception ex) {
			/* Don't let one bad marker kill the whole batch. */
			skipOther++;
			log("marker %d/%d skipped: %s", i + 1, markers.size(), ex);
		    }
		}

		log("prepared %d of %d markers (skipped: %d no grid, %d filtered by colour, %d errors)",
		    loadedMarkers.size(), markers.size(), skipNoGrid, skipColor, skipOther);

		if(loadedMarkers.isEmpty()) {
		    warn("Automap: no markers qualified for upload.");
		    return;
		}
		if(dead()) {
		    warn("Automap: client was destroyed before the upload could be queued.");
		    return;
		}
		log("scheduling upload for %d markers to %s/markerUpdate", loadedMarkers.size(), endpoint);
		if(!submit(new MarkerUpdate(new JSONArray(loadedMarkers.toArray())))) {
		    /* KamiClient: this used to be a println of the rejection and
		     * nothing else, so a dropped marker upload looked identical
		     * to a successful one. */
		    warn(String.format("Marker upload failed: could not queue %d markers.", loadedMarkers.size()));
		}
	    }
	    catch (Exception ex)
	    {
		warn("Automap: error while processing markers: " + ex);
		ex.printStackTrace(System.out);
	    }
	}
    }
    
    private class MarkerUpdate implements Runnable {
	JSONArray data;
	
	MarkerUpdate(JSONArray data) {
	    this.data = data;
	}
	
	@Override
	public void run() {
	    String url = endpoint + "/markerUpdate";
	    long t0 = System.currentTimeMillis();
	    try {
		final String json = data.toString();
		log("POST %s (%d markers, %d bytes)", url, data.length(), json.length());
		HttpURLConnection connection =
		    (HttpURLConnection) new URL(url).openConnection();
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
		connection.setConnectTimeout(15000);
		connection.setReadTimeout(30000);
		connection.setDoOutput(true);
		try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
		    out.write(json.getBytes(StandardCharsets.UTF_8));
		}
		int code = connection.getResponseCode();
		long ms = System.currentTimeMillis() - t0;
		/* KamiClient: the response code was read and thrown away, so a
		 * server-side rejection was indistinguishable from a successful
		 * upload. Read the body too - that is where the server explains
		 * itself. */
		if(code < 200 || code >= 300) {
		    warn(String.format("Marker upload rejected: HTTP %d %s after %dms",
				       code, connection.getResponseMessage(), ms));
		    log("server said: %s", readbody(connection, true));
		} else {
		    log("marker upload OK: HTTP %d in %dms, %d markers accepted", code, ms, data.length());
		}
		connection.disconnect();
	    } catch (Exception ex) {
		warn(String.format("Marker upload failed after %dms: %s",
				   System.currentTimeMillis() - t0, ex));
		ex.printStackTrace(System.out);
	    }
	}
    }

    private class PositionUpdates implements Runnable {
	private class Tracking {
	    public String name;
	    public String genus;
	    public String type;
	    public long gridId;
	    public Coord2d coords;
	    
	    public JSONObject getJSON() {
		JSONObject j = new JSONObject();
		j.put("name", name);
		j.put("genus", genus);
		j.put("type", type);
		j.put("gridID", String.valueOf(gridId));
		JSONObject c = new JSONObject();
		c.put("x", (int) (coords.x / 11));
		c.put("y", (int) (coords.y / 11));
		j.put("coords", c);
		return j;
	    }
	}
	
	private Map<Long, Tracking> tracking = new ConcurrentHashMap<Long, Tracking>();
	
	private PositionUpdates() {
	}
	
	private void Track(long id, Coord2d coordinates, long gridId, String genus) {
	    Tracking t = tracking.get(id);
	    if(t == null) {
		t = new Tracking();
		tracking.put(id, t);
		
		if(id == glob.sess.ui.gui.map.plgob) {
		    t.name = playerName;
		    t.type = "player";
		} else {
		    Glob g = glob;
		    Gob gob = g.oc.getgob(id);
		    t.name = "???";
		    t.type = "white";
		}
	    }
	    t.genus = genus;
	    t.gridId = gridId;
	    t.coords = gridOffset(coordinates);
	}
	
	@Override
	public void run() {
	    if(trackingEnabled) {
		Glob g = glob;
		Iterator<Map.Entry<Long, Tracking>> i = tracking.entrySet().iterator();
		JSONObject upload = new JSONObject();
		while (i.hasNext()) {
		    Map.Entry<Long, Tracking> e = i.next();
		    if(g.oc.getgob(e.getKey()) == null) {
			i.remove();
		    } else {
			upload.put(String.valueOf(e.getKey()), e.getValue().getJSON());
		    }
		}
		
		try {
		    final HttpURLConnection connection =
			(HttpURLConnection) new URL(endpoint + "/positionUpdate").openConnection();
		    connection.setRequestMethod("POST");
		    connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
		    connection.setConnectTimeout(15000);
		    connection.setReadTimeout(15000);
		    connection.setDoOutput(true);
		    try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
			final String json = upload.toString();
			out.write(json.getBytes(StandardCharsets.UTF_8));
		    } catch (Exception e) {
			log("positionUpdate: failed writing body: %s", e);
		    }
		    int code = connection.getResponseCode();
		    /* Position updates run every 5s, so only complain when they
		     * actually fail - otherwise this would flood the console. */
		    if(code < 200 || code >= 300)
			log("positionUpdate -> HTTP %d: %s", code, readbody(connection, true));
		    connection.disconnect();
		} catch (final Exception ex) {
		    log("positionUpdate FAILED: %s", ex);
		}
	    }
	}
    }
    
    private static class GridUpdate {
	String[][] grids;
	Map<String, WeakReference<MCache.Grid>> gridRefs;
	
	GridUpdate(final String[][] grids, Map<String, WeakReference<MCache.Grid>> gridRefs) {
	    this.grids = grids;
	    this.gridRefs = gridRefs;
	}
	
	@Override
	public String toString() {
	    return String.format("GridUpdate (%s)", grids[1][1]);
	}
    }
    
    private class GenerateGridUpdateTask implements Runnable {
	Coord coord;
	String genus;
	int retries = 3;
	
	GenerateGridUpdateTask(Coord c, String genus) {
	    this.coord = c;
	    this.genus = genus;
	}
	
	@Override
	public void run() {
	    if(gridEnabled) {
		final String[][] gridMap = new String[3][3];
		Map<String, WeakReference<MCache.Grid>> gridRefs = new HashMap<String, WeakReference<MCache.Grid>>();
		try {
		    for (int x = -1; x <= 1; x++) {
			for (int y = -1; y <= 1; y++) {
			    final MCache.Grid subg = glob.map.getgrid(coord.add(x, y));
			    gridMap[x + 1][y + 1] = String.valueOf(subg.id);
			    gridRefs.put(String.valueOf(subg.id), new WeakReference<MCache.Grid>(subg));
			}
		    }
		    submit(new UploadGridUpdateTask(new GridUpdate(gridMap, gridRefs), genus));
		} catch (LoadingMap lm) {
		    retries--;
		    if(retries >= 0) {
			submit(this, 1L, TimeUnit.SECONDS);
		    }
		} catch (Exception e) {
		    System.out.println(e);
		}
		;
	    }
	}
    }
    
    private class UploadGridUpdateTask implements Runnable {
	private final GridUpdate gridUpdate;
	private final String genus;
	UploadGridUpdateTask(final GridUpdate gridUpdate, String genus) {
	    this.gridUpdate = gridUpdate;
	    this.genus = genus;
	}
	
	@Override
	public void run() {
	    if(gridEnabled) {
		HashMap<String, Object> dataToSend = new HashMap<>();
		
		dataToSend.put("grids", this.gridUpdate.grids);
		dataToSend.put("genus", this.genus);
		try {
		    HttpURLConnection connection =
			(HttpURLConnection) new URL(endpoint + "/gridUpdate").openConnection();
		    connection.setRequestMethod("POST");
		    connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
		    connection.setDoOutput(true);
		    try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
			String json = new JSONObject(dataToSend).toString();
			out.write(json.getBytes(StandardCharsets.UTF_8));
		    }
		    int gcode = connection.getResponseCode();
		    if(gcode != 200)
			log("gridUpdate -> HTTP %d: %s", gcode, readbody(connection, true));
		    if(gcode == 200) {
			DataInputStream dio = new DataInputStream(connection.getInputStream());
			int nRead;
			byte[] data = new byte[1024];
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			while ((nRead = dio.read(data, 0, data.length)) != -1) {
			    buffer.write(data, 0, nRead);
			}
			buffer.flush();
			String response = buffer.toString(StandardCharsets.UTF_8.name());
			JSONObject jo = new JSONObject(response);
			JSONArray reqs = jo.optJSONArray("gridRequests");
			synchronized (cache) {
			    cache.put(Long.valueOf(gridUpdate.grids[1][1]), new MapRef(jo.getLong("map"), new Coord(jo.getJSONObject("coords").getInt("x"), jo.getJSONObject("coords").getInt("y"))));
			}
			for (int i = 0; reqs != null && i < reqs.length(); i++) {
			    gridsUploader.execute(new GridUploadTask(reqs.getString(i), gridUpdate.gridRefs.get(reqs.getString(i)), genus));
			}
			try {
			    JSONArray reqs2 = jo.optJSONArray("gridOverlayRequests");
			    for (int i = 0; reqs2 != null && i < reqs2.length(); i++) {
				gridsUploader.execute(new GridOverlayUploadTask(reqs2.getString(i), gridUpdate.gridRefs.get(reqs2.getString(i)), genus));
			    }
			}
			catch (Exception ex) {
			    log("gridUpdate: overlay request handling failed: %s", ex);
			}
		    }

		} catch (Exception ex) {
		    log("gridUpdate FAILED: %s", ex);
		    ex.printStackTrace(System.out);
		}
	    }
	}
    }
    
    private class GridUploadTask implements Runnable {
	private final String gridID;
	private final WeakReference<MCache.Grid> grid;
	private final String genus;
	
	GridUploadTask(String gridId, WeakReference<MCache.Grid> grid, String genus) {
	    this.gridID = gridId;
	    this.grid = grid;
	    this.genus = genus;
	}
	
	@Override
	public void run() {
	    try {
		MCache.Grid g = grid.get();
		if(g != null && glob != null && glob.map != null) {
		    BufferedImage image = MinimapImageGenerator.drawmap(glob.map, g);
		    if(image == null) {
			return;
		    }
		    try {
			JSONObject extraData = new JSONObject();
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ImageIO.write(image, "png", outputStream);
			ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
			MultipartUtility multipart = new MultipartUtility(endpoint + "/gridUpload", "utf-8");
			multipart.addFormField("id", this.gridID);
			multipart.addFormField("genus", this.genus);
			multipart.addFilePart("file", inputStream, "minimap.png");
			extraData.put("season", glob.ast.is);
			multipart.addFormField("extraData", extraData.toString());
			MultipartUtility.Response response = multipart.finish();
			if(response.statusCode != 200) {
			    System.out.println("Upload Error: Code" + response.statusCode + " - " + response.response);
			}
		    } catch (IOException e) {
			System.out.println("Cannot upload " + gridID + ": " + e.getMessage());
		    }
		}
	    } catch (Loading ex) {
		// Retry on Loading
		gridsUploader.submit(this);
	    }
	    
	}
    }
    
    private class GridOverlayUploadTask implements Runnable {
	private final String gridID;
	private final WeakReference<MCache.Grid> grid;
	private final String genus;
	
	GridOverlayUploadTask(String gridID, WeakReference<MCache.Grid> grid, String genus) {
	    this.gridID = gridID;
	    this.grid = grid;
	    this.genus = genus;
	}
	
	@Override
	public void run() {
	    try {
		MCache.Grid g = grid.get();
		if(g != null && glob != null && glob.map != null) {
		    BufferedImage image = MinimapImageGenerator.drawoverlay(glob.map, g);
		    if(image == null) {
			return;
		    }
		    try {
			JSONObject extraData = new JSONObject();
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ImageIO.write(image, "png", outputStream);
			ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
			MultipartUtility multipart = new MultipartUtility(endpoint + "/gridOverlayUpload", "utf-8");
			multipart.addFormField("id", this.gridID);
			multipart.addFormField("genus", this.genus);
			multipart.addFilePart("file", inputStream, "minimap.png");
			extraData.put("season", glob.ast.is);
			multipart.addFormField("extraData", extraData.toString());
			MultipartUtility.Response response = multipart.finish();
			if(response.statusCode != 200) {
			    System.out.println("Upload Error: Code" + response.statusCode + " - " + response.response);
			}
		    } catch (IOException e) {
			System.out.println("Cannot upload " + gridID + ": " + e.getMessage());
		    }
		}
	    } catch (Loading ex) {
		// Retry on Loading
		gridsUploader.submit(this);
	    }
	    
	}
    }
    
    private static Coord toGC(Coord2d c) {
	return new Coord(Math.floorDiv((int) c.x, 1100), Math.floorDiv((int) c.y, 1100));
    }
    
    private static Coord toGridUnit(Coord2d c) {
	return new Coord(Math.floorDiv((int) c.x, 1100) * 1100, Math.floorDiv((int) c.y, 1100) * 1100);
    }
    
    private static Coord2d gridOffset(Coord2d c) {
	Coord gridUnit = toGridUnit(c);
	return new Coord2d(c.x - gridUnit.x, c.y - gridUnit.y);
    }
    
    public class MapRef {
	public Coord gc;
	public long mapID;
	
	private MapRef(long mapID, Coord gc) {
	    this.gc = gc;
	    this.mapID = mapID;
	}
	
	public String toString() {
	    return (gc.toString() + " in map space " + mapID);
	}
    }
    
    private class UploadInspectResult implements Runnable {
	Coord2d coord;
	long gridId;
	String inspectResult;
	String genus;
	
	UploadInspectResult(long gridId, Coord2d coords, String inspectResult, String genus) {
	    this.coord = gridOffset(coords);
	    this.gridId = gridId;
	    this.inspectResult = inspectResult;
	    this.genus = genus;
	}
	
	public JSONObject getJSON() {
	    JSONObject j = new JSONObject();
	    j.put("genus", genus);
	    j.put("inspectResult", inspectResult);
	    j.put("gridId", String.valueOf(gridId));
	    JSONObject c = new JSONObject();
	    c.put("x", (int) (coord.x / 11));
	    c.put("y", (int) (coord.y / 11));
	    j.put("coords", c);
	    return j;
	}
	
	@Override
	public void run() {
	    if (trackingEnabled)
	    {
		try {
		    HttpURLConnection connection =
			(HttpURLConnection) new URL(endpoint + "/inspectUpdate").openConnection();
		    connection.setRequestMethod("POST");
		    connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
		    connection.setDoOutput(true);
		    try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
			String json = getJSON().toString();
			out.write(json.getBytes(StandardCharsets.UTF_8));
		    } catch (IOException e) {
			log("inspectUpdate: failed writing body: %s", e);
		    }
		    int code = connection.getResponseCode();
		    if(code < 200 || code >= 300)
			log("inspectUpdate -> HTTP %d: %s", code, readbody(connection, true));
		    connection.disconnect();
		} catch (Exception ex)
		{
		    log("Cannot upload inspect result: %s", ex);
		}
		
	    }
	}
    }
    
    public void UploadCacheGrid(String genus, long segmentId, ArrayList<GridInfo> grids)
    {
	try {
	    HttpURLConnection connection =
		(HttpURLConnection) new URL(endpoint + "/gridCacheUpdate").openConnection();
	    connection.setRequestMethod("POST");
	    connection.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
	    connection.setDoOutput(true);
	    JSONObject o = new JSONObject();
	    o.put("genus", genus);
	    o.put("segmentId", segmentId);
	    o.put("grids", grids);
	    try (DataOutputStream out = new DataOutputStream(connection.getOutputStream())) {
		out.write(o.toString().getBytes(StandardCharsets.UTF_8));
	    }
	    int code = connection.getResponseCode();
	    if (code != 200) {
		log("gridCacheUpdate -> HTTP %d: %s", code, readbody(connection, true));
		throw new Exception("Couldn't upload Cache. Errorcode: " + code);
	    }
	    connection.disconnect();
	}
	catch (Exception ex)
	{
	    log("gridCacheUpdate FAILED: %s", ex.getMessage());
	}
    }
    
    public void UploadCacheImage(long gridId, String genus, BufferedImage image)
    {
	if(image == null) {
	    return;
	}
	try {
	    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	    ImageIO.write(image, "png", outputStream);
	    ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
	    MultipartUtility multipart = new MultipartUtility(endpoint + "/gridCacheUpload", "utf-8");
	    multipart.addFormField("id", Long.toString(gridId));
	    multipart.addFormField("genus", genus);
	    multipart.addFilePart("file", inputStream, "minimap.png");
	    JSONObject extraData = new JSONObject();
	    extraData.put("season", 0);
	    multipart.addFormField("extraData", extraData.toString());
	    MultipartUtility.Response response = multipart.finish();
	    if(response.statusCode != 200) {
		System.out.println("Upload Error: Code" + response.statusCode + " - " + response.response);
	    }
	} catch (Exception e) {
	    System.out.println("Cannot upload " + gridId + ": " + e.getMessage());
	}
    }
    
    public void UploadCacheOverlayImage(long gridId, String genus, BufferedImage image)
    {
	if(image == null) {
	    return;
	}
	try {
	    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	    ImageIO.write(image, "png", outputStream);
	    ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
	    MultipartUtility multipart = new MultipartUtility(endpoint + "/gridCacheOverlayUpload", "utf-8");
	    multipart.addFormField("id", Long.toString(gridId));
	    multipart.addFormField("genus", genus);
	    multipart.addFilePart("file", inputStream, "minimap.png");
	    JSONObject extraData = new JSONObject();
	    extraData.put("season", 0);
	    multipart.addFormField("extraData", extraData.toString());
	    MultipartUtility.Response response = multipart.finish();
	    if(response.statusCode != 200) {
		System.out.println("Upload Error: Code" + response.statusCode + " - " + response.response);
	    }
	} catch (Exception e) {
	    System.out.println("Cannot upload " + gridId + ": " + e.getMessage());
	}
    }
}