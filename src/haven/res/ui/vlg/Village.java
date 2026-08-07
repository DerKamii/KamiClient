/* Preprocessed source code */
/* $use: ui/polity */

package haven.res.ui.vlg;

import haven.*;
import java.util.*;
import haven.res.ui.polity.*;
import java.awt.Color;
import static haven.BuddyWnd.width;
import static haven.PType.*;

/* >wdg: Village */
@haven.FromResource(name = "ui/vlg", version = 38)
public class Village extends Polity {
    public final BuddyWnd.GroupSelector gsel;
    private final Widget acts, actcnt;
    private final int my;
    private Collection<Widget> curacts = Collections.emptyList();

    public Village(String name) {
	super("Village", name);
	Widget prev = add(new AuthMeter(new Coord(width, UI.scale(20))), Coord.z);
	try {
	    new Member(new Member(0));
	} catch(LinkageError e) {
	    prev = add(new Label("Please update your client!", nmf), prev.pos("bl").adds(0, 15));
	}
	prev = add(new Label("Groups:"), prev.pos("bl").adds(0, 15));
	gsel = add(new BuddyWnd.GroupSelector(-1) {
		public void tick(double dt) {
		    if(mw instanceof GroupWidget)
			update(((GroupWidget)mw).id);
		    else
			update(-1);
		}

		public void select(int group) {
		    Village.this.wdgmsg("gsel", group);
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
	return("pol");
    }

    public static Widget mkwidget(UI ui, Object[] args) {
	String name = STR.of(args[0]);
	return(new Village(name));
    }

    public class VMember extends Member {
	public final int grp, state;

	public VMember(Integer id, int grp, int state) {
	    super(id);
	    this.grp = grp;
	    this.state = state;
	}

	public VMember(Member p, int grp, int state) {
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
	    return(new VMember(id, grp, state));
	} else {
	    return(new VMember(p, grp, state));
	}
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
	wdg.tooltip = new Widget.PaginaTip(res);
	return(wdg);
    }

    public void uimsg(String msg, Object... args) {
	if(msg == "perm") {
	    int fl = INT.of(args[0]);
	    for(Widget aw : curacts)
		aw.destroy();
	    Collection<Widget> newacts = new ArrayList<>();
	    Widget prev = acts;
	    newacts.add(prev = actcnt.add(settip(new Button(width, "Leave the Village", false).action(() -> wdgmsg("leave")),
					  Resource.classres(Village.class).pool.load("paginae/gov/leave", 6)),
				   prev.pos("bl").adds(0, 2)));
	    if((fl & 2) != 0)
		newacts.add(prev = actcnt.add(settip(new Button(width, "Oath of Allegiance", false).action(() -> wdgmsg("invite")),
					      Resource.classres(Village.class).pool.load("paginae/gov/invite", 12)),
				       prev.pos("bl").adds(0, 2)));
	    if((fl & 4) != 0)
		newacts.add(prev = actcnt.add(settip(new Button(width, "Revoke the Privilege", false).action(() -> wdgmsg("invite")),
					      Resource.classres(Village.class).pool.load("paginae/gov/revoke", 17)),
				       prev.pos("bl").adds(0, 2)));
	    this.curacts = newacts;
	    actcnt.pack();
	    pack();
	} else {
	    super.uimsg(msg, args);
	}
    }
}
