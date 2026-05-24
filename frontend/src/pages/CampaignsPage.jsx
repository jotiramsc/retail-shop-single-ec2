import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import DataTable from '../components/DataTable';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import OffersPage from './OffersPage';
import { retailService } from '../services/retailService';
import { currency, formatDate } from '../utils/format';
import { getStoredAuthSession } from '../utils/auth';
import { getApiErrorMessage } from '../utils/validation';
import { confirmAction, promptDateTimeAction, showError, showSuccess, showToast } from '../utils/notifications';

const TABS = [
  { value: 'campaigns', label: 'Campaigns' },
  { value: 'create', label: 'Campaign Studio' },
  { value: 'templates', label: 'Templates' },
  { value: 'offers', label: 'Offers & Coupons' },
  { value: 'approval', label: 'Approval Queue' },
  { value: 'schedule', label: 'Schedule View' },
  { value: 'analytics', label: 'Analytics' }
];

const CAMPAIGN_TYPES = ['FESTIVAL', 'OFFER', 'NEW_ARRIVAL', 'SEASONAL', 'CUSTOM'];
const CAMPAIGN_GOALS = [
  { value: 'OFFER', label: 'Offer / coupon campaign' },
  { value: 'GREETING', label: 'Festival greeting' },
  { value: 'QUOTE', label: 'Quote / congratulation' },
  { value: 'PRODUCT_STORY', label: 'Product story' },
  { value: 'AWARENESS', label: 'Brand awareness' }
];
const OFFER_MODES = [
  { value: 'NONE', label: 'No offer' },
  { value: 'ATTACH_EXISTING', label: 'Attach existing offer' },
  { value: 'MANUAL', label: 'Campaign-only offer' },
  { value: 'CREATE', label: 'Create active offer too' }
];
const PLATFORMS = ['INSTAGRAM', 'FACEBOOK', 'WHATSAPP'];
const LANGUAGES = ['MARATHI', 'ENGLISH', 'HINGLISH'];
const TONES = ['LUXURY', 'FESTIVE', 'EMOTIONAL', 'PREMIUM', 'SIMPLE'];
const STATUSES = ['DRAFT', 'GENERATING', 'GENERATED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'SCHEDULED', 'PUBLISHED', 'FAILED'];

const blankFilters = {
  status: '',
  platform: '',
  type: '',
  fromDate: '',
  toDate: ''
};

const blankForm = {
  campaignName: '',
  campaignType: 'FESTIVAL',
  campaignGoal: 'GREETING',
  offerMode: 'NONE',
  offerId: '',
  categoryId: '',
  categoryCode: '',
  productId: '',
  offerTitle: '',
  landingUrl: 'https://kpskrishnai.com',
  couponCode: '',
  discountType: 'NONE',
  discountValue: '',
  startDate: '',
  endDate: '',
  targetPlatforms: ['INSTAGRAM', 'FACEBOOK', 'WHATSAPP'],
  language: 'MARATHI',
  tone: 'FESTIVE'
};

const SUGGESTION_KIND_LABELS = {
  UPCOMING: 'Next 20 days',
  SEASONAL: 'Seasonal',
  EVERGREEN: 'Ready anytime',
  TEMPLATE: 'All templates'
};

function toDatetimeLocal(value) {
  if (!value) {
    return '';
  }
  const date = new Date(value);
  const pad = (segment) => String(segment).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

function formatLocalDateLabel(value) {
  if (!value) {
    return '';
  }
  const date = new Date(`${value}T00:00:00`);
  return date.toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

function normalizeCampaignPayload(form) {
  const discountType = form.offerMode === 'NONE' ? 'NONE' : form.discountType;
  const discountValue = discountType === 'NONE' || form.discountValue === '' ? null : Number(form.discountValue);
  return {
    campaignName: form.campaignName.trim(),
    campaignType: form.campaignType,
    campaignGoal: form.campaignGoal,
    offerMode: form.offerMode,
    offerId: form.offerMode === 'ATTACH_EXISTING' && form.offerId ? form.offerId : null,
    categoryId: form.categoryId || null,
    productId: form.productId || null,
    offerTitle: form.offerMode === 'NONE' ? null : form.offerTitle?.trim() || null,
    landingUrl: form.landingUrl?.trim() || null,
    couponCode: form.offerMode === 'NONE' ? null : form.couponCode?.trim().toUpperCase() || null,
    discountType,
    discountValue,
    startDate: form.startDate || null,
    endDate: form.endDate || null,
    inlineOffer: form.offerMode === 'CREATE' ? buildInlineOfferPayload(form, discountType, discountValue) : null,
    targetPlatforms: form.targetPlatforms,
    language: form.language,
    tone: form.tone
  };
}

function buildInlineOfferPayload(form, discountType, discountValue) {
  if (discountType === 'NONE' || discountValue == null) {
    return null;
  }
  return {
    name: form.offerTitle?.trim() || form.campaignName.trim(),
    type: discountType === 'FLAT' ? 'FLAT' : 'PERCENT',
    value: discountValue,
    couponCode: form.couponCode?.trim().toUpperCase() || null,
    category: form.productId ? null : form.categoryCode || null,
    productId: form.productId || null,
    startDate: form.startDate || null,
    endDate: form.endDate || null,
    active: true
  };
}

function marketingDiscountFromOfferType(type) {
  if (type === 'FLAT') {
    return 'FLAT';
  }
  if (type === 'PERCENT' || type === 'CATEGORY') {
    return 'PERCENTAGE';
  }
  return 'NONE';
}

function statusTone(status) {
  if (!status) return '';
  switch (status) {
    case 'APPROVED':
    case 'PUBLISHED':
      return 'is-good';
    case 'GENERATING':
    case 'GENERATED':
    case 'PENDING_APPROVAL':
    case 'SCHEDULED':
      return 'is-warm';
    case 'FAILED':
    case 'REJECTED':
      return 'is-bad';
    default:
      return '';
  }
}

function buildOfferPreviewLabel(campaign) {
  if (!campaign) {
    return '';
  }
  if (campaign.discountType === 'PERCENTAGE' && campaign.discountValue != null && campaign.discountValue !== '') {
    return `${campaign.discountValue}% off${campaign.couponCode ? ` · ${campaign.couponCode}` : ''}`;
  }
  if (campaign.discountType === 'FLAT' && campaign.discountValue != null && campaign.discountValue !== '') {
    return `Flat ${currency(campaign.discountValue)} off${campaign.couponCode ? ` · ${campaign.couponCode}` : ''}`;
  }
  return campaign.couponCode ? `Code ${campaign.couponCode}` : campaign.offerTitle || '';
}

function escapeSvgText(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function previewLine(value, fallback = '') {
  return String(value || fallback || '').replace(/\s+/g, ' ').trim();
}

function buildMarketingPreviewDataUrl(campaign, content, draft) {
  const contactLine = campaign?.landingUrl || 'kpskrishnai.com';
  const title = previewLine(campaign?.campaignName || campaign?.offerTitle || content?.platform, 'Marketing Draft');
  const caption = previewLine(draft?.captionText || content?.captionText || campaign?.offerTitle || title, title);
  const subtitle = previewLine(draft?.callToAction || content?.callToAction || content?.platform, 'Preview');
  const offer = buildOfferPreviewLabel(campaign) || campaign?.offerTitle || 'Campaign creative';
  const platform = content?.platform || 'SOCIAL';
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="1200" height="1200" viewBox="0 0 1200 1200">
      <defs>
        <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stop-color="#1d1714"/>
          <stop offset="100%" stop-color="#7d5420"/>
        </linearGradient>
        <radialGradient id="glow" cx="72%" cy="24%" r="46%">
          <stop offset="0%" stop-color="#f7d9a4" stop-opacity="0.96"/>
          <stop offset="100%" stop-color="#f7d9a4" stop-opacity="0"/>
        </radialGradient>
      </defs>
      <rect width="1200" height="1200" fill="url(#bg)"/>
      <circle cx="890" cy="250" r="290" fill="url(#glow)"/>
      <rect x="84" y="84" width="360" height="86" rx="28" fill="#193832" fill-opacity="0.82" stroke="#f7d9a4" stroke-opacity="0.42"/>
      <text x="116" y="138" fill="#f7d9a4" font-family="Arial, Helvetica, sans-serif" font-size="28" letter-spacing="3">${escapeSvgText(platform)}</text>
      <text x="92" y="372" fill="#ffffff" font-family="Georgia, serif" font-size="96" font-weight="700">${escapeSvgText(title.slice(0, 34))}</text>
      <text x="92" y="470" fill="#f6d8a9" font-family="Arial, Helvetica, sans-serif" font-size="42">${escapeSvgText(subtitle.slice(0, 42))}</text>
      <rect x="92" y="604" width="460" height="88" rx="28" fill="#f7d9a4" fill-opacity="0.16" stroke="#f7d9a4" stroke-opacity="0.55"/>
      <text x="126" y="662" fill="#fff6e8" font-family="Arial, Helvetica, sans-serif" font-size="34" font-weight="700">${escapeSvgText(offer.slice(0, 42))}</text>
      <rect x="72" y="770" width="1056" height="262" rx="38" fill="#201713" fill-opacity="0.76" stroke="#f7d9a4" stroke-opacity="0.35"/>
      <text x="110" y="850" fill="#fff8ec" font-family="Arial, Helvetica, sans-serif" font-size="44" font-weight="800">${escapeSvgText(caption.slice(0, 46))}</text>
      <text x="110" y="914" fill="#fff8ec" font-family="Arial, Helvetica, sans-serif" font-size="36" font-weight="700">${escapeSvgText(caption.slice(46, 96))}</text>
      <text x="110" y="1114" fill="#f7d9a4" font-family="Arial, Helvetica, sans-serif" font-size="28" letter-spacing="3">${escapeSvgText(contactLine.slice(0, 48))}</text>
    </svg>
  `;
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

function buildSuggestionPreviewDataUrl(suggestion) {
  if (suggestion.previewImageUrl || suggestion.imageUrl) {
    return suggestion.previewImageUrl || suggestion.imageUrl;
  }
  return buildMarketingPreviewDataUrl(
    {
      campaignName: suggestion.campaignName || suggestion.occasionName || 'Suggested campaign',
      offerTitle: suggestion.offerTitle || suggestion.windowLabel || 'Festival-ready offer',
      discountType: suggestion.discountType || 'NONE',
      discountValue: suggestion.discountValue ?? ''
    },
    {
      platform: suggestion.targetPlatforms?.[0] || 'INSTAGRAM'
    },
    {
      captionText: suggestion.description || suggestion.occasionName || suggestion.campaignName || 'Suggested campaign',
      callToAction: suggestion.windowLabel || 'Edit and generate'
    }
  );
}

function suggestionKindHelp(kind) {
  if (kind === 'UPCOMING') {
    return 'Festival-ready ideas inside the next 20 days';
  }
  if (kind === 'SEASONAL') {
    return 'Good to run while the retail season is active';
  }
  if (kind === 'EVERGREEN') {
    return 'Business-led campaigns you can run any time';
  }
  return 'Prepared festival and important-day templates for later use';
}

export default function CampaignsPage({
  initialTab = 'campaigns',
  hidePageHeader = false,
  hideTabs = false
}) {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const requestedTab = searchParams.get('tab');
  const requestedCampaignId = searchParams.get('campaignId');
  const auth = getStoredAuthSession() || {};
  const isAdminUser = auth.role === 'ADMIN' || auth.role === 'OWNER';
  const authPermissions = Array.isArray(auth.permissions) ? auth.permissions : [];
  const canManageCampaigns = isAdminUser || ['MARKETING_AUTOMATION', 'CAMPAIGNS', 'CAMPAIGNS_CREATE', 'CAMPAIGNS_LIST', 'CAMPAIGNS_APPROVAL'].some((permission) => authPermissions.includes(permission));
  const defaultTab = TABS.some((tab) => tab.value === initialTab) ? initialTab : 'campaigns';
  const [activeTab, setActiveTab] = useState(TABS.some((tab) => tab.value === requestedTab) && !hideTabs ? requestedTab : defaultTab);
  const [filters, setFilters] = useState(blankFilters);
  const [campaignsPage, setCampaignsPage] = useState({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [scheduledPage, setScheduledPage] = useState({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [selectedCampaign, setSelectedCampaign] = useState(null);
  const [contentDrafts, setContentDrafts] = useState({});
  const [suggestions, setSuggestions] = useState([]);
  const [activeOffers, setActiveOffers] = useState([]);
  const [approvalQueue, setApprovalQueue] = useState([]);
  const [analytics, setAnalytics] = useState(null);
  const [categories, setCategories] = useState([]);
  const [products, setProducts] = useState([]);
  const [form, setForm] = useState(blankForm);
  const [analyticsFilters, setAnalyticsFilters] = useState({ campaignId: '', platform: '', fromDate: '', toDate: '' });
  const [uploadingImageByContent, setUploadingImageByContent] = useState({});
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const generationRefreshTimers = useRef({});
  const createFormNoticeRef = useRef(null);

  useEffect(() => {
    if (hideTabs) return;
    if (requestedTab && TABS.some((tab) => tab.value === requestedTab) && requestedTab !== activeTab) {
      setActiveTab(requestedTab);
    }
  }, [requestedTab, activeTab, hideTabs]);

  useEffect(() => {
    if (!hideTabs) return;
    const nextTab = TABS.some((tab) => tab.value === initialTab) ? initialTab : 'campaigns';
    if (activeTab !== nextTab) {
      setActiveTab(nextTab);
    }
  }, [activeTab, hideTabs, initialTab]);

  useEffect(() => {
    if (activeTab !== 'create' || typeof window === 'undefined') return;
    const rawTemplate = window.sessionStorage.getItem('kps_campaign_template_draft');
    if (!rawTemplate) return;
    window.sessionStorage.removeItem('kps_campaign_template_draft');
    try {
      const draft = JSON.parse(rawTemplate);
      if (draft?.campaignName) {
        setForm({ ...blankForm, ...draft });
        setSuccess('Template loaded. Review details, adjust the offer or greeting type, then save or generate.');
      }
    } catch {
      setError('Unable to load the selected template. Please choose it again.');
    }
  }, [activeTab]);

  useEffect(() => {
    if (!requestedCampaignId || activeTab !== 'campaigns') return;
    loadCampaignDetails(requestedCampaignId).catch((requestError) => {
      setError(getApiErrorMessage(requestError, 'Unable to open campaign.'));
    });
  }, [requestedCampaignId, activeTab]);

  const selectTab = (tab) => {
    setActiveTab(tab);
    if (!hideTabs) {
      setSearchParams(tab === 'campaigns' ? {} : { tab });
    }
  };

  const currentCategory = useMemo(
    () => categories.find((entry) => entry.id === form.categoryId),
    [categories, form.categoryId]
  );

  const productOptions = useMemo(() => {
    if (!form.categoryId || !currentCategory?.code) {
      return products;
    }
    return products.filter((product) => product.category === currentCategory.code);
  }, [products, form.categoryId, currentCategory]);

  const loadCampaigns = async (page = 0, overrides = filters) => {
    const response = await retailService.getMarketingCampaigns({ ...overrides, page, size: 12 });
    setCampaignsPage(response);
    return response;
  };

  const loadScheduled = async (page = 0) => {
    const response = await retailService.getMarketingCampaigns({ status: 'SCHEDULED', page, size: 12 });
    setScheduledPage(response);
  };

  const loadCampaignDetails = async (campaignId) => {
    const response = await retailService.getMarketingCampaign(campaignId);
    setSelectedCampaign(response);
    setContentDrafts(
      Object.fromEntries(
        (response.contents || []).map((content) => [
          content.id,
          {
            captionText: content.captionText || '',
            hashtags: content.hashtags || '',
            callToAction: content.callToAction || '',
            imagePrompt: content.imagePrompt || '',
            imageUrl: content.imageUrl || '',
            scheduledAt: toDatetimeLocal(content.scheduledAt)
          }
        ])
      )
    );
    return response;
  };

  const loadApprovalQueue = async () => {
    setApprovalQueue(await retailService.getMarketingApprovalQueue());
  };

  const loadSuggestions = async () => {
    const response = await retailService.getMarketingSuggestions({ daysAhead: 20 });
    setSuggestions(response || []);
    return response;
  };

  const loadAnalytics = async (overrides = analyticsFilters) => {
    const response = await retailService.getMarketingAnalytics({
      campaignId: overrides.campaignId || undefined,
      platform: overrides.platform || undefined,
      fromDate: overrides.fromDate || undefined,
      toDate: overrides.toDate || undefined
    });
    setAnalytics(response);
    return response;
  };

  const scheduleGenerationRefresh = (campaignId, attempt = 0) => {
    if (!campaignId || attempt > 18) {
      return;
    }
    if (generationRefreshTimers.current[campaignId]) {
      window.clearTimeout(generationRefreshTimers.current[campaignId]);
    }
    const delay = attempt === 0 ? 8000 : 10000;
    generationRefreshTimers.current[campaignId] = window.setTimeout(async () => {
      try {
        const details = await loadCampaignDetails(campaignId);
        await loadCampaigns(0);
        await loadApprovalQueue();
        if (details.status === 'GENERATING') {
          scheduleGenerationRefresh(campaignId, attempt + 1);
          return;
        }
        delete generationRefreshTimers.current[campaignId];
        if (details.status === 'PENDING_APPROVAL') {
          setSuccess('AI drafts are ready in the approval queue.');
        } else if (details.status === 'FAILED') {
          setError('AI draft generation failed. Check OpenAI/image configuration and try again.');
        }
      } catch {
        scheduleGenerationRefresh(campaignId, attempt + 1);
      }
    }, delay);
  };

  useEffect(() => () => {
    Object.values(generationRefreshTimers.current).forEach((timerId) => window.clearTimeout(timerId));
  }, []);

  useEffect(() => {
    if (activeTab !== 'create') {
      return;
    }
    retailService.getOffers({ page: 0, size: 100 })
      .then((offersPage) => setActiveOffers(offersPage.items || []))
      .catch(() => {});
  }, [activeTab]);

  useEffect(() => {
    Promise.all([
      loadCampaigns(),
      loadScheduled(),
      loadSuggestions(),
      loadApprovalQueue(),
      loadAnalytics(),
      retailService.getProductCategoryOptions(),
      retailService.getProducts({ page: 0, size: 100 }),
      retailService.getOffers({ page: 0, size: 100 }).catch(() => ({ items: [] }))
    ])
      .then(([, , , , , categoryOptions, productPage, offersPage]) => {
        setCategories(categoryOptions || []);
        setProducts(productPage.items || []);
        setActiveOffers(offersPage.items || []);
      })
      .catch((requestError) => {
        setError(getApiErrorMessage(requestError, 'Unable to load marketing automation data.'));
      });
  }, []);

  const resetNotices = () => {
    setError('');
    setSuccess('');
  };

  const showCreateError = (message) => {
    setSuccess('');
    setError(message);
    window.requestAnimationFrame(() => {
      createFormNoticeRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
  };

  const handleFilterChange = async (key, value) => {
    const nextFilters = { ...filters, [key]: value };
    setFilters(nextFilters);
    resetNotices();
    try {
      await loadCampaigns(0, nextFilters);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to refresh campaign list.'));
    }
  };

  const handleCreateCampaign = async (generateAfterCreate = false) => {
    if (!form.campaignName.trim()) {
      showCreateError('Enter a campaign name before saving.');
      return;
    }
    if (!form.targetPlatforms.length) {
      showCreateError('Select at least one social platform before creating a campaign.');
      return;
    }
    if (form.offerMode === 'ATTACH_EXISTING' && !form.offerId) {
      showCreateError('Choose an existing offer or switch offer source to No offer.');
      return;
    }
    if ((form.offerMode === 'MANUAL' || form.offerMode === 'CREATE') && form.discountType !== 'NONE' && form.discountValue !== '' && Number(form.discountValue) < 0) {
      showCreateError('Discount value cannot be negative.');
      return;
    }
    if ((form.offerMode === 'MANUAL' || form.offerMode === 'CREATE') && form.discountType === 'PERCENTAGE' && Number(form.discountValue) > 100) {
      showCreateError('Percentage discount cannot exceed 100.');
      return;
    }
    if (form.offerMode === 'CREATE') {
      if (form.discountType === 'NONE' || form.discountValue === '') {
        showCreateError('Choose a discount type and value before creating an active offer.');
        return;
      }
      if (!form.productId && !form.categoryCode) {
        showCreateError('Choose a category or product for the active offer.');
        return;
      }
      if (!form.startDate || !form.endDate) {
        showCreateError('Start date and end date are required when creating an active offer.');
        return;
      }
    }
    if (form.endDate && form.startDate && form.endDate < form.startDate) {
      showCreateError('End date cannot be earlier than start date.');
      return;
    }
    setSubmitting(true);
    resetNotices();
    try {
      const created = await retailService.createMarketingCampaign(normalizeCampaignPayload(form));
      let response = created;
      if (generateAfterCreate) {
        const optimisticCampaign = { ...created, status: 'GENERATING' };
        setForm(blankForm);
        setSelectedCampaign(optimisticCampaign);
        setCampaignsPage((current) => ({
          ...current,
          items: [optimisticCampaign, ...(current.items || []).filter((item) => item.id !== created.id)]
        }));
        if (hideTabs) {
          navigate(`/app/campaigns/list?campaignId=${encodeURIComponent(created.id)}`);
        } else {
          selectTab('campaigns');
        }
        setSuccess('Campaign saved. AI draft generation has started in the background.');
        response = await retailService.generateMarketingCampaignAsync(created.id);
        scheduleGenerationRefresh(created.id);
      } else {
        setSuccess('Campaign draft created.');
      }
      setForm(blankForm);
      await loadCampaigns(0);
      await loadScheduled(0);
      await loadApprovalQueue();
      retailService.getOffers({ page: 0, size: 100 }).then((offersPage) => setActiveOffers(offersPage.items || [])).catch(() => {});
      if (response?.id) {
        await loadCampaignDetails(response.id);
        if (hideTabs) {
          navigate(`/app/campaigns/list?campaignId=${encodeURIComponent(response.id)}`);
        } else {
          selectTab('campaigns');
        }
      }
    } catch (requestError) {
      showCreateError(getApiErrorMessage(requestError, 'Unable to create campaign.'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleGenerate = async (campaignId) => {
    setLoading(true);
    resetNotices();
    try {
      await retailService.generateMarketingCampaignAsync(campaignId);
      setSuccess('AI draft generation has started. This page will refresh when drafts are ready for approval.');
      await loadCampaigns(campaignsPage.page || 0);
      await loadApprovalQueue();
      await loadCampaignDetails(campaignId);
      scheduleGenerationRefresh(campaignId);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to start AI draft generation.'));
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteCampaign = async (campaignId, campaignName) => {
    const confirmed = await confirmAction({
      title: 'Delete campaign?',
      message: `Delete "${campaignName || 'this campaign'}"? This removes drafts, approvals, schedules, and logs.`,
      confirmText: 'Delete',
      tone: 'danger'
    });
    if (!confirmed) {
      return;
    }
    setLoading(true);
    resetNotices();
    try {
      await retailService.deleteMarketingCampaign(campaignId);
      setSuccess('Campaign deleted.');
      if (selectedCampaign?.id === campaignId) {
        setSelectedCampaign(null);
        setContentDrafts({});
      }
      await loadCampaigns(0);
      await loadScheduled(0);
      await loadApprovalQueue();
      await loadAnalytics();
      showSuccess('Campaign deleted.');
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Unable to delete campaign.');
      setError(message);
      showError(message);
    } finally {
      setLoading(false);
    }
  };

  const updateDraft = (contentId, key, value) => {
    setContentDrafts((current) => ({
      ...current,
      [contentId]: {
        ...current[contentId],
        [key]: value
      }
    }));
  };

  const buildContentUpdatePayload = (contentId) => {
    const draft = contentDrafts[contentId] || {};
    return {
      ...draft,
      scheduledAt: draft.scheduledAt || null
    };
  };

  const saveContentDraft = async (contentId) => {
    resetNotices();
    try {
      await retailService.updateMarketingContent(contentId, buildContentUpdatePayload(contentId));
      setSuccess('Draft updated.');
      showSuccess('Draft updated.');
      if (selectedCampaign?.id) {
        await loadCampaignDetails(selectedCampaign.id);
      }
      await loadApprovalQueue();
      await loadCampaigns(campaignsPage.page || 0);
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Unable to update content draft.');
      setError(message);
      showError(message);
    }
  };

  const handleCustomImageUpload = async (contentId, file) => {
    if (!file) {
      return;
    }
    resetNotices();
    setUploadingImageByContent((current) => ({ ...current, [contentId]: true }));
    try {
      const existingDraft = contentDrafts[contentId] || {};
      const response = await retailService.uploadImage({ file, category: 'marketing-campaigns' });
      const uploadedUrl = response?.cloudfrontUrl || '';
      if (!uploadedUrl) {
        throw new Error('Image upload did not return a usable URL.');
      }
      setContentDrafts((current) => ({
        ...current,
        [contentId]: {
          ...(current[contentId] || {}),
          imageUrl: uploadedUrl
        }
      }));
      await retailService.updateMarketingContent(contentId, {
        ...existingDraft,
        scheduledAt: existingDraft.scheduledAt || null,
        imageUrl: uploadedUrl
      });
      setSuccess('Custom campaign image uploaded and saved.');
      showToast({ title: 'Upload saved', message: 'Custom campaign image uploaded and saved.', tone: 'success' });
      if (selectedCampaign?.id) {
        await loadCampaignDetails(selectedCampaign.id);
      }
      await loadApprovalQueue();
      await loadCampaigns(campaignsPage.page || 0);
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Unable to upload custom campaign image.');
      setError(message);
      showError(message, 'Upload failed');
    } finally {
      setUploadingImageByContent((current) => ({ ...current, [contentId]: false }));
    }
  };

  const approveContent = async (contentId) => {
    resetNotices();
    try {
      await retailService.approveMarketingContent(contentId, {});
      setSuccess('Content approved.');
      showSuccess('Content approved.');
      if (selectedCampaign?.id) {
        await loadCampaignDetails(selectedCampaign.id);
      }
      await loadApprovalQueue();
      await loadCampaigns(campaignsPage.page || 0);
      await loadScheduled(0);
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Unable to approve content.');
      setError(message);
      showError(message);
    }
  };

  const scheduleContent = async (contentId) => {
    const scheduledAt = await promptDateTimeAction({
      title: 'Schedule campaign',
      message: 'Choose the date and time this campaign should publish.',
      confirmText: 'Schedule',
      min: toDatetimeLocal(new Date(Date.now() + 5 * 60 * 1000).toISOString())
    });
    if (!scheduledAt) {
      return;
    }
    resetNotices();
    try {
      try {
        await retailService.approveMarketingContent(contentId, { comment: 'Auto-approved while scheduling' });
      } catch {
        // Already approved/scheduled/published content can continue to the schedule call.
      }
      await retailService.scheduleMarketingContent(contentId, { scheduledAt });
      setSuccess('Content scheduled.');
      showSuccess('Content scheduled.');
      if (selectedCampaign?.id) {
        await loadCampaignDetails(selectedCampaign.id);
      }
      await loadCampaigns(campaignsPage.page || 0);
      await loadScheduled(0);
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Unable to schedule content.');
      setError(message);
      showError(message);
    }
  };

  const publishContentNow = async (contentId) => {
    const confirmed = await confirmAction({
      title: 'Publish now?',
      message: 'This approved content will be published immediately.',
      confirmText: 'Publish now',
      tone: 'info'
    });
    if (!confirmed) {
      return;
    }
    resetNotices();
    try {
      try {
        await retailService.approveMarketingContent(contentId, { comment: 'Auto-approved for immediate publish' });
      } catch {
        // Already approved/scheduled/published content can continue to publish.
      }
      await retailService.publishMarketingContentNow(contentId);
      setSuccess('Content published.');
      showSuccess('Content published.');
      if (selectedCampaign?.id) {
        await loadCampaignDetails(selectedCampaign.id);
      }
      await loadCampaigns(campaignsPage.page || 0);
      await loadScheduled(0);
      await loadAnalytics();
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, 'Unable to publish content.');
      setError(message);
      showError(message);
    }
  };

  const approveAndPublishCampaign = async (platform = '') => {
    const contents = (selectedCampaign?.contents || []).filter((content) => !platform || content.platform === platform);
    if (!contents.length) {
      setError(platform ? `No ${platform} draft exists for this campaign.` : 'No drafts exist for this campaign.');
      return;
    }
    const label = platform ? platform : 'all platforms';
    const confirmed = await confirmAction({
      title: 'Publish now?',
      message: `Publish ${contents.length} draft${contents.length === 1 ? '' : 's'} for ${label} immediately?`,
      confirmText: 'Publish now',
      tone: 'info'
    });
    if (!confirmed) {
      return;
    }
    setLoading(true);
    resetNotices();
    try {
      for (const content of contents) {
        if (content.status !== 'APPROVED' && content.status !== 'PUBLISHED') {
          await retailService.approveMarketingContent(content.id, {});
        }
        if (content.status !== 'PUBLISHED') {
          await retailService.publishMarketingContentNow(content.id);
        }
      }
      setSuccess(`Published ${label}.`);
      showSuccess(`Published ${label}.`);
      if (selectedCampaign?.id) {
        await loadCampaignDetails(selectedCampaign.id);
      }
      await loadCampaigns(campaignsPage.page || 0);
      await loadApprovalQueue();
      await loadScheduled(0);
      await loadAnalytics();
    } catch (requestError) {
      const message = getApiErrorMessage(requestError, `Unable to approve and publish ${label}.`);
      setError(message);
      showError(message);
    } finally {
      setLoading(false);
    }
  };

  const selectedCampaignPlatforms = useMemo(
    () => (selectedCampaign?.targetPlatforms || []).join(', '),
    [selectedCampaign]
  );

  const groupedSuggestions = useMemo(() => ({
    UPCOMING: suggestions.filter((entry) => entry.kind === 'UPCOMING'),
    SEASONAL: suggestions.filter((entry) => entry.kind === 'SEASONAL'),
    EVERGREEN: suggestions.filter((entry) => entry.kind === 'EVERGREEN'),
    TEMPLATE: suggestions.filter((entry) => entry.kind === 'TEMPLATE')
  }), [suggestions]);

  const createPreviewUrl = useMemo(() => buildMarketingPreviewDataUrl(
    {
      campaignName: form.campaignName || 'Campaign preview',
      offerTitle: form.offerMode === 'NONE' ? '' : form.offerTitle || '',
      discountType: form.offerMode === 'NONE' ? 'NONE' : form.discountType,
      discountValue: form.discountValue === '' ? null : form.discountValue
    },
    {
      platform: form.targetPlatforms[0] || 'INSTAGRAM'
    },
    {
      captionText: form.campaignName || 'Campaign preview',
      callToAction: form.offerTitle || 'Edit offer details'
    }
  ), [form]);

  const analyticsPlatformRows = analytics?.byPlatform || [];
  const analyticsSourceRows = analytics?.bySource || [];
  const analyticsCampaignRows = analytics?.byCampaign || [];

  const useSuggestion = (suggestion, goal = 'OFFER') => {
    const shouldUseOffer = goal === 'OFFER';
    const templateForm = {
      ...blankForm,
      campaignName: suggestion.campaignName || suggestion.occasionName || '',
      campaignType: suggestion.campaignType || 'FESTIVAL',
      campaignGoal: shouldUseOffer ? 'OFFER' : 'GREETING',
      offerMode: shouldUseOffer ? 'MANUAL' : 'NONE',
      offerTitle: shouldUseOffer ? suggestion.offerTitle || '' : '',
      landingUrl: suggestion.landingUrl || blankForm.landingUrl,
      discountType: shouldUseOffer ? suggestion.discountType || 'PERCENTAGE' : 'NONE',
      discountValue: shouldUseOffer && suggestion.discountType && suggestion.discountType !== 'NONE' && suggestion.discountValue != null
        ? String(suggestion.discountValue)
        : '',
      startDate: suggestion.startDate || '',
      endDate: suggestion.endDate || '',
      targetPlatforms: suggestion.targetPlatforms?.length ? suggestion.targetPlatforms : blankForm.targetPlatforms,
      language: suggestion.language || 'MARATHI',
      tone: suggestion.tone || 'FESTIVE'
    };
    setForm(templateForm);
    if (activeTab === 'templates' && typeof window !== 'undefined') {
      window.sessionStorage.setItem('kps_campaign_template_draft', JSON.stringify(templateForm));
      navigate('/app/campaigns/create');
      return;
    }
    selectTab('create');
    setSuccess(`${suggestion.occasionName || suggestion.campaignName} template loaded as a ${shouldUseOffer ? 'coupon offer' : 'festival greeting'}. Edit anything, then generate drafts from the list.`);
    setError('');
  };

  const applyExistingOffer = (offerId) => {
    const offer = activeOffers.find((entry) => entry.id === offerId);
    if (!offer) {
      setForm((current) => ({ ...current, offerId, offerMode: 'ATTACH_EXISTING' }));
      return;
    }
    const matchedCategory = categories.find((category) => category.code === offer.category);
    setForm((current) => ({
      ...current,
      campaignGoal: 'OFFER',
      campaignType: current.campaignType === 'CUSTOM' ? 'OFFER' : current.campaignType,
      offerMode: 'ATTACH_EXISTING',
      offerId,
      offerTitle: offer.name || current.offerTitle,
      couponCode: offer.couponCode || '',
      discountType: marketingDiscountFromOfferType(offer.type),
      discountValue: offer.value != null ? String(offer.value) : '',
      categoryId: matchedCategory?.id || current.categoryId,
      categoryCode: offer.category || matchedCategory?.code || current.categoryCode,
      productId: offer.productId || '',
      startDate: offer.startDate || current.startDate,
      endDate: offer.endDate || current.endDate
    }));
  };

  const handleCategoryChange = (categoryId) => {
    const category = categories.find((entry) => entry.id === categoryId);
    setForm((current) => ({
      ...current,
      categoryId,
      categoryCode: category?.code || '',
      productId: ''
    }));
  };

  const handleOfferModeChange = (offerMode) => {
    setForm((current) => ({
      ...current,
      offerMode,
      offerId: offerMode === 'ATTACH_EXISTING' ? current.offerId : '',
      campaignGoal: offerMode === 'NONE' ? (current.campaignType === 'FESTIVAL' ? 'GREETING' : 'AWARENESS') : 'OFFER',
      discountType: offerMode === 'NONE' ? 'NONE' : current.discountType,
      discountValue: offerMode === 'NONE' ? '' : current.discountValue,
      offerTitle: offerMode === 'NONE' ? '' : current.offerTitle,
      couponCode: offerMode === 'NONE' ? '' : current.couponCode
    }));
  };

  const renderContentCard = (content) => {
    const draft = contentDrafts[content.id] || {};
    const offerPreviewLabel = buildOfferPreviewLabel(selectedCampaign);
    const previewUrl = draft.imageUrl || content.imageUrl || buildMarketingPreviewDataUrl(selectedCampaign, content, draft);
    return (
      <article key={content.id} className="marketing-content-card">
        <div className="marketing-content-card-head">
          <div>
            <div className="marketing-preview-channels">
              <span className={`marketing-status-badge ${statusTone(content.status)}`}>{content.platform}</span>
              <span className={`marketing-status-badge ${statusTone(content.status)}`}>{content.status}</span>
            </div>
            <h4>{content.platform} draft</h4>
          </div>
          <small>{formatDate(content.updatedAt || content.createdAt)}</small>
        </div>
        <div className="marketing-content-card-grid">
          <div className="marketing-content-preview">
            <div className="marketing-creative-frame">
              {offerPreviewLabel ? <span className="marketing-preview-offer-chip">{offerPreviewLabel}</span> : null}
              <img src={previewUrl} alt={`${content.platform} creative`} />
            </div>
            <div className="marketing-preview-upload-actions">
              <label className={`ghost-btn compact-btn marketing-upload-btn ${uploadingImageByContent[content.id] ? 'is-disabled' : ''}`}>
                {uploadingImageByContent[content.id] ? 'Uploading image...' : 'Upload custom image'}
                <input
                  type="file"
                  accept="image/png,image/jpeg,image/webp"
                  hidden
                  disabled={Boolean(uploadingImageByContent[content.id])}
                  onChange={(event) => {
                    const file = event.target.files?.[0];
                    event.target.value = '';
                    if (file) {
                      handleCustomImageUpload(content.id, file);
                    }
                  }}
                />
              </label>
            </div>
          </div>
          <div className="marketing-content-fields">
            <label>
              Caption / message
              <textarea
                rows="5"
                value={draft.captionText ?? content.captionText ?? ''}
                onChange={(event) => updateDraft(content.id, 'captionText', event.target.value)}
              />
            </label>
            <label>
              Hashtags
              <textarea
                rows="2"
                value={draft.hashtags ?? content.hashtags ?? ''}
                onChange={(event) => updateDraft(content.id, 'hashtags', event.target.value)}
              />
            </label>
            <label>
              CTA
              <input
                value={draft.callToAction ?? content.callToAction ?? ''}
                onChange={(event) => updateDraft(content.id, 'callToAction', event.target.value)}
              />
            </label>
            <label>
              Image prompt
              <textarea
                rows="3"
                value={draft.imagePrompt ?? content.imagePrompt ?? ''}
                onChange={(event) => updateDraft(content.id, 'imagePrompt', event.target.value)}
              />
            </label>
            <label>
              Image URL / preview URL
              <input
                value={draft.imageUrl ?? content.imageUrl ?? ''}
                readOnly
                aria-readonly="true"
              />
            </label>
          </div>
        </div>
        {content.rejectionReason ? <p className="error-text">Rejected: {content.rejectionReason}</p> : null}
        {content.platform === 'WHATSAPP' && (content.deliveryReport || content.deliveryError) ? (
          <div className={`marketing-delivery-report ${content.deliveryStatus === 'PUBLISHED' ? 'is-success' : 'is-failed'}`}>
            <strong>WhatsApp delivery: {content.deliveryStatus || content.status}</strong>
            {content.deliveryReport ? <p>{content.deliveryReport}</p> : null}
            {content.deliveryError ? <small>{content.deliveryError}</small> : null}
          </div>
        ) : null}
        <div className="marketing-content-actions">
          <button type="button" className="ghost-btn compact-btn" onClick={() => saveContentDraft(content.id)}>
            Save edits
          </button>
          {canManageCampaigns ? (
            <>
              <button type="button" className="ghost-btn compact-btn" onClick={() => scheduleContent(content.id)}>
                Schedule
              </button>
              <button type="button" className="primary-btn compact-btn" onClick={() => publishContentNow(content.id)}>
                Publish now
              </button>
            </>
          ) : null}
        </div>
        {content.approvalHistory?.length ? (
          <div className="marketing-history-list">
            {content.approvalHistory.map((entry) => (
              <div key={entry.id} className="marketing-history-row">
                <span className={`marketing-status-badge ${statusTone(entry.action)}`}>{entry.action}</span>
                <strong>{entry.actionBy}</strong>
                <small>{formatDate(entry.actionAt)}</small>
                {entry.comment ? <p>{entry.comment}</p> : null}
              </div>
            ))}
          </div>
        ) : null}
      </article>
    );
  };

  return (
    <div className="page marketing-automation-page">
      {hidePageHeader ? null : (
        <PageHeader
          eyebrow="Campaign Studio"
          title="Offers, AI creatives, approvals, and publishing"
          description="Create one campaign with or without an offer, generate one shared branded creative for Instagram, Facebook, and WhatsApp, then approve and publish when it is ready."
        />
      )}

      {hideTabs ? null : (
        <div className="marketing-tab-row">
          {TABS.map((tab) => (
            <button
              key={tab.value}
              type="button"
              className={`ghost-btn compact-btn ${activeTab === tab.value ? 'marketing-tab-active' : ''}`}
              onClick={() => selectTab(tab.value)}
            >
              {tab.label}
            </button>
          ))}
        </div>
      )}

      {error ? <p className="error-text">{error}</p> : null}
      {success ? <p className="success-text">{success}</p> : null}

      {activeTab === 'campaigns' ? (
        <div className="marketing-automation-stack">
          <Panel title="Campaign list" subtitle="Filter campaigns by status, platform, type, or date and open any campaign for review.">
            <div className="marketing-filter-grid">
              <select value={filters.status} onChange={(event) => handleFilterChange('status', event.target.value)}>
                <option value="">All statuses</option>
                {STATUSES.map((status) => <option key={status} value={status}>{status}</option>)}
              </select>
              <select value={filters.platform} onChange={(event) => handleFilterChange('platform', event.target.value)}>
                <option value="">All platforms</option>
                {PLATFORMS.map((platform) => <option key={platform} value={platform}>{platform}</option>)}
              </select>
              <select value={filters.type} onChange={(event) => handleFilterChange('type', event.target.value)}>
                <option value="">All campaign types</option>
                {CAMPAIGN_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
              </select>
              <input type="date" value={filters.fromDate} onChange={(event) => handleFilterChange('fromDate', event.target.value)} />
              <input type="date" value={filters.toDate} onChange={(event) => handleFilterChange('toDate', event.target.value)} />
            </div>
            <DataTable
              columns={[
                { key: 'campaignName', label: 'Campaign' },
                { key: 'campaignType', label: 'Type' },
                {
                  key: 'campaignGoal',
                  label: 'Goal',
                  render: (row) => row.campaignGoal || 'AWARENESS'
                },
                {
                  key: 'targetPlatforms',
                  label: 'Platforms',
                  render: (row) => (row.targetPlatforms || []).join(', ')
                },
                {
                  key: 'status',
                  label: 'Status',
                  render: (row) => <span className={`marketing-status-badge ${statusTone(row.status)}`}>{row.status}</span>
                },
                {
                  key: 'counts',
                  label: 'Workflow',
                  render: (row) => `${row.pendingApprovalCount} pending · ${row.approvedCount} approved · ${row.publishedCount} published`
                },
                {
                  key: 'createdAt',
                  label: 'Created',
                  render: (row) => formatDate(row.createdAt)
                },
                {
                  key: 'actions',
                  label: 'Actions',
                  render: (row) => (
                    <div className="marketing-inline-actions">
                      <button type="button" className="ghost-btn compact-btn" onClick={() => loadCampaignDetails(row.id)}>
                        View
                      </button>
                      <button type="button" className="ghost-btn compact-btn" disabled={loading || row.status === 'GENERATING'} onClick={() => handleGenerate(row.id)}>
                        {row.status === 'GENERATING' ? 'Generating...' : 'Generate'}
                      </button>
                      {isAdminUser ? (
                        <button type="button" className="ghost-btn compact-btn is-danger" disabled={loading} onClick={() => handleDeleteCampaign(row.id, row.campaignName)}>
                          Delete
                        </button>
                      ) : null}
                    </div>
                  )
                }
              ]}
              rows={campaignsPage.items || []}
              pagination={campaignsPage}
              onPageChange={loadCampaigns}
            />
          </Panel>

          {selectedCampaign ? (
            <Panel
              title={selectedCampaign.campaignName}
              subtitle={`${selectedCampaign.campaignType} · ${selectedCampaignPlatforms || 'No platforms selected'} · ${selectedCampaign.status}`}
            >
              <div className="marketing-selected-campaign-meta">
                <div><span>Offer</span><strong>{selectedCampaign.offerTitle || '—'}</strong></div>
                <div><span>Goal</span><strong>{selectedCampaign.campaignGoal || '—'}</strong></div>
                <div><span>Coupon</span><strong>{selectedCampaign.couponCode || '—'}</strong></div>
                <div><span>Landing URL</span><strong>{selectedCampaign.landingUrl || '—'}</strong></div>
                <div><span>Language</span><strong>{selectedCampaign.language}</strong></div>
                <div><span>Tone</span><strong>{selectedCampaign.tone}</strong></div>
              </div>
              {canManageCampaigns ? (
                <div className="marketing-selected-actions">
                  <button type="button" className="primary-btn compact-btn" disabled={loading} onClick={() => approveAndPublishCampaign()}>
                    Publish all now
                  </button>
                  {PLATFORMS.map((platform) => (
                    <button key={platform} type="button" className="ghost-btn compact-btn" disabled={loading} onClick={() => approveAndPublishCampaign(platform)}>
                      {platform}
                    </button>
                  ))}
                  {isAdminUser ? <button type="button" className="ghost-btn compact-btn is-danger" disabled={loading} onClick={() => handleDeleteCampaign(selectedCampaign.id, selectedCampaign.campaignName)}>
                    Delete campaign
                  </button> : null}
                </div>
              ) : null}
              <div className="marketing-content-list">
                {selectedCampaign.status === 'GENERATING' ? (
                  <p className="page-description">AI drafts are being generated in the background. Images can take a little longer, and this panel will refresh automatically.</p>
                ) : null}
                {(selectedCampaign.contents || []).map(renderContentCard)}
              </div>
            </Panel>
          ) : null}
        </div>
      ) : null}

      {activeTab === 'templates' ? (
        <div className="marketing-automation-stack">
          <Panel
            title="Campaign template library"
            subtitle="Festival, important-day, seasonal, and evergreen templates are prepared with preview art direction and Marathi descriptions. Load any template as a greeting or as an offer campaign."
          >
            <div className="campaign-template-hero">
              <div>
                <span className="sneat-eyebrow">Ready templates</span>
                <h3>{suggestions.length || 0} campaign ideas prepared</h3>
                <p>Choose greeting when you only want a festival wish, or choose offer when the campaign needs a coupon/discount. Generated drafts can still have images regenerated from the campaign list.</p>
              </div>
              <div className="campaign-template-hero-metrics">
                <span><strong>{groupedSuggestions.UPCOMING.length}</strong> Upcoming</span>
                <span><strong>{groupedSuggestions.SEASONAL.length}</strong> Seasonal</span>
                <span><strong>{groupedSuggestions.TEMPLATE.length}</strong> Later</span>
              </div>
            </div>
            <div className="marketing-suggestion-groups">
              {Object.entries(groupedSuggestions).map(([kind, items]) => (
                items.length ? (
                  <section key={kind} className="marketing-suggestion-group">
                    <div className="marketing-suggestion-group-head">
                      <h4>{SUGGESTION_KIND_LABELS[kind] || kind}</h4>
                      <small>{suggestionKindHelp(kind)}</small>
                    </div>
                    <div className="marketing-template-course-grid">
                      {items.map((suggestion) => (
                        <article key={suggestion.key} className="marketing-suggestion-card marketing-template-course-card">
                          <div className="marketing-suggestion-preview">
                            <img src={buildSuggestionPreviewDataUrl(suggestion)} alt={`${suggestion.occasionName} preview`} />
                            <span>{SUGGESTION_KIND_LABELS[suggestion.kind] || suggestion.kind}</span>
                          </div>
                          <div className="marketing-template-course-body">
                            <div className="marketing-preview-channels">
                              {suggestion.highlightDate ? (
                                <span className="marketing-status-badge">{formatLocalDateLabel(suggestion.highlightDate)}</span>
                              ) : null}
                              <span className="marketing-status-badge is-warm">{suggestion.tone}</span>
                            </div>
                            <h4>{suggestion.occasionName}</h4>
                            <p>{suggestion.description || suggestion.rationale}</p>
                            <div className="marketing-template-copy-box">
                              <span>Ready text</span>
                              <strong>{suggestion.offerTitle || suggestion.rationale || 'Greeting campaign ready'}</strong>
                            </div>
                            {suggestion.imagePrompt ? (
                              <details className="marketing-template-prompt">
                                <summary>Image prompt ready</summary>
                                <span>{suggestion.imagePrompt}</span>
                              </details>
                            ) : null}
                            <div className="marketing-suggestion-meta">
                              <div><span>Window</span><strong>{suggestion.windowLabel || 'Ready anytime'}</strong></div>
                              <div><span>Platforms</span><strong>{(suggestion.targetPlatforms || []).join(', ')}</strong></div>
                            </div>
                          </div>
                          <div className="marketing-suggestion-actions">
                            <button type="button" className="ghost-btn compact-btn" onClick={() => useSuggestion(suggestion, 'GREETING')}>
                              Greeting
                            </button>
                            <button type="button" className="primary-btn compact-btn" onClick={() => useSuggestion(suggestion, 'OFFER')}>
                              Use with offer
                            </button>
                          </div>
                        </article>
                      ))}
                    </div>
                  </section>
                ) : null
              ))}
            </div>
          </Panel>
        </div>
      ) : null}

      {activeTab === 'create' ? (
        <div className="marketing-automation-stack">
          <Panel title="Create campaign" subtitle="Build the campaign details first, then save it as a draft or save and generate AI content.">
            <div className="campaign-create-stepper" aria-label="Campaign creation steps">
              <span className="is-active"><b>1</b> Template</span>
              <span className={form.campaignName ? 'is-active' : ''}><b>2</b> Details</span>
              <span className={form.offerMode !== 'NONE' ? 'is-active' : ''}><b>3</b> Offer</span>
              <span className={form.targetPlatforms.length ? 'is-active' : ''}><b>4</b> Generate</span>
            </div>
            <form className="marketing-create-grid" onSubmit={(event) => event.preventDefault()}>
              <label>
                Campaign name
                <input value={form.campaignName} onChange={(event) => setForm((current) => ({ ...current, campaignName: event.target.value }))} />
              </label>
              <label>
                Campaign type
                <select value={form.campaignType} onChange={(event) => setForm((current) => ({ ...current, campaignType: event.target.value }))}>
                  {CAMPAIGN_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
                </select>
              </label>
              <label>
                Campaign goal
                <select value={form.campaignGoal} onChange={(event) => setForm((current) => ({ ...current, campaignGoal: event.target.value }))}>
                  {CAMPAIGN_GOALS.map((goal) => <option key={goal.value} value={goal.value}>{goal.label}</option>)}
                </select>
              </label>
              <label>
                Offer source
                <select value={form.offerMode} onChange={(event) => handleOfferModeChange(event.target.value)}>
                  {OFFER_MODES.map((mode) => <option key={mode.value} value={mode.value}>{mode.label}</option>)}
                </select>
              </label>
              {form.offerMode === 'ATTACH_EXISTING' ? (
                <label className="marketing-wide-field">
                  Existing active offer
                  <select value={form.offerId} onChange={(event) => applyExistingOffer(event.target.value)}>
                    <option value="">Choose offer</option>
                    {activeOffers.map((offer) => (
                      <option key={offer.id} value={offer.id}>
                        {offer.name} {offer.couponCode ? `(${offer.couponCode})` : ''} · {offer.value}{offer.type === 'FLAT' ? ' flat' : '%'}
                      </option>
                    ))}
                  </select>
                </label>
              ) : null}
              <label>
                Category
                <select value={form.categoryId} onChange={(event) => handleCategoryChange(event.target.value)}>
                  <option value="">All categories</option>
                  {categories.map((category) => (
                    <option key={category.id} value={category.id}>{category.displayName}</option>
                  ))}
                </select>
              </label>
              <label>
                Product
                <select value={form.productId} onChange={(event) => setForm((current) => ({ ...current, productId: event.target.value }))}>
                  <option value="">No specific product</option>
                  {productOptions.map((product) => (
                    <option key={product.id} value={product.id}>{product.name}</option>
                  ))}
                </select>
              </label>
              <label>
                Offer title
                <input
                  value={form.offerTitle}
                  disabled={form.offerMode === 'NONE' || form.offerMode === 'ATTACH_EXISTING'}
                  placeholder={form.offerMode === 'NONE' ? 'Not needed for non-offer campaigns' : 'Example: Wedding collection offer'}
                  onChange={(event) => setForm((current) => ({ ...current, offerTitle: event.target.value }))}
                />
              </label>
              <label>
                Landing URL
                <input value={form.landingUrl} onChange={(event) => setForm((current) => ({ ...current, landingUrl: event.target.value }))} />
              </label>
              <label>
                Coupon code
                <input
                  value={form.couponCode}
                  disabled={form.offerMode === 'NONE' || form.offerMode === 'ATTACH_EXISTING'}
                  placeholder={form.offerMode === 'NONE' ? 'No coupon for this campaign' : 'Optional coupon code'}
                  onChange={(event) => setForm((current) => ({ ...current, couponCode: event.target.value.toUpperCase() }))}
                />
              </label>
              <label>
                Discount type
                <select
                  value={form.discountType}
                  disabled={form.offerMode === 'NONE' || form.offerMode === 'ATTACH_EXISTING'}
                  onChange={(event) => setForm((current) => ({ ...current, discountType: event.target.value, discountValue: event.target.value === 'NONE' ? '' : current.discountValue }))}
                >
                  <option value="NONE">NONE</option>
                  <option value="PERCENTAGE">PERCENTAGE</option>
                  <option value="FLAT">FLAT</option>
                </select>
              </label>
              <label>
                Discount value
                <input
                  type="number"
                  min="0"
                  max={form.discountType === 'PERCENTAGE' ? '100' : undefined}
                  step="0.01"
                  disabled={form.offerMode === 'NONE' || form.offerMode === 'ATTACH_EXISTING' || form.discountType === 'NONE'}
                  value={form.discountValue}
                  onChange={(event) => setForm((current) => ({ ...current, discountValue: event.target.value }))}
                />
              </label>
              <label>
                Start date
                <input type="date" value={form.startDate} onChange={(event) => setForm((current) => ({ ...current, startDate: event.target.value }))} />
              </label>
              <label>
                End date
                <input type="date" value={form.endDate} onChange={(event) => setForm((current) => ({ ...current, endDate: event.target.value }))} />
              </label>
              <label>
                Language
                <select value={form.language} onChange={(event) => setForm((current) => ({ ...current, language: event.target.value }))}>
                  {LANGUAGES.map((language) => <option key={language} value={language}>{language}</option>)}
                </select>
              </label>
              <label>
                Tone
                <select value={form.tone} onChange={(event) => setForm((current) => ({ ...current, tone: event.target.value }))}>
                  {TONES.map((tone) => <option key={tone} value={tone}>{tone}</option>)}
                </select>
              </label>
              <fieldset className="marketing-platform-selectors">
                <legend>Platforms</legend>
                <div className="marketing-platform-checkboxes">
                  {PLATFORMS.map((platform) => (
                    <label
                      key={platform}
                      className={`marketing-platform-option ${form.targetPlatforms.includes(platform) ? 'is-selected' : ''}`}
                    >
                      <input
                        type="checkbox"
                        checked={form.targetPlatforms.includes(platform)}
                        onChange={() => {
                          setForm((current) => {
                            const next = current.targetPlatforms.includes(platform)
                              ? current.targetPlatforms.filter((entry) => entry !== platform)
                              : [...current.targetPlatforms, platform];
                            return {
                              ...current,
                              targetPlatforms: next
                            };
                          });
                        }}
                      />
                      <span>{platform}</span>
                    </label>
                  ))}
                </div>
              </fieldset>
              <div className="marketing-form-preview-card">
                <div className="marketing-form-preview-media">
                  <img src={createPreviewUrl} alt="Campaign preview" />
                </div>
                <div className="marketing-form-preview-copy">
                  <span>Preview</span>
                  <strong>{form.campaignName || 'Your campaign creative will appear here'}</strong>
                  <p>
                    {form.offerTitle || 'Add an offer title, platforms, and discount to see a richer campaign mockup before generation.'}
                  </p>
                </div>
              </div>
              {error || success ? (
                <div
                  ref={createFormNoticeRef}
                  className={`marketing-form-notice ${error ? 'is-error' : 'is-success'}`}
                  role="status"
                >
                  {error || success}
                </div>
              ) : null}
              <div className="marketing-form-actions">
                <button type="button" className="ghost-btn compact-btn" disabled={submitting} onClick={() => handleCreateCampaign(false)}>
                  {submitting ? 'Saving...' : 'Save campaign'}
                </button>
                <button type="button" className="primary-btn compact-btn" disabled={submitting} onClick={() => handleCreateCampaign(true)}>
                  {submitting ? 'Starting...' : 'Save & generate AI drafts'}
                </button>
              </div>
            </form>
          </Panel>
        </div>
      ) : null}

      {activeTab === 'offers' ? (
        <div className="marketing-automation-stack">
          <div className="campaign-studio-section-intro">
            <h2>Offers & coupons</h2>
            <p>Create reusable offers here, then attach them inside Campaign Studio when a campaign needs an actual discount or coupon.</p>
          </div>
          <OffersPage embedded />
        </div>
      ) : null}

      {activeTab === 'approval' ? (
        <div className="marketing-automation-stack">
          <Panel title="Publishing queue" subtitle="Owner and admin users can review pending content, then schedule it or publish immediately.">
            <div className="marketing-content-list">
              {approvalQueue.length === 0 ? (
                <p className="page-description">Nothing is waiting for approval right now.</p>
              ) : (
                approvalQueue.map((entry) => (
                  <article key={entry.contentId} className="marketing-content-card">
                    <div className="marketing-content-card-head">
                      <div>
                        <div className="marketing-preview-channels">
                          <span className="marketing-status-badge is-warm">{entry.platform}</span>
                          <span className="marketing-status-badge is-warm">PENDING APPROVAL</span>
                        </div>
                        <h4>{entry.campaignName}</h4>
                      </div>
                      <div className="marketing-inline-actions">
                        <button type="button" className="ghost-btn compact-btn" onClick={() => loadCampaignDetails(entry.campaignId).then(() => setActiveTab('campaigns'))}>
                          Open campaign
                        </button>
                        {canManageCampaigns ? (
                          <>
                            <button type="button" className="ghost-btn compact-btn" onClick={() => loadCampaignDetails(entry.campaignId).then(() => scheduleContent(entry.contentId))}>
                              Schedule
                            </button>
                            <button type="button" className="primary-btn compact-btn" onClick={() => loadCampaignDetails(entry.campaignId).then(() => publishContentNow(entry.contentId))}>
                              Publish now
                            </button>
                          </>
                        ) : null}
                      </div>
                    </div>
                    <div className="marketing-content-card-grid">
                      <div className="marketing-content-preview">
                        <div className="marketing-creative-frame">
                          <img src={entry.imageUrl || buildMarketingPreviewDataUrl({ campaignName: entry.campaignName, offerTitle: entry.offerTitle, discountType: entry.discountType, discountValue: entry.discountValue }, entry, null)} alt={entry.campaignName} />
                        </div>
                      </div>
                      <div className="marketing-content-fields">
                        <p><strong>Caption:</strong> {entry.captionText || '—'}</p>
                        <p><strong>Hashtags:</strong> {entry.hashtags || '—'}</p>
                        <p><strong>CTA:</strong> {entry.callToAction || '—'}</p>
                        <p><strong>Image prompt:</strong> {entry.imagePrompt || '—'}</p>
                        <p><strong>Created by:</strong> {entry.createdBy || '—'}</p>
                      </div>
                    </div>
                  </article>
                ))
              )}
            </div>
          </Panel>
        </div>
      ) : null}

      {activeTab === 'schedule' ? (
        <div className="marketing-automation-stack">
          <Panel title="Scheduled publishing" subtitle="These campaign items are approved and waiting for their scheduled publish time.">
            <DataTable
              columns={[
                { key: 'campaignName', label: 'Campaign' },
                { key: 'campaignType', label: 'Type' },
                {
                  key: 'platforms',
                  label: 'Platforms',
                  render: (row) => (row.targetPlatforms || []).join(', ')
                },
                {
                  key: 'nextScheduledAt',
                  label: 'Next scheduled time',
                  render: (row) => row.nextScheduledAt ? formatDate(row.nextScheduledAt) : '—'
                },
                {
                  key: 'status',
                  label: 'Status',
                  render: (row) => <span className={`marketing-status-badge ${statusTone(row.status)}`}>{row.status}</span>
                },
                {
                  key: 'actions',
                  label: 'Actions',
                  render: (row) => (
                    <button type="button" className="ghost-btn compact-btn" onClick={() => loadCampaignDetails(row.id).then(() => setActiveTab('campaigns'))}>
                      View
                    </button>
                  )
                }
              ]}
              rows={scheduledPage.items || []}
              pagination={scheduledPage}
              onPageChange={loadScheduled}
            />
          </Panel>
        </div>
      ) : null}

      {activeTab === 'analytics' ? (
        <div className="marketing-automation-stack">
          <Panel title="Analytics dashboard" subtitle="Review campaign reach and engagement across platforms for the selected date range.">
            <div className="marketing-filter-grid">
              <select value={analyticsFilters.campaignId} onChange={(event) => setAnalyticsFilters((current) => ({ ...current, campaignId: event.target.value }))}>
                <option value="">All campaigns</option>
                {(campaignsPage.items || []).map((campaign) => (
                  <option key={campaign.id} value={campaign.id}>{campaign.campaignName}</option>
                ))}
              </select>
              <select value={analyticsFilters.platform} onChange={(event) => setAnalyticsFilters((current) => ({ ...current, platform: event.target.value }))}>
                <option value="">All platforms</option>
                {PLATFORMS.map((platform) => <option key={platform} value={platform}>{platform}</option>)}
              </select>
              <input type="date" value={analyticsFilters.fromDate} onChange={(event) => setAnalyticsFilters((current) => ({ ...current, fromDate: event.target.value }))} />
              <input type="date" value={analyticsFilters.toDate} onChange={(event) => setAnalyticsFilters((current) => ({ ...current, toDate: event.target.value }))} />
              <button type="button" className="primary-btn compact-btn" onClick={() => loadAnalytics()}>Refresh analytics</button>
            </div>

            <div className="metric-grid">
              <article className="metric-card"><span>Impressions</span><strong>{analytics?.impressions ?? 0}</strong></article>
              <article className="metric-card"><span>Likes</span><strong>{analytics?.likes ?? 0}</strong></article>
              <article className="metric-card tone-accent"><span>Clicks</span><strong>{analytics?.clicks ?? 0}</strong></article>
              <article className="metric-card"><span>Comments</span><strong>{analytics?.comments ?? 0}</strong></article>
              <article className="metric-card"><span>Shares</span><strong>{analytics?.shares ?? 0}</strong></article>
              <article className="metric-card"><span>Conversions</span><strong>{analytics?.conversions ?? 0}</strong></article>
              <article className="metric-card tone-accent"><span>Lead visits</span><strong>{analytics?.leadVisits ?? 0}</strong></article>
              <article className="metric-card tone-accent"><span>WhatsApp delivered</span><strong>{analytics?.whatsappDelivered ?? 0}</strong></article>
              <article className="metric-card"><span>WhatsApp failed</span><strong>{analytics?.whatsappFailed ?? 0}</strong></article>
            </div>

            <div className="marketing-analytics-grid">
              <div>
                <h4>Source-wise leads</h4>
                <DataTable
                  columns={[
                    { key: 'source', label: 'Source' },
                    { key: 'visits', label: 'Visits' }
                  ]}
                  rows={analyticsSourceRows}
                />
              </div>
              <div>
                <h4>By platform</h4>
                <DataTable
                  columns={[
                    { key: 'platform', label: 'Platform' },
                    { key: 'impressions', label: 'Impressions' },
                    { key: 'likes', label: 'Likes' },
                    { key: 'comments', label: 'Comments' },
                    { key: 'shares', label: 'Shares' },
                    { key: 'clicks', label: 'Clicks' },
                    { key: 'conversions', label: 'Conversions' }
                  ]}
                  rows={analyticsPlatformRows}
                />
              </div>
              <div>
                <h4>Published content rows</h4>
                <DataTable
                  columns={[
                    { key: 'campaignName', label: 'Campaign' },
                    { key: 'platform', label: 'Platform' },
                    { key: 'impressions', label: 'Impressions' },
                    { key: 'likes', label: 'Likes' },
                    { key: 'clicks', label: 'Clicks' },
                    {
                      key: 'fetchedAt',
                      label: 'Fetched',
                      render: (row) => formatDate(row.fetchedAt)
                    }
                  ]}
                  rows={analyticsCampaignRows}
                />
              </div>
            </div>
          </Panel>
        </div>
      ) : null}
    </div>
  );
}
