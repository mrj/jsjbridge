/* 
 *    JSJBridge JSObject Class
 *    Copyright 2019 Mark Reginald James
 *     Licensed under Version 1 of the DevWheels Licence. See file LICENCE.txt and devwheels.com.
*/

package au.com.advancedcontrols.jsjbridge;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import org.json.JSONArray;
import org.json.JSONObject;

public class JSObject {

    // Public
	
	/*
	 * Emulation of the <a href="https://www.oracle.com/technetwork/java/javase/overview/liveconnect-docs-349790.html">LiveConnect API</a>
	 * of the Java Plugin, with the following changes"
	 * 
	 * 1. Java arrays are passed to JavaScript by value rather than reference, greatly speeding the transfer of large amounts of data
	 *    without having to encode that data as a String. To pass an array by reference so that changes are reflected in the Java object,
	 *    wrap arrays in an object such as an ArrayList.
	 *    
	 * 2. The arguments of a JavaScript function to call can be given as separate arguments to the call method, rather than an
	 *    Object array. An extra dummy argument must be added to call a JavaScript method that takes a single array parameter. 
	 */

    public static final JSObject UNDEFINED = new JSObject();

    public static JSObject getWindow(WebpageHelper helper) throws JSException {
        return (JSObject)helper.jsObject.sendRequest("getWindow", null);
    }

    public Object getMember(String name) throws JSException {
        return sendRequest("getMember", name);
    }

    public Object removeMember(String name) throws JSException {
        return sendRequest("removeMember", name);
    }

    public Object setMember(String name, Object value) throws JSException {
        return sendRequest("setMember", name, value);
    }

    public Object getSlot(int index) throws JSException {
        return sendRequest("getSlot", index);
    }

    public Object setSlot(int index, Object value) throws JSException {
        return sendRequest("setSlot", index, value);
    }

    public Object eval(String javaScriptCode) throws JSException {
        return sendRequest("eval", null, javaScriptCode);
    }

    public Object call(String methodName) throws JSException {
        return sendRequest("call", methodName);
    }

    public Object call(String methodName, Object[] args) throws JSException {
        return sendRequest("call", methodName, args);
    }

    /*
     * A convenience method, not in the original LiveConnect API, that allows the
     * arguments to a JavaScript function call to be provided as separate arguments, rather
     * than an Object array. To use this to call a function with a single array parameter,
     * a second argument must be added to distinguish this from an array of arguments.
     * This second argument can be <code>(Object [])null</code>.
     */
    public Object call(String methodName, Object firstArg, Object... otherArgs) throws JSException {
        Object[] allArgs = new Object[otherArgs == null ? 1 : otherArgs.length+1];
        allArgs[0] = firstArg;
        if (otherArgs != null) {
        	int i = 1;
        	for (Object otherArg: otherArgs) allArgs[i++] = otherArg;
        }
        return call(methodName, allArgs);
    }

    public static boolean isInitialized() { return initialized; }

    public String toString() { return uid == null ? "undefined" : (uid + "@" + portId); }

    // Package can access

    int portId;         // The content script port number associated with the browser tab/frame for this JavaScript object
    Integer uid = null; // The unique object ID within this tab/frame

    static void init()  {
        if (initialized) destroy();
        try {
            messageSender = new PrintStream(System.out, false, "UTF-8");
            sendingMessageLengthBuffer = makeMessageLengthBuffer();
            receivingMessageLengthBuffer = makeMessageLengthBuffer();
            receivingMessageLengthBytes = new byte[4];
            contextsByObject = new HashMap<Object, Integer>();
            contextsByUID = new ArrayList<Object>();
            methodsByClassAndName = new HashMap<Class<?>, HashMap<String, ArrayList<Method>>>();  
            replyFutures = new HashMap<Long, CompletableFuture<JSONObject>>();
            ReceiveMessagesFromWebpage messageReceiver = new ReceiveMessagesFromWebpage();
            receiveMessageThread = new Thread(messageReceiver, "Receive messages from JavaScript");
            receiveMessageThread.setDaemon(true);
            receiveMessageThread.start();
            initialized = true;
            try { receiveMessageThread.join(); } catch (InterruptedException e) {}
        } catch (Exception e) {
            crash(e, null);
        }
    }

