package me.ender;

import haven.*;

import java.awt.*;
import java.awt.image.BufferedImage;

public abstract class RichUText<T> implements Indir<Tex> {
    public final RichText.Foundry fnd;
    private TexI cur = null;
    private final Color bg;
    private T cv = null;
    
    public RichUText(RichText.Foundry fnd, Color bg) {
	this.fnd = fnd;
	this.bg = bg;
    }
    
    public RichUText(RichText.Foundry fnd) {this(fnd, null);}
    
    protected TexI render(String text) {
	BufferedImage img = fnd.render(text).img;
	img = process(img);
	if(bg == null) {
	    return new TexI(img);
	}
	Coord sz = Coord.of(img.getWidth(), img.getHeight());
	BufferedImage ret = TexI.mkbuf(sz);
	Graphics g = ret.getGraphics();
	g.setColor(bg);
	g.fillRect(0, 0, sz.x, sz.y);
	g.drawImage(img, 0, 0, null);
	g.dispose();
	return new TexI(ret);
    }
    
    protected BufferedImage process(BufferedImage img) {return img;}
    
    protected String text(T value) {return (value == null ? null : String.valueOf(value));}
    public abstract T value();
    
    public TexI get() {
	T value = value();
	if(!Utils.eq(value, cv)) {
	    if(cur != null) {cur.dispose();}
	    String text = text(cv = value);
	    cur = text != null ? render(text) : null;
	}
	return(cur);
    }
    
    public Indir<Tex> tex() {
	return(RichUText.this);
    }
}