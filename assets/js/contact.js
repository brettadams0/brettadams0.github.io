/* Builds the email link in the browser so the address isn't in the page source for scrapers. */
(function () {
  var addr = ['adamsbrett00', 'gmail.com'].join('@');
  var links = document.querySelectorAll('a[data-contact="email"]');
  for (var i = 0; i < links.length; i++) {
    links[i].href = 'mailto:' + addr;
    if (links[i].hasAttribute('data-email-text')) links[i].textContent = addr;
  }
})();
