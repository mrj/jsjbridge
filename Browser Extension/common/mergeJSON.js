function mergeJSON(msg1, msg2) {
  var continuesSpec = !msg1._continues;
  delete msg1._continues;

  // Unescape original _continues keys
  Object.entries(msg1).forEach(([k, v]) => {
    if (k.endsWith('_continues')) {
      delete msg1.k;
      msg1[k.substr(1)] = v;
    }
  });
   
  
  if (typeof continuesSpec === 'string') {
    target = getLastObject[continuesSpec];
  
  } else 


}
