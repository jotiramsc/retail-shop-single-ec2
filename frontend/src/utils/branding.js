const BRANDING_STORAGE_KEY = 'retail_shop_branding';

export const defaultBranding = {
  shopName: 'Krishnai Pearl Shopee',
  headerLine: 'Ladies Cosmetics and Jewellery',
  sidebarKicker: '',
  loginKicker: 'Customer Access',
  homepageTitle: '',
  homepageSubtitle:
    'Discover pearl jewellery, bangles, earrings, festive sets, and cosmetics curated by Krishnai Pearl Shopee for everyday wear and special occasions.',
  trustPoints: [
    'Premium quality designs',
    'Secure payments',
    'Worldwide shipping',
    'Festive and daily-wear collections'
  ],
  featuredCollections: [],
  contact: {
    phoneLabel: '+91 98765 43210',
    phoneHref: 'tel:+919876543210',
    email: '',
    address: '12 Fashion Street, City Center'
  },
  media: {
    logo: '',
    heroPrimary: '',
    heroSecondary: ''
  },
  metaPixelId: ''
};

const buildPhoneHref = (phoneNumber) => {
  if (!phoneNumber) {
    return '';
  }

  const digits = String(phoneNumber).replace(/[^+\d]/g, '');
  return digits ? `tel:${digits}` : '';
};

export const normalizeBranding = (settings = {}) => {
  const trustPoints = Array.isArray(settings.trustPoints)
    ? settings.trustPoints.filter(Boolean)
    : [
        settings.trustBadgeOne,
        settings.trustBadgeTwo,
        settings.trustBadgeThree,
        settings.trustBadgeFour
      ].filter(Boolean);

  const media = settings.media || {};
  const contact = settings.contact || {};
  const phoneLabel = contact.phoneLabel || settings.phoneNumber || defaultBranding.contact.phoneLabel;
  const phoneHref = contact.phoneHref || buildPhoneHref(phoneLabel) || defaultBranding.contact.phoneHref;

  return {
    ...defaultBranding,
    shopName: settings.shopName || defaultBranding.shopName,
    headerLine: settings.headerLine || defaultBranding.headerLine,
    sidebarKicker: settings.sidebarKicker || defaultBranding.sidebarKicker,
    loginKicker: settings.loginKicker || settings.sidebarKicker || defaultBranding.loginKicker,
    homepageTitle: settings.homepageTitle || defaultBranding.homepageTitle,
    homepageSubtitle: settings.homepageSubtitle || defaultBranding.homepageSubtitle,
    trustPoints: trustPoints.length ? trustPoints : defaultBranding.trustPoints,
    featuredCollections: Array.isArray(settings.featuredCollections)
      ? settings.featuredCollections
      : defaultBranding.featuredCollections,
    contact: {
      ...defaultBranding.contact,
      ...contact,
      phoneLabel,
      phoneHref,
      address: contact.address || settings.address || defaultBranding.contact.address
    },
    media: {
      ...defaultBranding.media,
      ...media,
      logo: media.logo || settings.logoUrl || defaultBranding.media.logo,
      heroPrimary: media.heroPrimary || settings.heroPrimaryImageUrl || defaultBranding.media.heroPrimary,
      heroSecondary: media.heroSecondary || settings.heroSecondaryImageUrl || defaultBranding.media.heroSecondary
    },
    metaPixelId: settings.metaPixelId || ''
  };
};

export const getStoredBranding = () => {
  try {
    const rawValue = window.localStorage.getItem(BRANDING_STORAGE_KEY);
    if (!rawValue) {
      return defaultBranding;
    }
    return normalizeBranding(JSON.parse(rawValue));
  } catch {
    return defaultBranding;
  }
};

export const storeBranding = (branding) => {
  const normalized = normalizeBranding(branding);
  window.localStorage.setItem(BRANDING_STORAGE_KEY, JSON.stringify(normalized));
  return normalized;
};
