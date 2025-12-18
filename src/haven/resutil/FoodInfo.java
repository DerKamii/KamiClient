/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven.resutil;

import haven.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FoodInfo extends ItemInfo.Tip {
    public static int PAD = UI.scale(5);
    public final double end, glut, sev, cons;
    public final Event[] evs;
    public final Effect[] efs;
    public final int[] types;
    
    private static BAttrWnd.Constipations constipation;
    private static BAttrWnd.GlutMeter glutmeter;
    private static BAttrWnd.FoodMeter feps;
    private static List<BAttrWnd.Attr> attr;
    
    public static int tablefep = 0;
    
    public static final Set<GItem> gitems = Collections.newSetFromMap(new ConcurrentHashMap<GItem, Boolean>());
    public static final Set<WItem> witems = Collections.newSetFromMap(new ConcurrentHashMap<WItem, Boolean>());
    
    public static final Color LITE_GREN = new Color(176, 255, 64);
    public static final Color LITER_GREN = new Color(255, 240, 96);
    public double fepHungerEfficiency;
    
    public FoodInfo(Owner owner, double end, double glut, double cons, double sev, Event[] evs, Effect[] efs, int[] types) {
	super(owner);
	this.end = end;
	this.glut = glut;
	this.sev = sev;
	this.cons = cons;
	this.evs = evs;
	this.efs = efs;
	this.types = types;
	
	try {
	    CharWnd cw = owner.context(Session.class).ui.gui.chrwdg;
	    if (cw != null && cw.battr != null) {
		constipation = cw.battr.cons;
	    }
	} catch (NullPointerException | OwnerContext.NoContext ignore) {
	}
    }
    
    private double calculateSatiation() {
	double effective = 1;
	if (constipation != null && types.length > 0) {
	    for (int type : types) {
		if (type >= 0 && type < constipation.els.size()) {
		    BAttrWnd.Constipations.El c = constipation.els.get(type);
		    if (c != null)
			effective = Math.min(effective, c.a);
		}
	    }
	}
	return effective;
    }
    
    public FoodInfo(Owner owner, double end, double glut, double cons, Event[] evs, Effect[] efs, int[] types) {
	this(owner, end, glut, cons, 0, evs, efs, types);
    }
    
    public static class Event {
	public static final Coord imgsz = new Coord(Text.std.height(), Text.std.height());
	public final BAttrWnd.FoodMeter.Event ev;
	public final BufferedImage img;
	public final double a;
	private final String res;
	
	public Event(Resource res, double a) {
	    this.ev = res.flayer(BAttrWnd.FoodMeter.Event.class);
	    this.img = PUtils.convolve(res.flayer(Resource.imgc).img, imgsz, CharWnd.iconfilter);
	    this.a = a;
	    this.res = res.name;
	}
    }
    
    public static class Effect {
	public final List<ItemInfo> info;
	public final double p;
	
	public Effect(List<ItemInfo> info, double p) {this.info = info; this.p = p;}
    }
    
    public void layout(Layout l) {
	String energy_hunger = glut != 0
	    ? Utils.odformat2(end / (10 * glut), 2)
	    : end == 0 ? "0" : "∞";
	String head = String.format("Energy: $col[128,128,255]{%s%%}, Hunger: $col[255,192,128]{%s\u2030}, Energy/Hunger: $col[128,128,255]{%s%%}", Utils.odformat2(end * 100, 2), Utils.odformat2(glut * 1000, 2), energy_hunger);
	if(cons != 0)
	    head += String.format(", Satiation: $col[192,192,128]{%s%%}", Utils.odformat2(cons * 100, 2));
	l.cmp.add(RichText.render(head, 0).img, Coord.of(0, l.cmp.sz.y));
	double fepSum = 0;
	for (Event ev : evs) {fepSum += ev.a;}
	for(int i = 0; i < evs.length; i++) {
	    Color col = Utils.blendcol(evs[i].ev.col, Color.WHITE, 0.5);
	    String fepStr = Utils.odformat2(evs[i].a, 2);
	    if(fepSum > 0) {
		double probability = 100 * evs[i].a / fepSum;
		fepStr += String.format(" (%s%%)", Utils.odformat2(probability, 2));
	    }
	    l.cmp.add(catimgsh(5, evs[i].img, RichText.render(String.format("%s: %s{%s}", evs[i].ev.nm, RichText.Parser.col2a(col), fepStr), 0).img),
		Coord.of(UI.scale(5), l.cmp.sz.y));
	}
	/* disabling this - custom total is added by ItemData.modifyFoodTooltip
	if(sev > 0)
	    l.cmp.add(RichText.render(String.format("Total: $col[128,192,255]{%s} ($col[128,192,255]{%s}/\u2030 hunger)", Utils.odformat2(sev, 2), Utils.odformat2(sev / (1000 * glut), 2)), 0).img,
		      Coord.of(UI.scale(5), l.cmp.sz.y));
		      */
	for(int i = 0; i < efs.length; i++) {
	    BufferedImage efi = ItemInfo.longtip(efs[i].info);
	    if(efs[i].p != 1)
		efi = catimgsh(5, efi, RichText.render(String.format("$i{($col[192,192,255]{%d%%} chance)}", (int)Math.round(efs[i].p * 100)), 0).img);
	    l.cmp.add(efi, Coord.of(UI.scale(5), l.cmp.sz.y));
	}
	ItemData.modifyFoodTooltip(owner, l.cmp, types, glut, fepSum);
    }
    
    private boolean getcw() {
	try {
	    CharWnd cw = owner.context(Session.class).ui.gui.chrwdg;
	    if (cw.battr != null) {
		FoodInfo.glutmeter = cw.battr.glut;
		FoodInfo.feps = cw.battr.feps;
		FoodInfo.attr = cw.battr.attrs;
		return true;
	    }
	} catch (NullPointerException | OwnerContext.NoContext ignore) {
	    return false;
	}
	return false;
    }
    
    public Pair<Double, Color> fepnum(WItem item) {
	if (attr == null && !getcw())
	    return new Pair<>(-1d, Color.WHITE);
	
	if (item != null)
	    witems.add(item);
	
	boolean feast = false;
	try {
	    UI ui = owner.context(Session.class).ui;
	    Resource curs = ui.root.getcurss(ui.mc);
	    if ("gfx/hud/curs/eat".equals(curs.name))
		feast = true;
	} catch (NullPointerException | OwnerContext.NoContext | Loading ignore) {
	}
	
	double evs_total = 0;
	for (int i = 0; i < evs.length; i++) {
	    double fep = evs[i].a;
	    if (feast)
		fep += fep * (double) tablefep / 100.0;
	    evs_total += fep;
	}
	
	double bonusmul = 1;
	if (GameUI.subscribedAccount)
	    bonusmul += 0.3;
	if (GameUI.verifiedAccount)
	    bonusmul += 0.2;
	
	double effective = 1;
	for (int type : types) {
	    if (type >= 0 && constipation != null && type < constipation.els.size()) {
		BAttrWnd.Constipations.El c = constipation.els.get(type);
		if (c != null)
		    effective = Math.min(effective, c.a);
	    }
	}
	
	int maxattr = 1;
	for (BAttrWnd.Attr a : attr)
	    if (a.attr.base > maxattr)
		maxattr = a.attr.base;
	
	double curfep = 0;
	double effmod = glutmeter.gmod * effective * bonusmul;
	double effep = evs_total * effmod;
	double resfep;
	Color col;
	
	if (feps.els.size() == 0) {
	    double rescap = feps.cap - Math.sqrt((double) maxattr * 2 * glutmeter.gmod / 5);
	    resfep = effep;
	    col = (rescap <= resfep) ? LITE_GREN : Color.WHITE;
	} else {
	    for (BAttrWnd.FoodMeter.El el : feps.els)
		curfep += el.a;
	    
	    resfep = effep + curfep;
	    double curcap = feps.cap;
	    double fep = maxattr;
	    double fepnext = maxattr;
	    int n = 0;
	    while (fepnext > curcap) {
		fep = fepnext;
		fepnext = fep - Math.sqrt((double) maxattr * 2 * glutmeter.gmod / 5 / (double) ++n);
	    }
	    if (fep - curcap > curcap - fepnext)
		++n;
	    double rescap = curcap - Math.sqrt((double) maxattr * 2 * glutmeter.gmod / 5 / (double) n);
	    
	    if (curcap <= resfep)
		col = LITE_GREN;
	    else if (rescap <= resfep)
		col = LITER_GREN;
	    else
		col = Color.WHITE;
	    
	    if (effective > 0)
		fepHungerEfficiency = effep / (effective * 100.0);
	    else
		fepHungerEfficiency = Double.POSITIVE_INFINITY;
	}
	return new Pair<>(effep, col);
    }
    
    public static void resettts() {
	new Thread(() -> {
	    int delay = 100;
	    try {
		Thread.sleep(delay);
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }
	    
	    for (WItem i : new ArrayList<>(witems)) {
		if (i != null && i.fepnum != null) {
		    i.fepnum.reset();
		}
	    }
	    gitems.clear();
	    witems.clear();
	}, "FoodInfo-resettts").start();
    }
}
