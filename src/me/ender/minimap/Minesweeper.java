package me.ender.minimap;

import haven.*;
import haven.render.Homo3D;
import haven.render.Pipe;
import haven.render.RenderTree;
import me.ender.ClientUtils;
import me.ender.CustomCursors;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;


import static haven.MapFile.*;

public class Minesweeper {
    private static final String INDEX = "ender-ms-index";
    private static final String GRID_NAME = "ender-ms-grid-%x";
    private static final int TILES = MCache.cmaps.x * MCache.cmaps.y;
    private static final Coord2d TILE_CENTER = MCache.tilesz.div(2);
    private static final RenderTree.Node NIL = RenderTree.Node.nil;
    
    //if this value is passed, count component will be ignored
    private static final byte NO_COUNT		= (byte) 0b0000_1111;
    //if this value is passed, flags component will be ignored
    private static final byte NO_FLAGS		= (byte) 0b1111_0000;
    
    private static final byte COUNT_MASK	= (byte) 0b0000_1111;
    private static final byte FLAGS_MASK	= (byte) 0b1111_0000;
    
    
    public static final byte CLEAR_FLAGS	= (byte) 0b0000_0000;
    public static final byte FLAG_SAFE		= (byte) 0b0001_0000;
    public static final byte FLAG_DANGER	= (byte) 0b0010_0000;
    public static final byte FLAG_MAYBE		= (byte) 0b0011_0000;
    
    //deprecated values, used for parsing old saved data
    private static final byte DEPRECATED_V2_SAFE = (byte) 0xff;
    private static final byte DEPRECATED_V2_DANGER = (byte) 0xfe;
    
    private final Object lock = new Object();
    private final Set<Long> gridIds = new HashSet<>();
    private final Map<Long, byte[]> values = new HashMap<>();
    private final Map<Long, byte[]> safe_values = new HashMap<>();
    private final Map<Long, SweeperNode[]> cuts = new HashMap<>();
    private final MapFile file;
    
    public Minesweeper(MapFile file) {
	this.file = file;
	MapFileUtils.load(file, this::loadIndex, INDEX);
    }
    
    public static void markDustSpawn(Sprite.Owner owner, float str) {
	ClientUtils.owner2ogob(owner).ifPresent(value -> addCountAtGob(value, (byte) (str / 30f)));
    }
    
    public static void markMinedOutTile(Sprite.Owner owner) {
	Gob gob = ClientUtils.owner2ogob(owner).orElse(null);
	if (gob == null) return;
	
	GameUI gui = gob.context(GameUI.class);
	if (gui == null) return;
	
	Coord2d rc = gob.rc;
	Minesweeper ms = gui.minesweeper;
	if (ms == null) return;
	
	// Determine which grid and tile index
	Coord gc = rc.floor(MCache.tilesz);
	MCache.Grid grid = gui.ui.sess.glob.map.getgridt(gc);
	if (grid == null) return;
	
	Coord tc = gc.sub(grid.gc.mul(MCache.cmaps));
	long id = grid.id;
	byte[] values = ms.values.get(id);
	if (values != null) {
	    int idx = index(tc);
	    byte current = values[idx];
	    byte count = (byte) (current & COUNT_MASK);
	    byte flags = (byte) (current & FLAGS_MASK);
	    
	    // Only mark safe if count is currently 0
	    if (count == 0) {
		byte newFlags = (byte) ((flags & ~FLAGS_MASK) | FLAG_SAFE);
		setValue(values, idx, NO_COUNT, newFlags);
		
		// Persist the grid
		ms.storeGrid(id, values);
	    }
	}
	gui.minesweeper.generateOutlineMap(id);
    }
    
    public static void markFlagAtPoint(Coord2d rc, byte flags, GameUI gui) {
	markPoint(rc, NO_COUNT, flags, gui);
    }
    
    private static void addCountAtGob(Gob gob, byte count) {
	GameUI gui = gob.context(GameUI.class);
	if(gui == null) {return;}
	
	markPoint(gob.rc, count, CLEAR_FLAGS, gui);
    }
    
