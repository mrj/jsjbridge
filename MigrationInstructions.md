# Migrating from Applets and the Java Plugin to WebpageHelpers and JSJBridge

## Java-Side (Java &ge; 8 required)

1. Put [jsjbridge.jar](https://advancedcontrols.com.au/jsjbridge/releases.html) in your buildpath and classpath in place of plugin.jar. If using version 1.7 or below, you also need [json.jar](https://github.com/stleary/JSON-java) in your buildpath and classpath.

1. Change imports of `netscape.javascript.*` to `au.com.advancedcontrols.jsjbridge.*`.
       
2. Change mentions of class `Applet` to `WebpageHelper`.
        
3. Turn your Applet `.jar` or `.class` file into a runnable JAR that uses `WebpageHelper` as its main class (or have `main` in one of your `WebpageHelper` classes call `WebpageHelper.main`).
         
4. Strip or comment any Applet graphics code that is causing compile errors. Support for (slow-changing) graphics may be added at some point.

5. You can no longer read from `System.in` or write to `System.out`. As a replacement for logging to the Java Console via the standard output, there is a `WebpageHelper` instance field called `nlog` (native log), which is a [java.util.logging.Logger](https://docs.oracle.com/javase/8/docs/api/java/util/logging/Logger.html). Logging to this object logs to both the standard error stream (which can be redirected to a file, as described below) and the browser console (unless one logs to level `NativeMessagingLogLevel.STDERR_ONLY`). The level of these loggers can be set via Applet `logLevel` parameters. e.g. 

  ```html
  <param name="logLevel" value="FINE" />
  ```
  
  The default log level is "INFO".

6. The [LiveConnect JSObject API](https://www.oracle.com/webfolder/technetwork/java/plugin2/liveconnect/jsobject-javadoc/index.html) should be able to be used unchanged, except that the `eval` method isn't available on the Firefox version, and a `JSObject.UNDEFINED` object is now returned when JavaScript returns `undefined`. Also, Java arrays are now passed to JavaScript by value rather than reference. This greatly speeds sending a large amount of data from Java to JavaScript, since there was previously a call to Java for each array access. But it also means that array changes are not automatically propagated back to Java.


## Browser-Side (Firefox &ge; 58, Chrome &ge; 63 required)

1. By default, the JSJBridge browser extension is inactive at all URLs. Go to the JSJBridge extension Preference page in the Extension Manager to set one or more URL prefixes at which the extension becomes active. If you're using Chrome, and your HTML is on the filesystem, remember to enable the "Allow access to file URLs" JavaScript-Java Bridge extension setting.

2. The `applet`/`object` HTML tags for your Applets can stay the same. JavaScript objects which represent your Applets must obtained via `document.getElementById` calls. It should also be OK when the actual `getElementById` calls are being made through a JavaScript library.

3. For each Java program (each of which can host more than one `Applet`/`WebpageHelper` class) you then need to create a Native Messaging manifest file ([Firefox](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Native_manifests#Native_messaging_manifests), [Chrome](https://developers.chrome.com/apps/nativeMessaging#native-messaging-host)).

   The `name` field in each manifest file (and the basename of the `.json` manifest filename) must be the same as the Applet `archive` parameter, with the `.jar` suffix removed, and the JSJBridge extension must be given permission to use it. You will need to create and point manifests to a script that runs your WebpageHelper JAR files via `java -jar myHelper.jar`.

   e.g., for Firefox

   ```json
   {
     "name": "<The value of the Applet-element 'archive' parameter without the '.jar' suffix = the basename of original Applet JAR file>",
     "description": "My Java WebpageHelper",
     "path": "/path/to/helper_start_script",
     "type": "stdio",
     "allowed_extensions": ["jsjbridge@advancedcontrols.com.au"]
   }
   ```

   It's useful for debugging to have the start-script append standard error output from the program to a log file: e.g.

   ```
   #!/bin/bash
   exec java -jar myHelper.jar 2>> /path/to/myJavaProgram.log
   ```

   Then put or link your manifest files in the correct location for your OS ([Firefox](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Native_manifests#Manifest_location), [Chrome](https://developers.chrome.com/apps/nativeMessaging#native-messaging-host-location)).

4. You can have as many active Applets as you like in and across tabs, and each program can have more than one `WebpageHelper` class, but only one instance of each Java program runs at any one time, meaning that there can only be one active Applet for each combination of program name and `WebpageHelper` class. 

5. The extension makes Applet DOM objects both ususal `HTMLElement` objects, on which you can get and set normal properties, and also objects on which you can access and set Java fields, and call Java methods.

   The `initialized` event will fire on the `applet`/`object` element when its Java `init()` method has returned, allowing you to delay work until this has occurred.

   If an attempt is made to access a nonexisting property of an Applet object, a call to Java is triggered,
   and the JavaScript expression returns a [Promise](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide/Using_promises).
   The JavaScript execution cycle will continue to execute the code after the Java call before the result is available.

   So if you want JavaScript code to do something dependent on a value retrieved from Java, you must either,

   (a) Preface your Java-calling expressions with `await`, inside an async function (or in a browser console).
   
   e.g. Original code

   ```javascript
   console.log('Vector size is ' + helper.javaVector.size());
   ```

   must become either

   ```javascript
   console.log('Vector size is ' + await helper.javaVector.size());
   ```

   or

   ```javascript
   async function showSize(javaVector) {
     console.log('Vector size is ' + await helper.javaVector.size());
   }
   showSize(javaVector);
   ```

   The `showSize` call will return immediately, but the logging will be executed when the size is returned from Java.

   If Java calls a JavaScript method that makes a number of `showSize` calls: e.g.

   ```javascript
   function showSizes(vector1, vector2, vector3) {
     showSize(vector1);
     showSize(vector2);
     showSize(vector3);
   }
   ```

   The function as written above will return before making any `size()` calls to Java, causing the calling Java thread to immediately continue. If you instead wish for Java calls to JavaScript functions to block until the function is fully evaluated, as was the case with the Java Plugin, you need to either,

   (i), If the sequence of `showSize` calls is important, add `await` to each `showSize` call:

   ```javascript
   async function showSizes(vector1, vector2, vector3) {
     await showSize(vector1);
     await showSize(vector2);
     await showSize(vector3);
   }
   ```

   or (ii), allow the calls to execute in any order, but prevent the function from resolving until all have been completed:

   ```javascript
   async function showSizes(vector1, vector2, vector3) {
     await Promise.all([
       showSize(vector1),
       showSize(vector2),
       showSize(vector3)
     ]);
   }
   ```

   or, equivalently, you can explicity return the Promise:

   ```javascript
   function showSizes(vector1, vector2, vector3) {
     return Promise.all([
       showSize(vector1),
       showSize(vector2),
       showSize(vector3)
     ]);
   }
   ```

   or (b) Instead of using `await`, you can put dependent code in a `then` block, which can be done at the JavaScript top-level:

  ```javascript
  helper.javaVector.size().then(size => console.log('Vector size is ' + size));
  ```

  You can also put `await` in front of a Java field access expression. e.g.

  ```javascript
  if (await helper.initialized) ...
  ```

  but you can't put `await` in front of a Java field assignment. To wait for the assignment you need to wait on its value e.g.

  ```javascript
  var scanner = await helper.scanner;
  scanner.enabled = false;
  await scanner.enabled;
  ```  
  
6. Expression Chains

Java can be called in an expression chain, which will return a Promise that resolves when the a value is available for the chain.

However, while JSJBridge knows whether the first property in a call chain on a Java Object is a field or a method, it cannot know the same for later properties in the chain before that result is available (unless JSJBridge pre-cached information on methods and fields in all Java classes that each program has included, which is not currently done).

So instead of writing 

```javascript
var l = await helper.javaVector.get(0).name.length;
```
  
one must use a function for all calls in the chain past the first `javaVector` one, even for JavaScript properties like the last string length one:

```javascript
var l = await helper.javaVector.get(0).name().length();
```
  
Such functions don't have to be used if the chain is split:

```javascript
var v0 = await helper.javaVector.get(0);
var name = await v0.name;
var l = name.length;
```
  
or, if the Promises are directly handled using `then`:

```javascript
var l = await helper.javaVector.get(0).then(v0 => v0.name).then(name => name.length);
```
---

5. JavaScript iterable objects except [typed arrays](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Typed_arrays) become Java `Object` arrays. Typed arrays become Java primitive arrays of the natural `byte`, `short`, `int`, `long`, `float`, or `double` types. Unsigned typed arrays become the size-equivalent Java signed type, so Java 8+ unsigned methods must be used to get their correct values.

