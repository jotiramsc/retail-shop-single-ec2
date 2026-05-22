import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import DataTable from '../components/DataTable';
import PageHeader from '../components/PageHeader';
import { retailService } from '../services/retailService';
import { currency, formatDate } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

const blankEngagement = {
  dateOfBirth: '',
  anniversaryDate: '',
  gender: '',
  spouseName: '',
  preferredLanguage: '',
  preferredCategories: '',
  preferredProducts: '',
  preferredBrands: '',
  preferredPriceRange: '',
  shoppingInterests: '',
  customerNotes: '',
  customerTags: '',
  birthdayReminderEnabled: true,
  anniversaryReminderEnabled: true
};

const segmentOptions = [
  '',
  'New Customer',
  'Returning Customer',
  'High Value Customer',
  'Offer Interested Customer',
  'Cart Abandoned Customer',
  'Birthday Upcoming',
  'Anniversary Upcoming',
  'Inactive Customer',
  'Frequent Viewer',
  'Frequent Buyer'
];

const detailTabs = [
  'Overview',
  'Timeline',
  'Orders',
  'Preferences',
  'Search Activity',
  'Login History',
  'Support Chat',
  'AI Insights',
  'Notes'
];

const onboardingSteps = [
  {
    key: 'dateOfBirth',
    label: 'Birthday',
    type: 'date',
    prompt: 'May I know the customer birthday?'
  },
  {
    key: 'anniversaryDate',
    label: 'Anniversary',
    type: 'date',
    prompt: 'Any anniversary date we should remember?'
  },
  {
    key: 'preferredLanguage',
    label: 'Preferred language',
    prompt: 'Which language should we use for offers and support?'
  },
  {
    key: 'preferredCategories',
    label: 'Favorite categories',
    prompt: 'Which categories do they usually like?'
  },
  {
    key: 'preferredPriceRange',
    label: 'Budget preference',
    prompt: 'What budget range feels right for recommendations?'
  },
  {
    key: 'shoppingInterests',
    label: 'Shopping interests',
    prompt: 'What are they shopping for right now?'
  }
];

function dateValue(value) {
  return value ? String(value).slice(0, 10) : '';
}

function joinValues(values) {
  return Array.isArray(values) ? values.join(', ') : (values || '');
}