    static void destroy() {
        try {
            if (receiveMessageThread != null) {
                receiveMessageThread.interrupt();
                receiveMessageThread.join();
                initialized = false;
            }
        } catch (Exception e) {}
    }

    Object sendRequest(String type, Object name, Object value) throws JSException {
        return doSendRequest(this, type, name, value);
    }

    Object sendRequest(String type, Object name) throws JSException {
        return sendRequest(type, name, null);
    }

    // Package & WebpageHelpers can access

    protected static Object log(String msg, JSObject target) {
        return doSendRequest(target, "log", null, msg);
    }

    // Private

    private JSObject() {}
    private JSObject(int pid, int wid) { portId = pid; uid = wid; }

    private static final int    REQUEST_TIMEOUT_S = 60,
                                MAXIMUM_MESSAGE_LENGTH_BYTES = 1024 * 1024,
                                MESSAGE_FRAGMENT_OVERHEAD_BYTES = 8,  // {"c":"<MSG>"}
                                MESSAGE_FRAGMENT_SUFFIX_BYTES = 2,
                                MAXIMUM_ENCODED_MESSAGE_LENGTH_BYTES = MAXIMUM_MESSAGE_LENGTH_BYTES - MESSAGE_FRAGMENT_OVERHEAD_BYTES,
                                MESSAGE_FRAGMENT_PREFIX_BYTES = MESSAGE_FRAGMENT_OVERHEAD_BYTES - MESSAGE_FRAGMENT_SUFFIX_BYTES;

    private static final byte[] MESSAGE_CONTINUES_PREFIX    = new byte[]{'{','"', 'c', '"', ':', '"'},
                                MESSAGE_FRAGMENT_END_PREFIX = new byte[]{'{','"', 'e', '"', ':', '"'},
                                MESSAGE_FRAGMENT_SUFFIX     = new byte[]{'"','}'};
    static byte[] fragmentSuffixSave = new byte[2];

    private static HashMap<Object, Integer> contextsByObject;
    private static ArrayList<Object> contextsByUID;
    private static HashMap<Class<?>, HashMap<String, ArrayList<Method>>>  methodsByClassAndName;

    private static ByteBuffer sendingMessageLengthBuffer, receivingMessageLengthBuffer;
    private static byte[] receivingMessageLengthBytes;
    private static PrintStream messageSender;

    private static boolean initialized = false;
    private static Thread receiveMessageThread;
    private static HashMap<Long, CompletableFuture<JSONObject>> replyFutures;
    private static Long nextRequestNumber = 1L;

    private static final String UNDEFINED_JSON_KEY = "JSundefined",
                                NAN_JSON_KEY = "NaN",
                                POSITIVE_INFINITY_JSON_KEY = "+Infinity",
                                NEGATIVE_INFINITY_JSON_KEY = "-Infinity",
                                REQUEST_NUMBER_JSON_KEY = "RequestNumber",
                                APPLET_JSON_KEY = "appletClass",
                                PARAMETERS_JSON_KEY = "parameters",
                                //APPLET_WIDTH_JSON_KEY = "width",
                                //APPLET_HEIGHT_JSON_KEY = "height",
                                URL_JSON_KEY = "url",
                                NAME_JSON_KEY = "name",
                                VALUE_JSON_KEY = "value",
                                TYPE_JSON_KEY = "type",
                                JAVA_UID_JSON_KEY = "javaUID",
                                JS_ITERABLE_JSON_KEY = "iterableClass",
                                //CANVAS_UID_JSON_KEY = "canvasUID",
                                JS_WINDOW_UID_JSON_KEY = "jsUID",
                                CONTENT_SCRIPT_PORTID_JSON_KEY = "portId";

