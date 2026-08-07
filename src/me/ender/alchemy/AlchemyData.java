package me.ender.alchemy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import haven.*;
import haven.res.ui.tt.attrmod.AttrMod;
import haven.res.ui.tt.attrmod.Entry;
import haven.res.ui.tt.attrmod.Mod;
import haven.res.ui.tt.attrmod.resattr;
import haven.rx.Reactor;
import me.ender.Reflect;

import java.util.*;
import java.util.stream.Collectors;

public class AlchemyData {
    private static final String INGREDIENTS_JSON = "ingredients.json";
    private static final String ELIXIRS_JSON = "elixirs.json";
    private static final String ALL_INGREDIENTS_JSON = "all_ingredients.json";
    private static final String COMBOS_JSON = "combos.json";
    private static final String EFFECTS_JSON = "all_effects.json";
    
    public static final String INGREDIENTS_UPDATED = "ALCHEMY:INGREDIENTS:UPDATED";
    public static final String ELIXIRS_UPDATED = "ALCHEMY:ELIXIRS:UPDATED";
    public static final String COMBOS_UPDATED = "ALCHEMY:COMBOS:UPDATED";
    public static final String EFFECTS_UPDATED = "ALCHEMY:EFFECTS:UPDATED";
    
    
    public static final String HERBAL_GRIND = "/herbalgrind";
    public static final String LYE_ABLUTION = "/lyeablution";
    public static final String MINERAL_CALCINATION = "/mineralcalcination";
    public static final String MEASURED_DISTILLATE = "/measureddistillate";
    public static final String FIERY_COMBUSTION = "/fierycombustion";
    
    private static final Gson GSON = new GsonBuilder()
	.registerTypeAdapter(Effect.class, new Effect.Adapter())
	.create();
    public static final int MAX_EFFECTS = 4;
    
    //Genus of loaded data
    private static String initializedIngredients = null;
    private static String initializedElixirs = null;
    private static String initializedCombos = null;
    private static String initializedEffects = null;
    
    private static final Map<String, Ingredient> INGREDIENTS = new HashMap<>();
    private static final Set<Elixir> ELIXIRS = new HashSet<>();
    private static final Set<String> INGREDIENT_LIST = new HashSet<>();
    private static final Map<String, Set<String>> COMBOS = new HashMap<>();
    private static final HashSet<Effect> EFFECTS = new HashSet<>();
    
    
    private static void initIngredients(String genus) {
	if(Objects.equals(initializedIngredients, genus)) {return;}
	initializedIngredients = genus;
	INGREDIENTS.clear();
	
	loadIngredients(Config.loadFSFile(INGREDIENTS_JSON, genus));
    }
    
    private static void initElixirs(String genus) {
	if(Objects.equals(initializedElixirs, genus)) {return;}
	initializedElixirs = genus;
	ELIXIRS.clear();

	// KamiClient: saves go through saveFile(..., genus) which writes to world-<genus>/elixirs.json,
	// so the read must be genus-scoped too. Upstream used loadFile() here, which only ever looked
	// at the root path and silently dropped per-world elixirs across restarts.
	loadElixirs(Config.loadFSFile(ELIXIRS_JSON, genus));
    }

    private static void initCombos(String genus) {
	if(Objects.equals(initializedCombos, genus)) {return;}
	initializedCombos = genus;
	INGREDIENT_LIST.clear();
	COMBOS.clear();

	loadIngredientList(Config.loadJarFile(ALL_INGREDIENTS_JSON));
	loadIngredientList(Config.loadFSFile(ALL_INGREDIENTS_JSON, genus));
	// KamiClient: same fix as initElixirs — combos are saved per-genus, so read them per-genus.
	loadCombos(Config.loadFSFile(COMBOS_JSON, genus));
    }
    
    private static void initEffects(String genus) {
	if(Objects.equals(initializedEffects, genus)) {return;}
	initializedEffects = genus;
	EFFECTS.clear();
	
	loadEffectList(Config.loadJarFile(EFFECTS_JSON));
	loadEffectList(Config.loadFSFile(EFFECTS_JSON, genus));
	
	boolean changed = false;
	
	initIngredients(genus);
	for (Ingredient ingredient : INGREDIENTS.values()) {
	    changed = tryAddUnknownEffects(ingredient, genus) || changed;
	}
	
	initElixirs(genus);
	for (Elixir elixir : ELIXIRS) {
	    changed = tryAddUnknownEffects(elixir, genus) || changed;
	}
	
	if(changed) {saveEffects(genus);}
    }
    
