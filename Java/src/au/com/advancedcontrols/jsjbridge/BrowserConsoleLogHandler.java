package au.com.advancedcontrols.jsjbridge;

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

class BrowserConsoleLogHandler extends Handler {
    static Formatter formatter;

    public BrowserConsoleLogHandler(Formatter f) { formatter = f; setLevel(Level.ALL); }

    private boolean stderrOnly(LogRecord record) {
        Level recordLevel = record.getLevel();
        return  NativeMessagingLogLevel.STDERR_ONLY.equals(recordLevel) ||
                NativeMessagingLogLevel.STDERR_ONLY_JSJBRIDGE_DEBUG.equals(recordLevel);
    }

    @Override
    public synchronized void publish(LogRecord record) {
        if (JSObject.isInitialized() && !stderrOnly(record)) {
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
