package haven;

import haven.render.Homo3D;
import haven.render.Pipe;
import haven.render.RenderTree;

public abstract class GobInfo extends GAttrib implements RenderTree.Node, PView.Render2D {
    protected Tex tex;
    private Coord3f pos = new Coord3f(0, 0, 1);
    protected final Object texLock = new Object();
    protected Pair<Double, Double> center = new Pair<>(0.5, 0.5);
    protected boolean dirty = true;
    protected boolean noContent = false;

    public GobInfo(Gob owner) {
	super(owner);
    }

    protected abstract boolean enabled();

    protected void up(int up) {pos.z = up;}

    @Override
    public void ctick(double dt) {
	synchronized (texLock) {
	    if(enabled() && !noContent && (dirty || tex == null)) {
		if(tex != null) {tex.dispose();}
		tex = render();
		dirty = false;
		if(tex == null) {
		    noContent = hasLoadedRes();
		}
	    }
	}
    }

    private boolean hasLoadedRes() {
	return gob != null && gob.resid() != null;
    }

    @Override
    public void draw(GOut g, Pipe state) {
	if(noContent) {return;}
	Tex t = tex;
	if(t != null && enabled()) {
	    Coord sc = Homo3D.obj2sc(pos, state, Area.sized(g.sz()));
	    if(sc == null) {return;}
	    if(sc.isect(Coord.z, g.sz())) {
		g.aimage(t, sc, center.a, center.b);
	    }
	}
    }

    protected abstract Tex render();

    public void clean() {
        synchronized(texLock) {
	    if(tex != null) {
		tex.dispose();
		tex = null;
	    }
	    noContent = false;
	}
    }

    public void dirty() {
	noContent = false;
	dirty = true;
    }

    public void dispose() {
	clean();
    }
}
