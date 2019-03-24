/* 
 *	JSJBridge WebpageHelper Class
 *	Copyright 2019 Mark Reginald James
 * 	Licensed under Version 1 of the DevWheels Licence. See file LICENCE.txt and devwheels.com.
*/

package au.com.advancedcontrols.jsjbridge;

import org.json.JSONObject;
import java.net.URL;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Panel;
import java.awt.image.ImageObserver;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.logging.Level;

public class WebpageHelper extends Panel {
	private static final long serialVersionUID = 5567096282445673447L;
	
	// Public

	public WebpageHelper() {
		className = this.getClass().asSubclass(this.getClass()).getSimpleName();
		nlog = new NativeMessagingLogger(className);
		instancesByClassName.put(className, this);
	}

	public void init() {}
	public void start() {}
	public void stop() {}
	public void destroy() {}
	public void finalize() throws Throwable { _stop(); destroy(); }
	public String[][] getParameterInfo() { return null; }
	public String getParameter(String name) { return parameters.has(name) ? parameters.getString(name) : null; }
	public boolean isActive() {	return active; }
	public URL getDocumentBase() { return url; }
	public URL getCodeBase() { return null; }
	public String getAppletInfo() { return null; }
	public void showStatus(String msg) { nlog.info("status: " + msg); }
	public static void main(String[] args) { hlog.setLevel(Level.INFO); JSObject.init(); }
	
    public static NativeMessagingLogger hlog = new NativeMessagingLogger("JSJBridge");
    public NativeMessagingLogger nlog;
    
    public synchronized void repaint(long tm, int x, int y, int width, int height) {
    	if (canvasGraphics != null) {
    		//canvasGraphics.setClip(x, y, width, height);
    		update(canvasGraphics);
    	}
    }
    
    public synchronized void update(Graphics g) {
    	super.update(g);
    	paint(g);
        canvasGraphics.sendQueue();
    }
    
    public void setBackground(Color c) {
    	if (canvasGraphics != null) {
    		if (canvasStyle == null) canvasStyle = (JSObject)canvasJsObject.getMember("style");
    		canvasStyle.setMember("backgroundColor", HTMLCanvas.cssColor(c));
    		canvasGraphics.setBackground(c);
    		backgroundSet = true;
    	}
    }
    
    public void setForegroundColor(Color c) {
    	if (canvasGraphics != null) canvasGraphics.setColor(c);
    }
    
    public void setFont(Font f) {
    	if (canvasGraphics != null) canvasGraphics.setFont(f);
    }
    
    public Graphics getGraphics() { return canvasGraphics; }
    
    public boolean isBackgroundSet() { return backgroundSet; }
    
    public boolean prepareImage(Image image, ImageObserver observer) {
		return canvasGraphics != null && canvasGraphics.prepareImage(image, observer);
    }

    public boolean prepareImage(Image image, int width, int height, ImageObserver observer) {
    	return prepareImage(image, observer);
    }
    
    public int checkImage(Image image, ImageObserver observer) {
    	return canvasGraphics != null ? canvasGraphics.checkImage(image, observer) : ImageObserver.ERROR;
    }

    public int checkImage(Image image, int width, int height, ImageObserver observer) {
    	return checkImage(image, observer);
    }

    // Package can access
    
    JSObject	jsObject, canvasJsObject = null;
    String		className;
	
	void _init(JSONObject params, String href) {
		parameters = params;
		String logLevel = getParameter("logLevel");
		try {
			Level level = logLevel != null ? Level.parse(logLevel) : Level.INFO;
			nlog.setLevel(level);
			hlog.setLevel(level);
		} catch (IllegalArgumentException e) {
			nlog.warning("Invalid logLevel \"" + logLevel + "\"");
		}
		try {
			url = new URL(href);
		} catch (MalformedURLException e) {
			nlog.warning("Malformed webpage URL \"" + href + "\"");
		}
		if (canvasJsObject != null) canvasGraphics = new HTMLCanvas(this);
		init();
		_start();
	}

	void _start() { active = true; start(); repaint(); }
	void _stop()  { active = false; stop(); }
	
	static WebpageHelper getInstanceForClassName(String className) { return instancesByClassName.get(className); }
	
	// Private
	
	private boolean		active = false, backgroundSet = false;
	private JSONObject	parameters = null;
	private URL			url = null;
	private JSObject	canvasStyle = null;
	private HTMLCanvas	canvasGraphics = null;
	
	private static HashMap<String, WebpageHelper> instancesByClassName = new HashMap<String, WebpageHelper>();
}
