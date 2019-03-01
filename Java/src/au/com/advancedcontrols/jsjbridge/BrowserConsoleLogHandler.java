package au.com.advancedcontrols.jsjbridge;

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

class BrowserConsoleLogHandler extends Handler {
	static Formatter formatter;

	public BrowserConsoleLogHandler(Formatter f) { formatter = f; setLevel(Level.ALL); }
	
	@Override
	public synchronized void publish(LogRecord record) {
		if (JSObject.isInitialized() && !NativeMessagingLogLevel.STDERR_ONLY.equals(record.getLevel())) {
			JSObject destination = null;
			Object[] parameters = record.getParameters();
			if (parameters != null) {
				destination = (JSObject)parameters[0];
				record.setThrown((Throwable) parameters[1]);
			} else {
				WebpageHelper helper = WebpageHelper.getInstanceForClassName(record.getLoggerName());
				if (helper != null) destination = helper.jsObject;
			}
			
			String msg = formatter.format(record);
			JSObject.log(msg, destination);
		}
	}

	@Override
	public void close() throws SecurityException {}

	@Override
	public void flush() {}
}
