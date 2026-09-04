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
	.setPrettyPrinting()
	.create();
    public static final int MAX_EFFECTS = 4;
    
    //Genus of loaded data
    private static String initializedIngredients = null;
    private static String initializedElixirs = null;
    private static String initializedCombos = null;
    private static String initializedEffects = null;
    
    public static class IngredientDef {
	public String ingredient_type;
	public String herbal_grind_craftable;
	public String mineral_calcination_craftable;

	public IngredientDef() {}

	public IngredientDef(String type) {
	    this.ingredient_type = type;
	}
    }

    public static final String HERBAL_GRINDS_JSON = "herbal_grinds.json";
    public static final String HERBAL_GRIND_PREFIX = "gfx/invobjs/herbalgrind:";
    private static String initializedHerbalGrinds = null;
    private static final Map<String, Ingredient> HERBAL_GRINDS = new LinkedHashMap<>();
    private static final List<String> HERBAL_GRIND_KEYS = new ArrayList<>();

    public static final String MINERAL_CALCINATIONS_JSON = "mineral_calcinations.json";
    public static final String MINERAL_CALCINATION_PREFIX = "gfx/invobjs/mineralcalcination:";
    private static String initializedMineralCalcinations = null;
    private static final Map<String, Ingredient> MINERAL_CALCINATIONS = new LinkedHashMap<>();
    private static final List<String> MINERAL_CALCINATION_KEYS = new ArrayList<>();

    private static final Map<String, Ingredient> INGREDIENTS = new HashMap<>();
    private static final Set<Elixir> ELIXIRS = new HashSet<>();
    private static final Set<String> INGREDIENT_LIST = new HashSet<>();
    private static final Map<String, IngredientDef> INGREDIENT_DEFS = new LinkedHashMap<>();
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
	INGREDIENT_DEFS.clear();
	COMBOS.clear();

	loadIngredientList(Config.loadJarFile(ALL_INGREDIENTS_JSON));
	String fsJson = Config.loadFSFile(ALL_INGREDIENTS_JSON, genus);
	boolean needMigration = false;
	if(fsJson != null) {
	    String trimmed = fsJson.trim();
	    if(!trimmed.startsWith("{")) {
		needMigration = true;
	    }
	}
	loadIngredientList(fsJson);
	if(needMigration && genus != null) {
	    saveIngredientList(genus);
	}
	// KamiClient: same fix as initElixirs — combos are saved per-genus, so read them per-genus.
	loadCombos(Config.loadFSFile(COMBOS_JSON, genus));
	initHerbalGrinds(genus);
	initMineralCalcinations(genus);
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
	    String trimmed = json.trim();
	    if(trimmed.startsWith("{")) {
		Map<String, IngredientDef> tmp = GSON.fromJson(json, new TypeToken<Map<String, IngredientDef>>() {
		}.getType());
		if(tmp != null) {
		    INGREDIENT_DEFS.putAll(tmp);
		    INGREDIENT_LIST.addAll(tmp.keySet());
		}
	    } else {
		Set<String> tmp = GSON.fromJson(json, new TypeToken<Set<String>>() {
		}.getType());
		if(tmp != null) {
		    for(String res : tmp) {
			INGREDIENT_DEFS.putIfAbsent(res, new IngredientDef(detectIngredientType(res)));
			INGREDIENT_LIST.add(res);
		    }
		}
	    }
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
	Config.saveFile(ALL_INGREDIENTS_JSON, GSON.toJson(INGREDIENT_DEFS), genus);
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
	} else if(!isElixir && res.contains(HERBAL_GRIND) && recipe != null && recipe.ingredients != null && recipe.ingredients.size() == 2) {
	    initHerbalGrinds(genus);
	    String sub1 = recipe.ingredients.get(0).res;
	    String sub2 = recipe.ingredients.get(1).res;
	    String grindKey = makeGrindKey(sub1, sub2);
	    if(!effects.isEmpty()) {
		Ingredient base = HERBAL_GRINDS.get(grindKey);
		Ingredient grind = new Ingredient(effects, base);
		if(base == null || !Objects.equals(grind, base)) {
		    HERBAL_GRINDS.put(grindKey, grind);
		    saveHerbalGrinds(genus);
		    Reactor.event(INGREDIENTS_UPDATED);
		}
		effectsChanged = tryAddUnknownEffects(grind, genus);
	    }
	} else if(!isElixir && (res.contains(MINERAL_CALCINATION) || res.contains("mineralcalcination")) && recipe != null && recipe.ingredients != null && recipe.ingredients.size() == 2) {
	    initMineralCalcinations(genus);
	    String sub1 = recipe.ingredients.get(0).res;
	    String sub2 = recipe.ingredients.get(1).res;
	    String calcKey = makeCalcinationKey(sub1, sub2);
	    if(!effects.isEmpty()) {
		Ingredient base = MINERAL_CALCINATIONS.get(calcKey);
		Ingredient calc = new Ingredient(effects, base);
		if(base == null || !Objects.equals(calc, base)) {
		    MINERAL_CALCINATIONS.put(calcKey, calc);
		    saveMineralCalcinations(genus);
		    Reactor.event(INGREDIENTS_UPDATED);
		}
		effectsChanged = tryAddUnknownEffects(calc, genus);
	    }
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
    
    public static String getAlchemyRes(Recipe r) {
	if(r != null && r.res != null) {
	    if(r.ingredients != null && r.ingredients.size() == 2) {
		String s1 = r.ingredients.get(0).res;
		String s2 = r.ingredients.get(1).res;
		if(r.res.contains(MINERAL_CALCINATION) || r.res.contains("mineralcalcination")) {
		    return makeCalcinationKey(s1, s2);
		} else if(r.res.contains(HERBAL_GRIND) || r.res.contains("herbalgrind")) {
		    return makeGrindKey(s1, s2);
		}
	    }
	}
	return (r != null) ? r.res : null;
    }

    public static String getAlchemyRes(GItem item) {
	if(item == null) {return null;}
	String res = item.resname();
	if(res != null && (res.contains(HERBAL_GRIND) || res.contains(MINERAL_CALCINATION) || res.contains("mineralcalcination") || res.contains("herbalgrind"))) {
	    try {
		for(ItemInfo info : item.info()) {
		    if(info instanceof haven.res.ui.tt.alch.recipe.Recipe) {
			Recipe r = Recipe.from(res, (haven.res.ui.tt.alch.recipe.Recipe) info);
			if(r.ingredients != null && r.ingredients.size() == 2) {
			    String s1 = r.ingredients.get(0).res;
			    String s2 = r.ingredients.get(1).res;
			    if(res.contains(MINERAL_CALCINATION) || res.contains("mineralcalcination")) {
				return makeCalcinationKey(s1, s2);
			    } else {
				return makeGrindKey(s1, s2);
			    }
			}
		    }
		}
	    } catch(Exception ignored) {}
	}
	return res;
    }

    public static boolean isValidIngredient(String res, String genus) {
	if(res == null) {return false;}
	if(res.startsWith(HERBAL_GRIND_PREFIX) || res.startsWith(MINERAL_CALCINATION_PREFIX)) {
	    return true;
	}
	return allIngredients(genus).contains(res);
    }

    public static boolean containsIngredient(String res, String target) {
	if(res != null && target != null) {
	    String sub = null;
	    if(res.startsWith(HERBAL_GRIND_PREFIX)) {
		sub = res.substring(HERBAL_GRIND_PREFIX.length());
	    } else if(res.startsWith(MINERAL_CALCINATION_PREFIX)) {
		sub = res.substring(MINERAL_CALCINATION_PREFIX.length());
	    }
	    if(sub != null) {
		for(String part : sub.split("\\+")) {
		    if(part.equals(target)) {
			return true;
		    }
		}
	    }
	}
	return false;
    }

    public static boolean canCombine(String res1, String res2) {
	if(res1 == null || res2 == null) {return false;}
	if(res1.equals(res2)) {return false;}
	if(containsIngredient(res1, res2) || containsIngredient(res2, res1)) {
	    return false;
	}
	boolean c1 = res1.startsWith(HERBAL_GRIND_PREFIX) || res1.startsWith(MINERAL_CALCINATION_PREFIX);
	boolean c2 = res2.startsWith(HERBAL_GRIND_PREFIX) || res2.startsWith(MINERAL_CALCINATION_PREFIX);
	if(c1 && c2) {
	    String sub1 = res1.substring(res1.indexOf(':') + 1);
	    String sub2 = res2.substring(res2.indexOf(':') + 1);
	    String[] parts1 = sub1.split("\\+");
	    String[] parts2 = sub2.split("\\+");
	    for(String p1 : parts1) {
		for(String p2 : parts2) {
		    if(p1.equals(p2)) {
			return false;
		    }
		}
	    }
	}
	return true;
    }

    private static void updateCombos(Elixir elixir, String genus) {
	List<String> components = elixir.recipe.ingredients.stream()
	    .map(AlchemyData::getAlchemyRes)
	    .filter(r -> r != null && (isNatural(r) || r.startsWith(HERBAL_GRIND_PREFIX) || r.startsWith(MINERAL_CALCINATION_PREFIX)))
	    .collect(Collectors.toList());
	
	if(components.isEmpty()) {return;}
	initCombos(genus);
	boolean listUpdated = false;
	boolean combosUpdated = false;
	for (String ingredient : components) {
	    if(isNatural(ingredient) && INGREDIENT_LIST.add(ingredient)) {
		INGREDIENT_DEFS.computeIfAbsent(ingredient, r -> new IngredientDef(detectIngredientType(r)));
		listUpdated = true;
	    }
	    Set<String> combos = COMBOS.computeIfAbsent(ingredient, k -> new HashSet<>());
	    if(combos.addAll(components)) {combosUpdated = true;}
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
	if(res != null) {
	    if(res.startsWith(HERBAL_GRIND_PREFIX)) {
		initHerbalGrinds(genus);
		return HERBAL_GRINDS.get(res);
	    } else if(res.startsWith(MINERAL_CALCINATION_PREFIX)) {
		initMineralCalcinations(genus);
		return MINERAL_CALCINATIONS.get(res);
	    }
	}
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
	List<String> result = new ArrayList<>(INGREDIENT_LIST);
	if(CFG.ALCHEMY_SHOW_GRINDS.get()) {
	    initHerbalGrinds(genus);
	    result.addAll(HERBAL_GRIND_KEYS);
	    initMineralCalcinations(genus);
	    result.addAll(MINERAL_CALCINATION_KEYS);
	}
	return result;
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
    
    public static String getIngredientType(String res) {
	IngredientDef def = INGREDIENT_DEFS.get(res);
	return (def != null && def.ingredient_type != null) ? def.ingredient_type : detectIngredientType(res);
    }

    public static String detectIngredientType(String res) {
	if(isMineral(res)) {
	    return "stone";
	}
	String lower = res.toLowerCase();
	if(lower.contains("shroom") || lower.contains("bolete") || lower.contains("chantrelle")
	    || lower.contains("lorchel") || lower.contains("puffball") || lower.contains("truffle")
	    || lower.contains("blewit") || lower.contains("snowtop") || lower.contains("stalagoom")
	    || lower.contains("champignon")) {
	    return "mushroom";
	}
	if(lower.contains("guano") || lower.contains("dovepoop") || lower.contains("sponge")) {
	    return "other";
	}
	return "herb";
    }

    public static boolean isStone(String res) {
	return "stone".equals(getIngredientType(res));
    }

    public static boolean isMushroom(String res) {
	return "mushroom".equals(getIngredientType(res));
    }

    public static boolean isHerb(String res) {
	return "herb".equals(getIngredientType(res));
    }

    public static boolean isHerbalGrindCraftable(String res) {
	IngredientDef def = INGREDIENT_DEFS.get(res);
	if(def != null && def.herbal_grind_craftable != null) {
	    return "yes".equalsIgnoreCase(def.herbal_grind_craftable);
	}
	return isHerb(res) || isMushroom(res);
    }

    public static boolean isMineralCalcinationCraftable(String res) {
	IngredientDef def = INGREDIENT_DEFS.get(res);
	if(def != null && def.mineral_calcination_craftable != null) {
	    return "yes".equalsIgnoreCase(def.mineral_calcination_craftable);
	}
	return isStone(res);
    }

    public static String makeGrindKey(String res1, String res2) {
	if(res1.compareTo(res2) <= 0) {
	    return HERBAL_GRIND_PREFIX + res1 + "+" + res2;
	} else {
	    return HERBAL_GRIND_PREFIX + res2 + "+" + res1;
	}
    }

    public static void initHerbalGrinds(String genus) {
	if(Objects.equals(initializedHerbalGrinds, genus)) {return;}
	initializedHerbalGrinds = genus;
	HERBAL_GRINDS.clear();
	HERBAL_GRIND_KEYS.clear();

	List<String> grindable = new ArrayList<>();
	for(String res : INGREDIENT_LIST) {
	    if(isHerbalGrindCraftable(res)) {
		grindable.add(res);
	    }
	}
	grindable.sort(String::compareTo);

	for(int i = 0; i < grindable.size(); i++) {
	    for(int j = i + 1; j < grindable.size(); j++) {
		HERBAL_GRIND_KEYS.add(makeGrindKey(grindable.get(i), grindable.get(j)));
	    }
	}

	loadHerbalGrinds(Config.loadFSFile(HERBAL_GRINDS_JSON, genus));
    }

    private static void loadHerbalGrinds(String json) {
	if(json == null) {return;}
	try {
	    Map<String, Ingredient> tmp = GSON.fromJson(json, new TypeToken<Map<String, Ingredient>>() {
	    }.getType());
	    if(tmp != null) {
		for(Map.Entry<String, Ingredient> entry : tmp.entrySet()) {
		    String res = entry.getKey();
		    HERBAL_GRINDS.put(res, new Ingredient(entry.getValue().effects, HERBAL_GRINDS.get(res)));
		}
	    }
	} catch (Exception ignore) {}
    }

    public static void saveHerbalGrinds(String genus) {
	Config.saveFile(HERBAL_GRINDS_JSON, GSON.toJson(HERBAL_GRINDS), genus);
    }

    public static String makeCalcinationKey(String res1, String res2) {
	if(res1.compareTo(res2) <= 0) {
	    return MINERAL_CALCINATION_PREFIX + res1 + "+" + res2;
	} else {
	    return MINERAL_CALCINATION_PREFIX + res2 + "+" + res1;
	}
    }

    public static void initMineralCalcinations(String genus) {
	if(Objects.equals(initializedMineralCalcinations, genus)) {return;}
	initializedMineralCalcinations = genus;
	MINERAL_CALCINATIONS.clear();
	MINERAL_CALCINATION_KEYS.clear();

	List<String> calcinable = new ArrayList<>();
	for(String res : INGREDIENT_LIST) {
	    if(isMineralCalcinationCraftable(res)) {
		calcinable.add(res);
	    }
	}
	calcinable.sort(String::compareTo);

	for(int i = 0; i < calcinable.size(); i++) {
	    for(int j = i + 1; j < calcinable.size(); j++) {
		MINERAL_CALCINATION_KEYS.add(makeCalcinationKey(calcinable.get(i), calcinable.get(j)));
	    }
	}

	loadMineralCalcinations(Config.loadFSFile(MINERAL_CALCINATIONS_JSON, genus));
    }

    private static void loadMineralCalcinations(String json) {
	if(json == null) {return;}
	try {
	    Map<String, Ingredient> tmp = GSON.fromJson(json, new TypeToken<Map<String, Ingredient>>() {
	    }.getType());
	    if(tmp != null) {
		for(Map.Entry<String, Ingredient> entry : tmp.entrySet()) {
		    String res = entry.getKey();
		    MINERAL_CALCINATIONS.put(res, new Ingredient(entry.getValue().effects, MINERAL_CALCINATIONS.get(res)));
		}
	    }
	} catch (Exception ignore) {}
    }

    public static void saveMineralCalcinations(String genus) {
	Config.saveFile(MINERAL_CALCINATIONS_JSON, GSON.toJson(MINERAL_CALCINATIONS), genus);
    }

    public static void reloadIngredientList(String genus) {
	String fsJson = Config.loadFSFile(ALL_INGREDIENTS_JSON, genus);
	if(fsJson != null) {
	    loadIngredientList(fsJson);
	}
    }

    public static void updateCraftableStatus(String res, String processType, boolean accepted, String genus) {
	initCombos(genus);
	reloadIngredientList(genus);
	String newStatus = accepted ? "yes" : "no";
	boolean changed = false;

	List<String> targets = new ArrayList<>();
	targets.add(res);
	if(res != null && res.matches(".+-(small|medium|large)$")) {
	    String base = res.substring(0, res.lastIndexOf('-'));
	    targets.add(base + "-small");
	    targets.add(base + "-medium");
	    targets.add(base + "-large");
	}

	for(String targetRes : targets) {
	    IngredientDef def = INGREDIENT_DEFS.get(targetRes);
	    if(def == null && targetRes.equals(res)) {
		def = new IngredientDef(detectIngredientType(targetRes));
		INGREDIENT_DEFS.put(targetRes, def);
	    }
	    if(def != null) {
		if("herbalgrind".equalsIgnoreCase(processType)) {
		    if(!Objects.equals(def.herbal_grind_craftable, newStatus)) {
			def.herbal_grind_craftable = newStatus;
			changed = true;
		    }
		} else if("mineralcalcination".equalsIgnoreCase(processType)) {
		    if(!Objects.equals(def.mineral_calcination_craftable, newStatus)) {
			def.mineral_calcination_craftable = newStatus;
			changed = true;
		    }
		}
	    }
	}

	if(changed) {
	    if(genus != null) {
		saveIngredientList(genus);
	    } else {
		Config.saveFile(ALL_INGREDIENTS_JSON, GSON.toJson(INGREDIENT_DEFS));
	    }
	    initializedHerbalGrinds = null;
	    initHerbalGrinds(genus);
	    initializedMineralCalcinations = null;
	    initMineralCalcinations(genus);
	    Reactor.event(INGREDIENTS_UPDATED);
	    Reactor.event(COMBOS_UPDATED);
	}
    }
}
