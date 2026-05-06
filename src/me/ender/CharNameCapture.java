package me.ender;

import haven.Config;
import haven.TextEntry;
import haven.Widget;
import haven.Window;

// New chars spawn with an empty chrid until they pick a name at the wizard.
// Watch the wizard's TextEntry, stash it, and commit on ON_DESTROY via
// Config.setPlayerName so Reactor.PLAYER fires for everyone downstream.
public class CharNameCapture {
    private static volatile String pending;

    public static String pending() {return pending;}

    public static void clear() {pending = null;}

    // Namechange (server class) never fires ON_PACK, and its TextEntry isn't
    // there at install time — Watcher resolves it lazily on first tick.
    public static void install(Window wnd) {
	wnd.add(new Watcher(wnd));
    }

    public static boolean promote() {
	String name = pending;
	if(name == null || name.isEmpty()) return false;
	String existing = Config.getPlayerName();
	if(existing != null && !existing.isEmpty()) return false;
	Config.setPlayerName(name);
	pending = null;
	return true;
    }

    private static class Watcher extends Widget {
	private final Window wnd;
	private TextEntry src;
	private String last = "";

	Watcher(Window wnd) {
	    super();
	    this.wnd = wnd;
	    hide();
	}

	@Override
	public void tick(double dt) {
	    super.tick(dt);
	    if(src == null || src.disposed()) {
		src = wnd.getchild(TextEntry.class);
		if(src == null) return;
	    }
	    String cur = src.text();
	    if(cur == null) return;
	    if(!cur.equals(last)) {
		last = cur;
		pending = cur.trim().isEmpty() ? null : cur.trim();
	    }
	}
    }
}
