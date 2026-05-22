function upsertMeta(selector, attributes, content) {
  let element = document.head.querySelector(selector);
  if (!element) {
    element = document.createElement('meta');
    Object.entries(attributes).forEach(([key, value]) => {
      element.setAttribute(key, value);
    });
    document.head.appendChild(element);
  }
  element.setAttribute('content', content);
}

function upsertLink(selector, attributes) {
  let element = document.head.querySelector(selector);
  if (!element) {
    element = document.createElement('link');
    document.head.appendChild(element);
  }
  Object.entries(attributes).forEach(([key, value]) => {
    if (value) {
      element.setAttribute(key, value);
    }
  });
}

function toAbsoluteUrl(value) {
  if (!value) {
    return '';
  }
  if (/^https?:\/\//i.test(value)) {
    return value;
  }
  return new URL(value, window.location.origin).toString();
}

export function applySeo({
  title,
  description,
  path = '/',
  image = '',
  keywords = '',
  jsonLd = null,
  extraMeta = []
}) {
  const resolvedTitle = title || 'Krishnai Pearl Shopee';
  const resolvedDescription = description || 'Krishnai Pearl Shopee offers pearl jewellery, bangles, earrings, festive sets, and cosmetics online.';
  const canonicalUrl = toAbsoluteUrl(path);
  const imageUrl = toAbsoluteUrl(image);

  document.title = resolvedTitle;
  upsertMeta('meta[name="description"]', { name: 'description' }, resolvedDescription);
  upsertMeta('meta[name="robots"]', { name: 'robots' }, 'index,follow');
  if (keywords) {
    upsertMeta('meta[name="keywords"]', { name: 'keywords' }, keywords);
  }

  upsertMeta('meta[property="og:type"]', { property: 'og:type' }, 'website');
  upsertMeta('meta[property="og:title"]', { property: 'og:title' }, resolvedTitle);
  upsertMeta('meta[property="og:description"]', { property: 'og:description' }, resolvedDescription);
  upsertMeta('meta[property="og:url"]', { property: 'og:url' }, canonicalUrl);
  upsertMeta('meta[property="og:site_name"]', { property: 'og:site_name' }, 'Krishnai Pearl Shopee');
  if (imageUrl) {
    upsertMeta('meta[property="og:image"]', { property: 'og:image' }, imageUrl);
  }

  upsertMeta('meta[name="twitter:card"]', { name: 'twitter:card' }, 'summary_large_image');
  upsertMeta('meta[name="twitter:title"]', { name: 'twitter:title' }, resolvedTitle);
  upsertMeta('meta[name="twitter:description"]', { name: 'twitter:description' }, resolvedDescription);
  if (imageUrl) {
    upsertMeta('meta[name="twitter:image"]', { name: 'twitter:image' }, imageUrl);
  }

  extraMeta.forEach((meta) => {
    if (!meta?.property || meta.content == null) {
      return;
    }
    upsertMeta(`meta[property="${meta.property}"]`, { property: meta.property }, String(meta.content));
  });

  upsertLink('link[rel="canonical"]', { rel: 'canonical', href: canonicalUrl });

  if (jsonLd) {
    const scriptId = 'seo-json-ld';
    let script = document.getElementById(scriptId);
    if (!script) {
      script = document.createElement('script');
      script.id = scriptId;
      script.type = 'application/ld+json';
      document.head.appendChild(script);
    }
    script.textContent = JSON.stringify(jsonLd);
  }
}

export function preloadImage(href, fetchPriority = 'high') {
  const absoluteHref = toAbsoluteUrl(href);
  if (!absoluteHref) {
    return;
  }

  const key = `link[rel="preload"][as="image"][href="${absoluteHref}"]`;
  upsertLink(key, { rel: 'preload', as: 'image', href: absoluteHref, fetchpriority: fetchPriority });

  const origin = new URL(absoluteHref).origin;
  if (origin !== window.location.origin) {
    upsertLink(`link[rel="preconnect"][href="${origin}"]`, { rel: 'preconnect', href: origin, crossorigin: '' });
  }
}
