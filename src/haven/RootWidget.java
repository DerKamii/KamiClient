/*
 *  This file is part of the Haven & Hearth game client.
 *  Copyright (C) 2009 Fredrik Tolf <fredrik@dolda2000.com>, and
 *                     Björn Johannessen <johannessen.bjorn@gmail.com>
 *
 *  Redistribution and/or modification of this file is subject to the
 *  terms of the GNU Lesser General Public License, version 3, as
 *  published by the Free Software Foundation.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  Other parts of this source tree adhere to other copying
 *  rights. Please see the file `COPYING' in the root directory of the
 *  source tree for details.
 *
 *  A copy the GNU Lesser General Public License is distributed along
 *  with the source tree of which this file is a part in the file
 *  `doc/LPGL-3'. If it is missing for any reason, please see the Free
 *  Software Foundation's website at <http://www.fsf.org/>, or write
 *  to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *  Boston, MA 02111-1307 USA
 */

package haven;

import java.util.*;
import java.awt.Color;
import java.awt.event.KeyEvent;

public class RootWidget extends ConsoleHost implements UI.MessageWidget, Console.Directory {
    public static final Text.Foundry msgfoundry = new Text.Foundry(Text.dfont, 14);
    public static final Resource defcurs = Resource.local().loadwait("gfx/hud/curs/arw");
    public boolean modtip = false;
    Profile guprof, grprof, ggprof;
    private Text lastmsg;
    private double msgtime;
    
    
    public RootWidget(UI ui, Coord sz) {
	super(ui, new Coord(0, 0), sz);
	setfocusctl(true);
	hasfocus = true;
	cursor = defcurs.indir();
    }
    
    public boolean globtype(char key, KeyEvent ev) {
	if(super.globtype(key, ev)) {
	    return false;
	}
	if(KeyBinder.handle(ui, ev)) {
	    return false;
	}
	if(key == '`') {
	    if(UIPanel.profile.get()) {
		add(new Profwnd(guprof, "UI profile"), UI.scale(100, 100));
		add(new Profwnd(grprof, "GL profile"), UI.scale(500, 100));
		    /* XXXRENDER
		    GameUI gi = findchild(GameUI.class);
		    if((gi != null) && (gi.map != null))
			add(new Profwnd(gi.map.prof, "Map profile"), UI.scale(100, 250));
		    */
	    }
	    if(UIPanel.profilegpu.get()) {
		add(new Profwnd(ggprof, "GPU profile"), UI.scale(500, 250));
	    }
	} else if(key == ':') {
	    if(super.globtype(key, ev)) {
		return false;
	    } else {
		entercmd();
		return true;
	    }
	} else if(key != 0) {
	    wdgmsg("gk", (int) key);
	}
	return true;
    }
    
    @Override
    public boolean keydown(KeyEvent ev) {
	return super.keydown(ev);
    }
    
    @Override
    public boolean keyup(KeyEvent ev) {
	return super.keyup(ev);
    }
    
    private boolean isCTRL(KeyEvent ev) {
	return ev.getModifiersEx() == (ev.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK)
	    && KeyEvent.VK_CONTROL == ev.getExtendedKeyCode()
	    && KeyEvent.VK_CONTROL == ev.getKeyCode();
    }
    
    private boolean isALT(KeyEvent ev) {
	return (
	    ev.getModifiersEx() == (ev.getModifiersEx() & KeyEvent.ALT_DOWN_MASK)
		&& KeyEvent.VK_ALT == ev.getExtendedKeyCode()
		&& KeyEvent.VK_ALT == ev.getKeyCode()
	) || (
	    ev.getModifiersEx() == (ev.getModifiersEx() & KeyEvent.META_DOWN_MASK)
		&& KeyEvent.VK_META == ev.getExtendedKeyCode()
		&& KeyEvent.VK_META == ev.getKeyCode()
	);
    }
    
    private boolean isSHIFT(KeyEvent ev) {
	return ev.getModifiersEx() == (ev.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK)
	    && KeyEvent.VK_SHIFT == ev.getExtendedKeyCode()
	    && KeyEvent.VK_SHIFT == ev.getKeyCode();
    }
    
    @Override
    public boolean mousedown(Coord c, int button) {
	return super.mousedown(c, button);
    }
    
