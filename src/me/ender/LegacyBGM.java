package me.ender;

import haven.Area;
import haven.Astronomy;
import haven.CFG;
import haven.Composite;
import haven.Coord;
import haven.Coord2d;
import haven.Glob;
import haven.Gob;
import haven.MCache;
import haven.MapView;
import haven.Resource;

import java.util.List;

public class LegacyBGM {
    private static final long COOLDOWN_MS = 10 * 60 * 1000L;
    private static final long FISHING_COOLDOWN_MS = 5 * 60 * 1000L;
    private static final long TICK_THROTTLE_MS = 1000L;

    private static boolean chrPlayed = false;
    private static boolean wasIndoors = false;
    private static boolean wasInCave = false;
    private static Boolean wasOnOwnLand = null;
    private static Boolean wasNight = null;
    private static Boolean wasSitting = null;
    private static long lastCabinAt = 0;
    private static long lastCaveAt = 0;
    private static long lastCmbAt = 0;
    private static long lastTravelAt = 0;
    private static long lastRestingAt = 0;
    private static long lastFishingAt = 0;
    private static long lastTickCheck = 0;
    private static String lastTileLogged = null;
    private static String lastPoseLogged = null;

    public static void onGameStart() {
	if(chrPlayed) return;
	chrPlayed = true;
	if(!CFG.LEGACY_BGM_ENABLED.get()) return;
	System.out.println("[LegacyBGM] game start -> playing chr");
	LegacyAudioPlayer.play("chr", false, CFG.LEGACY_BGM_VOLUME.get());
    }

    public static void onEnterGame() {
	System.out.println("[LegacyBGM] entered game session -> stopping chr");
	LegacyAudioPlayer.stop();
    }

    public static void onPaginaUse(String resname) {
	if(resname == null) return;
	if(!CFG.LEGACY_BGM_ENABLED.get()) return;
	if(!resname.equals("paginae/act/fish")) return;
	long now = System.currentTimeMillis();
	if(!cooldownPassed(lastFishingAt, FISHING_COOLDOWN_MS, now)) {
	    System.out.println("[LegacyBGM] fishing on cooldown, skipping (" + resname + ")");
	    return;
	}
	System.out.println("[LegacyBGM] fishing action: " + resname);
	if(LegacyAudioPlayer.play("fishing", false, CFG.LEGACY_BGM_VOLUME.get())) {
	    lastFishingAt = now;
	    System.out.println("[LegacyBGM] -> playing fishing");
	}
    }

    private static boolean cooldownPassed(long lastAt, long cooldownMs, long now) {
	if(CFG.LEGACY_BGM_NO_COOLDOWN.get()) return true;
	return now - lastAt >= cooldownMs;
    }

    public static void tick(MapView mv) {
	if(!CFG.LEGACY_BGM_ENABLED.get()) return;
	if(mv == null) return;
	long now = System.currentTimeMillis();
	if(now - lastTickCheck < TICK_THROTTLE_MS) return;
	lastTickCheck = now;

	Gob pl;
	try {
	    pl = mv.player();
	} catch(Exception e) {
	    return;
	}
	if(pl == null || pl.rc == null) return;

	String tileName = currentTileName(mv.glob.map, pl.rc);
	if(tileName != null && !tileName.equals(lastTileLogged)) {
	    System.out.println("[LegacyBGM] tile under player: " + tileName);
	    lastTileLogged = tileName;
	}

	boolean inCave = isCaveName(tileName);
	boolean indoors = !inCave && isIndoorsName(tileName);

	if(indoors && !wasIndoors) {
	    System.out.println("[LegacyBGM] outdoor->indoor edge, tile=" + tileName);
	    if(cooldownPassed(lastCabinAt, COOLDOWN_MS, now)) {
		if(LegacyAudioPlayer.play("cabin", false, CFG.LEGACY_BGM_VOLUME.get())) {
		    lastCabinAt = now;
		    System.out.println("[LegacyBGM] -> playing cabin");
		}
	    } else {
		System.out.println("[LegacyBGM] cabin on cooldown, skipping");
	    }
	}
	if(inCave && !wasInCave) {
	    System.out.println("[LegacyBGM] entered cave, tile=" + tileName);
	    if(cooldownPassed(lastCaveAt, COOLDOWN_MS, now)) {
		if(LegacyAudioPlayer.play("cave", false, CFG.LEGACY_BGM_VOLUME.get())) {
		    lastCaveAt = now;
		    System.out.println("[LegacyBGM] -> playing cave");
		}
	    } else {
		System.out.println("[LegacyBGM] cave on cooldown, skipping");
	    }
	}

	Boolean onOwnLand = isOnOwnLand(mv.glob.map, pl.rc);
	if(onOwnLand != null) {
	    if(wasOnOwnLand != null && wasOnOwnLand && !onOwnLand && !indoors && !inCave) {
		System.out.println("[LegacyBGM] left own claim/village");
		if(cooldownPassed(lastCmbAt, COOLDOWN_MS, now)) {
		    if(LegacyAudioPlayer.play("cmb", false, CFG.LEGACY_BGM_VOLUME.get())) {
			lastCmbAt = now;
			System.out.println("[LegacyBGM] -> playing cmb");
		    }
		} else {
		    System.out.println("[LegacyBGM] cmb on cooldown, skipping");
		}
	    }
	    wasOnOwnLand = onOwnLand;
	}

	Boolean night = currentNight(mv.glob);
	if(night != null) {
	    boolean outdoors = !indoors && !inCave;
	    if(wasNight != null && !wasNight && night && outdoors) {
		System.out.println("[LegacyBGM] dusk fell while outdoors");
		if(cooldownPassed(lastTravelAt, COOLDOWN_MS, now)) {
		    if(LegacyAudioPlayer.play("travel", false, CFG.LEGACY_BGM_VOLUME.get())) {
			lastTravelAt = now;
			System.out.println("[LegacyBGM] -> playing travel");
		    }
		} else {
		    System.out.println("[LegacyBGM] travel on cooldown, skipping");
		}
	    }
	    wasNight = night;
	}

	List<String> poses = currentPoses(pl);
	if(poses != null) {
	    String poseStr = String.join(",", poses);
	    if(!poseStr.equals(lastPoseLogged)) {
		System.out.println("[LegacyBGM] poses: " + poseStr);
		lastPoseLogged = poseStr;
	    }
	    boolean sitting = isSittingPose(poses);
	    if(wasSitting != null && !wasSitting && sitting) {
		System.out.println("[LegacyBGM] started sitting");
		if(cooldownPassed(lastRestingAt, COOLDOWN_MS, now)) {
		    if(LegacyAudioPlayer.play("resting", false, CFG.LEGACY_BGM_VOLUME.get())) {
			lastRestingAt = now;
			System.out.println("[LegacyBGM] -> playing resting");
		    }
		} else {
		    System.out.println("[LegacyBGM] resting on cooldown, skipping");
		}
	    }
	    wasSitting = sitting;
	}

	wasIndoors = indoors;
	wasInCave = inCave;
    }

