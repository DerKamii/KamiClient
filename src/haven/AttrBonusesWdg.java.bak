package haven;

import haven.res.ui.tt.attrmod.AttrMod;
import haven.res.ui.tt.attrmod.Attribute;
import haven.res.ui.tt.attrmod.Mod;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import static haven.ItemInfo.*;

public class AttrBonusesWdg extends Widget implements ItemInfo.Owner {
    private static final Coord bonusc = UI.scale(0, 20);
    private static final OwnerContext.ClassResolver<AttrBonusesWdg> ctxr = new ClassResolver<AttrBonusesWdg>()
	.add(Glob.class, wdg -> wdg.ui.sess.glob)
	.add(Session.class, wdg -> wdg.ui.sess);
    private final Scrollbar bar;
    
    private boolean needUpdate = false;
    private boolean needBuild = false;
    private boolean needRedraw = false;
    
    private WItem[] items;
    private Map<Resource, Integer> bonuses;
    private List<ItemInfo> info = null;
    private BufferedImage tip = null;
    
    private CharWnd charWnd = null;
    private long attrseq = 0;
    
    public AttrBonusesWdg(int y) {
	super(new Coord(UI.scale(175), y));
	add(new Label("Equipment bonuses:"));
	bar = adda(new Scrollbar(y - bonusc.y, 0, 0), sz.x, bonusc.y, 1, 0);
    }
    
    @Override
    public boolean mousewheel(MouseWheelEvent ev) {
	bar.ch(15 * ev.a);
	return true;
    }
    
    public void update(WItem[] items) {
	this.items = items;
	needUpdate = true;
    }
    
    private boolean isMe() {
	return parent == ui.gui.equipory;
    }
    
    @Override
    public void draw(GOut g) {
	checkAttributes();
	if(needUpdate) {
	    doUpdate();
	}
	if(charWnd == null) {
	    charWnd = ui.gui.chrwdg;
	    if(charWnd != null) {needBuild = true;}
	}
	if(needBuild) {
	    build();
	}
	if(needRedraw) {
	    render();
	}
	
	if(tip != null) {
	    Coord c = Coord.z;
	    if(bar.visible) {
		c = c.sub(0, bar.val);
	    }
	    g.reclip(bonusc, sz).image(tip, c);
	}
	super.draw(g);
    }
    
    private void render() {
	try {
	    if(info != null && !info.isEmpty()) {
		tip = ItemInfo.longtip(info);
	    } else {
		tip = null;
	    }
	    
	    int delta = tip != null ? tip.getHeight() : 0;
	    bar.visible = delta > bar.sz.y;
	    bar.max = delta - bar.sz.y;
	    bar.ch(0);
	    
	    needRedraw = false;
	} catch (Loading ignored) {}
    }
    
    private void checkAttributes() {
	long tseq = ui.sess.glob.attrseq;
	if(attrseq != tseq) {
	    needUpdate = true;
	    attrseq = tseq;
	}
    }
    
    private void doUpdate() {
	try {
	    if(items == null) {return;}
	    boolean isMe = isMe();
	    List<Entry<Resource, Integer>> tmp = Arrays.stream(items)
		.filter(Objects::nonNull)
		.map(wItem -> wItem.item)
		.distinct()
		.map(GItem::info)
		.map(info -> getBonuses(info, isMe ? cattr() : null))
		.map(Map::entrySet)
		.flatMap(Collection::stream)
		.collect(Collectors.toList());
	    
	    bonuses = tmp.stream()
		.filter(e -> e.getKey() != mining)
		.collect(
		    Collectors.toMap(
			Entry::getKey,
			Entry::getValue,
			Integer::sum
		    )
		);
	    
	    if(isMe) {
		int miningStrength = 0;
		for (Entry<Resource, Integer> e : tmp) {
		    int value;
		    if(e.getKey() == mining && miningStrength < (value = e.getValue())) {
			miningStrength = value;
		    }
		}
		if(miningStrength > 0) {
		    bonuses.put(mining, miningStrength);
		}
		addDerivedStat(detection, "prc", "explore");
		addDerivedStat(sneak, "int", "stealth");
	    }
	    needUpdate = false;
	    needBuild = true;
	} catch (Loading ignored) {}
    }
    
    private Map<String, Glob.CAttr> cattr() {
	if(ui != null) {
	    return ui.sess.glob.cattr;
	}
	return null;
    }
    
    private void addDerivedStat(Resource res, String attr1, String attr2) {
	Map<String, Glob.CAttr> cattr = cattr();
	if(cattr == null) {return;}
	Glob.CAttr a1 = cattr.get(attr1);
	Glob.CAttr a2 = cattr.get(attr2);
	if(a1 != null && a2 != null) {
	    bonuses.put(res, a1.comp * a2.comp);
	}
    }
    
    private void build() {
	try {
	    if(bonuses != null) {
		ItemInfo compiled = make(bonuses.entrySet()
		    .stream()
		    .sorted(this::BY_PRIORITY)
		    .collect(Collectors.toList())
		);
		info = compiled != null ? Collections.singletonList(compiled) : null;
	    }
	    
	    needBuild = false;
	    needRedraw = true;
	} catch (Loading ignored) {}
    }
    
    private ItemInfo make(Collection<Entry<Resource, Integer>> values) {
	if(values.isEmpty()) {
	    return null;
	}
	
	return new AttrMod(this, values.stream()
	    .map(m -> new Mod(Attribute.get(m.getKey()), m.getValue()))
	    .collect(Collectors.toList()));
    }
    
    private int BY_PRIORITY(Entry<Resource, Integer> o1, Entry<Resource, Integer> o2) {
	Resource r1 = o1.getKey();
	Resource r2 = o2.getKey();
	
	if(charWnd != null) {
	    return charWnd.BY_PRIORITY(r1, r2);
	}
	return r1.name.compareTo(r2.name);
    }
    
    @Override
    public List<ItemInfo> info() {
	return info;
    }
    
    @Override
    public <T> T context(Class<T> cl) {
	return (ctxr.context(cl, this));
    }
}