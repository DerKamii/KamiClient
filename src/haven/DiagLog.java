package haven;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/* KamiClient: append-only log for "this should not have happened, but crashing
 * over it would be worse" conditions. Goes to logs/ so users have one place to
 * look, and tells them once in chat that there is something to send us.
 *
 * The point is that a silent suppression teaches us nothing: if a condition
 * never fires we can drop the check, and if it fires constantly we know where
 * to look. Neither is knowable while it fails quietly. */
public class DiagLog {
    private static final Object lock = new Object();
    private static final SimpleDateFormat stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /* One chat notice per kind per session - these can fire in a loop and
     * nobody needs their chat filled with it. */
    private static final java.util.Set<String> told = new java.util.HashSet<>();

    public static void log(String kind, String fmt, Object... args) {
	String line = String.format(fmt, args);
	boolean first;
	synchronized(lock) {
	    first = told.add(kind);
	    File dir = new File("logs");
	    if(dir.isDirectory() || dir.mkdirs()) {
		File f = new File(dir, kind + ".log");
		try(PrintWriter w = new PrintWriter(new FileWriter(f, true))) {
		    synchronized(stamp) {
			w.printf("%s %s%n", stamp.format(new Date()), line);
		    }
		} catch(IOException e) {
		    /* Nothing useful to do - do not let logging break the caller. */
		}
	    }
	}
	if(first) {
	    Warning.warn("%s: %s", kind, line);
	    notice(kind);
	}
    }

    /* GameUI registers itself here so code down in haven.render, which has no
     * widget to talk through, can still get a word to the player. */
    private static volatile GameUI gui = null;
    public static void setgui(GameUI gui) {DiagLog.gui = gui;}
    public static void clrgui(GameUI gui) {if(DiagLog.gui == gui) DiagLog.gui = null;}

    /* Tell the player once, if there is a GameUI to tell. */
    private static void notice(String kind) {
	try {
	    GameUI gui = DiagLog.gui;
	    if(gui != null) {
		gui.msg(String.format("Something unexpected happened (%s). There's a file in the logs " +
				      "folder - please send it to the developer.", kind),
			GameUI.MsgType.BAD);
	    }
	} catch(Exception e) {
	    /* Diagnostics must never be the thing that breaks. */
	}
    }
}
