/* JSJBridge Licensing Pop-ups Script
   Copyright 2019 Mark Reginald James
   Licensed under Version 1 of the DevWheels Licence. See file LICENCE.txt and devwheels.com.
*/

document.body.addEventListener('click', function (event) {
  if (event.target.className == 'optionsPageLink') {
    event.preventDefault();
    browser.runtime.openOptionsPage();
  }
});
