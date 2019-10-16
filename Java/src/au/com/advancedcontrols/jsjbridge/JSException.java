/* 
 *    JSJBridge JSException Class
 *    Copyright 2019 Mark Reginald James
 *     Licensed under Version 1 of the DevWheels Licence. See file LICENCE.txt and devwheels.com.
*/

package au.com.advancedcontrols.jsjbridge;

public class JSException extends RuntimeException {
    private String message = "";
    protected JSException() {}
    protected JSException(String s) { message = s; }
    public String getMessage() { return message; }
    private static final long serialVersionUID = -5458368125994102955L;
}