    private static void markPoint(Coord2d rc, byte count, byte flags, GameUI gui) {
	Coord gc = rc.floor(MCache.tilesz);
	MCache.Grid grid = gui.ui.sess.glob.map.getgridt(gc);
	if(grid == null) {return;}
	
	Coord tc = gc.sub(grid.gc.mul(MCache.cmaps));
	long id = grid.id;
	
	gui.minesweeper.addValue(id, tc, count, flags);
	gui.minesweeper.generateOutlineMap(id);
    }
    
    public static boolean paginaAction(OwnerContext ctx, MenuGrid.Interaction iact) {
	boolean was = CFG.SHOW_MINESWEEPER_OVERLAY.get();
	if(iact != null && iact.modflags == UI.MOD_SHIFT) {
	    MapView map = ctx.context(UI.class).gui.map;
	    CustomCursors.toggleSweeperMode(map);
	    if(!was && CustomCursors.isSweeping(map)) {
		CFG.SHOW_MINESWEEPER_OVERLAY.set(true);
		return true;
	    }
	    return false;
	}
	CFG.SHOW_MINESWEEPER_OVERLAY.set(!was);
	return true;
    }
    
    private void addValue(long id, Coord tc, byte count, byte flags) {
	synchronized (lock) {
	    Map<Long, byte[]> grids = values;
	    byte[] values;
	    if(loadGrid(id)) {
		values = grids.get(id);
	    } else {
		values = new byte[TILES];
		grids.put(id, values);
		gridIds.add(id);
		storeIndex();
	    }
	    setValue(values, index(tc), count, flags);
	    storeGrid(id, values);
	    
	    // DEBUG PRINT
	    // debugPrintAllGrids();
	    
	}
    }
    
    public void generateOutlineMap(long gridId) {
	synchronized (lock) {
	    byte[] baseGrid = values.get(gridId);
	    if (baseGrid == null) return;
	    
	    byte[] outlineMap = new byte[baseGrid.length];
	    int w = MCache.cmaps.x;
	    int h = MCache.cmaps.y;
	    
	    for (int y = 0; y < h; y++) {
		for (int x = 0; x < w; x++) {
		    int idx = x + y * w;
		    byte tile = baseGrid[idx];
		    byte count = (byte)(tile & COUNT_MASK);
		    byte flags = (byte)(tile & FLAGS_MASK);
		    
		    // Skip if tile already has value
		    if (count != 0 || flags != CLEAR_FLAGS) continue;
		    
		    // Check neighbors for a zero
		    boolean hasZeroNeighbor = false;
		    for (int ny = Math.max(0, y - 1); ny <= Math.min(h - 1, y + 1); ny++) {
			for (int nx = Math.max(0, x - 1); nx <= Math.min(w - 1, x + 1); nx++) {
			    if (nx == x && ny == y) continue;
			    int nidx = nx + ny * w;
			    byte neighbor = baseGrid[nidx];
			    byte neighborCount = (byte)(neighbor & COUNT_MASK);
			    byte neighborFlags = (byte)(neighbor & FLAGS_MASK);
			    
			    if (neighborCount == 0 && neighborFlags == CLEAR_FLAGS) {
				// We only care about existing zeros, not unknown tiles
				continue;
			    }
			    
			    if (neighborCount == 0) {
				hasZeroNeighbor = true;
				break;
			    }
			}
			if (hasZeroNeighbor) break;
		    }
		    
		    if (hasZeroNeighbor) {
			outlineMap[idx] = FLAG_SAFE; // mark as 'O'
		    }
		}
	    }
	    
	    safe_values.put(gridId, outlineMap);
	}
    }
    
