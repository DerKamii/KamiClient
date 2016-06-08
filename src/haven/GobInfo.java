package haven;

public abstract class GobInfo extends PView.Draw2D {
    protected final Gob gob;
    public boolean ready = false;
    protected Tex tex;
    private GLState.Buffer state;
    protected int up = 1;
    protected Pair<Double, Double> center = new Pair<>(0.5, 0.5);

    public GobInfo(Gob owner) {this.gob = owner;}

    @Override
    public Object staticp() {
	return Rendered.CONSTANS;
    }

    @Override
    final public void draw2d(GOut g) {
	Coord sc = null;
	if(state != null) {sc = Utils.world2screen(gob.getc(), state, up);}
	if(sc != null && sc.isect(Coord.z, g.sz)) {
	    cdraw(g, sc);
	}
    }


    protected void cdraw(GOut g, Coord sc) {
	if(tex != null) {
	    g.aimage(tex, sc, center.a, center.b);
	}
    }

    @Override
    public boolean setup(RenderList d) {
	state = d.state();
	if(!ready) {
	    try {
		tex = render();
		ready = true;
	    } catch (Loading ignored) {
	    } catch (Exception e) {
		tex = null;
		ready = true;
	    }
	}
	return ready && tex != null;
    }

    protected abstract Tex render();

    protected void clean() {
	if(tex != null) {
	    tex.dispose();
	    tex = null;
	}
	ready = false;
    }

    public void dispose() {
	clean();
	state = null;
    }
}
