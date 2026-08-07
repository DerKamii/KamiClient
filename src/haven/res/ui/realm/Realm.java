/* Preprocessed source code */
/* $use: ui/polity */

package haven.res.ui.realm;

import haven.*;
import java.util.*;
import haven.res.ui.polity.*;
import static haven.BuddyWnd.width;
import static haven.PType.*;

/* >wdg: Realm */
@haven.FromResource(name = "ui/realm", version = 35)
public class Realm extends Polity {
    public static final Map<String, Resource.Image> authimg = Utils.<String, Resource.Image>map().
	put("t", Loading.waitfor(Resource.classres(Realm.class).pool.load("gfx/terobjs/mm/thingwall", 4)).layer(Resource.imgc)).
	map();
    final BuddyWnd.GroupSelector gsel;
    public final Map<String, Integer> authn = new HashMap<>();
    public Window actwnd;
    private final Widget acts, actcnt;
    private final int my;
    private Collection<Widget> curacts = Collections.emptyList();

    public Realm(String name) {
	super("Realm", name);
	Widget prev = add(new AuthMeter(new Coord(width, UI.scale(20))), Coord.z);
	try {
	    new Member(new Member(0));
	} catch(LinkageError e) {
	    prev = add(new Label("Please update your client!", nmf), prev.pos("bl").adds(0, 15));
	}
	prev = add(new Authobj("t"), prev.pos("bl").adds(0, 5));
	prev = add(new Button(width - UI.scale(20), "Realm Blessings") {
		public void click() {
		    if((actwnd != null) && actwnd.show(!actwnd.visible)) {
			actwnd.raise();
		    }
		}
	    }, prev.pos("bl").adds(0, 5).xs(10));
	prev = add(new Label("Groups:"), prev.pos("bl").adds(0, 10).x(0));
	gsel = add(new BuddyWnd.GroupSelector(-1) {
		public void tick(double dt) {
		    if(mw instanceof GroupWidget)
			update(((GroupWidget)mw).id);
		    else
			update(-1);
		}

		public void select(int group) {
		    Realm.this.wdgmsg("gsel", group);
		}
	    }, prev.pos("bl").adds(0, 2));
	Widget cprev = prev = add(new Label("Members and known hearthlings:"), gsel.pos("bl").adds(0, 5));
	prev = add(Frame.with(new MemberList(width, 7), true), prev.pos("bl").adds(0, 2));
	actcnt = add(new Widget(Coord.z), Coord.of(prev.pos("ur").x + UI.scale(10), cprev.pos("ur").y));
	acts = actcnt.add(new Label("Actions:"), Coord.z);
	actcnt.pack();
	pack();
	this.my = prev.pos("bl").adds(0, 5).y;
    }

    public String type() {
	return("rlm");
    }

    public class RMember extends Member {
	public final int grp, state;

	public RMember(Integer id, int grp, int state) {
	    super(id);
	    this.grp = grp;
	    this.state = state;
	}

	public RMember(Member p, int grp, int state) {
	    super(p);
	    this.grp = grp;
	    this.state = state;
	}

	public void draw(GOut g) {
	    int m = UI.scale(10);
	    if(state == 1)
		g.aimage(BuddyWnd.online, Coord.of(m), 0.5, 0.5);
	    else if(state == 0)
		g.aimage(BuddyWnd.offline, Coord.of(m), 0.5, 0.5);
	    if(id != null)
		g.chcolor(BuddyWnd.gc[grp]);
	    g.aimage(rname().tex(), Coord.of(BuddyWnd.online.sz().x + UI.scale(5), m), 0, 0.5);
	    g.chcolor();
	}
    }

    protected Member parsememb(Object[] args, Member p) {
	int grp = args.length > 1 ? INT.of(args[1]) : 0;
	int state = args.length > 2 ? INT.of(args[2]) : -1;
	if(p == null) {
	    Integer id = INT.of(args[0]);
	    return(new RMember(id, grp, state));
	} else {
	    return(new RMember(p, grp, state));
	}
    }

    public class Authobj extends Widget {
	public final String t;
	public final Resource.Image img;
	private Text rend;
	private int cn;

