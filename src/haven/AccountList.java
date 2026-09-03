package haven;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.*;

public class AccountList extends Widget {
    public static final String ACCOUNTS_JSON = "accounts.json";
    private static final Coord SZ = UI.scale(230, 30);
    private static final Comparator<Account> accountComparator = Comparator.comparing(o -> o.name);
    
    public int height, y;
    public final String confname;
    public final List<Account> accounts = new ArrayList<>();
    
    /* KamiClient: this class used to keep its own token store in accounts.json,
     * keyed by username alone. That put saved logins somewhere a vanilla client
     * could not see, and since the server rotates the token on each login, the
     * two clients kept invalidating each other's saved login.
     *
     * It reads upstream's store now - the saved-tokens@<host> list, per host, the
     * same place vanilla keeps them - so a login saved in either client works in
     * both. Nothing here writes tokens any more; Bootstrap.settoken does that.
     *
     * One-shot import of any old accounts.json below, so nobody has to retype a
     * login they had already saved here. */
    public static List<String> accountnames(String confname) {
	return(new ArrayList<>(Utils.getprefsl("saved-tokens@" + confname, new String[] {})));
    }
    
    private static boolean imported = false;
    public static void importold(String confname) {
	synchronized(AccountList.class) {
	    if(imported)
		return;
	    imported = true;
	}
	String json = Config.loadFile(ACCOUNTS_JSON);
	if(json == null)
	    return;
	try {
	    Gson gson = (new GsonBuilder()).create();
	    Type collectionType = new TypeToken<HashMap<String, String>>() {}.getType();
	    Map<String, String> old = gson.fromJson(json, collectionType);
	    if(old == null)
		return;
	    List<String> have = accountnames(confname);
	    for(Map.Entry<String, String> ent : old.entrySet()) {
		if(have.contains(ent.getKey()) || (ent.getValue() == null))
		    continue;
		/* Only fill in names the prefs store does not already know. A
		 * token vanilla wrote is newer than anything in here. */
		Bootstrap.settoken2(ent.getKey(), confname, Utils.hex2byte(ent.getValue()));
	    }
	    /* Imported, so get it out of the way - leaving it would re-import
	     * stale tokens if the prefs store is ever cleared. */
	    java.io.File old_ = Config.getFile(ACCOUNTS_JSON);
	    if(old_.exists() && !old_.renameTo(new java.io.File(old_.getPath() + ".imported")))
		old_.delete();
	} catch(Exception ignored) {
	}
    }
    
    public static void removeToken(String user, String hostname) {
	Bootstrap.settoken2(user, hostname, null);
    }
    
    public static class Account {
	public String name;
	Button plb, del;
	
	public Account(String name) {
	    this.name = name;
	}
    }
    
    public AccountList(int height, String confname) {
	super();
	this.height = height;
	this.confname = confname;
	this.sz = new Coord(SZ.x, SZ.y * height);
	y = 0;
	
	importold(confname);
	for(String name : accountnames(confname))
	    add(name);
	accounts.sort(accountComparator);
    }
    
    public void scroll(int amount) {
	y += amount;
	synchronized(accounts) {
	    if(y > accounts.size() - height)
		y = accounts.size() - height;
	}
	if(y < 0)
	    y = 0;
    }
    
    public void draw(GOut g) {
	Coord step = UI.scale(5, 5);
	Coord cc = UI.scale(5, 5);
	synchronized (accounts) {
	    for (Account account : accounts) {
		account.plb.hide();
		account.del.hide();
	    }
	    for (int i = 0; (i < height) && (i + this.y < accounts.size()); i++) {
		Account account = accounts.get(i + this.y);
		account.plb.show();
		account.plb.c = cc;
		account.del.show();
		account.del.c = cc.add(account.plb.sz.x + step.x, step.y);
		cc = cc.add(0, SZ.y);
	    }
	}
	super.draw(g);
    }
    
    public boolean mousewheel(MouseWheelEvent ev) {
	scroll(ev.a);
	return (true);
    }
    
    public void wdgmsg(Widget sender, String msg, Object... args) {
	if(sender instanceof Button) {
	    synchronized(accounts) {
		for(Account account : accounts) {
		    if(sender == account.plb) {
			super.wdgmsg("account", account.name);
			break;
		    } else if(sender == account.del) {
			remove(account);
			break;
		    }
		}
	    }
	} else {
	    super.wdgmsg(sender, msg, args);
	}
    }
    
    public void add(String name) {
	Account c = new Account(name);
	c.plb = add(new Button(UI.scale(200), name) {
	    @Override
	    protected boolean i10n() { return false; }
	});
	c.plb.hide();
	c.del = add(new Button(UI.scale(20), "X") {
	    @Override
	    protected boolean i10n() { return false; }
	});
	c.del.hide();
	synchronized (accounts) {
	    accounts.add(c);
	}
    }
    
    public void remove(Account account) {
	synchronized(accounts) {
	    accounts.remove(account);
	}
	scroll(0);
	removeToken(account.name, confname);
	ui.destroy(account.plb);
	ui.destroy(account.del);
    }
}