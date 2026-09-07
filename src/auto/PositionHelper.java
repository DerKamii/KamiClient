package auto;

import haven.Coord2d;
import haven.GameUI;
import haven.Gob;

import java.util.Comparator;
import java.util.concurrent.CompletableFuture;

public class PositionHelper {
    public static Comparator<Gob> byDistanceToPlayer = (o1, o2) -> {
	try {
	    Gob p = o1.glob.oc.getgob(o1.glob.sess.ui.gui.plid);
	    return Double.compare(p.rc.dist(o1.rc), p.rc.dist(o2.rc));
	} catch (Exception ignored) {}
	return Long.compare(o1.id, o2.id);
    };
    
    static CompletableFuture<Coord2d> mapPosOfMouse(GameUI gui) {
	return gui.map.hit(gui.ui.mc);
    }
    
    static double distanceToPlayer(Gob gob) {
	//KamiClient: the player gob can be missing from the cache for a moment - during a combat
	//relation update, on load, while it's being replaced. Callers all use this as a range check,
	//so answer "infinitely far" and let them skip instead of taking the client down.
	GameUI gui = gob.glob.sess.ui.gui;
	if(gui == null) {return Double.MAX_VALUE;}
	Gob p = gob.glob.oc.getgob(gui.plid);
	if(p == null) {return Double.MAX_VALUE;}
	return p.rc.dist(gob.rc);
    }
    
    static double distanceToCoord(Coord2d c, Gob gob) {
	if(c == null) {return Double.MAX_VALUE;}
	return c.dist(gob.rc);
    }
}
