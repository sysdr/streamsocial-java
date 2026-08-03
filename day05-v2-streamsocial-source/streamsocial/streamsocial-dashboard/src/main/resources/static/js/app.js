// StreamSocial dashboard - vanilla JS, no framework (Appendix B).
// One connectStream() call per panel; later lessons add their own
// call here without touching this file's existing calls.

function connectStream(streamName, eventName, { countElId, feedElId, render }) {
  const countEl = document.getElementById(countElId);
  const feedEl = document.getElementById(feedElId);
  let count = 0;

  const source = new EventSource(`/api/streams/${streamName}`);

  source.addEventListener(eventName, (evt) => {
    const item = JSON.parse(evt.data);
    count += 1;
    if (countEl) countEl.textContent = count;

    if (feedEl) {
      const li = document.createElement('li');
      li.className = 'feed__item';
      li.innerHTML = render(item);
      feedEl.appendChild(li);

      // Cap the DOM list so a long-running dashboard tab doesn't grow forever.
      while (feedEl.children.length > 200) {
        feedEl.removeChild(feedEl.firstChild);
      }
    }
  });

  source.onerror = () => {
    // EventSource retries automatically; nothing to do here except
    // avoid crashing the page if the backing service is briefly down.
  };

  return source;
}

connectStream('user-actions', 'user-action', {
  countElId: 'user-actions-count',
  feedElId: 'user-actions-feed',
  render: (item) => `
    <span class="feed__type">${item.eventType}</span>
    <span class="feed__key">${item.primaryKey}</span>
    <span class="feed__meta">p${item.partition}@${item.offset}</span>
  `,
});

connectStream('content-interactions', 'content-interaction', {
  countElId: 'content-interactions-count',
  feedElId: 'content-interactions-feed',
  render: (item) => `
    <span class="feed__type">${item.eventType}</span>
    <span class="feed__key">${item.primaryKey} → ${item.detail}</span>
    <span class="feed__meta">p${item.partition}@${item.offset}</span>
  `,
});