    private static final JSONObject UNDEFINED_JSON = (new JSONObject()).put(UNDEFINED_JSON_KEY, true),
                                    NAN_JSON = (new JSONObject()).put(NAN_JSON_KEY, true),
                                    POSITIVE_INFINITY_JSON = (new JSONObject()).put(POSITIVE_INFINITY_JSON_KEY, true),
                                    NEGATIVE_INFINITY_JSON = (new JSONObject()).put(NEGATIVE_INFINITY_JSON_KEY, true);

    private static final List<Class<?>> primitiveArrayTypes = Arrays.asList(
        // No bulk conversion of floating point, because they could contain the NaN or Infinities that JSON lacks
        int[].class, long[].class, short[].class, String[].class, Boolean[].class, boolean[].class, byte[].class, char[].class
    );

    private static void crash(Exception e, JSObject target) {
        if (e instanceof JSException) {
            WebpageHelper.hlog.log(Level.SEVERE, e.getMessage(), new Object[]{target, e});
            if (target != null) throw (JSException)e;
        } else {
            WebpageHelper.hlog.log(Level.SEVERE, "JSJBridge crashed", e);
        }
    }

    private static ByteBuffer makeMessageLengthBuffer() {
        return ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());
    }

    private static Object doSendRequest(JSObject target, String type, Object name, Object value) throws JSException {
        try {
            final CompletableFuture<JSONObject> replyFuture = new CompletableFuture<JSONObject>();
            JSONObject messageJson = new JSONObject().put(TYPE_JSON_KEY, type);

            if (target != null) messageJson.put(CONTENT_SCRIPT_PORTID_JSON_KEY, target.portId).put(JS_WINDOW_UID_JSON_KEY, target.uid);
            if (name != null) messageJson.put(NAME_JSON_KEY, name);
            if (value != null) messageJson.put(VALUE_JSON_KEY, getJsonValue(value));

            synchronized (nextRequestNumber) {
                messageJson.put(REQUEST_NUMBER_JSON_KEY, nextRequestNumber);
                replyFutures.put(nextRequestNumber++, replyFuture);
            }

            messageWebpage(messageJson);

            try {
                final JSONObject reply = replyFuture.get(REQUEST_TIMEOUT_S, TimeUnit.SECONDS);
                if (reply.has("exception")) throw new JSException("Exception in JavaScript: " + reply.getString("exception"));
                final Object replyValue = reply.get(VALUE_JSON_KEY);
                return jSONValueToJava(replyValue, reply.getInt(CONTENT_SCRIPT_PORTID_JSON_KEY));
            } catch (TimeoutException e) {
                // Don't get caught in an infinite exchange of messages if there's a log reply timeout
                if (!type.equals("log")) throw new JSException("No response to " + type + " \"" + String.valueOf(name) + "\" after " + REQUEST_TIMEOUT_S + "s");
            }
        } catch (Exception e) {
            crash(e, target);
        }

        return null;
    }

    private static Object getJsonValue(Object value) throws NoSuchMethodException, SecurityException {
        WebpageHelper.hlog.log(NativeMessagingLogLevel.STDERR_ONLY_JSJBRIDGE_DEBUG, "value = " + (value==null ? "null" : value) + " class = " + (value == null ? "null" : value.getClass().getTypeName()));
        if (value instanceof Float) {
            Float fvalue = (Float)value;
            return  Float.isNaN(fvalue) ? NAN_JSON :
                    fvalue == Float.POSITIVE_INFINITY ? POSITIVE_INFINITY_JSON :
                    fvalue == Float.NEGATIVE_INFINITY ? NEGATIVE_INFINITY_JSON :
                    fvalue;
        } else if (value instanceof Double) {
            Double dvalue = (Double)value;
            return  Double.isNaN(dvalue) ? NAN_JSON :
                    dvalue == Double.POSITIVE_INFINITY ? POSITIVE_INFINITY_JSON :
                    dvalue == Double.NEGATIVE_INFINITY ? NEGATIVE_INFINITY_JSON :
                    dvalue;
        } else if (value == null || value instanceof Number || value instanceof String || value instanceof Boolean) {
            return value;
        } else if (value instanceof Void) {
            return null;
        } else if (primitiveArrayTypes.stream().anyMatch(arrayClass -> arrayClass.isInstance(value))) {
            return new JSONArray(value);
        } else if (value.getClass().isArray()) {
            JSONArray jarray = new JSONArray();
            if       (value instanceof  float[]) for (float  n: (float[]) value) jarray.put(getJsonValue(n));
            else if  (value instanceof double[]) for (double n: (double[])value) jarray.put(getJsonValue(n));
            else                                 for (Object o: (Object[])value) jarray.put(getJsonValue(o));
            return jarray;
        } else if (value instanceof JSObject) {
            return value == UNDEFINED ? UNDEFINED_JSON : (new JSONObject()).put(JS_WINDOW_UID_JSON_KEY, ((JSObject)value).uid);
        } else {
            Integer javaUID;
            synchronized (contextsByObject) {
                javaUID = contextsByObject.get(value);
                if (javaUID == null) {
                    javaUID = contextsByUID.size();
                    WebpageHelper.hlog.log(NativeMessagingLogLevel.STDERR_ONLY_JSJBRIDGE_DEBUG, "New Java object " + javaUID + " class " + value.getClass().toString());
                    contextsByObject.put(value, javaUID);
                    contextsByUID.add(value);
                }
            }
            Class<?> valueClass = value.getClass();
            JSONArray fieldNames = null, publicMethodNames = null;
            synchronized (methodsByClassAndName) {
                if (!methodsByClassAndName.containsKey(valueClass)) {
                    Field[] fields = valueClass.getFields();
                    String[] fieldNameStrings = new String[fields.length];
                    for (int i=fields.length-1; i>=0; --i) fieldNameStrings[i] = fields[i].getName();
                    fieldNames = new JSONArray(fieldNameStrings);

                    HashMap<String, ArrayList<Method>> methodsByName = new HashMap<String, ArrayList<Method>>();
                    for (Method method: valueClass.getMethods()) {
                        String methodName = method.getName();
                        ArrayList<Method> methodsWithName = methodsByName.get(methodName);
                        if (methodsWithName == null) {
                            methodsWithName = new ArrayList<Method>();
                            methodsByName.put(methodName, methodsWithName);
                        }
                        methodsWithName.add(method);
                    }
                    publicMethodNames = new JSONArray(methodsByName.keySet());
                    methodsByClassAndName.put(valueClass, methodsByName);
                }
            }
            JSONObject jo = (new JSONObject()).put(JAVA_UID_JSON_KEY, javaUID).put("className", value.getClass().getTypeName());
            if (fieldNames != null) jo.put("fieldNames", fieldNames).put("methodNames", publicMethodNames);
            return jo;
        } 
    }

    private static Object jSONValueToJava(Object o, int portId) {
        if (o == JSONObject.NULL) {
            return null;
        } else if (o instanceof JSONObject) {
            JSONObject jo = (JSONObject)o;
            if (jo.has(JAVA_UID_JSON_KEY)) {
                synchronized (contextsByObject) { return contextsByUID.get(jo.getInt(JAVA_UID_JSON_KEY)); }
            } else if (jo.has(JS_ITERABLE_JSON_KEY)) {
                JSONArray jarray = (JSONArray)jo.get(VALUE_JSON_KEY);
                int length = jarray.length();
                //
                // Is there any efficient way to avoid this duplication?
                //
                switch ((String)jo.get(JS_ITERABLE_JSON_KEY)) {
                    case "Int8Array":
                    case "Uint8Array":
                    case "Uint8ClampedArray":
                        byte[] byteArray = new byte[length];
                        for (int i=length-1; i>=0; i--) byteArray[i] = (byte)jarray.getInt(i);
                        return byteArray;
                    case "Int16Array":
                    case "Uint16Array":
                        short[] shortArray = new short[length];
                        for (int i=length-1; i>=0; i--) shortArray[i] = (short)jarray.getInt(i);
                        return shortArray;
                    case "Int32Array":
                    case "Uint32Array":
                        int[] intArray = new int[length];
                        for (int i=length-1; i>=0; i--) intArray[i] = jarray.getInt(i);
                        return intArray;
                    case "BigInt64Array":
                    case "BigUint64Array":
                        long[] longArray = new long[length];
                        for (int i=length-1; i>=0; i--) longArray[i] = jarray.getLong(i);
                        return longArray;
                    case "Float32Array":
                        double[] floatArray = new double[length];
                        for (int i=length-1; i>=0; i--) floatArray[i] = (double)jarray.getDouble(i);
                        return floatArray;                        
                    case "Float64Array":
                        double[] doubleArray = new double[length];
                        for (int i=length-1; i>=0; i--) doubleArray[i] = jarray.getDouble(i);
                        return doubleArray;                                    

                    default: return jSONValueToJava(jarray, portId);
                }
            } else {
                return  jo.has(UNDEFINED_JSON_KEY) ? UNDEFINED :
                        jo.has(NAN_JSON_KEY) ? Double.NaN :
                        jo.has(POSITIVE_INFINITY_JSON_KEY) ? Double.POSITIVE_INFINITY :
                        jo.has(NEGATIVE_INFINITY_JSON_KEY) ? Double.NEGATIVE_INFINITY :
                                                             new JSObject(portId, jo.getInt(JS_WINDOW_UID_JSON_KEY));
            }
        } else if (o instanceof JSONArray) {
            JSONArray jarray = (JSONArray)o;
            int length = jarray.length();
            Object[] oarray = new Object[length];
            for (int i=length-1; i>=0; i--) oarray[i] = jSONValueToJava(jarray.get(i), portId);
            return oarray;
        } else {
            return o;
        }
    }

    private static void messageWebpage(JSONObject messageJson) {
        final String message = messageJson.toString();
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

        synchronized (messageSender) {
            // Avoid unnecessary String construction
            boolean doLog = WebpageHelper.hlog.getLevel().intValue() <= NativeMessagingLogLevel.STDERR_ONLY_JSJBRIDGE_DEBUG.intValue();

            if (doLog) WebpageHelper.hlog.log(NativeMessagingLogLevel.STDERR_ONLY_JSJBRIDGE_DEBUG, "SENDING MESSAGE: " + message + "\n");
            if (messageBytes.length <= MAXIMUM_MESSAGE_LENGTH_BYTES) {
                sendMessage(messageBytes, 0, messageBytes.length);
            } else {
                String messageAsJSONString = "XXXXX" + JSONObject.quote(message) + "X";
                messageBytes = messageAsJSONString.getBytes(StandardCharsets.UTF_8);
                int nextFragmentOffset, messageLength = messageBytes.length - MESSAGE_FRAGMENT_OVERHEAD_BYTES;
                for (int offset = 0; offset < messageLength; offset = nextFragmentOffset) {
                    nextFragmentOffset = Math.min(offset + MAXIMUM_ENCODED_MESSAGE_LENGTH_BYTES, messageLength);
                    int nextFragmentStart = nextFragmentOffset + MESSAGE_FRAGMENT_PREFIX_BYTES;

                    if (messageBytes[nextFragmentStart-1] == '\\' && messageBytes[nextFragmentStart-2] != '\\') {
                        nextFragmentOffset--;
                        nextFragmentStart--;
                    }

                    boolean continues = nextFragmentOffset < messageLength;
                    byte[] prefix = continues ? MESSAGE_CONTINUES_PREFIX : MESSAGE_FRAGMENT_END_PREFIX;
                    int fragmentLength = nextFragmentStart - offset + MESSAGE_FRAGMENT_SUFFIX_BYTES;

                    System.arraycopy(prefix, 0, messageBytes, offset, MESSAGE_FRAGMENT_PREFIX_BYTES);
                    if (continues) System.arraycopy(messageBytes, nextFragmentStart, fragmentSuffixSave, 0, MESSAGE_FRAGMENT_SUFFIX_BYTES);
                    System.arraycopy(MESSAGE_FRAGMENT_SUFFIX, 0, messageBytes, nextFragmentStart, MESSAGE_FRAGMENT_SUFFIX_BYTES);
                    if (doLog) {
                        String fragment = new String(messageBytes, offset, fragmentLength, StandardCharsets.UTF_8);
                        WebpageHelper.hlog.log(NativeMessagingLogLevel.STDERR_ONLY_JSJBRIDGE_DEBUG, "SENDING FRAGMENT: " + fragment + "\n");
                    }
                    sendMessage(messageBytes, offset, fragmentLength);
                    if (continues) System.arraycopy(fragmentSuffixSave, 0, messageBytes, nextFragmentStart, MESSAGE_FRAGMENT_SUFFIX_BYTES);
                }
            }
        }
    }

    private static void sendMessage(byte[] messageBytes, int offset, int length) {
        messageSender.write(sendingMessageLengthBuffer.putInt(length).array(), 0, 4);
        messageSender.write(messageBytes, offset, length);
        messageSender.flush();
        sendingMessageLengthBuffer.rewind();
    }

    private static class ReceiveMessagesFromWebpage implements Runnable {
        private final List<String> JAVASCRIPT_TO_JAVA_REQUESTS = Arrays.asList("newApplet", "start", "stop", "destroy", "invoke", "get", "set");

        private void setException(JSONObject message, String error, Object context, Throwable e) {
            if (context instanceof WebpageHelper) {
                ((WebpageHelper) context).nlog.log(Level.SEVERE, error, e);
            } else {
                WebpageHelper.hlog.log(Level.SEVERE, error, e);
            }
            message.put("JavaException", error);
            message.remove(VALUE_JSON_KEY);
        }

        private String errorMsg(Object context, String name) {
            return "\"" + name + "\" of class \"" + context.getClass().getTypeName() + "\"";
        }    

        private String errorMsg(String prefix, String invocation) {
            return prefix + " " + invocation;
        }

        private int calculateMethodMatchScore(Object[] args, Class<?>[] argClasses, Method candidateMethod) {
            // TODO: Implement something like the LiveConnect overloaded method resolution
            // https://www.oracle.com/technetwork/java/javase/overview/liveconnect-docs-349790.html#METHOD_CALLS
            return 0;
        }

        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    JSONObject receivedMessage;

                    System.in.read(receivingMessageLengthBytes, 0, 4);
                    receivingMessageLengthBuffer.put(receivingMessageLengthBytes);
                    receivingMessageLengthBuffer.rewind();
                    int messageLength = receivingMessageLengthBuffer.getInt();
                    receivingMessageLengthBuffer.rewind();
                    if (messageLength == 0) continue;
                    byte[] messageBytes = new byte[messageLength];
                    System.in.read(messageBytes, 0, messageLength);
                    String message = new String(messageBytes, StandardCharsets.UTF_8);
                    if (message.charAt(0) != '{' ) return;
                    WebpageHelper.hlog.log(NativeMessagingLogLevel.STDERR_ONLY_JSJBRIDGE_DEBUG,"GOT " + messageLength + " BYTE MESSAGE \"" + message + "\"\n");
                    receivedMessage = new JSONObject(message);

                    final String messageType = receivedMessage.getString(TYPE_JSON_KEY);

                    if (JAVASCRIPT_TO_JAVA_REQUESTS.contains(messageType)) {
                        final int portId = receivedMessage.getInt(CONTENT_SCRIPT_PORTID_JSON_KEY);
                        WebpageHelper newHelper = null;
                        String newHelperClassName = null, newHelperHref = null;
                        JSONObject newHelperParameters = null;

                        if (messageType.equals("newApplet")) {
                            newHelperClassName = receivedMessage.getString(APPLET_JSON_KEY);
                            try {
                                final Class<?> helperClass = Class.forName(newHelperClassName);
                                if (WebpageHelper.class.isAssignableFrom(helperClass)) {
                                    newHelper = (WebpageHelper) helperClass.newInstance();
                                    JSONObject value = (JSONObject) getJsonValue(newHelper);
                                    receivedMessage.put(VALUE_JSON_KEY, value);
                                    newHelper.jsObject = new JSObject(portId, value.getInt(JAVA_UID_JSON_KEY));
                                /*
                                    Object canvasUID = receivedMessage.remove(CANVAS_UID_JSON_KEY);
                                    if (canvasUID != null) {
                                        newHelper.canvasJsObject = new JSObject(portId, (int)canvasUID);
                                        newHelper.setSize(    Integer.parseInt((String)receivedMessage.remove(APPLET_WIDTH_JSON_KEY)),
                                                            Integer.parseInt((String)receivedMessage.remove(APPLET_HEIGHT_JSON_KEY)));
                                    }
                                */
                                    newHelperParameters = (JSONObject)receivedMessage.remove(PARAMETERS_JSON_KEY);
                                    newHelperHref = (String)receivedMessage.remove(URL_JSON_KEY);
                                } else {
                                    setException(receivedMessage, "\"" + newHelperClassName + "\" is not a subclass of WebpageHelper", null, null);
                                }
                            } catch (ClassNotFoundException e) {
                                setException(receivedMessage, "No such Java class \"" + newHelperClassName + "\".", null, null);
                            } catch (InstantiationException | IllegalAccessException e) {
                                setException(receivedMessage, "Could not create an instance of Java class \"" + newHelperClassName + "\".", null, null);
                            } 
                        } else {
                            final int javaUID = receivedMessage.getInt(JAVA_UID_JSON_KEY);
                            final Object context;
                            synchronized (contextsByObject) { context = contextsByUID.get(javaUID); }

                            if (messageType.equals("start")) {
                                final WebpageHelper helper = ((WebpageHelper)context);
                                new Thread(() -> {
                                    try {
                                        helper._start();
                                    } catch (Exception e) {
                                        helper.nlog.log(Level.SEVERE, "Exception starting " + helper.className, e);
                                    }
                                }, "Starting " + helper.className).start();
                            } else if (messageType.equals("stop")) {
                                final WebpageHelper helper = ((WebpageHelper)context);
                                new Thread(() -> {
                                    try {
                                        helper._stop();
                                    } catch (Exception e) {
                                        helper.nlog.log(Level.SEVERE, "Exception stopping " + helper.className, e);
                                    }
                                }, "Stopping " + helper.className).start();
                            } else if (messageType.equals("destroy")) {
                                final WebpageHelper helper = ((WebpageHelper)context);
                                new Thread(() -> {
                                    try {
                                        helper._destroy();
                                    } catch (Exception e) {
                                        helper.nlog.log(Level.SEVERE, "Exception destroying " + helper.className, e);
                                    }
                                }, "Destroying " + helper.className).start();
                            } else {
                                final String name = receivedMessage.getString(NAME_JSON_KEY);
                                final Object messageValue = receivedMessage.get(VALUE_JSON_KEY);

                                if (messageType.equals("invoke")) {
                                    final Object[] args = (Object[]) jSONValueToJava(messageValue, portId);
                                    int numberoFCallingArgs = args.length;
                                    final Class<?>[] argClasses = new Class<?>[args.length];
                                    for (int i=0; i<args.length; i++) argClasses[i] = args[i] == null ? (Class<?>)null : args[i].getClass();
                                    final String[] argClassNames = new String[argClasses.length];
                                    for (int i=0; i<args.length; i++) argClassNames[i] = argClasses[i].getSimpleName();
                                    final String invocation = errorMsg(context, name + "(" + String.join(", ", argClassNames) + ")"); 

                                    ArrayList<Method> invocationCandidates, remainingCandidates;
                                    synchronized (methodsByClassAndName) { remainingCandidates = invocationCandidates = methodsByClassAndName.get(context.getClass()).get(name); }
                                    Method method = null;

                                    if (!invocationCandidates.isEmpty()) {
                                        remainingCandidates = new ArrayList<Method>(invocationCandidates);
                                        remainingCandidates.removeIf(m -> m.isVarArgs() ? numberoFCallingArgs < m.getParameterCount() : numberoFCallingArgs != m.getParameterCount());
                                    }

                                    String exception = null;

                                    if (remainingCandidates.isEmpty()) {
                                        exception = "No such method";
                                    } else if (remainingCandidates.size() == 1) {
                                        method = remainingCandidates.get(0);
                                    } else {
                                        int maxScore = -1, maxScoreAt = 0;
                                        for (int i=remainingCandidates.size()-1; i>=0; --i) {
                                            int score = calculateMethodMatchScore(args, argClasses, remainingCandidates.get(i));
                                            if (score == maxScore) {
                                                exception = "Ambiguous invocation";
                                                maxScoreAt = -1;
                                                break;
                                            } else if (score > maxScore) {
                                                maxScoreAt = i;
                                                maxScore = score;
                                            }
                                        }
                                        if (maxScoreAt >= 0) method = remainingCandidates.get(maxScoreAt);
                                    }

                                    if (exception != null) {
                                        setException(receivedMessage, errorMsg(exception, invocation), context, null);
                                    } else {
                                        WebpageHelper.hlog.finer("Invoking " + invocation);
                                        final Method chosenMethod = method;
                                        new Thread(() -> {
                                            String exceptionMsg = null;
                                            Exception exceptionObj = null;
                                            try {
                                                receivedMessage.put(VALUE_JSON_KEY, getJsonValue(chosenMethod.invoke(context, args)));
                                            } catch (InvocationTargetException e) {
                                                exceptionMsg = "Exception calling method"; exceptionObj = e;
                                            } catch (IllegalAccessException e) {
                                                exceptionMsg = "Could not access method"; exceptionObj = e;
                                            } catch (IllegalArgumentException e) {
                                                exceptionMsg = "Illegal argument to method"; exceptionObj = e;
                                            } catch (NoSuchMethodException e) {
                                            } finally {
                                                if (exceptionMsg != null) {
                                                    setException(receivedMessage, errorMsg(exceptionMsg, invocation), context, exceptionObj);
                                                    receivedMessage.remove(VALUE_JSON_KEY);
                                                }
                                                messageWebpage(receivedMessage);
                                            }
                                        }, "JavaScript invocation " + invocation).start();
                                        continue;
                                    }
                                } else {
                                    try {
                                        Field field = context.getClass().getField(name);
                                        if (messageType.equals("get")) {
                                            receivedMessage.put(VALUE_JSON_KEY, getJsonValue(field.get(context)));
                                        } else {
                                            field.set(context, jSONValueToJava(messageValue, portId));
                                        }
                                    } catch (NoSuchFieldException|IllegalAccessException e) {
                                        String prefix = e instanceof NoSuchFieldException ? "No such field" : "Could not access field";
                                        setException(receivedMessage, errorMsg(prefix, errorMsg(context, name)), context, null);
                                    }
                                }
                            }
                        }
                        messageWebpage(receivedMessage);

                        // Initialise the Helper after making the newApplet reply, so JavaScript has a uid for it if the init code makes JSObject calls
                        //
                        final WebpageHelper helper = newHelper;
                        final JSONObject parameters = newHelperParameters;
                        final String href = newHelperHref;
                        if (helper != null) {
                            new Thread(() -> {
                                try {
                                    helper._init(parameters, href);
                                    helper.jsObject.sendRequest("appletInitialized", helper.className, null);
                                } catch (Exception e) {
                                    helper.nlog.log(Level.SEVERE, "Exception initializing " + helper.className, e);
                                }
                            }, "Initialization of " + newHelper.className).start();
                        }
                    } else {
                        final long requestNumber = receivedMessage.getLong(REQUEST_NUMBER_JSON_KEY);
                        CompletableFuture<JSONObject> replyFuture;
                        synchronized (nextRequestNumber) { replyFuture = replyFutures.remove(requestNumber); }
                        if (replyFuture != null) replyFuture.complete(receivedMessage);
                    }
                }
            } catch (Exception e) { crash(e, null); }
        }
    }
}