package me.ender.minimap;

import haven.*;

import java.awt.*;

public class PMarker extends MapFile.PMarker {
    public static final Resource.Image flagbg, flagfg;
    public static final Coord flagcc;

    static {
	Resource flag = Resource.local().loadwait("gfx/hud/mmap/flag");
	flagbg = flag.layer(Resource.imgc, 1);
	flagfg = flag.layer(Resource.imgc, 0);
	flagcc = UI.scale(flag.layer(Resource.negc).cc);
    }

    public PMarker(MapFile file, long seg, Coord tc, String nm, Color color, boolean onmap) {
	super(file, seg, tc, nm, color, onmap);
    }

    // KamiClient: no equals/hashCode on purpose, same reason as CustomMarker. MapFile.markers is an
    // ArrayList and MiniMap.Markers keys a HashMap/HashSet on markers, so value equality collapses two
    // same-named markers on one tile into a single icon and they flicker. Worse, nm/color/tc are all
    // mutable, so hashing on them strands a live key the moment a marker is renamed or recoloured.
    // Compare by value explicitly via equals(a, b) below.

    public static boolean equals(PMarker a, PMarker b) {
	return a.seg == b.seg
	    && a.tc.equals(b.tc)
	    && a.nm.equals(b.nm)
	    && a.color.equals(b.color);
    }

    @Override
    public void draw(final GOut g, final Coord c, final Text tip, final float scale, final MapFile file) {
	final Coord ul = c.sub(flagcc);
	g.chcolor(color);
	g.image(flagfg, ul);
	g.chcolor();
	g.image(flagbg, ul);
	if(tip != null && CFG.MMAP_SHOW_MARKER_NAMES.get()) {
	    g.aimage(tip.tex(), c, 0.5, 0.75);
	}
    }

    @Override
    public Area area() {
	return Area.sized(flagcc.inv(), UI.scale(flagbg.sz));
    }
}
