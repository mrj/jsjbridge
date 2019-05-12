/* JSJBridge Background Script
   Copyright 2019 Mark Reginald James
   Licensed under Version 1 of the DevWheels Licence. See file LICENCE.txt and devwheels.com.

  Relay messages between Java programs and webpages via their content scripts.
  
  Handle arbitrarily many Applets in pages and across tabs by routing messages
  from webpages via the "programName" message field, and routing messages
  from Java programs via the (content script) "portId" message field.

  Also allow native messages longer than the 1MiB limit by reconstructing fragments.
*/

const VERSION_LAST_CHARGABLE = 1.0;

var licensed, licensedVersion; // At top level so can be changed by the options page

chrome.storage.local.get(['licensed', 'licensedVersion', 'lastLicenceCheckTimeMS'], storedValues => {
  var nextContentScriptPortId = 1,
      contentScriptPorts = {},
      nativePortsByProgramName = {},
      appletClassesByProgramName = {},
      contentScriptPortsByNativePort = new Map();

  const ONE_WEEK_MS = 1000*60*60*24*7;
      
  licensed = storedValues.licensed;
  licensedVersion = storedValues.licensedVersion;

  function nativeDisconnect(portToNative) {
    var errorMessage = portToNative.error && portToNative.error.message;
    var programName = Object.entries(nativePortsByProgramName).find(([name, port]) => port == portToNative)[0];
    delete nativePortsByProgramName[programName];
    var csPortsForNative = contentScriptPortsByNativePort.get(portToNative);
    if (csPortsForNative) {
      csPortsForNative.forEach(contentScriptPort =>
        contentScriptPort.postMessage({type: 'nativeDisconnect', programName: programName, value: errorMessage})
      );
      contentScriptPortsByNativePort.delete(portToNative);
    }
    delete appletClassesByProgramName[programName];
  }
  
  chrome.runtime.onConnect.addListener(portToContentScript => {

    var contentScriptPortNumber = nextContentScriptPortId++;
    contentScriptPorts[contentScriptPortNumber] = portToContentScript;

    if (licensed && (!licensedVersion || licensedVersion < VERSION_LAST_CHARGABLE)) {
      portToContentScript.disconnect();
      chrome.windows.create({type: "panel", url: "unlicensed-popup.html", width: 650, height: 130});
      return;
    }

    portToContentScript.onMessage.addListener(messageFromContentScript => {
    
      if (!licensed) {
        var nowMS = Date.now();
        if (!storedValues.lastLicenceCheckTimeMS || nowMS - storedValues.lastLicenceCheckTimeMS >= ONE_WEEK_MS) {
          chrome.windows.create({type: 'panel', url: 'licence-check-popup.html', width: 650, height: 230});
          chrome.storage.local.set({lastLicenceCheckTimeMS: nowMS});
          storedValues.lastLicenceCheckTimeMS = nowMS;
        }
      }

      var programName = messageFromContentScript.programName, portToNative = nativePortsByProgramName[programName];

      if (messageFromContentScript.type == 'newApplet') {
        var appletClassesForProgram = appletClassesByProgramName[programName], appletClass = messageFromContentScript.appletClass;
        if (appletClassesForProgram && appletClassesForProgram.includes(appletClass)) {
          portToContentScript.postMessage({type: 'error', programName: programName, value: `A tab is already running Applet class "${appletClass}".`});
          return;
        } else {
          if (appletClassesForProgram)
            appletClassesForProgram.push(appletClass);
          else
            appletClassesByProgramName[programName] = [appletClass];
          
          if (portToNative) {
            var csPortsForNative = contentScriptPortsByNativePort.get(portToNative);
            if (!csPortsForNative.includes(portToContentScript)) csPortsForNative.push(portToContentScript);
          } else {
            portToNative = nativePortsByProgramName[programName] = chrome.runtime.connectNative(programName);
            portToNative.assembledMessage = '';
            contentScriptPortsByNativePort.set(portToNative, [portToContentScript]);
            portToNative.onDisconnect.addListener(nativeDisconnect);
            portToNative.onMessage.addListener(messageFromNative => {
              var messageFragment = messageFromNative.c || messageFromNative.e;
              if (messageFragment) {
                portToNative.assembledMessage += messageFragment;
                if (messageFromNative.e) {
                  messageFromNative = JSON.parse(portToNative.assembledMessage);
                  portToNative.assembledMessage = '';
                } else {
                  return;
                }
              }
              messageFromNative.programName = programName;
              var portId = messageFromNative.portId;
              if (portId) {
                delete messageFromNative.portId;
                contentScriptPorts[portId].postMessage(messageFromNative);
              } else { // Broadcast to all active tabs for this program
                contentScriptPortsByNativePort.get(portToNative).forEach(contentScriptPort => contentScriptPort.postMessage(messageFromNative));
              }
            });
          }
        } 
      }

      messageFromContentScript.portId = contentScriptPortNumber;
      delete messageFromContentScript.programName;
      portToNative.postMessage(messageFromContentScript);
    });
     
    portToContentScript.onDisconnect.addListener(() => {
      delete contentScriptPorts[contentScriptPortNumber];
      contentScriptPortsByNativePort.forEach((csPortsForNative, nativePort) => {
        var newPorts = csPortsForNative.filter(contentScriptPort => contentScriptPort != portToContentScript);
        if (newPorts.length) {
          contentScriptPortsByNativePort.set(nativePort, newPorts);
        } else {
          contentScriptPortsByNativePort.delete(nativePort);
          nativePort.disconnect();
          nativeDisconnect(nativePort);
        }
      });
    });
  });    
});
