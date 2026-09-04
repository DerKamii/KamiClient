package me.ender.alchemy;

import haven.CFG;
import haven.GItem;
import haven.ItemInfo;

import java.util.HashSet;
import java.util.Set;

public class EffectFilter extends AlchemyItemFilter {
    
    private final String trackedRes;
    private final Set<String> testedIngredients;
    
    public EffectFilter(Ingredient ingredient, Set<Effect> effects, Set<String> ingredients) {
	this(null, ingredient, effects, ingredients);
    }

    public EffectFilter(String trackedRes, Ingredient ingredient, Set<Effect> effects, Set<String> ingredients) {
	super(ingredient, effects);
	this.trackedRes = trackedRes;
	this.testedIngredients = ingredients;
    }
    
    @Override
    public boolean matches(GItem item) {
	String res = AlchemyData.getAlchemyRes(item);
	String genus = item.ui.gui.genus;
	if(!AlchemyData.isValidIngredient(res, genus)) {return false;}
	if(trackedRes != null && (trackedRes.equals(res) || AlchemyData.containsIngredient(res, trackedRes))) {return false;}
	if(testedIngredients.contains(res)) {return false;}
	
	Set<Effect> effects = new HashSet<>();
	
	for (ItemInfo info : item.info()) {
	    AlchemyData.tryAddIngredientEffect(effects, info);
	}
	
	if(!testedEffects.containsAll(effects)) {return true;}
	
	if(!CFG.ALCHEMY_DEEP_EFFECT_TRACK.get()) {return false;}
	
	if(effects.size() >= AlchemyData.MAX_EFFECTS) {return false;}
	
	return !testedEffects.containsAll(AlchemyData.untestedEffects(res, genus));
    }
}
