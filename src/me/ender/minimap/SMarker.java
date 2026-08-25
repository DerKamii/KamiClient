package me.ender.minimap;

import haven.*;
import me.ender.QuestCondition;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SMarker extends MapFile.SMarker {
    public List<QuestCondition> questConditions = new ArrayList<>();
    public Iterator<QuestCondition> questIterator;

    public SMarker(MapFile file, long seg, Coord tc, String nm, UID oid, Resource.Saved res) {
	this(file, seg, tc, nm, oid, res, new byte[0]);
    }

    public SMarker(MapFile file, long seg, Coord tc, String nm, UID oid, Resource.Saved res, byte[] data) {
	super(file, seg, tc, nm, oid, res, (data != null) ? data : new byte[0]);
	questIterator = Utils.circularIterator(questConditions);
    }

    // KamiClient: no equals/hashCode on purpose, same reason as CustomMarker and PMarker. See the
    // comment there. The smarkers map is keyed by oid so it does not care, but MiniMap.Markers keys a
    // HashMap/HashSet on the marker itself and nm/tc are mutable.

    public static boolean equals(SMarker a, SMarker b) {
	return a.seg == b.seg
	    && a.tc.equals(b.tc)
	    && a.nm.equals(b.nm)
	    && a.oid.equals(b.oid)
	    && a.res.equals(b.res);
    }

    @Override
    public void draw(GOut g, Coord c, Text tip, final float scale, final MapFile file) {
	try {
	    final Resource res = this.res.loadsaved();
	    final Resource.Image img = res.layer(Resource.imgc);
	    final Resource.Neg neg = res.layer(Resource.negc);
	    final Coord cc = neg != null ? neg.cc : img.ssz.div(2);
	    final Coord ul = c.sub(cc);
	    if(CFG.QUESTHELPER_HIGHLIGHT_QUESTGIVERS.get() && !questConditions.isEmpty()) {
		for(QuestCondition item : new ArrayList<>(questConditions)) {
		    g.chcolor(item.questGiverMarkerColor());
		    g.fellipse(c, img.ssz.div(2).sub(1, 1));
		}
		g.chcolor();
	    }
	    g.image(img, ul);
	    if(tip != null && CFG.MMAP_SHOW_MARKER_NAMES.get()) {
		g.aimage(tip.tex(), c.addy(UI.scale(3)), 0.5, 0);
	    }
	} catch (Loading ignored) {}
    }

    @Override
    public Area area() {
	try {
	    final Resource res = this.res.loadsaved();
	    final Resource.Image img = res.layer(Resource.imgc);
	    final Resource.Neg neg = res.layer(Resource.negc);
	    final Coord cc = neg != null ? neg.cc : img.ssz.div(2);
	    return Area.sized(cc.inv(), img.ssz);
	} catch (Loading ignored) {
	    return null;
	}
    }
}
