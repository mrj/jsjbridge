/* 
 *	JSJBridge NativeMessagingLogger Class
 *	Copyright 2019 Mark Reginald James
 * 	Licensed under Version 1 of the DevWheels Licence. See file LICENCE.txt and devwheels.com.
*/

package au.com.advancedcontrols.jsjbridge;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NativeMessagingLogger extends Logger {
	
	public NativeMessagingLogger(String name) {
		super(name, null);
		setUseParentHandlers(false);
		TimestampedLogFormatter formatter = new TimestampedLogFormatter();
		this.addHandler(new BrowserConsoleLogHandler(formatter));
		ConsoleHandler stderrHandler = new ConsoleHandler();
		stderrHandler.setFormatter(formatter);
		stderrHandler.setLevel(Level.ALL);
		this.addHandler(stderrHandler);
	}
}
