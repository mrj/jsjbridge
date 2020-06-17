/* JSJBridge Options Page Script
   Copyright 2020 Mark Reginald James
   Licensed under Version 1 of the DevWheels Licence. See file LICENCE.txt and devwheels.com.
*/

const versionPrices = [[1.0, 20]]; // Only add a version when the price goes up

const VERSION_LAST_CHARGABLE = '1.0'; // versionPrices[versionPrices.length-1][0];

function paymentURL(priceExcludingTax, taxPercentage, test) {
  var product = 'JSJBridge ' + (licensedVersionFloat ? `${licensedVersion} to ${currentVersion} upgrade` : currentVersion);
  return  'https://www.' +
          (test ? 'sandbox.' : '') +
          'paypal.com/cgi-bin/webscr?cmd=_cart&add=1&no_shipping=1&rm=0&currency_code=AUD&business=' +
          (test ? 'Q8FD3RXC4BJXC' : 'ELVGDL93D3LFJ') +
          '&return=https%3a%2f%2fadvancedcontrols%2ecom%2eau%2fjsjbridge%2fbought.html%3f' + currentVersion +
          '&item_name=' + product +
          '&item_number=' + product +
          '&amount=' + priceExcludingTax +
          (taxPercentage ? `&tax_rate=${taxPercentage}` : '');
}

var currentVersion = browser.runtime.getManifest().version, licensedVersion, licensedVersionFloat;

document.addEventListener('DOMContentLoaded', () => {
  browser.storage.local.get(['allowedURLPrefixes', 'licensed', 'licensedVersion', 'isAustralian']).then(storedValues => {

    var urlPrefixBox = document.querySelector('textarea'),
        licensedVersionBox = document.getElementById('licensedVersion'),
        buyLink = document.getElementById('buy'),
        gstLink = document.getElementById('chargeGST'),
        saveErrorSpan = document.getElementById('saveError'),
        paymentTesting = false;

    function upgradePrice() {
      if (isNaN(licensedVersionFloat)) return -1;
      function getPrice(index) { return versionPrices[index-1][1]; }
      var currentVersionPrice = getPrice(versionPrices.length),
          licensedVersionNextIndex = versionPrices.findIndex(increment => increment[0] > licensedVersionFloat),
          licensedVersionPrice = licensedVersionNextIndex == 0 ? 0 : getPrice(licensedVersionNextIndex < 0 ? versionPrices.length : licensedVersionNextIndex);
          
      return currentVersionPrice - licensedVersionPrice;
    }
    
    function setLicensedState() {
      var price = upgradePrice();
      if (price == 0) licensedVersion = currentVersion;
      licensedVersionFloat = parseFloat(licensedVersion);
      buyLink.href = price > 0 ? paymentURL(price, chargeGST && 10, paymentTesting) : '';
      buyLink.onclick = price > 0 ? null : function() { alert("No purchase necessary."); };
    }
    
    licensedVersionBox.value = licensedVersion = storedValues.licensedVersion || 0;
    document.getElementById(storedValues.licensed ? 'licensedUsage' : 'freeUsage').checked = true;
    setLicensedState();
    document.getElementById('versionLastChargeable').innerText = VERSION_LAST_CHARGABLE;
    urlPrefixBox.value = (storedValues.allowedURLPrefixes||[]).join('\n');
    
    function setLicensedVersion() {
      licensedVersion = licensedVersionBox.value;
      licensedVersionFloat = parseFloat(licensedVersion);
      setLicensedState();
    }
    
    licensedVersionBox.addEventListener('keyup', setLicensedVersion);
    licensedVersionBox.addEventListener('mouseup', setLicensedVersion);
    
    var chargeGST = !storedValues.isAustralian;
    gstLink.addEventListener('click', () => {gstLink.className = (chargeGST=!chargeGST) ? 'inactive': ''; setLicensedState(); });
    gstLink.click();
    
    document.querySelector('button').addEventListener('click', () => {
      try {
        licensedVersion = licensedVersionBox.value || '0';
        licensedVersionFloat = parseFloat(licensedVersion);
        if (isNaN(licensedVersionFloat) || licensedVersionFloat != 0 && licensedVersionFloat < 1 || licensedVersionFloat > currentVersion)
          throw('The licensed version must be either 0 or between 1.0 and ' + currentVersion + '.');

        var licensed = document.getElementById('licensedUsage').checked;

        storedValues = {  allowedURLPrefixes: urlPrefixBox.value.split('\n').map(u => u.trim()).filter(u => u.length),
                          licensed: licensed,
                          licensedVersion: licensedVersionBox.value,
                          isAustralian: chargeGST
                       };

        browser.storage.local.set(storedValues).then(() => {
          licensedVersion = storedValues.licensedVersion;
          setLicensedState();
          browser.runtime.getBackgroundPage().then(backgroundPageWindow => {
            backgroundPageWindow.licensed = licensed;
            backgroundPageWindow.licensedVersion = licensedVersion;
          });
          saveErrorSpan.style.visibility = 'hidden';
          var tick = document.querySelector('div span');
          tick.style.visibility = 'visible';
          setTimeout(() => tick.style.visibility = 'hidden', 1000);
        }).catch(e => { throw e; });
      } catch(e) {
        saveErrorSpan.style.visibility = 'visible';
        saveErrorSpan.innerText = 'Could not save changes: ' + e.toString();
      }
    });
  });
});
