/* Preprocessed source code */
/* $use: ui/pag/toggle */

package haven.res.gfx.fx.mscover;

import haven.*;
import haven.render.*;

import java.awt.*;
import java.util.*;
import haven.res.ui.pag.toggle.*;
import haven.MenuGrid.Pagina;
import me.ender.CFGOverlayId;

import static haven.MCache.*;

/* >objdelta: Radius */
@haven.FromResource(name = "gfx/fx/mscover", version = 2)
public class Global implements LocalOverlay {
    public static final String OL_TAG = "mscover";
    public static final OverlayInfo ol_1 = new CFGOverlayId(CFG.COLOR_MINE_SUPPORT_SINGLE_OVERLAY, OL_TAG);
    public static final OverlayInfo ol_m = new CFGOverlayId(CFG.COLOR_MINE_SUPPORT_OVERLAY, OL_TAG);
    public static final OverlayInfo ol_v = new CFGOverlayId(CFG.COLOR_MINE_SUPPORT_VIRTUAL_OVERLAY, OL_TAG);
    public static final OverlayInfo ol_d = new CFGOverlayId(CFG.COLOR_MINE_SUPPORT_DAMAGED_OVERLAY, OL_TAG);
    public static final int GRAN = 25;
    public final Glob glob;
    public final Collection<Coverage> current = new HashSet<>();
    public MapView map;
    public Data dat = null;
    public boolean update = false;
    private boolean hasvirt = false;
    public float healthThreshold = CFG.MINE_SUPPORT_DANGER_THRESHOLD.get() / 100.0f;

    public Global(Glob glob) {
	this.glob = glob;
	CFG.MINE_SUPPORT_DANGER_THRESHOLD.observe(cfg -> healthThreshold = cfg.get() / 100.0f);
    }

    public abstract class Overlay implements LocalOverlay {
	public final OverlayInfo id;

	public Overlay(OverlayInfo id) {
	    this.id = id;
	}

	public OverlayInfo id() {return(id);}

	public void fill(Area a, boolean[] buf) {
	    Data dat = Global.this.dat;
	    if((dat == null) || ((a = a.overlap(dat.area)) == null))
		return;
	    fill2(dat, a, buf);
	}

	protected abstract void fill2(Data dat, Area a, boolean[] buf);

	public boolean filter(Area a) {
	    return((dat == null) || (a.overlap(dat.area) == null));
	}
    }
    public final LocalOverlay[] ols = {
	this,
	new Overlay(ol_1) {
	    public void fill2(Data dat, Area a, boolean[] buf) {
		for(Coord tc : a)
		    buf[a.ridx(tc)] |= (dat.cc[dat.area.ridx(tc)] == 1) &&
			(dat.dc[dat.area.ridx(tc)] == 0);
	    }
	},
	new Overlay(ol_m) {
	    public void fill2(Data dat, Area a, boolean[] buf) {
		for(Coord tc : a)
		    buf[a.ridx(tc)] |= (dat.cc[dat.area.ridx(tc)] > 1) &&
			(dat.dc[dat.area.ridx(tc)] < dat.cc[dat.area.ridx(tc)]);
	    }
	},
	new Overlay(ol_v) {
	    public void fill2(Data dat, Area a, boolean[] buf) {
		for(Coord tc : a)
		    buf[a.ridx(tc)] |= (dat.vc[dat.area.ridx(tc)] > 0) &&
			(dat.cc[dat.area.ridx(tc)] <= 1);
	    }
	},
	new Overlay(ol_d) {
	    public void fill2(Data dat, Area a, boolean[] buf) {
		for(Coord tc : a)
		    buf[a.ridx(tc)] |= (dat.cc[dat.area.ridx(tc)] > 0) &&
			(dat.dc[dat.area.ridx(tc)] >= dat.cc[dat.area.ridx(tc)]);
	    }
	},
    };

    private static final Map<Glob, Global> globs = new WeakHashMap<>();
    public static Global get(Glob glob) {
	synchronized(globs) {
	    Global ret = globs.get(glob);
	    if(ret == null)
		globs.put(glob, ret = new Global(glob));
	    return(ret);
	}
    }

    public void add(Coverage rad) {
	synchronized(current) {
	    if(current.isEmpty()) {
		for(LocalOverlay ol : ols)
		    glob.map.add(ol);
	    }
	    current.add(rad);
	    update = true;
	}
    }

    public OverlayInfo id() {return(null);}
    public void fill(Area a, boolean[] buf) {}
    public boolean filter(Area a) {return(true);}

    public void tick() {
	synchronized(current) {
	    /* XXX: This shouldn't be necessary, if only
	     * GAttrib.dispose were called properly */
	    for(Coverage cov : current) {
		if(cov.gob.removed) {
		    cov.removed = true;
		    update = true;
		}
	    }
	}
	if(update) {
	    boolean ch = false, hasvirt = false;
	    synchronized(current) {
		Area aa = null;
		for(Coverage cov : current) {
		    Coord2d cc = cov.gob.rc;
		    double a = cov.gob.a;
		    if(cc == Coord2d.z)
			continue;
		    Area oa = cov.extent(cc, a);
		    aa = (aa == null) ? oa : aa.include(oa);
		}
		if(aa == null) {
		    dat = null;
		    glob.map.olseq++;
		    return;
		}

		aa = Area.corn(aa.ul.div(GRAN).mul(GRAN), aa.br.div(GRAN).add(1, 1).mul(GRAN));
		if((dat == null) || !Utils.eq(dat.area, aa)) {
		    ch = true;
		    dat = new Data(aa);
		    for(Coverage cov : current)
			cov.cc = null;
		}

		for(Iterator<Coverage> i = current.iterator(); i.hasNext();) {
		    Coverage cov = i.next();
		    int fl = cov.fl();
		    Coord2d cc = cov.gob.rc;
		    double a = cov.gob.a;
		    if(cov.removed) {
			if(cov.cc != null)
			    dat.mod(cov.cc, cov.a, cov, cov.cfl, -1);
			i.remove();
			ch = true;
			continue;
		    } else if(!Utils.eq(cov.cc, cc) || (cov.a != a) || (cov.cfl != fl)) {
			if(cov.cc != null)
			    dat.mod(cov.cc, cov.a, cov, cov.cfl, -1);
			dat.mod(cov.cc = cc, cov.a = a, cov, cov.cfl = fl, 1);
			ch = true;
		    }
		    if(!cov.real)
			hasvirt = true;
		}

		if(current.isEmpty()) {
		    for(LocalOverlay ol : ols) {
			glob.map.remove(ol);
			ch = true;
		    }
		}
		if(ch)
		    glob.map.olseq++;
	    }
	    if(hasvirt != this.hasvirt) {
		if(map != null) {
		    if(hasvirt)
			map.enol("mscover");
		    else
			map.disol("mscover");
		}
		this.hasvirt = hasvirt;
	    }
	    update = false;
	}
    }
}

/* >pagina: ShowCover$Fac */
