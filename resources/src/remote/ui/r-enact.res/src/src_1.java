Enactment.java /* Preprocessed source code */
import haven.*;
import haven.CharWnd.LoadingTextBox;
import java.util.*;
import java.awt.Color;

public class Enactment {
    public final int id;
    public final Indir<Resource> res;
    public int lvl, mlvl;
    public Cost cost, dcost, icost;
    String sortkey = "\uffff";
    Tex small;
    Text rnm, rlvl;

    public Enactment(int id, Indir<Resource> res) {
	this.id = id;
	this.res = res;
    }

    public String rendertext() {
	StringBuilder buf = new StringBuilder();
	Resource res = this.res.get();
	buf.append("$img[" + res.name + "]\n\n");
	buf.append("$b{$font[serif,16]{" + res.layer(Resource.tooltip).t + "}}\n\n\n");
	buf.append(res.layer(Resource.pagina).text);
	return(buf.toString());
    }
}

/* >wdg: Enactments */