    public Map<Long, byte[]> mergeMaps(Map<Long, byte[]> map1, Map<Long, byte[]> map2) {
	Map<Long, byte[]> result = new HashMap<>();
	
	// Add all entries from map1
	for (Map.Entry<Long, byte[]> entry : map1.entrySet()) {
	    long key = entry.getKey();
	    byte[] value1 = entry.getValue();
	    byte[] value2 = map2.get(key);
	    
	    if (value2 != null) {
		// Merge arrays element-wise
		if (value1.length != value2.length)
		    throw new IllegalArgumentException("Grid sizes must match for key " + key);
		
		byte[] merged = new byte[value1.length];
		for (int i = 0; i < value1.length; i++) {
		    merged[i] = (byte)(value1[i] | value2[i]); // merge flags (e.g., safe tiles)
		}
		result.put(key, merged);
	    } else {
		// Only in map1
		result.put(key, value1.clone());
	    }
	}
	
	// Add entries from map2 that weren't in map1
	for (Map.Entry<Long, byte[]> entry : map2.entrySet()) {
	    if (!result.containsKey(entry.getKey())) {
		result.put(entry.getKey(), entry.getValue().clone());
	    }
	}
	
	return result;
    }
    
    /** Debug: prints the entire grid (for all loaded grids) to console as ASCII
    public void debugPrintAllGrids() {
	synchronized (lock) {
	    System.out.println("=== MINESWEEPER ASCII MAP DUMP ===");
	    
	    for (Map.Entry<Long, byte[]> entry : mergeMaps(values, safe_values).entrySet()) {
		long gridId = entry.getKey();
		byte[] grid = entry.getValue();
		
		System.out.println("Grid ID: " + gridId);
		printGridAscii(grid);
		System.out.println();
	    }
	}
    }
    
    // Prints a single grid
    private void printGridAscii(byte[] grid) {
	int w = MCache.cmaps.x;
	int h = MCache.cmaps.y;
	
	for (int y = 0; y < h; y++) {
	    StringBuilder line = new StringBuilder();
	    for (int x = 0; x < w; x++) {
		byte v = grid[x + y * w];
		
		byte count = (byte) (v & COUNT_MASK);
		byte flags = (byte) (v & FLAGS_MASK);
		
		char c;
		
		if(flags == FLAG_DANGER) c = 'X';
		else if(flags == FLAG_SAFE) c = 'O';
		else if(flags == FLAG_MAYBE) c = '?';
		else if(count > 0) c = (char) ('0' + count);
		else c = '.';
		
		line.append(c);
	    }
	    System.out.println(line);
	}
    }
    */
    
    private void updateGrid(long grid, byte[] newValues) {
	if(gridIds.contains(grid) && loadGrid(grid)) {
	    byte[] curValues = values.get(grid);
	    for (int i = 0; i < newValues.length; i++) {
		byte newValue = newValues[i];
		if(newValue == 0) {continue;}
		byte count = (byte) (newValue & COUNT_MASK);
		if(count == 0) {count = NO_COUNT;}
		setValue(curValues, i, count, (byte) (newValue & FLAGS_MASK));
	    }
	    storeGrid(grid, curValues);
	} else {
	    gridIds.add(grid);
	    storeIndex();
	    storeGrid(grid, newValues);
	}
    }
    
    private static void setValue(byte[] values, int idx, byte count, byte flags) {
	byte value = values[idx];
	count = (byte) (count & COUNT_MASK);
	flags = (byte) (flags & FLAGS_MASK);
	if(count != NO_COUNT) {value = (byte) ((value & FLAGS_MASK) | count);}
	if(flags != NO_FLAGS) {value = (byte) ((value & COUNT_MASK) | flags);}
	values[idx] = value;
    }
    
    private static int index(Coord tc) {
	return tc.x + tc.y * MCache.cmaps.x;
    }
    
    public static RenderTree.Node getcut(UI ui, Coord cc) {
	if(!CFG.SHOW_MINESWEEPER_OVERLAY.get()) {return NIL;}
	GameUI gui = ui.gui;
	if(gui == null) {return NIL;}
	Minesweeper minesweeper = gui.minesweeper;
	if(minesweeper == null) {return NIL;}
	
	return minesweeper.getcut(ui.sess.glob.map.getgrid(cc.div(MCache.cutn)), cc.mod(MCache.cutn));
    }
    
