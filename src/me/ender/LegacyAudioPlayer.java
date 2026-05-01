package me.ender;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.InputStream;

public class LegacyAudioPlayer {
    private static final Object lock = new Object();
    private static PlayerThread current;

    public static boolean play(String name, boolean loop, double volume) {
	if(LegacyAudioPlayer.class.getResource("/sfx/legacy/" + name + ".ogg") == null) {
	    System.out.println("[LegacyAudio] not found: " + name + ".ogg");
	    return false;
	}
	synchronized(lock) {
	    if(current != null && name.equals(current.name) && current.isAlive()) {
		System.out.println("[LegacyAudio] " + name + " already playing, skipping");
		return false;
	    }
	}
	stop();
	PlayerThread t = new PlayerThread(name, loop, volume);
	synchronized(lock) { current = t; }
	t.start();
	return true;
    }

    public static boolean isPlaying(String name) {
	synchronized(lock) {
	    return current != null && current.isAlive() && name.equals(current.name);
	}
    }

    public static void stop() {
	PlayerThread t;
	synchronized(lock) { t = current; current = null; }
	if(t != null) {
	    t.cancel();
	    try { t.join(500); } catch(InterruptedException ignore) {}
	}
    }

    public static void setVolume(double volume) {
	synchronized(lock) {
	    if(current != null) current.setVolume(volume);
	}
    }

    private static class PlayerThread extends Thread {
	private final String name;
	private final boolean loop;
	private volatile boolean cancelled = false;
	private volatile double volume;
	private volatile SourceDataLine line;

	PlayerThread(String name, boolean loop, double volume) {
	    super("LegacyAudio " + name);
	    setDaemon(true);
	    this.name = name;
	    this.loop = loop;
	    this.volume = volume;
	}

	void cancel() {
	    cancelled = true;
	    SourceDataLine l = line;
	    if(l != null) {
		try { l.stop(); } catch(Exception ignore) {}
		try { l.flush(); } catch(Exception ignore) {}
	    }
	}

	void setVolume(double v) {
	    this.volume = v;
	    SourceDataLine l = line;
	    if(l != null) applyGain(l, v);
	}

	public void run() {
	    try {
		do {
		    decodeOnce();
		} while(loop && !cancelled);
	    } catch(Throwable t) {
		System.out.println("[LegacyAudio] error playing " + name + ": " + t);
	    } finally {
		SourceDataLine l = line;
		if(l != null) {
		    try { l.drain(); } catch(Exception ignore) {}
		    try { l.close(); } catch(Exception ignore) {}
		    line = null;
		}
		synchronized(lock) {
		    if(current == this) current = null;
		}
	    }
	}

