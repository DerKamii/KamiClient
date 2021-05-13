package haven;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import rx.functions.Action0;

import java.awt.*;
import java.util.*;
import java.util.List;

import static haven.TileHighlight.*;

public class ItemAutoDrop {
    private static final List<Action0> updateCallbacks = new LinkedList<>();
    private static final Map<String, Boolean> cfg = new HashMap<>();
    private static final String CFG_NAME = "item_drop.json";
    private static Gson gson;
    private static CFGWnd wnd;
    
    public static void addCallback(Action0 callback) {
	updateCallbacks.add(callback);
    }
    
    public static void removeCallback(Action0 callback) {
	updateCallbacks.remove(callback);
    }
    
    public static void toggle(UI ui) {
	tryInit();
	if(wnd == null) {
	    wnd = ui.gui.add(new CFGWnd(), ui.gui.invwnd.c);
	} else {
	    wnd.destroy();
	}
    }
    
    public static boolean needDrop(String name) {
	tryInit();
	return cfg.getOrDefault(name, false);
    }
    
    private static void toggle(String name) {
	boolean value = !cfg.getOrDefault(name, false);
	if(cfg.put(name, value) == null) {
	    save();
	    if(wnd != null) {wnd.addItem(name);}
	}
	if(value) {
	    updateCallbacks.forEach(Action0::call);
	}
    }
    
    private static boolean add(String name) {
	if(cfg.put(name, true) == null) {
	    save();
	    updateCallbacks.forEach(Action0::call);
	    return true;
	}
	return false;
    }
    
    
    private static void remove(String name) {
	if(cfg.remove(name)) {
	    save();
	}
    }
    
    private static void tryInit() {
	if(gson != null) {return;}
	gson = new GsonBuilder().create();
	try {
	    cfg.putAll(gson.fromJson(Config.loadFSFile(CFG_NAME), new TypeToken<Map<String, Boolean>>() {
	    }.getType()));
	} catch (Exception ignored) {}
    }
    
    private static void save() {
	if(gson != null) {
	    Config.saveFile(CFG_NAME, gson.toJson(cfg));
	}
    }
    
    private static class DropItem {
	private final String name;
	private final Tex tex;
	
	private DropItem(String name) {
	    //TODO: add I10N support
	    this.name = name;
	    this.tex = elf.render(this.name).tex();
	}
    }
    
    public static class CFGWnd extends WindowX implements DTarget2 {
	public static final String FILTER_DEFAULT = "Start typing to filter";
	public static final Comparator<DropItem> BY_NAME = Comparator.comparing(dropItem -> dropItem.name);
	public static final Coord chb_c = new Coord(0, elh / 2);
	public static final Coord text_c = new Coord(CheckBox.sbox.sz().x + UI.scale(5), elh / 2);
	
	private boolean raised = false;
	private final DropList list;
	private final Label filter;
	
	public CFGWnd() {
	    super(Coord.z, "Auto Drop");
	    justclose = true;
	    
	    int h = add(new CheckBox("Select All") {
		@Override
		public void changed(boolean val) {
		    list.filtered.forEach(item -> ItemAutoDrop.cfg.put(item.name, val));
		    save();
		    if(val) {
			updateCallbacks.forEach(Action0::call);
		    }
		}
	    }).pos("bl").y + UI.scale(3);
	    
	    list = add(new DropList(UI.scale(220), UI.unscale(12)), 0, h);
	    Position ur = list.pos("ur");
	    filter = adda(new Label(FILTER_DEFAULT), ur, 1, 1);
	    
	    h = add(new Label("Drop item on this window to add it to list"), list.pos("bl").addys(10)).pos("bl").y;
	    add(new Label("Right-click item to remove it"), 0, h);
	    
	    pack();
	    setfocus(list);
	    populateList();
	}
	
	@Override
	public void tick(double dt) {
	    super.tick(dt);
	    if(!raised) {
		parent.setfocus(this);
		raise();
		raised = true;
	    }
	}
	
	private void populateList() {
	    List<DropItem> items = new ArrayList<>(ItemAutoDrop.cfg.size());
	    ItemAutoDrop.cfg.forEach((s, aBoolean) -> items.add(new DropItem(s)));
	    items.sort(BY_NAME);
	    list.setItems(items);
	}
	
	private void addItem(String name) {
	    list.items.add(new DropItem(name));
	    list.items.sort(BY_NAME);
	    list.needfilter();
	}
	
	private void updateFilter(String text) {
	    filter.settext((text == null || text.isEmpty()) ? FILTER_DEFAULT : text);
	    filter.c = list.pos("ur").sub(filter.sz).addys(-3);
	}
	
	@Override
	public void destroy() {
	    ItemAutoDrop.wnd = null;
	    super.destroy();
	}
	
	@Override
	public boolean drop(WItem target, Coord cc, Coord ul) {
	    String name = target.name.get(null);
	    if(name != null) {
		if(ItemAutoDrop.add(name)) {
		    addItem(name);
		}
	    }
	    return true;
	}
	
	@Override
	public boolean iteminteract(WItem target, Coord cc, Coord ul) {
	    return false;
	}
	
	private class DropList extends FilteredListBox<DropItem> {
	    
	    public DropList(int w, int h) {
		super(w, h, elh);
		bgcolor = new Color(0, 0, 0, 84);
		showFilterText = false;
	    }
	    
	    @Override
	    protected void filter() {
		super.filter();
		updateFilter(this.filter.line);
	    }
	    
	    @Override
	    protected boolean match(DropItem item, String filter) {
		if(filter.isEmpty()) {
		    return true;
		}
		if(item.name == null)
		    return (false);
		return (item.name.toLowerCase().contains(filter.toLowerCase()));
	    }
	    
	    public boolean keydown(java.awt.event.KeyEvent ev) {
		if(ev.getKeyCode() == java.awt.event.KeyEvent.VK_SPACE) {
		    if(sel != null) {
			toggle(sel.name);
		    }
		    return (true);
		}
		return (super.keydown(ev));
	    }
	    
	    @Override
	    protected void drawitem(GOut g, DropItem item, int idx) {
		g.chcolor(((idx % 2) == 0) ? every : other);
		g.frect(Coord.z, g.sz());
		g.chcolor();
		g.aimage(item.tex, text_c, 0.0, 0.5);
		g.aimage(CheckBox.sbox, chb_c, 0, 0.5);
		if(needDrop(item.name))
		    g.aimage(CheckBox.smark, chb_c, 0, 0.5);
	    }
	    
	    @Override
	    protected void itemclick(DropItem item, int button) {
		if(button == 1) {
		    toggle(item.name);
		} else if(button == 3) {
		    ItemAutoDrop.remove(item.name);
		    list.items.remove(item);
		    list.needfilter();
		}
	    }
	}
    }
}
