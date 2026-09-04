package me.ender.alchemy;

import haven.*;
import haven.Label;
import me.ender.ClientUtils;
import me.ender.ItemHelpers;

import java.awt.*;
import java.awt.image.BufferedImage;

public class TrackWnd extends WindowX implements DTarget {
    private final Label label;
    Tex image;
    String res = null;
    private boolean trackEffects;
    private final Coord IMG_C;
    
    private TrackWnd() {
	super(Coord.of(AlchemyWnd.LIST_W, UI.scale(32)), "Ingredient Track");
	justclose = true;
	label = new Label("");
	IMG_C = add(label).pos("bl");
	updateLabel();
	
	listen(AlchemyData.COMBOS_UPDATED, this::update);
	listen(AlchemyData.EFFECTS_UPDATED, this::update);
	listen(AlchemyData.INGREDIENTS_UPDATED, this::update);
    }
    
    private static final BufferedImage IMG_MATCH = RichText.render(RichText.color("$font[monospaced]{✓}", new Color(160, 255, 160)), 0).img;
    private static final BufferedImage IMG_NO_MATCH = RichText.render(RichText.color("$font[monospaced]{✗}", new Color(255, 160, 160)), 0).img;
    private static final BufferedImage IMG_UNKNOWN = RichText.render(RichText.color("$font[monospaced]{?}", new Color(96, 255, 255)), 0).img;
    
    public static BufferedImage getMark(Effect effect) {
	if(effect == null) {return null;}
	
	AlchemyItemFilter filter = GItem.alchemyFilter;
	if(filter == null) {return null;}
	
	Ingredient tracked = filter.tracked();
	
	if(tracked != null && tracked.effects.contains(effect)) {
	    return IMG_MATCH;
	} else if(filter.testedEffects().contains(effect)) {
	    return IMG_NO_MATCH;
	}
	
	return IMG_UNKNOWN;
    }
    
    public static void track(UI ui, String target, boolean trackEffects, boolean update, NamesProvider namesProvider) {
	TrackWnd tracker = ui.gui.getchild(TrackWnd.class);
	
	if(tracker == null) {
	    if(update) {return;}
	    tracker = ui.gui.add(new TrackWnd(), ClientUtils.getScreenCenter(ui));
	}
	
	if(tracker.trackEffects != trackEffects) {
	    tracker.trackEffects = trackEffects;
	    tracker.updateLabel();
	}
	
	if(target == null) {
	    tracker.highlight(null, null);
	} else {
	    tracker.highlight(target, namesProvider.tex(target));
	}
    }
    
    private void updateLabel() {
	if(trackEffects) {
	    label.settext("Highlight untested effects for:");
	} else {
	    label.settext("Highlight untested combinations for:");
	}
    }
    
    @Override
    public boolean drop(Drop ev) {
	AlchemyData.process(ev.src.item, !CFG.ALCHEMY_LIMIT_RECIPE_SAVE.get());
	return true;
    }
    
    private void update() {
	if(res != null) {
	    highlight(res, image);
	}
    }
    
    @Override
    public void dispose() {
	GItem.setAlchemyFilter(null);
	ItemHelpers.invalidateIngredientTooltips(ui);
	super.dispose();
    }
    
    @Override
    public void cdraw(GOut g) {
	if(image != null) {
	    g.image(image, IMG_C);
	}
	super.cdraw(g);
    }
    
    public void highlight(String res, Tex img) {
	image = img;
	this.res = res;
	String genus = ui.gui.genus;
	if(res == null) {
	    GItem.setAlchemyFilter(null);
	} else if(trackEffects) {
	    GItem.setAlchemyFilter(new EffectFilter(res, AlchemyData.ingredient(res, genus), AlchemyData.testedEffects(res, genus), AlchemyData.combos(res, genus)));
	} else {
	    GItem.setAlchemyFilter(new ComboFilter(res, AlchemyData.ingredient(res, genus), AlchemyData.testedEffects(res, genus), AlchemyData.combos(res, genus)));
	}
	ItemHelpers.invalidateIngredientTooltips(ui);
    }
}
