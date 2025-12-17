package haven;

import haven.resutil.FoodInfo;
import me.ender.ItemHelpers;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.function.Function;

import static haven.BAttrWnd.Constipations.*;
import static haven.PUtils.*;
import static me.ender.ResName.*;

public class CharacterInfo {
    private final Session sess;
    
    public boolean verified = false;
    public boolean subscribed = false;
    public double gluttony = 1.0;
    
    public final Constipation constipation = new Constipation();
    
    public CharacterInfo(Session ses) {
	this.sess = ses;
    }
    
    public void updateGluttony(double value) {
	double delta = Math.abs(gluttony - value);
	gluttony = value;
	if(delta > 0.01) {ItemHelpers.invalidateFoodItemTooltips(sess.ui);}
    }
    
    public void updateAccountStatus(Set<? extends Widget> children) {
	verified = false;
	subscribed = false;
	try {
	    children.forEach(it -> {
		if(it.tooltip instanceof Widget.KeyboundTip) {
		    String tip = ((Widget.KeyboundTip) it.tooltip).base;
		    
		    if("Verified account".equals(tip)) {verified = true;}
		    
		    if("Subscribed until".contains(tip)) {subscribed = true;}
		}
	    });
	} catch (Exception ignore) {}
	
	accBonusInvalid = true;
    }
    
    private String lastCurs = null;
    
    public void updateCursor(Indir<Resource> cursor) {
	if(cursor == null) {
	    lastCurs = null;
	    return;
	}
	
	boolean invalidateFoodTips;
	try {
	    String name = cursor.get().name;
	    if(Objects.equals(name, lastCurs)) {return;}
	    invalidateFoodTips = CURSOR_EAT.equals(name) || CURSOR_EAT.equals(lastCurs);
	    lastCurs = name;
	} catch (Loading ignore) {
	    invalidateFoodTips = true;
	}
	
	if(invalidateFoodTips) {ItemHelpers.invalidateFoodItemTooltips(sess.ui);}
    }
    
    private Optional<ItemData.FEPMod> accBonus = Optional.empty();
    private boolean accBonusInvalid = true;
    
    public Optional<ItemData.FEPMod> getAccountFEPBonus() {
	if(!accBonusInvalid) {return accBonus;}
	accBonusInvalid = false;
	
	double accountBonus = 1.0;
	List<String> paidDescriptions = new ArrayList<>(2);
	if(verified) {
	    accountBonus += 0.2;
	    paidDescriptions.add("verified");
	}
	
	if(subscribed) {
	    accountBonus += 0.3;
	    paidDescriptions.add("subscribed");
	}
	
	if(accountBonus > 1.0) {
	    accBonus = Optional.of(new ItemData.FEPMod(accountBonus, String.join(", ", paidDescriptions)));
	} else {
	    accBonus = Optional.empty();
	}
	return accBonus;
    }
    
    public double getEnergy() {
	IMeter nrj = sess.ui.gui.getIMeter("nrj");
	if(nrj == null) {return -1;}
	return nrj.meter(0);
    }
    
    private static final Optional<ItemData.FEPMod> ENERGY_LOW = Optional.of(new ItemData.FEPMod(0, "too tired to eat without table"));
    
    public Optional<ItemData.FEPMod> getEnergyFEPMod() {
	double nrj = getEnergy();
	return (nrj < 0 || nrj > 0.8 || sess.ui.isCursor(CURSOR_EAT)) ? Optional.empty() : ENERGY_LOW;
    }
    
    public static class Constipation {
	public final List<Data> els = new ArrayList<>();
	private Integer[] order = {};
	
	private Constipation() {
	    addRenderer(FoodInfo.class, Constipation::renderConstipation);
	}
	
	public void update(ResData t, double a) {
	    prev:
	    {
		for (Iterator<Data> i = els.iterator(); i.hasNext(); ) {
		    Data el = i.next();
		    if(!Utils.eq(el.rd, t))
			continue;
		    if(a == 1.0)
			i.remove();
		    else
			el.update(a);
		    break prev;
		}
		els.add(new Data(t, a));
	    }
	    FoodInfo.resettts();
	    order();
	}
	
	private void order() {
	    int n = els.size();
	    order = new Integer[n];
	    for (int i = 0; i < n; i++)
		order[i] = i;
	    Arrays.sort(order, (a, b) -> (ecmp.compare(els.get(a), els.get(b))));
	}
	
	private static final Comparator<Data> ecmp = (a, b) -> {
	    if(a.value < b.value)
		return (-1);
	    else if(a.value > b.value)
		return (1);
	    return (0);
	};
	
	private static BufferedImage renderConstipation(CharacterInfo.Constipation.Data data) {
	    int h = 14;
	    BufferedImage img = data.res.get().layer(Resource.imgc).img;
	    String nm = data.res.get().layer(Resource.tooltip).t;
	    Color col = color(data.value);
	    Text rnm = RichText.render(String.format("%s: $col[%d,%d,%d]{%s%%}", nm, col.getRed(), col.getGreen(), col.getBlue(), Utils.odformat2(100 * data.value, 2)), 0);
	    BufferedImage tip = TexI.mkbuf(new Coord(h + 5 + rnm.sz().x, h));
	    Graphics g = tip.getGraphics();
	    g.drawImage(convolvedown(img, new Coord(h, h), tflt), 0, 0, null);
	    g.drawImage(rnm.img, h + 5, ((h - rnm.sz().y) / 2) + 1, null);
	    g.dispose();
	    
	    return tip;
	}
	
	public Data get(int i) {
	    return els.size() > i ? els.get(i) : null;
	}
	
	public static class Data {
	    private final Map<Class, BufferedImage> renders = new HashMap<>();
	    public final Indir<Resource> res;
	    private ResData rd;
	    public double value;
	    
	    public Data(ResData rd, double value) {
		this.rd = rd;
		this.res = rd.res;
		this.value = value;
	    }
	    
	    public void update(double a) {
		value = a;
		renders.clear();
	    }
	    
	    private BufferedImage render(Class type, Function<Data, BufferedImage> renderer) {
		if(!renders.containsKey(type)) {
		    renders.put(type, renderer.apply(this));
		}
		return renders.get(type);
	    }
	}
	
	private final Map<Class, Function<Data, BufferedImage>> renderers = new HashMap<>();
	
	public void addRenderer(Class type, Function<Data, BufferedImage> renderer) {
	    renderers.put(type, renderer);
	}
	
	public boolean hasRenderer(Class type) {
	    return renderers.containsKey(type);
	}
	
	public BufferedImage render(Class type, Data data) {
	    try {
		return renderers.containsKey(type) ? data.render(type, renderers.get(type)) : null;
	    } catch (Loading ignored) {}
	    return null;
	}
    }
}