    private static void loadIngredients(String json) {
	if(json == null) {return;}
	try {
	    Map<String, Ingredient> tmp = GSON.fromJson(json, new TypeToken<Map<String, Ingredient>>() {
	    }.getType());
	    for (Map.Entry<String, Ingredient> entry : tmp.entrySet()) {
		String res = entry.getKey();
		INGREDIENTS.put(res, new Ingredient(entry.getValue().effects, INGREDIENTS.get(res)));
	    }
	} catch (Exception ignore) {}
    }
    
    private static void loadElixirs(String json) {
	if(json == null) {return;}
	try {
	    Set<Elixir> tmp = GSON.fromJson(json, new TypeToken<Set<Elixir>>() {
	    }.getType());
	    ELIXIRS.addAll(tmp);
	} catch (Exception ignore) {}
    }
    
    private static void loadIngredientList(String json) {
	if(json == null) {return;}
	try {
	    Set<String> tmp = GSON.fromJson(json, new TypeToken<Set<String>>() {
	    }.getType());
	    INGREDIENT_LIST.addAll(tmp);
	} catch (Exception ignore) {}
    }
    
    private static void loadCombos(String json) {
	if(json == null) {return;}
	try {
	    Map<String, Set<String>> tmp = GSON.fromJson(json, new TypeToken<Map<String, Set<String>>>() {
	    }.getType());
	    for (Map.Entry<String, Set<String>> entry : tmp.entrySet()) {
		String key = entry.getKey();
		Set<String> combos = COMBOS.computeIfAbsent(key, k -> new HashSet<>());
		combos.addAll(entry.getValue());
	    }
	} catch (Exception ignore) {}
    }
    
    private static void loadEffectList(String json) {
	if(json == null) {return;}
	try {
	    Set<Effect> tmp = GSON.fromJson(json, new TypeToken<Set<Effect>>() {
	    }.getType());
	    EFFECTS.addAll(tmp);
	} catch (Exception ignore) {}
    }
    
    public static void saveIngredients(String genus) {
	Config.saveFile(INGREDIENTS_JSON, GSON.toJson(INGREDIENTS), genus);
    }
    
    private static void saveElixirs(String genus) {
	Config.saveFile(ELIXIRS_JSON, GSON.toJson(ELIXIRS), genus);
    }
    
    private static void saveIngredientList(String genus) {
	Config.saveFile(ALL_INGREDIENTS_JSON, GSON.toJson(INGREDIENT_LIST), genus);
    }
    
    private static void saveCombos(String genus) {
	Config.saveFile(COMBOS_JSON, GSON.toJson(COMBOS), genus);
    }
    
    private static void saveEffects(String genus) {
	Config.saveFile(EFFECTS_JSON, GSON.toJson(EFFECTS), genus);
    }
    
    public static void autoProcess(GItem item) {
	if(!CFG.ALCHEMY_AUTO_PROCESS.get()) {return;}
	if(item.ui.gui.getchild(AlchemyWnd.class) != null || item.ui.gui.getchild(TrackWnd.class) != null) {
	    process(item, false);
	}
    }
    
