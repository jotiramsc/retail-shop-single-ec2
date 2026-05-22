let loadedPixelId = '';

export function loadMetaPixel(pixelId) {
  const normalized = String(pixelId || '').trim();
  if (!normalized || loadedPixelId === normalized || typeof window === 'undefined') {
    return;
  }

  window.fbq = window.fbq || function fbqProxy() {
    window.fbq.callMethod
      ? window.fbq.callMethod.apply(window.fbq, arguments)
      : window.fbq.queue.push(arguments);
  };
  if (!window._fbq) {
    window._fbq = window.fbq;
  }
  window.fbq.push = window.fbq;
  window.fbq.loaded = true;
  window.fbq.version = '2.0';
  window.fbq.queue = window.fbq.queue || [];

  if (!document.getElementById('meta-pixel-script')) {
    const script = document.createElement('script');
    script.id = 'meta-pixel-script';
    script.async = true;
    script.src = 'https://connect.facebook.net/en_US/fbevents.js';
    document.head.appendChild(script);
  }

  window.fbq('init', normalized);
  loadedPixelId = normalized;
}

export function trackMetaEvent(pixelId, eventName, payload = {}) {
  const normalized = String(pixelId || '').trim();
  if (!normalized || typeof window === 'undefined') {
    return;
  }
  loadMetaPixel(normalized);
  if (typeof window.fbq === 'function') {
    window.fbq('track', eventName, payload);
  }
}
