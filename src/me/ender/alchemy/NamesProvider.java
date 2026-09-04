package me.ender.alchemy;

import haven.*;
import me.ender.ClientUtils;

import java.util.HashMap;
import java.util.Map;

public class NamesProvider implements Disposable {
    private static final Tex LOADING = Text.render("???").tex();
    private final Map<String, Tex> cache = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private final int width;

    public NamesProvider(int width) {
	this.width = width;
    }

    public String name(String res) {
	if(res != null && (res.startsWith(AlchemyData.HERBAL_GRIND_PREFIX) || res.startsWith(AlchemyData.MINERAL_CALCINATION_PREFIX))) {
	    return names.computeIfAbsent(res, r -> {
		String prefix = r.startsWith(AlchemyData.HERBAL_GRIND_PREFIX) ? AlchemyData.HERBAL_GRIND_PREFIX : AlchemyData.MINERAL_CALCINATION_PREFIX;
		String sub = r.substring(prefix.length());
		String[] parts = sub.split("\\+");
		String n1 = ClientUtils.loadPrettyResName(parts[0]);
		String n2 = parts.length > 1 ? ClientUtils.loadPrettyResName(parts[1]) : "";
		return n1 + " + " + n2;
	    });
	}
	return names.computeIfAbsent(new Effect(res).res, ClientUtils::loadPrettyResName);
    }

    public Tex tex(String res) {
	try {
	    return cache.computeIfAbsent(res, this::render);
	} catch (Loading e) {
	    return LOADING;
	}
    }

    public Tex tex(Effect item) {
	try {
	    Tex tex = cache.getOrDefault(item.raw, null);
	    if(tex == null) {
		tex = new TexI(item.ingredientInfo().alchtip());
		cache.put(item.raw, tex);
	    }
	    return tex;
	} catch (Loading e) {
	    return LOADING;
	}
    }

    private Tex render(String res) {
	if(res != null && res.startsWith(AlchemyData.HERBAL_GRIND_PREFIX)) {
	    String name = name(res);
	    try {
		return RichText.render(String.format("$img[gfx/invobjs/herbalgrind,h=16,c] %s", name), width).tex();
	    } catch (Exception ignore) {}
	    return RichText.render(String.format("$img[gfx/invobjs/missing,h=16,c] %s", name), width).tex();
	}
	if(res != null && res.startsWith(AlchemyData.MINERAL_CALCINATION_PREFIX)) {
	    String name = name(res);
	    try {
		return RichText.render(String.format("$img[gfx/invobjs/mineralcalcination,h=16,c] %s", name), width).tex();
	    } catch (Exception ignore) {}
	    return RichText.render(String.format("$img[gfx/invobjs/missing,h=16,c] %s", name), width).tex();
	}
	Effect effect = new Effect(res);
	if(effect.type != null) {
	    return new TexI(effect.ingredientInfo().alchtip());
	}
	String name = name(res);
	try {
	    return RichText.render(String.format("$img[%s,h=16,c] %s", res, name), width).tex();
	} catch (Exception ignore) {
	}
	return RichText.render(String.format("$img[gfx/invobjs/missing,h=16,c] %s", name), width).tex();
    }

    public int compare(String o1, String o2) {
	boolean p1 = o1 != null && (o1.startsWith(AlchemyData.HERBAL_GRIND_PREFIX) || o1.startsWith(AlchemyData.MINERAL_CALCINATION_PREFIX));
	boolean p2 = o2 != null && (o2.startsWith(AlchemyData.HERBAL_GRIND_PREFIX) || o2.startsWith(AlchemyData.MINERAL_CALCINATION_PREFIX));
	if(p1 != p2) {
	    return p1 ? 1 : -1;
	}
	if(p1 && p2) {
	    boolean hg1 = o1.startsWith(AlchemyData.HERBAL_GRIND_PREFIX);
	    boolean hg2 = o2.startsWith(AlchemyData.HERBAL_GRIND_PREFIX);
	    if(hg1 != hg2) {
		return hg1 ? -1 : 1;
	    }
	}
	return name(o1).compareToIgnoreCase(name(o2));
    }

    @Override
    public void dispose() {
	names.clear();
	cache.values().forEach(Tex::dispose);
	cache.clear();
    }
}
