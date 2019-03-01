/* 
 *	JSJBridge TimestampedLogFormatter Class
 *	Copyright 2019 Mark Reginald James
 * 	Licensed under Version 1 of the DevWheels Licence. See file LICENCE.txt and devwheels.com.
*/

package au.com.advancedcontrols.jsjbridge;

import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

class TimestampedLogFormatter extends Formatter {
	static DateFormat timestampFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	private String getExceptionText(Throwable exception) {
		String trace = exception.toString() + "\n";
		for (StackTraceElement level: exception.getStackTrace()) trace += "  " + level.toString() + "\n";
		return trace;
	}

	@Override
	public String format(LogRecord record) {
		Throwable exception = record.getThrown();
		String exceptionText = exception == null ? "" : getExceptionText(exception.getCause() == null ? exception : exception.getCause());
		
		return	"[" + timestampFormatter.format(new Date(record.getMillis())) + "] " +
				record.getLevel().getName() + " " + 
				record.getLoggerName() + ": " +
				record.getMessage() + " " +
				exceptionText +
				"\n";
	}
}