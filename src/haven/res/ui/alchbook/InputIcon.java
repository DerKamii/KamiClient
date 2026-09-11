/* Preprocessed source code */
package haven.res.ui.alchbook;

import java.util.*;
import java.util.function.*;
import haven.*;
import haven.MenuGrid.*;
import java.awt.Color;
import java.awt.image.BufferedImage;
import static haven.PType.*;

@haven.FromResource(name = "ui/alchbook", version = 4)
public class InputIcon extends Icon implements ItemInfo.Owner {
    public final Book book;
    public final Input inp;
    public final Consumer<Input> action;

    public InputIcon(int sz, Book book, Input inp, Consumer<Input> action) {
	super(sz, inp.type);
	this.book = book;
	this.inp = inp;
	this.action = action;
    }
    public InputIcon(int sz, Book book, Input inp) {
	this(sz, book, inp, null);
    }

    private List<ItemInfo> info = null;
    public List<ItemInfo> info() {
	if(info == null) {
	    List<ItemInfo> info = new ArrayList<>();
	    info.add(new ItemInfo.Name(this, spec.name()));
	    KnownEffects ik = book.el.knowledge.get(inp);
	    if(ik != null) {
		for(EffectInfo ei : ik.effs)
		    info.add((ItemInfo)ei);
	    }
	    if(ik == null || ik.effs.isEmpty()) {
		String genus = (ui != null && ui.gui != null) ? ui.gui.genus : ((book.ui != null && book.ui.gui != null) ? book.ui.gui.genus : null);
		if(genus != null) {
		    String resKey = null;
		    if(inp.sub != null && inp.sub.size() == 2) {
			try {
			    String sub1 = inp.sub.get(0).type.resource().name;
			    String sub2 = inp.sub.get(1).type.resource().name;
			    if(inp.type != null && inp.type.res != null && inp.type.resource().name.contains(me.ender.alchemy.AlchemyData.MINERAL_CALCINATION)) {
				resKey = me.ender.alchemy.AlchemyData.makeCalcinationKey(sub1, sub2);
			    } else {
				resKey = me.ender.alchemy.AlchemyData.makeGrindKey(sub1, sub2);
			    }
			} catch(Loading ignore) {}
		    } else if(inp.type != null && inp.type.res != null) {
			try {
			    resKey = inp.type.resource().name;
			} catch(Loading ignore) {}
		    }
		    if(resKey != null) {
			me.ender.alchemy.Ingredient ingr = me.ender.alchemy.AlchemyData.ingredient(resKey, genus);
			if(ingr != null && ingr.effects != null && !ingr.effects.isEmpty()) {
			    info.addAll(me.ender.alchemy.Effect.ingredientInfo(ingr.effects));
			}
		    }
		}
	    }
	    this.info = info;
	}
	return(this.info);
    }

    public <T> T context(Class<T> cl) {
	return(wdgctx.context(cl, this));
    }

    private Tex tip = null;
    public Object tooltip(Coord c, Widget prev) {
	if(info != null && info.size() <= 1) {
	    info = null;
	    tip = null;
	}
	if(tip == null)
	    tip = new TexI(ItemInfo.longtip(info()));
	return(tip);
    }

    public boolean mousedown(MouseDownEvent ev) {
	if((action != null) && (ev.b == 1)) {
	    action.accept(inp);
	    return(true);
	}
	return(super.mousedown(ev));
    }
}