    public static void process(GItem item, boolean storeRecipe) {
	String genus = item.ui.gui.genus;
	String res = item.resname();
	List<ItemInfo> infos = item.info();
	double q = item.quality();
	double qc = q > 0 ? 1d / Math.sqrt(10 * q) : 1d;
	
	ItemInfo.Contents contents = ItemInfo.find(ItemInfo.Contents.class, infos);
	if(contents != null) {infos = contents.sub;}
	
	List<Effect> effects = new LinkedList<>();
	boolean isElixir = false;
	Recipe recipe = null;
	
	for (ItemInfo info : infos) {
	    if(Reflect.is(info, "Elixir")) {
		isElixir = true;
		//noinspection unchecked
		List<ItemInfo> effs = (List<ItemInfo>) Reflect.getFieldValue(info, "effs");
		for (ItemInfo eff : effs) {
		    tryAddElixirEffect(qc, effects, eff);
		}
	    } else if(info instanceof haven.res.ui.tt.alch.recipe.Recipe) {
		recipe = Recipe.from(res, (haven.res.ui.tt.alch.recipe.Recipe) info);
	    } else {
		tryAddIngredientEffect(effects, info);
	    }
	}
	
	boolean effectsChanged = false;
	if(isElixir && recipe != null) {
	    //TODO: option to ignore bad-only elixirs?
	    Elixir elixir = new Elixir(recipe, effects);
	    if(storeRecipe) {
		initElixirs(genus);
		ELIXIRS.add(elixir);
		saveElixirs(genus);
		Reactor.event(ELIXIRS_UPDATED);
	    }
	    effectsChanged = tryAddUnknownEffects(elixir, genus);
	    updateCombos(elixir, genus);
	} else if(!isElixir && !effects.isEmpty() && isNatural(res)) {
	    initIngredients(genus);
	    Ingredient base = INGREDIENTS.get(res);
	    Ingredient ingredient = new Ingredient(effects, base);
	    if(base == null || !Objects.equals(ingredient, base)) {
		INGREDIENTS.put(res, ingredient);
		saveIngredients(genus);
		updateIngredientList(res, genus);
		Reactor.event(INGREDIENTS_UPDATED);
	    }
	    effectsChanged = tryAddUnknownEffects(ingredient, genus);
	}
	
	if(effectsChanged) {
	    saveEffects(genus);
	    Reactor.event(EFFECTS_UPDATED);
	}
    }
    
    private static void updateIngredientList(String ingredient, String genus) {
	initCombos(genus);
	if(INGREDIENT_LIST.add(ingredient)) {
	    saveIngredientList(genus);
	    Reactor.event(COMBOS_UPDATED);
	}
    }
    
    private static void updateCombos(Elixir elixir, String genus) {
	List<String> natural = elixir.recipe.ingredients.stream()
	    .map(i -> i.res)
	    .filter(AlchemyData::isNatural)
	    .collect(Collectors.toList());
	
	if(natural.isEmpty()) {return;}
	initCombos(genus);
	boolean listUpdated = false;
	boolean combosUpdated = false;
	for (String ingredient : natural) {
	    if(INGREDIENT_LIST.add(ingredient)) {listUpdated = true;}
	    Set<String> combos = COMBOS.computeIfAbsent(ingredient, k -> new HashSet<>());
	    if(combos.addAll(natural)) {combosUpdated = true;}
	}
	
	if(listUpdated) {saveIngredientList(genus);}
	if(combosUpdated) {saveCombos(genus);}
	
	if(listUpdated || combosUpdated) {Reactor.event(COMBOS_UPDATED);}
    }
    
    public static List<String> ingredients(String genus) {
	initIngredients(genus);
	return new ArrayList<>(INGREDIENTS.keySet());
    }
    
    public static Ingredient ingredient(String res, String genus) {
	initIngredients(genus);
	return INGREDIENTS.getOrDefault(res, null);
    }
    
    public static List<Elixir> elixirs(String genus) {
	initElixirs(genus);
	return ELIXIRS.stream().sorted().collect(Collectors.toList());
    }
    
    public static void rename(Elixir elixir, String name, String genus) {
	initElixirs(genus);
	elixir.name(name);
	saveElixirs(genus);
	Reactor.event(ELIXIRS_UPDATED);
    }
    
    public static void remove(Elixir elixir, String genus) {
	initElixirs(genus);
	ELIXIRS.remove(elixir);
	saveElixirs(genus);
	Reactor.event(ELIXIRS_UPDATED);
    }
    
    public static List<String> allIngredients(String genus) {
	initCombos(genus);
	return new ArrayList<>(INGREDIENT_LIST);
    }
    
    public static Set<String> combos(String target, String genus) {
	initCombos(genus);
	return COMBOS.getOrDefault(target, Collections.emptySet());
    }
    
    public static Set<Effect> effects(String genus) {
	initEffects(genus);
	return EFFECTS;
    }
    