function splitValues(value) {
  return String(value || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function fuzzyMatch(customer, query) {
  const normalized = query.trim().toLowerCase();
  if (!normalized) return true;
  const haystack = [
    customer.name,
    customer.mobile,
    customer.email,
    ...(customer.segments || [])
  ].join(' ').toLowerCase();
  if (haystack.includes(normalized)) return true;
  return normalized.split(/\s+/).every((part) => haystack.includes(part));
}

function activityText(activity) {
  return [
    activity.searchKeyword ? `Search: ${activity.searchKeyword}` : '',
    activity.productName ? `Product: ${activity.productName}` : '',
    activity.clickedProduct ? `Clicked: ${activity.clickedProduct}` : '',
    activity.category ? `Category: ${activity.category}` : '',
    activity.filterUsed ? `Filter: ${activity.filterUsed}` : ''
  ].filter(Boolean).join(' · ') || activity.activityType || 'Activity';
}

function typeLabel(value) {
  return String(value || 'Activity').replaceAll('_', ' ').toLowerCase().replace(/^\w/, (letter) => letter.toUpperCase());
}

function Field({ label, value }) {
  return (
    <div className="crm-field">
      <span>{label}</span>
      <strong>{value || 'Not captured'}</strong>
    </div>
  );
}

function InsightCard({ label, value, note, tone = '' }) {
  const displayValue = value === null || value === undefined || value === '' ? 'Learning' : value;
  return (
    <article className={`crm-insight-card ${tone}`}>
      <span>{label}</span>
      <strong>{displayValue}</strong>
      {note ? <p>{note}</p> : null}
    </article>
  );
}

export default function CustomersPage({
  initialTab = 'Overview',
  hidePageHeader = false,
  hideInternalTabs = false
}) {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const requestedTab = searchParams.get('tab');
  const [customersPage, setCustomersPage] = useState({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [recentCustomers, setRecentCustomers] = useState([]);
  const [history, setHistory] = useState([]);
  const [selectedCustomer, setSelectedCustomer] = useState(null);
  const [customerDetails, setCustomerDetails] = useState(null);
  const [engagementForm, setEngagementForm] = useState(blankEngagement);
  const [activeTab, setActiveTab] = useState(detailTabs.includes(initialTab) ? initialTab : 'Overview');
  const [loadingCustomers, setLoadingCustomers] = useState(false);
  const [loadingDetails, setLoadingDetails] = useState(false);
  const [savingDetails, setSavingDetails] = useState(false);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [segment, setSegment] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [onboardingStep, setOnboardingStep] = useState(0);
  const [onboardingValue, setOnboardingValue] = useState('');

  useEffect(() => {
    if (hideInternalTabs) return;
    const tabMap = {
      overview: 'Overview',
      timeline: 'Timeline',
      orders: 'Orders',
      preferences: 'Preferences',
      search: 'Search Activity',
      login: 'Login History',
      support: 'Support Chat',
      ai: 'AI Insights',
      notes: 'Notes'
    };
    const nextTab = tabMap[requestedTab || ''];
    if (nextTab && nextTab !== activeTab) {
      setActiveTab(nextTab);
    }
  }, [requestedTab, activeTab, hideInternalTabs]);

  useEffect(() => {
    if (!hideInternalTabs) return;
    const nextTab = detailTabs.includes(initialTab) ? initialTab : 'Overview';
    if (nextTab !== activeTab) {
      setActiveTab(nextTab);
    }
  }, [initialTab, activeTab, hideInternalTabs]);

  const loadCustomers = async (page = 0, nextSegment = segment) => {
    setLoadingCustomers(true);
    try {
      const response = await retailService.getCustomers({ page, size: 12, segment: nextSegment });
      setCustomersPage(response);
      setRecentCustomers((response.items || []).slice(0, 5));
      setError('');
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to load customers.'));
    } finally {
      setLoadingCustomers(false);
    }
  };

  useEffect(() => {
    loadCustomers();
  }, []);

  useEffect(() => {
    const timeout = window.setTimeout(() => setDebouncedSearch(search), 240);
    return () => window.clearTimeout(timeout);
  }, [search]);

  const syncEngagementForm = (details) => {
    setEngagementForm({
      dateOfBirth: dateValue(details.dateOfBirth),
      anniversaryDate: dateValue(details.anniversaryDate),
      gender: details.gender || '',
      spouseName: details.spouseName || '',
      preferredLanguage: details.preferredLanguage || '',
      preferredCategories: joinValues(details.preferredCategories),
      preferredProducts: joinValues(details.preferredProducts),
      preferredBrands: joinValues(details.preferredBrands),
      preferredPriceRange: details.preferredPriceRange || '',
      shoppingInterests: details.shoppingInterests || '',
      customerNotes: details.customerNotes || '',
      customerTags: joinValues(details.customerTags),
      birthdayReminderEnabled: details.birthdayReminderEnabled !== false,
      anniversaryReminderEnabled: details.anniversaryReminderEnabled !== false
    });
  };

  const loadCustomerDetails = async (customer) => {
    setSelectedCustomer(customer);
    setActiveTab('Overview');
    setError('');
    setSuccess('');
    setLoadingDetails(true);
    try {
      const details = await retailService.getCustomerDetails(customer.id);
      setCustomerDetails(details);
      syncEngagementForm(details);
      setOnboardingStep(0);
      setOnboardingValue('');
      try {
        setHistory(await retailService.getCustomerHistory(details.mobile || customer.mobile));
      } catch {
        setHistory([]);
      }
    } catch (requestError) {
      setCustomerDetails(null);
      setHistory([]);
      setError(getApiErrorMessage(requestError, 'Unable to load customer details.'));
    } finally {
      setLoadingDetails(false);
    }
  };

  const saveCustomerDetails = async (event, override = {}) => {
    event?.preventDefault?.();
    if (!selectedCustomer?.id) return null;
    setSavingDetails(true);
    setError('');
    setSuccess('');
    try {
      const nextForm = { ...engagementForm, ...override };
      const details = await retailService.updateCustomerDetails(selectedCustomer.id, nextForm);
      setCustomerDetails(details);
      syncEngagementForm(details);
      setSuccess('Customer profile updated.');
      await loadCustomers(customersPage.page);
      return details;
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to update customer profile.'));
      return null;
    } finally {
      setSavingDetails(false);
    }
  };

  const answerOnboardingStep = async () => {
    const step = onboardingSteps[onboardingStep];
    if (!step || !onboardingValue.trim()) return;
    const nextValue = onboardingValue.trim();
    setEngagementForm((current) => ({ ...current, [step.key]: nextValue }));
    const saved = await saveCustomerDetails(null, { [step.key]: nextValue });
    if (saved) {
      setOnboardingStep((current) => Math.min(current + 1, onboardingSteps.length));
      setOnboardingValue('');
    }
  };

  const startSupportChat = async () => {
    if (!selectedCustomer?.id) return;
    setError('');
    setSuccess('');
    try {
      const response = await retailService.startCustomerSupportChat(selectedCustomer.id);
      setSuccess('Greeting added and support chat opened.');
      if (response?.conversationId) {
        navigate(`/app/support?conversationId=${response.conversationId}`);
      }
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to start support chat.'));
    }
  };

  const changeSegment = (nextSegment) => {
    setSegment(nextSegment);
    loadCustomers(0, nextSegment);
  };

  const customers = customersPage.items || [];
  const filteredCustomers = customers.filter((customer) => fuzzyMatch(customer, debouncedSearch));
  const selectedId = selectedCustomer?.id;

  const stats = useMemo(() => {
    const items = customersPage.items || [];
    const highValue = items.filter((customer) => (customer.segments || []).some((item) => item.toLowerCase().includes('high value'))).length;
    const recent = items.filter((customer) => customer.createdAt).length;
    return [
      ['Total customers', customersPage.totalItems || items.length],
      ['Recent loaded', recent],
      ['High-value signals', highValue],
      ['Active segment', segment || 'All']
    ];
  }, [customersPage, segment]);

  const profileCompletion = useMemo(() => {
    if (!customerDetails) return 0;
    const fields = [
      customerDetails.name,
      customerDetails.mobile,
      customerDetails.email,
      customerDetails.gender,
      customerDetails.dateOfBirth,
      customerDetails.anniversaryDate,
      customerDetails.preferredLanguage,
      customerDetails.preferredCategories,
      customerDetails.preferredPriceRange,
      customerDetails.shoppingInterests
    ];
    return Math.round((fields.filter(Boolean).length / fields.length) * 100);
  }, [customerDetails]);

  const renderOverview = () => (
    <div className="crm-overview-grid">
      <section className="crm-profile-hero">
        <div>
          <span className="crm-kicker">Customer 360</span>
          <h3>{customerDetails.name || selectedCustomer?.name || 'Unnamed customer'}</h3>
          <p>{customerDetails.mobile || 'No mobile'} · {customerDetails.email || 'No email captured'}</p>
        </div>
        <div className="crm-score-ring">
          <strong>{customerDetails.engagementScore ?? 0}</strong>
          <span>Engagement</span>
        </div>
      </section>

      <section className="crm-field-grid">
        <Field label="Name" value={customerDetails.name} />
        <Field label="Mobile" value={customerDetails.mobile} />
        <Field label="Email" value={customerDetails.email} />
        <Field label="Gender" value={customerDetails.gender} />
        <Field label="DOB" value={customerDetails.dateOfBirth ? formatDate(customerDetails.dateOfBirth) : ''} />
        <Field label="Anniversary" value={customerDetails.anniversaryDate ? formatDate(customerDetails.anniversaryDate) : ''} />
        <Field label="Preferred language" value={customerDetails.preferredLanguage} />
        <Field label="Customer since" value={customerDetails.customerSince ? formatDate(customerDetails.customerSince) : ''} />
        <Field label="Last active" value={customerDetails.lastActiveAt ? formatDate(customerDetails.lastActiveAt) : ''} />
        <Field label="Lifetime value" value={currency(customerDetails.totalSpent || 0)} />
        <Field label="Last location" value={customerDetails.lastKnownLocation} />
        <Field label="Support status" value={customerDetails.supportChatStatus} />
      </section>

      <div className="crm-analytics-grid">
        <InsightCard label="Orders" value={customerDetails.totalOrders || 0} note={`${customerDetails.pendingOrders || 0} pending`} />
        <InsightCard label="Profile completion" value={`${profileCompletion}%`} note="Conversational onboarding coverage" />
        <InsightCard label="Churn risk" value={customerDetails.churnRisk} tone={customerDetails.churnRisk === 'High' ? 'is-risk' : 'is-good'} />
      </div>

      <div className="crm-chip-row">
        {(customerDetails.segments || []).map((item) => <span key={item}>{item}</span>)}
        {customerDetails.highValueBadge ? <span className="is-gold">{customerDetails.highValueBadge}</span> : null}
        {!(customerDetails.segments || []).length ? <span>New Customer</span> : null}
      </div>
    </div>
  );

  const renderPreferences = () => (
    <div className="crm-preferences-grid">
      <section className="crm-large-card">
        <div className="crm-section-head">
          <span className="crm-kicker">Preferences</span>
          <h3>Shopping profile</h3>
        </div>
        <div className="crm-preference-list">
          <Field label="Favorite categories" value={customerDetails.preferredCategories} />
          <Field label="Favorite product types" value={customerDetails.preferredProducts} />
          <Field label="Preferred brands" value={customerDetails.preferredBrands} />
          <Field label="Budget range" value={customerDetails.preferredPriceRange} />
          <Field label="Shopping behavior" value={customerDetails.shoppingInterests || (customerDetails.preferenceInsights || []).join(', ')} />
          <Field label="Wishlist" value={(customerDetails.activityHistory || []).some((item) => item.activityType === 'WISHLIST_ADD') ? 'Wishlist activity found' : 'No wishlist activity captured'} />
        </div>
      </section>
      <form className="crm-edit-panel" onSubmit={saveCustomerDetails}>
        <h3>Refine profile</h3>
        <div className="crm-form-grid">
          <label>Gender<input value={engagementForm.gender} onChange={(event) => setEngagementForm({ ...engagementForm, gender: event.target.value })} /></label>
          <label>DOB<input type="date" value={engagementForm.dateOfBirth} onChange={(event) => setEngagementForm({ ...engagementForm, dateOfBirth: event.target.value })} /></label>
          <label>Anniversary<input type="date" value={engagementForm.anniversaryDate} onChange={(event) => setEngagementForm({ ...engagementForm, anniversaryDate: event.target.value })} /></label>
          <label>Language<input value={engagementForm.preferredLanguage} onChange={(event) => setEngagementForm({ ...engagementForm, preferredLanguage: event.target.value })} /></label>
          <label>Categories<input value={engagementForm.preferredCategories} onChange={(event) => setEngagementForm({ ...engagementForm, preferredCategories: event.target.value })} /></label>
          <label>Products<input value={engagementForm.preferredProducts} onChange={(event) => setEngagementForm({ ...engagementForm, preferredProducts: event.target.value })} /></label>
          <label>Brands<input value={engagementForm.preferredBrands} onChange={(event) => setEngagementForm({ ...engagementForm, preferredBrands: event.target.value })} /></label>
          <label>Budget<input value={engagementForm.preferredPriceRange} onChange={(event) => setEngagementForm({ ...engagementForm, preferredPriceRange: event.target.value })} /></label>
          <label className="is-wide">Shopping interests<input value={engagementForm.shoppingInterests} onChange={(event) => setEngagementForm({ ...engagementForm, shoppingInterests: event.target.value })} /></label>
        </div>
        <button className="primary-btn compact-btn" type="submit" disabled={savingDetails}>{savingDetails ? 'Saving...' : 'Save profile intelligence'}</button>
      </form>
    </div>
  );

  const renderTimeline = () => (
    <section className="crm-large-card">
      <div className="crm-section-head">
        <span className="crm-kicker">Timeline</span>
        <h3>Chronological customer journey</h3>
      </div>
      <div className="crm-timeline">
        {(customerDetails.timeline || []).map((event) => (
          <article key={`${event.type}-${event.createdAt}-${event.title}`}>
            <span>{formatDate(event.createdAt)}</span>
            <strong>{event.title}</strong>
            <p>{event.detail}</p>
          </article>
        ))}
        {!(customerDetails.timeline || []).length ? <div className="crm-empty-state">Timeline appears as the customer searches, logs in, orders, clicks offers, or chats.</div> : null}
      </div>
    </section>
  );

  const renderSearchActivity = () => (
    <div className="crm-detail-stack">
      <div className="crm-chip-row">
        {(customerDetails.preferenceInsights || []).map((item) => <span key={item}>{item}</span>)}
        {!(customerDetails.preferenceInsights || []).length ? <span>Preference summary will appear after searches and views.</span> : null}
      </div>
      <DataTable columns={[
        { key: 'createdAt', label: 'Timestamp', render: (row) => formatDate(row.createdAt) },
        { key: 'searchKeyword', label: 'Search term', render: (row) => row.searchKeyword || '—' },
        { key: 'resultCount', label: 'Results', render: (row) => row.resultCount ?? '—' },
        { key: 'clickedProduct', label: 'Clicked product', render: (row) => row.clickedProduct || row.productName || '—' },
        { key: 'category', label: 'Category', render: (row) => row.category || '—' },
        { key: 'activityType', label: 'Event', render: (row) => typeLabel(row.activityType) }
      ]} rows={customerDetails.searchHistory?.length ? customerDetails.searchHistory : customerDetails.activityHistory || []} emptyMessage="No search history captured yet." />
    </div>
  );

  const renderLoginHistory = () => (
    <DataTable columns={[
      { key: 'loginTime', label: 'Login time', render: (row) => formatDate(row.loginTime) },
      { key: 'device', label: 'Device', render: (row) => row.device || '—' },
      { key: 'browser', label: 'Browser', render: (row) => row.browser || '—' },
      { key: 'ip', label: 'IP', render: (row) => row.ip || '—' },
      { key: 'location', label: 'Location', render: (row) => row.location || '—' },
      { key: 'sessionDuration', label: 'Session', render: () => 'Active session' },
      { key: 'status', label: 'Status' }
    ]} rows={customerDetails.loginHistory || []} emptyMessage="No login history captured yet." />
  );

  const renderOrders = () => (
    <div className="crm-detail-stack">
      <DataTable columns={[
        { key: 'orderNumber', label: 'Order' },
        { key: 'status', label: 'Status' },
        { key: 'amount', label: 'Amount', render: (row) => currency(row.amount) },
        { key: 'createdAt', label: 'Ordered on', render: (row) => formatDate(row.createdAt) }
      ]} rows={customerDetails.orderHistory || []} emptyMessage="No orders found." />
      <DataTable columns={[
        { key: 'invoiceNumber', label: 'Invoice' },
        { key: 'finalAmount', label: 'Amount', render: (row) => currency(row.finalAmount) },
        { key: 'createdAt', label: 'Purchased on', render: (row) => formatDate(row.createdAt) }
      ]} rows={history} emptyMessage="No invoices found." />
    </div>
  );

  const renderSupportChat = () => {
    const step = onboardingSteps[onboardingStep];
    return (
      <div className="crm-chat-workspace">
        <section className="crm-chat-card">
          <div className="crm-chat-bubble is-shop">
            <strong>Krishnai Pearl Shopee</strong>
            <p>👋 Welcome to Krishnai Pearl Shopee!</p>
            <p>We’re happy to assist you today.</p>
            <p>I can help with jewellery recommendations, cosmetics suggestions, orders, offers, and support.</p>
            <p>Before we begin, may I know the customer birthday, anniversary date, and preferred language?</p>
          </div>
          {onboardingSteps.slice(0, onboardingStep).map((completedStep) => (
            <div className="crm-chat-bubble is-customer" key={completedStep.key}>
              <strong>{completedStep.label}</strong>
              <p>{engagementForm[completedStep.key] || 'Captured'}</p>
            </div>
          ))}
          {step ? (
            <div className="crm-chat-capture">
              <p>{step.prompt}</p>
              <div>
                <input
                  type={step.type || 'text'}
                  value={onboardingValue}
                  onChange={(event) => setOnboardingValue(event.target.value)}
                  placeholder={step.label}
                />
                <button type="button" className="primary-btn compact-btn" onClick={answerOnboardingStep} disabled={savingDetails || !onboardingValue.trim()}>
                  Save answer
                </button>
              </div>
            </div>
          ) : (
            <div className="crm-empty-state">Onboarding complete. Continue the conversation from Support Inbox when needed.</div>
          )}
        </section>
        <aside className="crm-large-card">
          <span className="crm-kicker">Support</span>
          <h3>{customerDetails.supportChatStatus || 'No active chat'}</h3>
          <p>Opening support creates the greeting inside the WhatsApp support thread and keeps this profile context visible for staff.</p>
          <button className="primary-btn" type="button" onClick={startSupportChat}>Open support chat</button>
        </aside>
      </div>
    );
  };

  const renderAiInsights = () => (
    <div className="crm-ai-grid">
      <InsightCard label="Sentiment" value={customerDetails.customerSentiment} note="Based on cart, browsing, and purchase signals." />
      <InsightCard label="Purchase prediction" value={customerDetails.purchasePrediction} />
      <InsightCard label="Smart segment" value={(customerDetails.segments || [])[0] || 'Learning'} />
      <InsightCard label="Churn risk" value={customerDetails.churnRisk} tone={customerDetails.churnRisk === 'High' ? 'is-risk' : 'is-good'} />
      <InsightCard label="Engagement score" value={`${customerDetails.engagementScore ?? 0}/100`} />
      <section className="crm-large-card">
        <span className="crm-kicker">Recommended products</span>
        <div className="crm-chip-row">
          {(customerDetails.recommendedProducts || []).map((item) => <span key={item}>{item}</span>)}
        </div>
      </section>
    </div>
  );

  const renderNotes = () => (
    <form className="crm-notes-form" onSubmit={saveCustomerDetails}>
      <textarea value={engagementForm.customerNotes} onChange={(event) => setEngagementForm({ ...engagementForm, customerNotes: event.target.value })} placeholder="Notes about preferences, sizing, gifting, follow-ups, or support context." />
      <label>Tags<input value={engagementForm.customerTags} onChange={(event) => setEngagementForm({ ...engagementForm, customerTags: event.target.value })} placeholder="Regular customer, bridal interest, VIP" /></label>
      <button className="primary-btn compact-btn" type="submit" disabled={savingDetails}>{savingDetails ? 'Saving...' : 'Save notes'}</button>
    </form>
  );

  const renderTabContent = () => {
    if (!customerDetails) return null;
    if (activeTab === 'Overview') return renderOverview();
    if (activeTab === 'Timeline') return renderTimeline();
    if (activeTab === 'Orders') return renderOrders();
    if (activeTab === 'Preferences') return renderPreferences();
    if (activeTab === 'Search Activity') return renderSearchActivity();
    if (activeTab === 'Login History') return renderLoginHistory();
    if (activeTab === 'Support Chat') return renderSupportChat();
    if (activeTab === 'AI Insights') return renderAiInsights();
    if (activeTab === 'Notes') return renderNotes();
    return null;
  };

  return (
    <div className="page customer-intelligence-page">
      {hidePageHeader ? null : (
        <PageHeader
          eyebrow="Customer Intelligence"
          title="Customer CRM"
          description="A premium customer workspace for behavior, lifetime value, onboarding, support, and AI-assisted selling."
        />
      )}

      <section className="crm-command-bar">
        <div>
          <span className="crm-kicker">Search and filters</span>
          <h3>Find every customer signal quickly</h3>
        </div>
        <input placeholder="Search by name, mobile, email, or segment" value={search} onChange={(event) => setSearch(event.target.value)} />
        <select value={segment} onChange={(event) => changeSegment(event.target.value)}>
          {segmentOptions.map((item) => <option key={item || 'all'} value={item}>{item || 'All segments'}</option>)}
        </select>
      </section>

      <section className="crm-stat-row">
        {stats.map(([label, value]) => <InsightCard key={label} label={label} value={value} />)}
      </section>

      {error ? <p className="error-text">{error}</p> : null}
      {success ? <p className="success-text">{success}</p> : null}

      <div className="crm-workspace">
        <aside className="crm-left-panel">
          <section className="crm-customer-list">
            <div className="crm-list-head">
              <div>
                <span className="crm-kicker">{loadingCustomers ? 'Loading customers' : `${filteredCustomers.length} shown`}</span>
                <strong>Customer list</strong>
              </div>
              <small>Fast view</small>
            </div>
            <div className="crm-segment-list crm-segment-inline" aria-label="Customer segments">
              {segmentOptions.map((item) => (
                <button key={item || 'all'} type="button" className={segment === item ? 'is-active' : ''} onClick={() => changeSegment(item)}>
                  {item || 'All'}
                </button>
              ))}
            </div>
            {filteredCustomers.map((customer) => (
              <button
                key={customer.id}
                type="button"
                className={selectedId === customer.id ? 'crm-customer-row is-active' : 'crm-customer-row'}
                onClick={() => loadCustomerDetails(customer)}
              >
                <span className="crm-avatar">{(customer.name || customer.mobile || '?').slice(0, 1).toUpperCase()}</span>
                <span>
                  <strong>{customer.name || 'Unnamed customer'}</strong>
                  <small>{customer.mobile || customer.email || 'No contact'}</small>
                  <em>{(customer.segments || []).slice(0, 2).join(' · ') || 'Learning segment'}</em>
                </span>
              </button>
            ))}
            {!filteredCustomers.length ? <div className="crm-empty-state">No customers matched. Try another search or segment.</div> : null}
            <div className="crm-pagination">
              <button type="button" className="ghost-btn compact-btn" disabled={!customersPage.hasPrevious} onClick={() => loadCustomers(customersPage.page - 1)}>Previous</button>
              <span>Page {(customersPage.page || 0) + 1} of {customersPage.totalPages || 1}</span>
              <button type="button" className="ghost-btn compact-btn" disabled={!customersPage.hasNext} onClick={() => loadCustomers(customersPage.page + 1)}>Next</button>
            </div>
            <div className="crm-recent-strip">
              <span className="crm-kicker">Recent customers</span>
              {recentCustomers.map((customer) => <button key={customer.id} type="button" onClick={() => loadCustomerDetails(customer)}>{customer.name || customer.mobile}</button>)}
            </div>
          </section>
        </aside>

        <main className="crm-right-panel">
          {loadingDetails ? <div className="crm-empty-state">Loading customer intelligence...</div> : null}
          {customerDetails ? (
            <>
              <section className="crm-detail-header">
                <div>
                  <span className="crm-kicker">Selected customer</span>
                  <h2>{customerDetails.name || selectedCustomer?.name || 'Unnamed customer'}</h2>
                  <p>{customerDetails.mobile || 'No mobile'} · LTV {currency(customerDetails.totalSpent || 0)} · Last active {customerDetails.lastActiveAt ? formatDate(customerDetails.lastActiveAt) : 'not captured'}</p>
                </div>
                <div className="crm-chip-row">
                  {(customerDetails.segments || []).slice(0, 3).map((item) => <span key={item}>{item}</span>)}
                </div>
              </section>
              {hideInternalTabs ? (
                <div className="sneat-context-strip">
                  <span className="crm-kicker">Current routed view</span>
                  <strong>{activeTab}</strong>
                </div>
              ) : (
                <div className="crm-tabs" role="tablist">
                  {detailTabs.map((tab) => (
                    <button key={tab} type="button" className={activeTab === tab ? 'is-active' : ''} onClick={() => setActiveTab(tab)}>{tab}</button>
                  ))}
                </div>
              )}
              {renderTabContent()}
            </>
          ) : (
            <section className="crm-empty-hero">
              <span className="crm-kicker">Customer 360</span>
              <h2>Select a customer to open the intelligence workspace</h2>
              <p>Search, segment, or choose a recent customer to see overview, timeline, search activity, login history, support chat, notes, and AI insights.</p>
            </section>
          )}
        </main>
      </div>
    </div>
  );
}