    private RenderTree.Node getcut(MCache.Grid grid, Coord cc) {
	SweeperNode[] nodes;
	int index = cc.x + cc.y * MCache.cutn.x;
	synchronized (lock) {
	    if(!cuts.containsKey(grid.id)) {
		if(!loadGrid(grid.id)) {return NIL;}
		nodes = new SweeperNode[MCache.cutn.x * MCache.cutn.y];
		cuts.put(grid.id, nodes);
	    } else {
		nodes = cuts.get(grid.id);
	    }
	    
	    byte[] merged = mergeMaps(values, safe_values).get(grid.id);
	    if(merged == null) { return NIL; }
	    
	    // Always recreate the node to reflect latest values
	    nodes[index] = new SweeperNode(merged, cc);
	}
	return nodes[index];
    }
    
    public static void trim(Session sess, List<Long> removed) {
	UI ui = sess.ui;
	if(ui == null) {return;}
	GameUI gui = ui.gui;
	if(gui == null) {return;}
	if(gui.minesweeper != null) {
	    gui.minesweeper.trim(removed);
	}
    }
    
    private void trim(List<Long> removed) {
	synchronized (lock) {
	    if(removed == null) {
		values.clear();
		cuts.clear();
	    } else {
		for (Long id : removed) {
		    values.remove(id);
		    cuts.remove(id);
		}
	    }
	}
    }
    
    private void storeIndex() {
	synchronized (lock) {
	    OutputStream fp;
	    try {
		fp = file.sstore(INDEX);
	    } catch (IOException e) {
		throw (new StreamMessage.IOError(e));
	    }
	    try (StreamMessage out = new StreamMessage(fp)) {
		out.adduint8(1);
		for (Long id : gridIds) {
		    out.addint64(id);
		}
	    }
	}
    }
    
    private void storeGrid(long id, byte[] grid) {
	OutputStream fp;
	try {
	    fp = file.sstore(GRID_NAME, id);
	} catch (IOException e) {
	    throw (new StreamMessage.IOError(e));
	}
	try (StreamMessage out = new StreamMessage(fp)) {
	    out.adduint8(3); //version
	    ZMessage zout = new ZMessage(out);
	    zout.addbytes(grid);
	    zout.finish();
	}
    }
    
    private boolean loadIndex(StreamMessage data) {
	synchronized (lock) {
	    Set<Long> ids = doLoadIndex(data);
	    if(ids == null) {return false;}
	    gridIds.addAll(ids);
	}
	return true;
    }
    
    private static Set<Long> doLoadIndex(StreamMessage data) {
	int ver = data.uint8();
	if(ver == 1) {
	    Set<Long> gridIds = new HashSet<>();
	    while (!data.eom()) {
		gridIds.add(data.int64());
	    }
	    return gridIds;
	} else {
	    warn("unknown mapfile ender-minesweeper version: %d", ver);
	}
	return null;
    }
    
    private boolean loadGrid(long id) {
	synchronized (lock) {
	    if(!gridIds.contains(id)) {return false;}
	    if(values.containsKey(id)) {return true;}
	    
	    if(!MapFileUtils.load(file, data -> loadGrid(data, id), GRID_NAME, id)) {
		cuts.remove(id);
		values.remove(id);
		gridIds.remove(id);
		storeIndex();
		return false;
	    }
	}
	return true;
    }
    
    private boolean loadGrid(StreamMessage data, long id) {
	byte[] v = doLoadGrid(data, id);
	if(v == null) {return false;}
	values.put(id, v);
	return true;
    }
    
    private static byte[] doLoadGrid(StreamMessage data, long id) {
	int ver = data.uint8();
	if(ver == 2 || ver == 3) {
	    byte[] values = new ZMessage(data).bytes(TILES);
	    if(ver == 2) {convertToV3(values);}
	    return values;
	} else {
	    warn("unknown mapfile ender-minesweeper-grid %d version: %d", id, ver);
	}
	return null;
    }
    