	private void decodeOnce() throws Exception {
	    InputStream raw = LegacyAudioPlayer.class.getResourceAsStream("/sfx/legacy/" + name + ".ogg");
	    if(raw == null) return;
	    try(InputStream in = new BufferedInputStream(raw)) {
		SyncState oy = new SyncState();
		StreamState os = new StreamState();
		Page og = new Page();
		Packet op = new Packet();
		Info vi = new Info();
		Comment vc = new Comment();
		DspState vd = new DspState();
		Block vb = new Block(vd);

		oy.init();

		int index = oy.buffer(4096);
		int bytes = in.read(oy.data, index, 4096);
		oy.wrote(bytes);

		if(oy.pageout(og) != 1) {
		    if(bytes < 4096) return;
		    System.out.println("[LegacyAudio] not an Ogg stream: " + name);
		    return;
		}

		os.init(og.serialno());
		vi.init();
		vc.init();
		if(os.pagein(og) < 0) return;
		if(os.packetout(op) != 1) return;
		if(vi.synthesis_headerin(vc, op) < 0) return;

		int hdrs = 1;
		while(hdrs < 3 && !cancelled) {
		    int result = oy.pageout(og);
		    if(result == 0) {
			index = oy.buffer(4096);
			bytes = in.read(oy.data, index, 4096);
			if(bytes == 0) return;
			oy.wrote(bytes);
			continue;
		    }
		    if(result == 1) {
			os.pagein(og);
			while(hdrs < 3) {
			    int pr = os.packetout(op);
			    if(pr == 0) break;
			    if(pr == -1) return;
			    vi.synthesis_headerin(vc, op);
			    hdrs++;
			}
		    }
		}

		int convsize = 4096 / vi.channels;
		byte[] convbuffer = new byte[convsize * vi.channels * 2];

		AudioFormat fmt = new AudioFormat(vi.rate, 16, vi.channels, true, false);
		DataLine.Info linfo = new DataLine.Info(SourceDataLine.class, fmt);
		SourceDataLine l = (SourceDataLine) AudioSystem.getLine(linfo);
		l.open(fmt);
		applyGain(l, volume);
		l.start();
		this.line = l;

		vd.synthesis_init(vi);
		vb.init(vd);

		float[][][] _pcm = new float[1][][];
		int[] _index = new int[vi.channels];
		int eos = 0;

		while(eos == 0 && !cancelled) {
		    while(eos == 0 && !cancelled) {
			int result = oy.pageout(og);
			if(result == 0) break;
			if(result == -1) continue;
			os.pagein(og);
			while(!cancelled) {
			    int pr = os.packetout(op);
			    if(pr == 0) break;
			    if(pr == -1) continue;
			    int samples;
			    if(vb.synthesis(op) == 0) vd.synthesis_blockin(vb);
			    while((samples = vd.synthesis_pcmout(_pcm, _index)) > 0 && !cancelled) {
				float[][] pcm = _pcm[0];
				int bout = Math.min(samples, convsize);
				for(int ch = 0; ch < vi.channels; ch++) {
				    int ptr = ch * 2;
				    int mono = _index[ch];
				    for(int j = 0; j < bout; j++) {
					int val = (int)(pcm[ch][mono + j] * 32767.0);
					if(val > 32767) val = 32767;
					if(val < -32768) val = -32768;
					convbuffer[ptr] = (byte) val;
					convbuffer[ptr + 1] = (byte)(val >>> 8);
					ptr += 2 * vi.channels;
				    }
				}
				int outBytes = 2 * vi.channels * bout;
				int written = 0;
				while(written < outBytes && !cancelled) {
				    int w = l.write(convbuffer, written, outBytes - written);
				    if(w <= 0) break;
				    written += w;
				}
				vd.synthesis_read(bout);
			    }
			}
			if(og.eos() != 0) eos = 1;
		    }
		    if(eos == 0 && !cancelled) {
			index = oy.buffer(4096);
			bytes = in.read(oy.data, index, 4096);
			oy.wrote(bytes);
			if(bytes == 0) eos = 1;
		    }
		}

		try { l.drain(); } catch(Exception ignore) {}
		os.clear();
		vb.clear();
		vd.clear();
		vi.clear();
		oy.clear();
	    } finally {
		SourceDataLine l = line;
		if(l != null) {
		    try { l.close(); } catch(Exception ignore) {}
		    line = null;
		}
	    }
	}

	private static void applyGain(SourceDataLine line, double volume) {
	    if(line == null) return;
	    try {
		if(line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
		    FloatControl c = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
		    double v = Math.max(0.0001, Math.min(1.0, volume));
		    float db = (float)(20.0 * Math.log10(v));
		    db = Math.max(c.getMinimum(), Math.min(c.getMaximum(), db));
		    c.setValue(db);
		} else if(line.isControlSupported(FloatControl.Type.VOLUME)) {
		    FloatControl c = (FloatControl) line.getControl(FloatControl.Type.VOLUME);
		    float v = (float)Math.max(c.getMinimum(), Math.min(c.getMaximum(), volume * c.getMaximum()));
		    c.setValue(v);
		}
	    } catch(Exception ignore) {}
	}
    }
}
