# JavaScript-Java Bridge (JSJBridge)

A web browser is a good user interface for a native application. The Java browser Plugin provided an easy way for Java programs, configured as Applets, to manipulate webpages, and for the webpages to both get and set Java fields and call Java methods via the [LiveConnect API](https://www.oracle.com/technetwork/java/javase/overview/liveconnect-docs-349790.html). This was in addition to the ability of Applets to display their graphics in a rectangle on their webpages.

However both the Java Plugin and Applets are now being deprecated as security and browser-stability risks. Continued use of them requires use of an older browser version, and soon an older Java version.

This [Java library](Java/dist/jsjbridge.jar), and extension for both [Firefox](https://addons.mozilla.org/en-US/firefox/addon/javascript-java-bridge/) and [Chrome](https://chrome.google.com/webstore/detail/javascript-java-bridge/beglnkgbajkhcmmdkldmgbkggoeegabe), allows Applets which display no graphics but only interact with JavaScript and the DOM to continue to be used on current browser and Java versions with minor changes on both the Java and JavaScript sides. Display of Applet graphics may be supported in the future.

The alternative to using this extension to turn a browser into a Java user interface would be to write the Java Program as a backend API, and have the browser frontend interact with this via Ajax/XMLHttpRequest calls, perhaps under a framework such as [React](https://reactjs.org/). This does have the advantage of weak coupling, allowing the backend and frontend to be separately developed, and simultaneously accessed from multiple browsers. But, besides the obvious advantage for UIs already written as Applets, the remote procedure call and remote DOM manipulation paradigm of JSJBridge/LiveConnect, and the automatic start-stop synchronisation of the front and back ends, can continue to make this an easy, efficient, and powerful solution.

### Licence

This repository and its associated binaries are licensed under Version 1 of the [DevWheels Licence](https://devwheels.com). Read the licence for the precise conditions, but a simple summary would be:

1. You can use this package without payment for evaluation, internal testing, and development work on your application or fork.
2. However you must pay me AUD $20 for each browser on which this is installed and otherwise run.
3. The cost of an update is the difference between the costs of the versions you are upgrading between.
4. You can release your own modified or unmodified version, and charge what you like, as long as you keep the same licence, which means complying with point 2 and forwarding AUD $20 to me for each production installation.
  
The advantage of this licence is that it both retains the enhanced debugging, customisation, risk-mitigation, and community development of Free/Open Source software (its most important features IMHO), while making it practical for developers to earn a living directly from their software (or their documentation writing, or their marketing nous).

If you have an idea for, or an implementation of, enhancements, I encourage you see whether they can be incorporated into my extension rather than publishing your own fork. We may be able to come to an agreement for a revenue share, and reduce the number of similar extensions.
  
### Migrating from Applets and the Java Plugin to WebpageHelpers and JSJBridge

See the [migration instructions](MigrationInstructions.md).

### [Release History](https://advancedcontrols.com.au/jsjbridge/releases.html)