    public void draw(GOut g) {
	super.draw(g);
	if(cmdline != null) {
	    drawcmd(g, new Coord(UI.scale(20), sz.y - UI.scale(20)));
	} else if(lastmsg != null) {
	    if((Utils.rtime() - msgtime) > 3.0) {
		lastmsg = null;
	    } else {
		Coord msgc = pos("cbl").adds(20, -20).sub(0, lastmsg.sz().y);
		g.chcolor(0, 0, 0, 192);
		g.frect(msgc.sub(UI.scale(2, 2)), lastmsg.sz().add(UI.scale(4, 4)));
		g.chcolor();
		g.image(lastmsg.tex(), msgc);
	    }
	}
    }
    
    public void uimsg(String msg, Object... args) {
	if(msg == "err") {
	    ui.error((String)args[0]);
	} else if(msg == "msg") {
	    if(args.length == 1) {
		ui.msg((String)args[0]);
	    } else {
		ui.loader.defer(() -> {
		    int a = 0;
		    UI.SimpleMessage info = new UI.InfoMessage((String)args[a++]);
		    if(args[a] instanceof Color)
			info.color = (Color)args[a++];
		    if(args.length > a) {
			Indir<Resource> res = ui.sess.getresv(args[a++]);
			info.sfx = (res == null) ? null : Audio.resclip(res.get());
		    }
		    ui.msg(info);
		}, null);
	    }
	} else if(msg == "msg2") {
	    ui.loader.defer(() -> {
		    Resource res = ui.sess.getresv(args[0]).get();
		    UI.Notice.Factory fac = res.getcode(UI.Notice.Factory.class, true);
		    ui.msg(fac.format(new OwnerContext() {
			    public <T> T context(Class<T> cl) {
				return(wdgctx.context(cl, RootWidget.this));
			    }
			}, Utils.splice(args, 1)));
		}, null);
	} else if(msg == "sfx") {
	    int a = 0;
	    Indir<Resource> resid = ui.sess.getresv(args[a++]);
	    double vol = (args.length > a) ? Utils.dv(args[a++]) : 1.0;
	    double spd = (args.length > a) ? Utils.dv(args[a++]) : 1.0;
	    ui.sess.glob.loader.defer(() -> {
		Audio.CS clip = Audio.fromres(resid.get());
		if(spd != 1.0)
		    clip = new Audio.Resampler(clip).sp(spd);
		if(vol != 1.0)
		    clip = new Audio.VolAdjust(clip, vol);
		Audio.play(clip);
	    }, null);
	} else if(msg == "bgm") {
	    int a = 0;
	    Indir<Resource> resid = (args.length > a) ? ui.sess.getresv(args[a++]) : null;
	    boolean loop = (args.length > a) ? Utils.bv(args[a++]) : false;
	    if(Music.enabled) {
		if(resid == null)
		    Music.play(null, false);
		else
		    Music.play(resid, loop);
	    }
	} else {
	    super.uimsg(msg, args);
	}
    }
    
    public void msg(String msg, Color color) {
	lastmsg = msgfoundry.render(msg, color);
	msgtime = Utils.rtime();
    }

    public void msg(UI.Notice msg) {
	msg(msg.message(), msg.color());
	ui.sfxrl(msg.sfx());
    }
    
    public void msg(String msg, Color color, Audio.Clip sfx) {
	msg(msg, color);
	ui.sfxrl(sfx);
    }
    
    public void error(String msg) {
	ui.error(msg);
    }
    
    public Object tooltip(Coord c, Widget prev) {
	if(modtip && (ui.modflags() != 0))
	    return(KeyMatch.modname(ui.modflags()));
	return(super.tooltip(c, prev));
    }
    
    private Map<String, Console.Command> cmdmap = new TreeMap<String, Console.Command>();
    {
	cmdmap.put("wdgtree", new Console.Command() {
	    public void run(Console cons, String[] args) throws Exception {
		for(Widget w = RootWidget.this; w != null; w = w.rnext()) {
		    for(Widget p = w.parent; p != null; p = p.parent)
			cons.out.write('\t');
		    cons.out.write(w.visible ? 'S' : 'H');
		    cons.out.write(' ');
		    cons.out.write(w.hasfocus ? "F" : "f");
		    cons.out.write(w.focusctl ? "C" : "c");
		    cons.out.write(w.focustab ? "T" : "t");
		    cons.out.write(w.canfocus ? "A" : "a");
		    cons.out.write(w.autofocus ? "T" : "t");
		    cons.out.write(((w.parent != null) && (w.parent.focused == w)) ? "P" : "p");
		    cons.out.write(' ');
		    cons.out.write(w.toString());
		    cons.out.write('\n');
		}
	    }
	});
    }
    public Map<String, Console.Command> findcmds() {
	return(cmdmap);
    }
}
