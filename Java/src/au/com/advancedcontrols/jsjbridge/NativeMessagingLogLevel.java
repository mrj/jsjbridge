/* 
 *    JSJBridge NativeMessagingLogLevel Class
 *    Copyright 2019 Mark Reginald James
 *     Licensed under Version 1 of the DevWheels Licence. See file LICENCE.txt and devwheels.com.
*/

package au.com.advancedcontrols.jsjbridge;

import java.util.logging.Level;

public class NativeMessagingLogLevel extends Level {
    private static final long serialVersionUID = 1367501055376035397L;

    protected NativeMessagingLogLevel(String name, int value) {    super(name, value);    }

    public static Level STDERR_ONLY = new NativeMessagingLogLevel("STDERR_ONLY", 600),
                        STDERR_ONLY_JSJBRIDGE_DEBUG = new NativeMessagingLogLevel("STDERR_ONLY_JSJBRIDGE_DEBUG", 0);
}