	public Authobj(String t) {
	    super(authimg.get(t).ssz.add(UI.scale(25, 0)));
	    this.t = t;
	    this.img = authimg.get(t);
	}

	private int aseq = -1;
	private Tex rauth = null;
	public void draw(GOut g) {
	    int n;
	    synchronized(authn) {
		Integer apa = Realm.this.authn.get(t);
		n = (apa == null) ? 0 : apa;
	    }
	    g.image(img, Coord.z);
	    if((rend == null) || (n != cn))
		rend = Text.render(Integer.toString(n));
	    g.aimage(rend.tex(), new Coord(img.ssz.x + UI.scale(5), img.ssz.y / 2), 0, 0.5);
	}

	public Object tooltip(Coord c, Widget prev) {
	    return(this.img.getres().layer(Resource.tooltip).t);
	}
    }

    public static Widget mkwidget(UI ui, Object[] args) {
	String name = STR.of(args[0]);
	return(new Realm(name));
    }

    public void addchild(Widget child, Object... args) {
	if(args[0] instanceof String) {
	    String p = STR.of(args[0]);
	    if(p.equals("m")) {
		mw = child;
		add(child, 0, my);
		/* actcnt.move(child.pos("bl").adds(0, 5)); */
		pack();
		return;
	    } else if(p.equals("act")) {
		try {
		    actwnd = new GameUI.Hidewnd(Coord.z, "Realm Blessings");
		    actwnd.add(child);
		    actwnd.pack();
		    actwnd.hide();
		    getparent(GameUI.class).add(actwnd);
		} catch(LinkageError e) {
		    new Warning(e).issue();
		    ui.error("Please update your client!");
		}
		return;
	    }
	}
	super.addchild(child, args);
    }

    public void cdestroy(Widget w) {
	/*
	if(w == mw)
	    actcnt.move(Coord.of(0, my));
	*/
	super.cdestroy(w);
    }

    private static Widget settip(Widget wdg, Indir<Resource> res) {
	if(res != null)
	    wdg.tooltip = new Widget.PaginaTip(res);
	return(wdg);
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "authn") {
	    String tp = STR.of(args[0]);
	    int n = INT.of(args[1]);
	    synchronized(authn) {
		authn.put(tp, n);
	    }
	} else if(msg == "perm") {
	    int fl = INT.of(args[0]);
	    for(Widget aw : curacts)
		aw.destroy();
	    Collection<Widget> newacts = new ArrayList<>();
	    Widget prev = acts;
	    newacts.add(prev = actcnt.add(settip(new Button(width, "Leave the Realm", false).action(() -> wdgmsg("leave")),
					  null),
				   prev.pos("bl").adds(0, 2)));
	    newacts.add(prev = actcnt.add(settip(new Button(width, "Show Realm Challenges", false).action(() -> wdgmsg("showchal")),
						 Resource.classres(Realm.class).pool.load("paginae/gov/showchal", 4)),
					  prev.pos("bl").adds(0, 2)));
	    newacts.add(prev = actcnt.add(settip(new Button(width, "Show Thingpeace Challenges", false).action(() -> wdgmsg("showtchal")),
						 Resource.classres(Realm.class).pool.load("paginae/gov/showtchal", 2)),
					  prev.pos("bl").adds(0, 2)));
	    if((fl & 1) != 0)
		newacts.add(prev = actcnt.add(settip(new Button(width, "Oath of Allegiance", false).action(() -> wdgmsg("invite")),
					      null),
				       prev.pos("bl").adds(0, 2)));
	    if((fl & 2) != 0) {
		newacts.add(prev = actcnt.add(settip(new Button(width, "From the Public Coffer", false).action(() -> wdgmsg("inspire")),
					      Resource.classres(Realm.class).pool.load("paginae/gov/r-inspire", 3)),
				       prev.pos("bl").adds(0, 2)));
		newacts.add(prev = actcnt.add(settip(new Button(width, "State Funeral", false).action(() -> wdgmsg("bury")),
					      Resource.classres(Realm.class).pool.load("paginae/gov/r-bury", 5)),
				       prev.pos("bl").adds(0, 2)));
	    }
	    this.curacts = newacts;
	    actcnt.pack();
	    pack();
	}else {
	    super.uimsg(msg, args);
	}
    }
}