    private static List<String> currentPoses(Gob pl) {
	try {
	    if(pl.drawable instanceof Composite) {
		return ((Composite) pl.drawable).getPoses();
	    }
	} catch(Exception ignore) {}
	return null;
    }

    private static boolean isSittingPose(List<String> poses) {
	for(String p : poses) {
	    String n = p.toLowerCase();
	    if(n.contains("sit")) return true;
	}
	return false;
    }

    private static Boolean currentNight(Glob glob) {
	try {
	    Astronomy ast = glob.ast;
	    return (ast != null) ? ast.night : null;
	} catch(Exception e) {
	    return null;
	}
    }

    private static String currentTileName(MCache map, Coord2d rc) {
	try {
	    Coord tc = rc.floor(MCache.tilesz);
	    int t = map.gettile(tc);
	    Resource r = map.tilesetr(t);
	    return (r != null) ? r.name : null;
	} catch(Exception e) {
	    return null;
	}
    }

    private static final String[] INDOOR_TILES = {
	"gfx/tiles/boards",
	"gfx/tiles/clayfloor",
	"gfx/tiles/stonefloor",
	"gfx/tiles/clayslab",
	"gfx/tiles/bloodstone",
	"gfx/tiles/linoleum",
    };

    private static boolean isIndoorsName(String name) {
	if(name == null) return false;
	String n = name.toLowerCase();
	if(n.contains("floor")) return true;
	for(String t : INDOOR_TILES) {
	    if(n.equals(t)) return true;
	}
	return false;
    }

    private static final String[] CAVE_TILES = {
	"gfx/tiles/cave",
	"gfx/tiles/cavefloor",
	"gfx/tiles/mine",
	"gfx/tiles/rough",
	"gfx/tiles/cavewall",
	"gfx/tiles/caveobsidian",
    };

    private static boolean isCaveName(String name) {
	if(name == null) return false;
	String n = name.toLowerCase();
	if(n.contains("/cave") || n.contains("/mine")) return true;
	for(String t : CAVE_TILES) {
	    if(n.equals(t)) return true;
	}
	return false;
    }

    private static Boolean isOnOwnLand(MCache map, Coord2d rc) {
	try {
	    Coord tc = rc.floor(MCache.tilesz);
	    Area a = Area.sized(tc, new Coord(1, 1));
	    boolean[] buf = new boolean[1];
	    for(MCache.OverlayInfo info : map.getols(a)) {
		if(!(info instanceof MCache.ResOverlay)) continue;
		MCache.ResOverlay r = (MCache.ResOverlay) info;
		if(r.tags == null) continue;
		if(!(r.tags.contains("cplot") || r.tags.contains("vlg"))) continue;
		buf[0] = false;
		map.getol(info, a, buf);
		if(buf[0]) return true;
	    }
	    return false;
	} catch(haven.Loading l) {
	    return null;
	} catch(Exception e) {
	    return null;
	}
    }
}
