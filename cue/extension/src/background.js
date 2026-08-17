// The service worker exists to open the side panel and to get out of the way.
//
// It holds no conversation data. Everything read from the page goes straight from
// the content script to the panel, and the panel is where drafting happens —
// keeping her messages out of a worker that Chrome can restart at any moment,
// with no persistence layer to lose them from.

chrome.runtime.onInstalled.addListener(() => {
  chrome.sidePanel
    .setPanelBehavior({ openPanelOnActionClick: true })
    .catch(() => {
      // Older Chrome without setPanelBehavior: the action click below covers it.
    });
});

chrome.action.onClicked.addListener(async (tab) => {
  if (!tab.id) return;
  await chrome.sidePanel.open({ tabId: tab.id });
});
