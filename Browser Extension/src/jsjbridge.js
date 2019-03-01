/* JSJBridge Webpage Script
   Copyright 2019 Mark Reginald James
   Licensed under Version 1 of the DevWheels Licence. See file LICENCE.txt and devwheels.com.
*/

(()=>{ // Encapsulate

  var appletsById = {},
      javaObjectsByProgramNameAndUID = {},
      fieldAndMethodNamesByJavaClassId = {},
      jsObjectsByUID = [],
      jsObjectsByObject = new Map(),
      nextRequestNumber = 1,
      REPLY_EVENT_PREFIX = 'replyToJSJBridgeRequest';

  function log(source, msg, throwit) {
    var message = source + ': ' + msg;
    if (throwit) {
      console.trace();
      throw message;
    } else {
      console.log(message);
    }
  }
  
  function javaClassId(programName, className) { return programName + '.' + className; }

  function JavaObject(programName, uid, className, appletEl) {
    this._programName = programName;
    this._uid = uid;
    this._className = className;
    this._appletEl = appletEl; // Only set for Applets
  }
  
  JavaObject.prototype = {
    [Symbol.toPrimitive](hint) { return hint === 'number' ? this._uid : this._id(); },
    _classId() { return javaClassId(this._programName, this._className); },
    _id() { return this._classId() + ':' + this._uid; },
    _isApplet() { return !!this._appletEl; },
    _isRunning() { return this._uid != null; }, // Can only be false for Applets
    _hasField(fieldName)   { return this._isRunning() && !!fieldAndMethodNamesByJavaClassId[this._classId()][0][fieldName];  },
    _hasMethod(methodName) { return this._isRunning() && !!fieldAndMethodNamesByJavaClassId[this._classId()][1][methodName]; },

    _access(fieldOrMethodName, functionArgs) {
      var javaObject = this;
      return this._hasField(fieldOrMethodName) ?
               this.javaAccessProxy('get', fieldOrMethodName) :
             this._hasMethod(fieldOrMethodName) ?
               (functionArgs ?
                 this.javaAccessProxy('invoke', fieldOrMethodName, functionArgs) :
                 function(...args) { return javaObject.javaAccessProxy('invoke', fieldOrMethodName, args); }) :
             undefined;
    },
    _accessError(name) { log(this._programName, `No such public field or method "${name}" for Java class "${this._className}"`, true); },

    _messageContentScript(message) { messageContentScript(this._programName, message); },
    
    _messageJava(type, name, value) {
      return this._isRunning() ? this._messageJavaAppletIsRunning(nextRequestNumber++, type, name, value) :
        (new Promise(appletInitialized => this._appletEl.addEventListener('initialized', appletInitialized))).
        then(() => this._messageJavaAppletIsRunning(nextRequestNumber++, type, name, value));
    },
        
    _messageJavaAppletIsRunning(requestNumber, type, name, value) {
      var requestEventName = REPLY_EVENT_PREFIX + requestNumber, javaObject = this;
      this._messageContentScript({requestNumber: requestNumber, type: type, javaUID: this._uid, name: name, value: castToJava(value)});
      return new Promise(receivedReply => window.addEventListener(requestEventName,
        function handleRequestReply(event) {
          window.removeEventListener(requestEventName, handleRequestReply);
          var message = event.detail;
          if (message.JavaException) {
            log(javaObject._programName, 'exception: ' + message.JavaException, true);
          } else {
            var v = castFromJava(javaObject._programName, message.value);
            receivedReply(v);
          }
        }
      ));
    },
    
    javaAccessProxy(type, name, value) { return new Proxy(this._messageJava(type, name, value), JavaAccessProxyHandlers); }
  };
  
  var JavaObjectProxyHandlers = {
    get(javaObject, fieldOrMethodName, receiver) {
      return !(typeof fieldOrMethodName == 'string') ||
             fieldOrMethodName == 'then' || // Not a Promise
             javaObject.hasOwnProperty(fieldOrMethodName) ||
             JavaObject.prototype[fieldOrMethodName] ?
               javaObject[fieldOrMethodName] :
               javaObject._access(fieldOrMethodName) ||
               (javaObject._isApplet() ? javaObject._appletEl[fieldOrMethodName] :             
                 javaObject._accessError(fieldOrMethodName));
     },

    set(javaObject, fieldName, value) {
      if (javaObject.hasOwnProperty(fieldName)) {
        javaObject[fieldName] = value;
        return true;
      } else if (javaObject._hasField(fieldName)) {
        return javaObject._messageJava('set', fieldName, value).then(()=>true).catch(()=>false);
      } else if (javaObject._isApplet()) {
        javaObject._appletEl[fieldName] = value;
        return true;
      } else {
        return false;
      }
    }
  }
  
  JavaObject.create = (programName, uid, className, appletEl) => 
    new Proxy(new JavaObject(programName, uid, className, appletEl), JavaObjectProxyHandlers);

  JavaObject.get = (programName, uid, className, appletEl) => {
    var javaObjectsByUID = javaObjectsByProgramNameAndUID[programName];
    var javaObject = uid != null && javaObjectsByUID && javaObjectsByUID[uid];
    if (!javaObject) {
      if (appletEl && uid != null) {
        javaObject = appletsById[appletEl.id];
        javaObject._uid = uid;
      } else {
        javaObject = JavaObject.create(programName, uid, className, appletEl);
      } 
      if (!javaObjectsByUID) javaObjectsByUID = javaObjectsByProgramNameAndUID[programName] = {};
      if (uid != null) javaObjectsByUID[uid] = javaObject;
    }
    return javaObject;
  };
  
  // Allow Java field-access and function-calls to be chained (as functions)
  
  var JavaAccessProxyHandlers = {
    get(javaMessenger, fieldOrMethodName) {
      if (fieldOrMethodName in javaMessenger) {
        var propValue = javaMessenger[fieldOrMethodName];
        return typeof propValue == 'function' ? propValue.bind(javaMessenger) : propValue;
      } else {
        return function chainAccess(...args) {
          var promiseChain = javaMessenger.then(javaReturnValue =>
            javaReturnValue instanceof JavaObject ?
              (javaReturnValue._access(fieldOrMethodName, args) || javaReturnValue._accessError(fieldOrMethodName)) :
              javaReturnValue[fieldOrMethodName]
          );
          return new Proxy(promiseChain, JavaAccessProxyHandlers);
        };
      }
    },
    
    set(javaMessenger, fieldName, value) {
     return javaMessenger.then(javaReturnValue =>
      (javaReturnValue instanceof JavaObject) && javaReturnValue._hasField(fieldName) ?
        javaReturnValue._messageJava('set', fieldName, value).then(()=>true).catch(()=>false) :
        false);
    }
  }
  
  function castToJava(value) {
    if (Array.isArray(value)) {
      if (value.length) {
        var castArray = new Array(value.length);
        for (var i=value.length-1; i>=0; i--) castArray[i] = castToJava(value[i]);
        value = castArray;
      }
    } else if (value instanceof JavaObject) {
      value = {javaUID: value._uid};
    } else if (typeof value === 'function' && value.name === 'JavaMethod') {
      value = value.javaFieldValue;
    } else if (value && (typeof value === 'object' || typeof value === 'function')) {
      if (value instanceof String) {
        value = value.valueOf();
      } else {
        var jsUID = jsObjectsByObject.get(value);
        if (!jsUID) {
          jsUID = jsObjectsByUID.length;
          jsObjectsByUID.push(value);
          jsObjectsByObject.set(value, jsUID);
        }
        value = {jsUID: jsUID};
      }
    } else if (value === undefined) {
      value = {JSundefined: true};
    } else if (typeof value === 'number') {
      if (isNaN(value)) {
        value = {NaN: true};
      } else if (value === Number.POSITIVE_INFINITY) {
        value = {"+Infinity": true};
      } else if (value === Number.NEGATIVE_INFINITY) {
        value = {"-Infinity": true};
      }
    }
    return value;
  }

  function getJSObject(programName, uid) {
    return jsObjectsByUID[uid] || log(programName, 'referenced invalid JavaScript object #' + uid);
  }
  
  function getJavaObject(programName, value, appletEl) {
    if (value.fieldNames) {
      var getLookup = array => array.length ? Object.assign(...array.map(k =>({[k]:true}))) : {};
      fieldAndMethodNamesByJavaClassId[javaClassId(programName, value.className)] = [getLookup(value.fieldNames), getLookup(value.methodNames)];
    }
    return JavaObject.get(programName, value.javaUID, value.className, appletEl);
  }
    
  function castFromJava(programName, value) {
    if (Array.isArray(value)) {
      for (var i=value.length-1; i>=0; i--) value[i] = castFromJava(programName, value[i]);
    } else if (value != null && typeof value === 'object') {
      value = value.JSundefined  ? undefined :
              value.NaN          ? NaN :
              value["+Infinity"] ? Number.POSITIVE_INFINITY :
              value["-Infinity"] ? Number.NEGATIVE_INFINITY :
              value.jsUID        ? getJSObject(programName, value.jsUID)
                                 : getJavaObject(programName, value);
    }
    return value;
  }

  function messageContentScript(programName, message) {
    message.programName = programName;
    window.postMessage({to: 'jsjbridge', message: message}, '*');
  }
  
  window.addEventListener('message', event => {
    var mdata = event.data
    if (event.source == window && typeof(mdata) === 'object' && mdata.to == 'jsjbridgePage') {
      var message = mdata.message;

      if (message.type == 'backgroundDisconnect') throw('The JSJBridge background script stopped.');
      
      var programName = message.programName, returnValue, needsCast = true, needsReply = true;
     
      function context() { return getJSObject(programName, message.jsUID);  }
      
      try {
        switch (message.type) {
          case 'newApplet':
          case 'appletInitialized':
            needsReply = message.type != 'newApplet';
            var appletClass = needsReply ? message.name : message.appletClass;
            if (message.JavaException) {
              log(programName, `Java exception creating WebpageHelper "${appletClass}": ${message.JavaException}`, true);
            } else {
              Object.values(appletsById).forEach(applet => {
                if (applet._programName == programName && applet._className == appletClass) {
                  if (needsReply) {
                    applet._appletEl.dispatchEvent(new Event('initialized'));
                  } else {
                    getJavaObject(programName, message.value, applet._appletEl); // Set UID on Applet creation
                  }
                  return;
                }
              });
            }
            break;
            
          case 'log':
          case 'error':
            needsReply = message.type != 'error';
            log(programName, message.value, !needsReply);
            break;
            
          case 'nativeDisconnect':
            needsReply = false;
            Object.values(appletsById).forEach(applet => { if (applet._programName == programName) applet._uid = null; });
            log(programName, 'disconnected' + (message.value ? ': ' + message.value : ''));
            break;

          case 'getWindow':
            returnValue = window;
            break; 
            
          case 'call': // Java call JavaScript function
            var cContext = context();
            var jsMeth = cContext[message.name];
            if (typeof jsMeth === 'function') {
              returnValue = jsMeth.apply(cContext, castFromJava(programName, message.value));
            } else {
              log(programName, `called unknown JavaScript function "${message.name}"`);
            }
            break;
            
          case 'eval':
            with(context()) { returnValue = eval(message.value); }
            break;
            
          case 'getMember':
          case 'getSlot':
            returnValue = context()[message.name];
            break;

          case 'setMember':
          case 'setSlot':
            context()[message.name] = castFromJava(programName, message.value);
            break;
          
          case 'removeMember':
            delete context()[message.name];
            break;
            
          case 'invoke':
          case 'get':
          case 'set':
          case 'start':
          case 'stop':
            needsReply = false;
            window.dispatchEvent(new CustomEvent(REPLY_EVENT_PREFIX+message.requestNumber, {detail: message}));
            break;
            
          default:
            needsReply = false;
            log(programName, `sent unknown message "${message.type}"`);
        }
      } catch (e) {
        message.exception = exception_message(e);
        needsCast = false;
        delete message.value;
      }

      if (needsReply) {
        if (returnValue && typeof returnValue === 'object' && returnValue.constructor.name === 'Promise') {
          // Ensure that Java threads that end up executing a JavaScript function don't get a reply
          // message, and thereby continue, until all JavaScript to Java work done by that function
          // has completed. Java's JavaScript (and other) work will thereby be in the correct
          // sequence, as though each JavaScript request was synchronous.
          //
          returnValue
            .then(promiseValue => message.value = castToJava(promiseValue))
            .catch(e => {message.exception = exception_message(e); delete message.value; })
            .finally(() => messageContentScript(programName, message));
        } else {
          if (needsCast) message.value = castToJava(returnValue);
          messageContentScript(programName, message);
        }
      }
    }
  });
  
  function exception_message(e) {
    return (e.fileName && e.lineNumber ? e.fileName+':'+ e.lineNumber+' ' : '') + e.toString();
  }
  
  function invokeoOnApplets(method) {
     Object.values(appletsById).forEach(applet => applet._isRunning() && applet._messageJava(method));
  }  

  window.addEventListener('blur',  () => invokeoOnApplets('stop'));
  window.addEventListener('focus', () => invokeoOnApplets('start'));
 
  function getAppletProxy(appletEl) {
    var applet = appletsById[appletEl.id];
    if (!applet) {
      var parameters = {};
      appletEl.querySelectorAll('param').forEach(param => parameters[param.name] = param.value);
      var programName = parameters.archive && parameters.archive.replace(/\.jar$/, '');
      if (programName) {
        var appletClass = parameters.code && parameters.code.replace(/\.class$/, '');
        if (appletClass) {
          applet = appletsById[appletEl.id] = JavaObject.get(programName, null, appletClass, appletEl);
          applet._messageContentScript({type: 'newApplet', appletClass: appletClass, parameters: parameters, url: location.href});
        } else {
          log(appletEl.id, '"code" parameter (Applet class) is not set');
        }
      } else {
        log(appletEl.id, '"archive" parameter (program name) is not set');
      }
    }
    return applet;
  }
  
  // Change getElementById to return a proxied JavaObject for Applet elements
  //
  var origGetElementById  = document.getElementById;
  document.getElementById = function(id) {
    var el = origGetElementById.call(document, id);
    return el && (el.tagName == 'APPLET' || el.tagName == 'OBJECT' && el.type == 'application/x-java-applet')
           ? getAppletProxy(el) : el;
  };
  
  document.addEventListener('DOMContentLoaded', () =>
    document.querySelectorAll('applet, object[type="application/x-java-applet"]').forEach( appletEl => {
      if (appletEl.id) {
        getAppletProxy(appletEl);
      } else {
        throw("The id attribute isn't set on Applet element " + appletEl);
      }
    })
  );
})();
