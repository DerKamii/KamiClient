package me.ender.alchemy;

import haven.GItem;

import java.util.Set;

public class ComboFilter extends AlchemyItemFilter {
    private final String trackedRes;
    private final Set<String> tested;
    
    public ComboFilter(Ingredient ingredient, Set<Effect> testedEffects, Set<String> testedIngredients) {
	this(null, ingredient, testedEffects, testedIngredients);
    }

    public ComboFilter(String trackedRes, Ingredient ingredient, Set<Effect> testedEffects, Set<String> testedIngredients) {
	super(ingredient, testedEffects);
	this.trackedRes = trackedRes;
	this.tested = testedIngredients;
    }
    
    public boolean matches(GItem item) {
	String res = AlchemyData.getAlchemyRes(item);
	if(!AlchemyData.isValidIngredient(res, item.ui.gui.genus)) {
	    return false;
	}
	if(trackedRes != null && !AlchemyData.canCombine(trackedRes, res)) {
	    return false;
	}
	return !tested.contains(res);
    }
}