    private static void convertToV3(byte[] values) {
	for (int i = 0; i < values.length; i++) {
	    byte value = values[i];
	    if(value == DEPRECATED_V2_SAFE) {
		values[i] = FLAG_SAFE;
	    } else if(value == DEPRECATED_V2_DANGER) {
		values[i] = FLAG_DANGER;
	    }
	}
    }
    
    public static void doExport(MapFile mapFile, UI ui) {
	java.awt.EventQueue.invokeLater(() -> {
	    JFileChooser fc = new JFileChooser();
	    fc.setFileFilter(new FileNameExtensionFilter("Exported Haven Minesweeper data", "hems"));
	    if(fc.showSaveDialog(null) != JFileChooser.APPROVE_OPTION)
		return;
	    Path path = fc.getSelectedFile().toPath();
	    if(path.getFileName().toString().indexOf('.') < 0)
		path = path.resolveSibling(path.getFileName() + ".hems");
	    
	    doExport(mapFile, path, ui);
	});
    }
    
    private static void doExport(MapFile mapFile, Path path, UI ui) {
	new HackThread(() -> {
	    boolean complete = false;
	    try {
		try {
		    complete = MapFileUtils.load(mapFile, data -> doExport(mapFile, path, doLoadIndex(data)), INDEX);
		} finally {
		    if(!complete) {
			Files.deleteIfExists(path);
			ui.gui.msg("Error while exporting minesweeper data", GameUI.MsgType.ERROR);
		    } else {
			ui.gui.msg("Finished exporting minesweeper data", GameUI.MsgType.INFO);
		    }
		}
	    } catch (IOException e) {
		e.printStackTrace(Debug.log);
		//gui.error("Unexpected error occurred when exporting map.");
	    }
	}, "Minesweeper exporter").start();
    }
    
    private static final byte[] EXPORT_SIG = "Haven Minesweeper 1".getBytes(Utils.ascii);
    
