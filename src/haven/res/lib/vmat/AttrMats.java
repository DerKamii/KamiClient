/* Preprocessed source code */
package haven.res.lib.vmat;

import haven.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@haven.FromResource(name = "lib/vmat", version = 39)
public class AttrMats extends VarMats {
    public final Map<Integer, Material> mats;
    public final List<Resource> res;

    public AttrMats(Gob gob, Map<Integer, Material> mats) {
	super(gob);

	this.mats = mats;
	this.res = null;
    }

    public AttrMats(Gob gob, Pair<Map<Integer, Material>, List<Resource>> data) {
	super(gob);

	this.mats = data.a;
	this.res = data.b;
    }

    public Material varmat(int id) {
	return (mats.get(id));
    }

    public static Pair<Map<Integer, Material>, List<Resource>> decode(Resource.Resolver rr, Message sdt) {
	Map<Integer, Material> ret = new IntMap<>();
	List<Resource> resources = new LinkedList<>();
	int idx = 0;
	while (!sdt.eom()) {
	    Indir<Resource> mres = rr.getres(sdt.uint16());
	    int mid = sdt.int8();
	    Material.Res mat;
	    Resource res = mres.get();
	    resources.add(res);
	    if(mid >= 0)
		mat = res.layer(Material.Res.class, mid);
	    else
		mat = res.layer(Material.Res.class);
	    ret.put(idx++, mat.get());
	}
	return new Pair<>(ret, resources);
    }

    public static void parse(Gob gob, Message dat) {
	gob.setattr(new AttrMats(gob, decode(gob.context(Resource.Resolver.class), dat)));
    }
}

/* >spr: VarSprite */