    public static Set<Effect> testedEffects(String res, String genus) {
	if(res == null) {return Collections.emptySet();}
	initEffects(genus);
	Ingredient ingredient = ingredient(res, genus);
	Set<Effect> tested;
	if(ingredient != null) {
	    if(ingredient.effects.size() == MAX_EFFECTS) {return effects(genus);}
	    tested = new HashSet<>(ingredient.effects);
	} else {
	    tested = new HashSet<>();
	}
	for (String combo : AlchemyData.combos(res, genus)) {
	    ingredient = AlchemyData.ingredient(combo, genus);
	    if(ingredient == null) {continue;}
	    tested.addAll(ingredient.effects);
	}
	return tested;
    }
    
    public static Set<Effect> untestedEffects(String res, String genus) {
	Set<Effect> tested = testedEffects(res, genus);
	if(tested.isEmpty()) {return Collections.emptySet();}
	
	HashSet<Effect> effects = new HashSet<>(effects(genus));
	if(effects.removeAll(tested)) {
	    return effects;
	}
	return Collections.emptySet();
    }
    
    public static boolean tryAddUnknownEffects(Ingredient ingredient, String genus) {
	boolean changed = false;
	initEffects(genus);
	for (Effect effect : ingredient.effects) {
	    changed = EFFECTS.add(new Effect(effect.type, effect.res)) || changed;
	}
	return changed;
    }
    
    public static boolean tryAddUnknownEffects(Elixir elixir, String genus) {
	boolean changed = false;
	initEffects(genus);
	for (Effect effect : elixir.effects) {
	    if(Effect.WOUND.equals(effect.type)) {continue;}
	    changed = EFFECTS.add(new Effect(effect.type, effect.res)) || changed;
	}
	return changed;
    }
    
    public static Tex tex(Collection<Effect> effects) {
	try {
	    List<ItemInfo> tips = Effect.ingredientInfo(effects);
	    if(tips.isEmpty()) {return null;}
	    return new TexI(ItemInfo.longtip(tips));
	    
	} catch (Loading ignore) {}
	return null;
    }
    
    public static void tryAddIngredientEffect(Collection<Effect> effects, ItemInfo info) {
	Effect effect = Effect.from(info);
	if(effect != null) {
	    effects.add(effect);
	}
    }
    
    public static void tryAddElixirEffect(double qc, Collection<Effect> effects, ItemInfo info) {
	if(info instanceof AttrMod) {
	    for (Entry e : ((AttrMod) info).tab) {
		if(!(e instanceof Mod)) {continue;}
		Mod mod = (Mod) e;
		if(!(mod.attr instanceof resattr)) {continue;}
		long a = Math.round(qc * mod.mod);
		effects.add(new Effect(Effect.BUFF, ((resattr) mod.attr).res.name, Long.toString(a)));
	    }
	} else if(Reflect.is(info, "HealWound")) {
	    //this is from elixir, it uses different resource and has value
	    //noinspection unchecked
	    Indir<Resource> res = (Indir<Resource>) Reflect.getFieldValue(info, "res");
	    long a = Math.round(qc * Reflect.getFieldValueInt(info, "a"));
	    effects.add(new Effect(Effect.HEAL, res, Long.toString(a)));
	} else if(Reflect.is(info, "AddWound")) {
	    //this is from elixir
	    //noinspection unchecked
	    Indir<Resource> res = (Indir<Resource>) Reflect.getFieldValue(info, "res");
	    //TODO: try to find base wound magnitude
	    int a = Reflect.getFieldValueInt(info, "a");
	    effects.add(new Effect(Effect.WOUND, res, Long.toString(a)));
	}
	//TODO: detect less/more time effects in elixirs?
    }
    
    public static boolean isNatural(String res) {
	return !res.contains(HERBAL_GRIND)
	    && !res.contains(LYE_ABLUTION)
	    && !res.contains(MINERAL_CALCINATION)
	    && !res.contains(MEASURED_DISTILLATE)
	    && !res.contains(FIERY_COMBUSTION);
    }
    
    public static boolean isMineral(String res) {
	return GobIconCategoryList.GobCategory.isRock(res) || GobIconCategoryList.GobCategory.isOre(res);
    }
}
