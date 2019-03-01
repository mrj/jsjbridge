/* JSJBridge Content Script
   Copyright 2019 Mark Reginald James
   Licensed under Version 1 of the DevWheels Licence. See file LICENCE.txt and devwheels.com.

   Relay messages between the webpage and the background script,
   which is the only script allowed to native-message Java.
*/

chrome.storage.local.get('allowedURLPrefixes', storedValues => {
  if (Array.isArray(storedValues.allowedURLPrefixes)) {
    var url = document.location.href;
    if (storedValues.allowedURLPrefixes.find(prefix => url.startsWith(prefix))) {
      var portToBackground;
      window.addEventListener('message', event => {
        function messageWebpage(message) { window.postMessage({to: 'jsjbridgePage', message: message}, '*'); }
        var msgFromWebpage = event.data;
        if (event.source == window && typeof(msgFromWebpage) === 'object' && msgFromWebpage.to === 'jsjbridge') {
           if (!portToBackground) {
             portToBackground = chrome.runtime.connect();
             portToBackground.onDisconnect.addListener(() => messageWebpage({type: 'backgroundDisconnect'}));
              portToBackground.onMessage.addListener(message => messageWebpage(message));
           }        
           portToBackground.postMessage(msgFromWebpage.message);
        }
      });

      // Inject the main code into the page so it can see all JavaScript and (non-proxied) DOM objects.
      //
      var s = document.createElement('script');
      s.src = chrome.runtime.getURL('jsjbridge.js');
      s.async = false;
      document.documentElement.appendChild(s);
    }
  }
});

