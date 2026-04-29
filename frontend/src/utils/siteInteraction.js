import { retailService } from '../services/retailService';

const VISITOR_ID_KEY = 'retailshop.site.visitorId';
const VISIT_COUNT_KEY = 'retailshop.site.visitCount';
const GEO_ENRICHED_DATE_KEY = 'retailshop.site.geoEnrichedDate';
const GEO_ATTEMPTED_DATE_KEY = 'retailshop.site.geoAttemptedDate';
let geoEnrichmentPromise = null;

function todayKey() {
  return new Date().toISOString().slice(0, 10);
}

function createVisitorId() {
  if (window.crypto?.randomUUID) {
    return window.crypto.randomUUID();
  }
  return `visitor-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function getOrCreateVisitorId() {
  if (typeof window === 'undefined') {
    return null;
  }
  const existing = window.localStorage.getItem(VISITOR_ID_KEY);
  if (existing) {
    return existing;
  }
  const visitorId = createVisitorId();
  window.localStorage.setItem(VISITOR_ID_KEY, visitorId);
  return visitorId;
}

export function getStoredVisitCount() {
  if (typeof window === 'undefined') {
    return null;
  }
  const rawValue = window.localStorage.getItem(VISIT_COUNT_KEY);
  const parsed = Number(rawValue);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
}

export function storeVisitCount(totalVisits) {
  if (typeof window === 'undefined' || !Number.isFinite(Number(totalVisits))) {
    return null;
  }
  const normalized = Math.max(Number(totalVisits), 0);
  window.localStorage.setItem(VISIT_COUNT_KEY, String(normalized));
  return normalized;
}

function getBrowserTimezone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || '';
  } catch {
    return '';
  }
}

function rememberGeoEnrichmentForToday() {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.setItem(GEO_ENRICHED_DATE_KEY, todayKey());
  window.localStorage.setItem(GEO_ATTEMPTED_DATE_KEY, todayKey());
}

function alreadyEnrichedToday() {
  if (typeof window === 'undefined') {
    return true;
  }
  return window.localStorage.getItem(GEO_ENRICHED_DATE_KEY) === todayKey();
}

function alreadyAttemptedGeoToday() {
  if (typeof window === 'undefined') {
    return true;
  }
  return window.localStorage.getItem(GEO_ATTEMPTED_DATE_KEY) === todayKey();
}

function getCurrentPosition() {
  if (typeof window === 'undefined' || !navigator.geolocation) {
    return Promise.resolve(null);
  }

  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (position) => resolve(position),
      () => resolve(null),
      {
        enableHighAccuracy: false,
        timeout: 3500,
        maximumAge: 15 * 60 * 1000
      }
    );
  });
}

async function enrichVisitWithBrowserLocation(basePayload) {
  if (alreadyEnrichedToday() || alreadyAttemptedGeoToday()) {
    return null;
  }
  if (!geoEnrichmentPromise) {
    geoEnrichmentPromise = (async () => {
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(GEO_ATTEMPTED_DATE_KEY, todayKey());
      }
      const position = await getCurrentPosition();
      if (!position?.coords) {
        return null;
      }
      const response = await retailService.recordSiteVisit({
        ...basePayload,
        timezone: getBrowserTimezone(),
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        accuracyMeters: position.coords.accuracy
      });
      rememberGeoEnrichmentForToday();
      if (typeof response?.totalVisits === 'number') {
        storeVisitCount(response.totalVisits);
      }
      return response;
    })()
      .catch(() => null)
      .finally(() => {
        geoEnrichmentPromise = null;
      });
  }
  return geoEnrichmentPromise;
}

export async function trackCurrentSiteVisit() {
  if (typeof window === 'undefined') {
    return null;
  }

  const params = new URLSearchParams(window.location.search);
  const payload = {
    visitorId: getOrCreateVisitorId(),
    path: window.location.pathname,
    referrer: document.referrer || '',
    utmSource: params.get('utm_source') || '',
    utmMedium: params.get('utm_medium') || '',
    utmCampaign: params.get('utm_campaign') || '',
    timezone: getBrowserTimezone()
  };
  const response = await retailService.recordSiteVisit(payload);

  if (typeof response?.totalVisits === 'number') {
    storeVisitCount(response.totalVisits);
  }
  void enrichVisitWithBrowserLocation(payload);
  return response;
}
