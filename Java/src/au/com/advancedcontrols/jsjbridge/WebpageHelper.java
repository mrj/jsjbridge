/* 
 *	JSJBridge WebpageHelper Class
 *	Copyright 2019 Mark Reginald James
 * 	Licensed under Version 1 of the DevWheels Licence. See file LICENCE.txt and devwheels.com.
*/

package au.com.advancedcontrols.jsjbridge;

import org.json.JSONObject;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.logging.Level;

public class WebpageHelper {
	
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

    // Package can access
    
    JSObject	jsObject;
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
		init();
		_start();
	}

	void _start() { active = true; start(); }
	void _stop()  { active = false; stop(); }
	
	static WebpageHelper getInstanceForClassName(String className) { return instancesByClassName.get(className); }
	
	// Private
	
	private boolean		active = false;
	private JSONObject	parameters = null;
	private URL			url = null;
	
	private static HashMap<String, WebpageHelper> instancesByClassName = new HashMap<String, WebpageHelper>();
}