    private static boolean doExport(MapFile mapFile, Path path, Set<Long> grids) {
	if(grids == null || grids.isEmpty()) {return false;}
	try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(path))) {
	    StreamMessage msg = new StreamMessage(null, out);
	    msg.addbytes(EXPORT_SIG);
	    msg.adduint8(3);//version
	    ZMessage zout = new ZMessage(msg);
	    for (Long grid : grids) {
		if(grid == null) {continue;}
		long id = grid;
		MapFileUtils.load(mapFile, (data) -> {
		    byte[] src = doLoadGrid(data, id);
		    if(src == null) {return false;}
		    zout.addint64(id);
		    zout.addbytes(src);
		    return true;
		}, GRID_NAME, grid);
	    }
	    zout.close();
	} catch (IOException e) {
	    return false;
	}
	return true;
    }
    
    public static void doImport(UI ui) {
	java.awt.EventQueue.invokeLater(() -> {
	    JFileChooser fc = new JFileChooser();
	    fc.setFileFilter(new FileNameExtensionFilter("Exported Haven Minesweeper data", "hems"));
	    if(fc.showOpenDialog(null) != JFileChooser.APPROVE_OPTION)
		return;
	    doImport(fc.getSelectedFile().toPath(), ui);
	});
    }
    
    private static void doImport(Path path, UI ui) {
	new HackThread(() -> {
	    boolean complete = false;
	    try {
		try (SeekableByteChannel fp = Files.newByteChannel(path)) {
		    complete = doImport(new BufferedInputStream(Channels.newInputStream(fp)), ui);
		} finally {
		    if(complete) {
			ui.gui.msg("Finished importing minesweeper data", GameUI.MsgType.INFO);
		    } else {
			ui.gui.msg("Error while importing minesweeper data", GameUI.MsgType.ERROR);
		    }
		}
	    } catch (IOException e) {
		e.printStackTrace(Debug.log);
		//gui.error("Unexpected error occurred when exporting map.");
	    }
	}, "Minesweeper exporter").start();
    }
    
    private static boolean doImport(BufferedInputStream input, UI ui) {
	Message data = new StreamMessage(input);
	if(!Arrays.equals(EXPORT_SIG, data.bytes(EXPORT_SIG.length))) {return false;}
	int ver = data.uint8();
	if(ver == 1 || ver == 3) {
	    Minesweeper m = ui.gui.minesweeper;
	    ZMessage zdata = new ZMessage(data);
	    synchronized (m.lock) {
		while (!zdata.eom()) {
		    long grid = zdata.int64();
		    byte[] values = zdata.bytes(TILES);
		    if(ver == 1) {convertToV3(values);}
		    
		    m.updateGrid(grid, values);
		}
	    }
	    
	    return true;
	}
	
	return false;
    }
    
    private static class SweeperNode implements RenderTree.Node, PView.Render2D {
	private static final Text.Foundry TEXT_FND = new Text.Foundry(Text.monobold, 12);
	private static final Color SAFE_COL = new Color(32, 220, 80);
	private static final Color DANGER_COL = new Color(240, 32, 100);
	private static final Color MAYBE_COL = new Color(193, 87, 251);
	private static final Color[] COLORS = new Color[]{
	    new Color(150, 200, 245),
	    new Color(142, 225, 207),
	    new Color(182, 210, 127),
	    new Color(233, 225, 34),
	    new Color(250, 195, 56),
	    new Color(255, 150, 65),
	    new Color(230, 80, 32),
	    new Color(235, 20, 16),
	};
	private static final Map<Byte, Tex> CACHE = new HashMap<>();
	
	private final byte[] values;
	private final Coord cc;
	
	public SweeperNode(byte[] values, Coord cc) {
	    this.values = values;
	    this.cc = cc;
	}
	
	private static Tex getTex(byte val) {
	    if(val == 0) {return null;}
	    if(CACHE.containsKey(val)) {return CACHE.get(val);}
	    BufferedImage flags = flagImg(val);
	    BufferedImage count = countImg(val);
	    Tex tex = null;
	    if(flags != null || count != null) {
		tex = new TexI(ItemInfo.catimgsh(0, flags, count));
	    }
	    CACHE.put(val, tex);
	    return tex;
	}
	
	private static BufferedImage flagImg(byte flags) {
	    flags = (byte) (flags & FLAGS_MASK);
	    Color color;
	    String text;
	    if(flags == FLAG_SAFE) {
		color = SAFE_COL;
		text = "·";
	    } else if(flags == FLAG_DANGER) {
		color = DANGER_COL;
		text = "×";
	    } else if(flags == FLAG_MAYBE) {
		color = MAYBE_COL;
		text = "?";
	    } else {
		return null;
	    }
	    return Text.renderstroked(text, color, Color.BLACK, TEXT_FND).img;
	}
	
	private static BufferedImage countImg(byte count) {
	    count = (byte) (count & COUNT_MASK);
	    if(count == 0) {return null;}
	    
	    Color color = COLORS[Utils.clip(count - 1, 0, COLORS.length - 1)];
	    String text = String.valueOf(count);
	    
	    return Text.renderstroked(text, color, Color.BLACK, TEXT_FND).img;
	}
	
	public Coord3f origin(Coord tc) {
	    Coord2d mc = tc.mul(MCache.tilesz).add(TILE_CENTER);
	    return new Coord3f((float) mc.x, (float) -mc.y, 1f);
	}
	
	@Override
	public void draw(GOut g, Pipe state) {
	    Coord ul = cc.mul(MCache.cutsz);
	    Coord o = new Coord();
	    for (o.x = 0; o.x < MCache.cutsz.x; o.x++) {
		for (o.y = 0; o.y < MCache.cutsz.y; o.y++) {
		    
		    Tex tex = getTex(values[index(ul.add(o))]);
		    if(tex == null) {continue;}
		    
		    Coord sc = Homo3D.obj2sc(origin(o), state, Area.sized(g.sz()));
		    if(sc == null) {continue;}
		    if(!sc.isect(Coord.z, g.sz())) {continue;}
		    
		    g.aimage(tex, sc, 0.5f, 0.5f);
		}
	    }
	}
    }
}
