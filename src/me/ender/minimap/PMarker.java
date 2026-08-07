package me.ender.minimap;

import haven.*;

import java.awt.*;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
	if(this == o) return true;
	if(o == null || getClass() != o.getClass()) return false;
	PMarker pMarker = (PMarker) o;
	return seg == pMarker.seg && tc.equals(pMarker.tc) && nm.equals(pMarker.nm) && color.equals(pMarker.color);
    }

    @Override
    public int hashCode() {
	return Objects.hash(seg, tc, nm, color);
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
