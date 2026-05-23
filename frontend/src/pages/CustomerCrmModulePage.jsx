import { Link, useSearchParams } from 'react-router-dom';
import { useEffect, useMemo, useState } from 'react';
import { retailService } from '../services/retailService';
import { currency, formatDate } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

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

const screenMeta = {
  dashboard: {
    eyebrow: 'Customer Intelligence',
    title: 'CRM Dashboard',
    description: 'Customer signals, support, revenue, and activity summarized from the live KPS APIs.'
  }
};

const customerDetailTabs = [
  { label: 'Overview', path: 'overview' },
  { label: 'Search Activity', path: 'search-activity' },
  { label: 'Login History', path: 'login-history' },
  { label: 'Support Chat', path: 'support-chat' },
  { label: 'AI Insights', path: 'ai-insights' }
];

const previewCustomers = [
  {
    id: 'preview-1',
    name: 'Snehal Chikane',
    mobile: '+91 9764909221',
    email: 'snehal@example.com',
    gender: 'Female',
    segment: 'Returning Customer',
    segments: ['Returning Customer', 'Frequent Viewer'],
    preferredLanguage: 'Marathi',
    preferredCategories: ['Necklace', 'Mangalsutra'],
    preferredProducts: ['Pearl set', 'Green bangles'],
    preferredPriceRange: '₹500 - ₹2,000',
    totalSpent: 4200,
    customerSince: '2026-05-11T10:30:00',
    lastActiveAt: '2026-05-22T14:55:00',
    supportChatStatus: 'Resolved',
    lastKnownLocation: 'Pune',
    customerSentiment: 'Positive',
    purchasePrediction: 'Likely',
    churnRisk: 'Low',
    searchHistory: [
      { id: 's1', createdAt: '2026-05-22T11:00:00', searchKeyword: 'mangalsutra under 1500', resultCount: 8, clickedProduct: 'Black Beads Short Mangalsutra', category: 'Mangalsutra' }
    ],
    loginHistory: [
      { id: 'l1', loginTime: '2026-05-22T09:25:00', device: 'Mobile', browser: 'Chrome', ip: 'Preview', location: 'Pune', status: 'Success' }
    ]
  },
  {
    id: 'preview-2',
    name: 'NEHA LONARI',
    mobile: '+91 7219292058',
    email: '',
    gender: 'Female',
    segment: 'New Customer',
    segments: ['New Customer'],
    preferredLanguage: 'Hindi',
    preferredCategories: ['Necklace', 'Cosmetics'],
    preferredProducts: ['Daily wear jewellery'],
    preferredPriceRange: '₹1,000 - ₹3,000',
    totalSpent: 0,
    customerSince: '2026-05-20T21:17:00',
    lastActiveAt: '2026-05-20T21:17:00',
    supportChatStatus: 'In Progress',
    lastKnownLocation: 'Mumbai',
    customerSentiment: 'Learning',
    purchasePrediction: 'Medium',
    churnRisk: 'Learning'
  },
  {
    id: 'preview-3',
    name: 'Meena Bhosale',
    mobile: '+91 8888770948',
    email: 'meena@example.com',
    gender: 'Female',
    segment: 'High Value Customer',
    segments: ['High Value Customer', 'Frequent Buyer'],
    preferredLanguage: 'Marathi',
    preferredCategories: ['Bangles', 'Bridal jewellery'],
    preferredProducts: ['Festival set', 'Bridal set'],
    preferredPriceRange: '₹3,000 - ₹10,000',
    totalSpent: 18500,
    customerSince: '2026-04-18T12:00:00',
    lastActiveAt: '2026-05-21T18:20:00',
    supportChatStatus: 'No active chat',
    lastKnownLocation: 'Nashik',
    customerSentiment: 'Positive',
    purchasePrediction: 'High',
    churnRisk: 'Low'
  }
];

const previewCustomersPage = {
  items: previewCustomers,
  totalItems: previewCustomers.length,
  page: 0,
  totalPages: 1,
  hasPrevious: false,
  hasNext: false
};

function isLocalCrmPreviewEnabled() {
  if (!import.meta.env.DEV || typeof window === 'undefined') return false;
  return ['localhost', '127.0.0.1', '::1'].includes(window.location.hostname);
}

function statValue(value, fallback = 0) {
  return value == null || value === '' ? fallback : value;
}

function customerInitial(customer) {
  return String(customer?.name || customer?.mobile || '?').slice(0, 1).toUpperCase();
}

function normalizeText(value) {
  return String(value || '').trim().toLowerCase();
}

function customerMatches(customer, query) {
  const needle = normalizeText(query);
  if (!needle) return true;
  return [
    customer.name,
    customer.mobile,
    customer.email,
    customer.segment,
    ...(customer.segments || [])
  ].some((value) => normalizeText(value).includes(needle));
}

function SneatPageTitle({ eyebrow, title, description, actions }) {
  return (
    <section className="sneat-page-title">
      <div>
        <span className="sneat-eyebrow">{eyebrow}</span>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {actions ? <div className="sneat-page-actions">{actions}</div> : null}
    </section>
  );
}

function StatCard({ icon, label, value, note, tone = 'primary' }) {
  return (
    <article className={`sneat-stat-card is-${tone}`}>
      <span className="sneat-stat-icon"><i className={`bx ${icon}`} /></span>
      <div>
        <small>{label}</small>
        <strong>{value}</strong>
        {note ? <span>{note}</span> : null}
      </div>
    </article>
  );
}

function EmptyCard({ title, description, icon = 'bx-data' }) {
  return (
    <article className="sneat-empty-card">
      <i className={`bx ${icon}`} />
      <h3>{title}</h3>
      <p>{description}</p>
    </article>
  );
}

function CustomerRow({ customer, active, onSelect }) {
  return (
    <button type="button" className={`sneat-user-row ${active ? 'is-active' : ''}`} onClick={() => onSelect(customer)}>
      <span className="sneat-avatar">{customerInitial(customer)}</span>
      <span className="sneat-user-main">
        <strong>{customer.name || 'Unnamed customer'}</strong>
        <small>{customer.mobile || customer.email || 'No contact captured'}</small>
      </span>
      <span className="badge bg-label-primary">{customer.segment || customer.segments?.[0] || 'Customer'}</span>
      <i className="bx bx-chevron-right" />
    </button>
  );
}

function DetailField({ label, value }) {
  return (
    <div className="sneat-detail-field">
      <span>{label}</span>
      <strong>{value || 'Not captured'}</strong>
    </div>
  );
}

function MiniTable({ columns, rows, emptyMessage }) {
  if (!rows?.length) {
    return <EmptyCard title={emptyMessage} description="This section fills automatically as customer activity is captured." icon="bx-info-circle" />;
  }
  return (
    <div className="sneat-table-scroll">
      <table className="table table-hover align-middle">
        <thead>
          <tr>{columns.map((column) => <th key={column.key}>{column.label}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={row.id || `${row.createdAt || row.loginTime || index}-${index}`}>
              {columns.map((column) => (
                <td key={column.key}>{column.render ? column.render(row) : (row[column.key] || '-')}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function joinValue(value) {
  if (Array.isArray(value)) {
    return value.filter(Boolean).join(', ');
  }
  return value;
}

function KeyValueTable({ rows }) {
  return (
    <div className="sneat-table-scroll crm-kv-table-wrap">
      <table className="table table-hover align-middle crm-kv-table">
        <tbody>
          {rows.map((row) => (
            <tr key={row.label}>
              <th>{row.label}</th>
              <td>{row.value || 'Not captured'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function CrmTableSection({ eyebrow, title, action, children }) {
  return (
    <section className="sneat-card crm-table-section">
      <div className="sneat-card-head">
        <div><small>{eyebrow}</small><h3>{title}</h3></div>
        {action}
      </div>
      {children}
    </section>
  );
}

function AccountOverview({ customer, profileCompletion }) {
  const summaryRows = [
    { label: 'Name', value: customer.name },
    { label: 'Mobile', value: customer.mobile },
    { label: 'Email', value: customer.email },
    { label: 'Gender', value: customer.gender },
    { label: 'DOB', value: customer.dateOfBirth ? formatDate(customer.dateOfBirth) : '' },
    { label: 'Anniversary', value: customer.anniversaryDate ? formatDate(customer.anniversaryDate) : '' },
    { label: 'Preferred language', value: customer.preferredLanguage },
    { label: 'Customer since', value: customer.customerSince ? formatDate(customer.customerSince) : '' },
    { label: 'Last active', value: customer.lastActiveAt ? formatDate(customer.lastActiveAt) : '' },
    { label: 'Last order', value: customer.lastOrderDate ? formatDate(customer.lastOrderDate) : '' },
    { label: 'Location', value: customer.lastKnownLocation || customer.fullAddress },
    { label: 'Segments', value: joinValue(customer.segments) || customer.segment }
  ];
  const preferenceRows = [
    { label: 'Favorite categories', value: joinValue(customer.preferredCategories) },
    { label: 'Favorite product types', value: joinValue(customer.preferredProducts) },
    { label: 'Preferred brands', value: joinValue(customer.preferredBrands) },
    { label: 'Budget range', value: customer.preferredPriceRange },
    { label: 'Shopping interests', value: customer.shoppingInterests },
    { label: 'Preference insights', value: joinValue(customer.preferenceInsights) }
  ];
  const engagementRows = [
    { label: 'Lifetime value', value: currency(customer.totalSpent || 0) },
    { label: 'Total orders', value: customer.totalOrders },
    { label: 'Pending orders', value: customer.pendingOrders },
    { label: 'Support status', value: customer.supportChatStatus },
    { label: 'Sentiment', value: customer.customerSentiment || 'Learning' },
    { label: 'Purchase prediction', value: customer.purchasePrediction || 'Learning' },
    { label: 'Churn risk', value: customer.churnRisk || 'Learning' },
    { label: 'Engagement score', value: customer.engagementScore },
    { label: 'Recommended products', value: joinValue(customer.recommendedProducts) }
  ];
  const activityRows = customer.searchHistory?.length ? customer.searchHistory : customer.activityHistory || [];

  return (
    <div className="sneat-user-view-grid crm-tabular-overview">
      <aside className="sneat-user-profile-card">
        <span className="sneat-avatar xl">{customerInitial(customer)}</span>
        <h3>{customer.name || 'Unnamed customer'}</h3>
        <p>{customer.mobile || customer.email || 'No contact captured'}</p>
        <span className="badge bg-label-success">{customer.highValueBadge || customer.segments?.[0] || 'Customer'}</span>
        <div className="sneat-profile-progress">
          <div><span style={{ width: `${profileCompletion}%` }} /></div>
          <strong>{profileCompletion}% profile ready</strong>
        </div>
      </aside>

      <CrmTableSection
        eyebrow="Single View"
        title="Account summary"
        action={<Link className="btn btn-sm btn-outline-primary" to="/app/support/active"><i className="bx bx-conversation me-1" /> Support</Link>}
      >
        <KeyValueTable rows={summaryRows} />
      </CrmTableSection>

      <CrmTableSection eyebrow="Preferences" title="Shopping intelligence">
        <KeyValueTable rows={preferenceRows} />
      </CrmTableSection>

      <CrmTableSection eyebrow="Engagement" title="Revenue, AI, and support">
        <KeyValueTable rows={engagementRows} />
      </CrmTableSection>

      <CrmTableSection eyebrow="Orders" title="Order history">
        <MiniTable
          emptyMessage="No orders yet"
          rows={customer.orderHistory || []}
          columns={[
            { key: 'orderNumber', label: 'Order' },
            { key: 'createdAt', label: 'Date', render: (row) => formatDate(row.createdAt) },
            { key: 'amount', label: 'Amount', render: (row) => currency(row.amount || 0) },
            { key: 'status', label: 'Status' }
          ]}
        />
      </CrmTableSection>

      <CrmTableSection eyebrow="Signals" title="Search and site activity">
        <MiniTable
          emptyMessage="No activity yet"
          rows={activityRows}
          columns={[
            { key: 'createdAt', label: 'Time', render: (row) => formatDate(row.createdAt) },
            { key: 'activityType', label: 'Type', render: (row) => row.activityType || (row.searchKeyword ? 'SEARCH' : '-') },
            { key: 'searchKeyword', label: 'Search / Page', render: (row) => row.searchKeyword || row.page || '-' },
            { key: 'category', label: 'Category', render: (row) => row.category || '-' },
            { key: 'clickedProduct', label: 'Clicked / Product', render: (row) => row.clickedProduct || row.productName || '-' },
            { key: 'resultCount', label: 'Results', render: (row) => row.resultCount ?? '-' }
          ]}
        />
      </CrmTableSection>

      <CrmTableSection eyebrow="Security" title="Login history">
        <MiniTable
          emptyMessage="No login history yet"
          rows={customer.loginHistory || []}
          columns={[
            { key: 'loginTime', label: 'Login time', render: (row) => formatDate(row.loginTime) },
            { key: 'loginMethod', label: 'Method', render: (row) => row.loginMethod || '-' },
            { key: 'device', label: 'Device' },
            { key: 'browser', label: 'Browser' },
            { key: 'ip', label: 'IP' },
            { key: 'location', label: 'Location' },
            { key: 'status', label: 'Status' }
          ]}
        />
      </CrmTableSection>

      <CrmTableSection eyebrow="Location" title="Location history">
        <MiniTable
          emptyMessage="No location history yet"
          rows={customer.locationHistory || []}
          columns={[
            { key: 'createdAt', label: 'Captured', render: (row) => formatDate(row.createdAt) },
            { key: 'city', label: 'City' },
            { key: 'state', label: 'State' },
            { key: 'country', label: 'Country' },
            { key: 'pincode', label: 'Pincode' },
            { key: 'locationSource', label: 'Source' }
          ]}
        />
      </CrmTableSection>
    </div>
  );
}

function CustomerAccountPanel({ detailTab, customer, loading }) {
  const profileCompletion = useMemo(() => {
    if (!customer) return 0;
    const fields = [
      customer.name,
      customer.mobile,
      customer.email,
      customer.gender,
      customer.dateOfBirth,
      customer.anniversaryDate,
      customer.preferredLanguage,
      customer.preferredCategories?.length,
      customer.preferredPriceRange,
      customer.shoppingInterests
    ];
    return Math.round((fields.filter(Boolean).length / fields.length) * 100);
  }, [customer]);

  if (loading) {
    return <div className="sneat-card"><div className="kps-skeleton sneat-loading-block" /></div>;
  }

  if (!customer) {
    return <EmptyCard title="Select a customer" description="Choose a customer from the list to open the account view." icon="bx-user-circle" />;
  }

  if (detailTab === 'Search Activity') {
    return (
      <section className="sneat-card">
        <div className="sneat-card-head"><div><small>Customer Signals</small><h3>Search activity</h3></div></div>
        <MiniTable
          emptyMessage="No search history yet"
          rows={customer.searchHistory?.length ? customer.searchHistory : customer.activityHistory || []}
          columns={[
            { key: 'createdAt', label: 'Timestamp', render: (row) => formatDate(row.createdAt) },
            { key: 'searchKeyword', label: 'Search term', render: (row) => row.searchKeyword || '-' },
            { key: 'resultCount', label: 'Results', render: (row) => row.resultCount ?? '-' },
            { key: 'clickedProduct', label: 'Clicked product', render: (row) => row.clickedProduct || row.productName || '-' },
            { key: 'category', label: 'Category', render: (row) => row.category || '-' }
          ]}
        />
      </section>
    );
  }

  if (detailTab === 'Login History') {
    return (
      <section className="sneat-card">
        <div className="sneat-card-head"><div><small>Security</small><h3>Login history</h3></div></div>
        <MiniTable
          emptyMessage="No login history yet"
          rows={customer.loginHistory || []}
          columns={[
            { key: 'loginTime', label: 'Login time', render: (row) => formatDate(row.loginTime) },
            { key: 'device', label: 'Device' },
            { key: 'browser', label: 'Browser' },
            { key: 'ip', label: 'IP' },
            { key: 'location', label: 'Location' },
            { key: 'status', label: 'Status' }
          ]}
        />
      </section>
    );
  }

  if (detailTab === 'Support Chat') {
    return (
      <section className="sneat-user-view-grid">
        <article className="sneat-card">
          <div className="sneat-card-head"><div><small>Support</small><h3>Support handoff</h3></div></div>
          <p className="text-muted">Open the support inbox with customer context, latest order, preferences, and captured profile signals.</p>
          <Link className="btn btn-primary" to="/app/support/active"><i className="bx bx-message-rounded-dots me-1" /> Open Support</Link>
        </article>
        <article className="sneat-card">
          <div className="sneat-card-head"><div><small>Status</small><h3>{customer.supportChatStatus || 'No active chat'}</h3></div></div>
          <div className="sneat-chip-row">
            {(customer.segments || ['Customer']).map((item) => <span key={item}>{item}</span>)}
          </div>
        </article>
      </section>
    );
  }

  if (detailTab === 'AI Insights') {
    return (
      <section className="sneat-grid-3">
        <StatCard icon="bx-smile" label="Sentiment" value={customer.customerSentiment || 'Learning'} note="Based on behavior" />
        <StatCard icon="bx-trending-up" label="Prediction" value={customer.purchasePrediction || 'Learning'} note="Purchase intent" tone="success" />
        <StatCard icon="bx-shield-quarter" label="Churn risk" value={customer.churnRisk || 'Learning'} note="Retention signal" tone="warning" />
        <article className="sneat-card span-2">
          <div className="sneat-card-head"><div><small>Recommended products</small><h3>Next best action</h3></div></div>
          <div className="sneat-chip-row">
            {(customer.recommendedProducts || ['Pearl jewellery', 'Festival offers', 'Budget recommendations']).map((item) => <span key={item}>{item}</span>)}
          </div>
        </article>
      </section>
    );
  }

  return <AccountOverview customer={customer} profileCompletion={profileCompletion} />;
}

function CustomerDirectoryScreen({ detailTab = 'Overview' }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedCustomerId = searchParams.get('customerId');
  const requestedMobile = searchParams.get('mobile');
  const [customersPage, setCustomersPage] = useState({ items: [], totalItems: 0, page: 0, totalPages: 0 });
  const [selectedCustomer, setSelectedCustomer] = useState(null);
  const [customerDetails, setCustomerDetails] = useState(null);
  const [segment, setSegment] = useState('');
  const [search, setSearch] = useState('');
  const [loadingCustomers, setLoadingCustomers] = useState(false);
  const [loadingDetails, setLoadingDetails] = useState(false);
  const [error, setError] = useState('');

  const loadCustomers = async (page = 0, nextSegment = segment) => {
    setLoadingCustomers(true);
    try {
      const response = await retailService.getCustomers({ page, size: 12, segment: nextSegment });
      setCustomersPage(response || { items: [], totalItems: 0, page, totalPages: 0 });
      setError('');
    } catch (requestError) {
      if (isLocalCrmPreviewEnabled()) {
        const filteredPreviewItems = nextSegment
          ? previewCustomers.filter((customer) => (customer.segments || []).includes(nextSegment) || customer.segment === nextSegment)
          : previewCustomers;
        setCustomersPage({ ...previewCustomersPage, items: filteredPreviewItems, totalItems: filteredPreviewItems.length });
        setError('');
        return;
      }
      setError(getApiErrorMessage(requestError, 'Unable to load customers.'));
    } finally {
      setLoadingCustomers(false);
    }
  };

  const loadCustomerDetails = async (customer) => {
    setSelectedCustomer(customer);
    if (customer?.id) {
      setSearchParams({ customerId: customer.id });
    } else if (customer?.mobile) {
      setSearchParams({ mobile: customer.mobile });
    }
    setLoadingDetails(true);
    setError('');
    try {
      const details = await retailService.getCustomerDetails(customer.id);
      setCustomerDetails(details || customer);
    } catch (requestError) {
      setCustomerDetails(customer);
      setError(isLocalCrmPreviewEnabled() ? '' : getApiErrorMessage(requestError, 'Unable to load customer details.'));
    } finally {
      setLoadingDetails(false);
    }
  };

  useEffect(() => {
    const openRequestedCustomer = async () => {
      if (requestedCustomerId) {
        setLoadingCustomers(true);
        try {
          const details = await retailService.getCustomerDetails(requestedCustomerId);
          const items = details ? [details] : [];
          setCustomersPage({ items, totalItems: items.length, page: 0, totalPages: 1, hasNext: false, hasPrevious: false });
          if (details?.id) {
            setSelectedCustomer(details);
            setCustomerDetails(details);
          }
          setError('');
          return;
        } catch (requestError) {
          setError(isLocalCrmPreviewEnabled() ? '' : getApiErrorMessage(requestError, 'Unable to open requested customer.'));
        } finally {
          setLoadingCustomers(false);
        }
      }

      if (requestedMobile) {
        setLoadingCustomers(true);
        try {
          const matches = await retailService.searchCustomers(requestedMobile);
          setSearch(requestedMobile);
          setCustomersPage({ items: matches || [], totalItems: matches?.length || 0, page: 0, totalPages: 1, hasNext: false, hasPrevious: false });
          if (matches?.[0]?.id) {
            await loadCustomerDetails(matches[0]);
          }
          setError('');
          return;
        } catch (requestError) {
          setError(isLocalCrmPreviewEnabled() ? '' : getApiErrorMessage(requestError, 'Unable to search customer by mobile.'));
        } finally {
          setLoadingCustomers(false);
        }
      }

      await loadCustomers(0, segment);
    };

    openRequestedCustomer();
  }, []);

  useEffect(() => {
    const customers = customersPage.items || [];
    if (selectedCustomer || !customers.length) {
      return;
    }
    const normalizedRequestedMobile = String(requestedMobile || '').replace(/\D/g, '');
    const requestedCustomer = customers.find((customer) => {
      if (requestedCustomerId && customer.id === requestedCustomerId) {
        return true;
      }
      if (!normalizedRequestedMobile) {
        return false;
      }
      return String(customer.mobile || '').replace(/\D/g, '').endsWith(normalizedRequestedMobile.slice(-10));
    });
    const firstCustomer = requestedCustomer || customers[0];
    if (firstCustomer?.id) {
      loadCustomerDetails(firstCustomer);
      if (requestedMobile && !search) {
        setSearch(requestedMobile);
      }
    }
  }, [customersPage.items, requestedCustomerId, requestedMobile, search, selectedCustomer]);

  const customers = customersPage.items || [];
  const filteredCustomers = customers.filter((customer) => customerMatches(customer, search));
  const highValueCount = customers.filter((customer) => (customer.segments || []).some((item) => /high|vip/i.test(item))).length;

  return (
    <div className="sneat-module-page sneat-customer-module">
      <SneatPageTitle
        eyebrow="Customer Intelligence"
        title="Customers"
        description="A Sneat-style customer list and account workspace connected to live KPS customer APIs."
        actions={<Link className="btn btn-primary" to="/app/crm/dashboard"><i className="bx bx-grid-alt me-1" /> CRM Dashboard</Link>}
      />

      <section className="sneat-stat-grid">
        <StatCard icon="bx-group" label="Total customers" value={customersPage.totalItems || customers.length} note="Live customer records" />
        <StatCard icon="bx-crown" label="High value" value={highValueCount} note="VIP/revenue signals" tone="success" />
        <StatCard icon="bx-search" label="Shown" value={filteredCustomers.length} note="Current filter" tone="info" />
        <StatCard icon="bx-filter-alt" label="Segment" value={segment || 'All'} note="Active view" tone="warning" />
      </section>

      <section className="sneat-card sneat-filter-card">
        <div className="sneat-card-head">
          <div><small>Search Filters</small><h3>Customer directory</h3></div>
        </div>
        <div className="sneat-filter-grid">
          <label>
            <span>Search</span>
            <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Name, mobile, email, or segment" />
          </label>
          <label>
            <span>Segment</span>
            <select value={segment} onChange={(event) => {
              setSegment(event.target.value);
              setSelectedCustomer(null);
              setCustomerDetails(null);
              loadCustomers(0, event.target.value);
            }}>
              {segmentOptions.map((item) => <option key={item || 'all'} value={item}>{item || 'All segments'}</option>)}
            </select>
          </label>
        </div>
      </section>

      {error ? <div className="alert alert-danger">{error}</div> : null}

      <nav className="crm-detail-tab-row" aria-label="Customer CRM sections">
        {customerDetailTabs.map((tab) => (
          <Link
            key={tab.path}
            className={detailTab === tab.label ? 'is-active' : ''}
            to={customerDetailsPath(customerDetails || selectedCustomer, tab.path)}
          >
            {tab.label}
          </Link>
        ))}
      </nav>

      <section className="sneat-customer-layout">
        <aside className="sneat-card sneat-user-list-card">
          <div className="sneat-card-head">
            <div><small>{loadingCustomers ? 'Loading' : `${filteredCustomers.length} shown`}</small><h3>Customer list</h3></div>
          </div>
          <div className="sneat-user-list">
            {filteredCustomers.map((customer) => (
              <CustomerRow
                key={customer.id}
                customer={customer}
                active={selectedCustomer?.id === customer.id}
                onSelect={loadCustomerDetails}
              />
            ))}
            {!filteredCustomers.length ? <EmptyCard title="No customers found" description="Try another search term or segment." icon="bx-search" /> : null}
          </div>
          <div className="sneat-pagination-row">
            <button type="button" className="btn btn-sm btn-outline-secondary" disabled={!customersPage.hasPrevious} onClick={() => loadCustomers((customersPage.page || 0) - 1)}>Previous</button>
            <span>Page {(customersPage.page || 0) + 1} of {customersPage.totalPages || 1}</span>
            <button type="button" className="btn btn-sm btn-outline-secondary" disabled={!customersPage.hasNext} onClick={() => loadCustomers((customersPage.page || 0) + 1)}>Next</button>
          </div>
        </aside>

        <main className="sneat-account-panel">
          <CustomerAccountPanel detailTab={detailTab} customer={customerDetails || selectedCustomer} loading={loadingDetails} />
        </main>
      </section>
    </div>
  );
}

function customerDetailsPath(customer, detailPath = 'overview') {
  const query = customer?.id
    ? `customerId=${encodeURIComponent(customer.id)}`
    : customer?.mobile
      ? `mobile=${encodeURIComponent(customer.mobile)}`
      : '';
  return `/app/crm/customers/${detailPath}${query ? `?${query}` : ''}`;
}

function CustomerRowCompact({ customer }) {
  return (
    <Link className="sneat-list-row sneat-list-row-link" to={customerDetailsPath(customer)}>
      <span className="sneat-avatar">{customerInitial(customer)}</span>
      <div>
        <strong>{customer.name || 'Unnamed customer'}</strong>
        <small>{customer.mobile || customer.email || 'No contact captured'}</small>
      </div>
      <span className="badge bg-label-primary">{customer.segment || customer.segments?.[0] || 'Customer'}</span>
    </Link>
  );
}

export default function CustomerCrmModulePage({ screen = 'dashboard', detailTab = 'Overview' }) {
  const [customersPage, setCustomersPage] = useState({ items: [], totalItems: 0 });
  const [supportSummary, setSupportSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const meta = screenMeta[screen] || screenMeta.dashboard;

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.allSettled([
      retailService.getCustomers({ page: 0, size: 10 }),
      retailService.getSupportSummary()
    ]).then(([customersResult, supportResult]) => {
      if (cancelled) return;
      if (customersResult.status === 'fulfilled') {
        setCustomersPage(customersResult.value || { items: [], totalItems: 0 });
      } else {
        if (isLocalCrmPreviewEnabled()) {
          setCustomersPage(previewCustomersPage);
        } else {
        setError(getApiErrorMessage(customersResult.reason, 'Unable to load customer CRM data.'));
        }
      }
      if (supportResult.status === 'fulfilled') {
        setSupportSummary(supportResult.value || null);
      }
    }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const customers = customersPage.items || [];
  const highValueCustomers = useMemo(
    () => customers.filter((customer) => (customer.totalSpent || 0) > 0 || (customer.segments || []).some((segment) => /high|vip|buyer/i.test(segment))),
    [customers]
  );
  const totalLtv = customers.reduce((sum, customer) => sum + Number(customer.totalSpent || 0), 0);

  if (screen === 'customers') {
    return <CustomerDirectoryScreen detailTab={detailTab} />;
  }

  return (
    <div className="sneat-module-page">
      <SneatPageTitle eyebrow={meta.eyebrow} title={meta.title} description={meta.description} />
      {error ? <div className="alert alert-danger">{error}</div> : null}
      <section className="sneat-stat-grid">
        <StatCard icon="bx-group" label="Customers" value={statValue(customersPage.totalItems, customers.length)} note={loading ? 'Loading live API' : 'Live customer records'} />
        <StatCard icon="bx-crown" label="High value" value={highValueCustomers.length} note="Revenue signals" tone="success" />
        <StatCard icon="bx-conversation" label="Open support" value={statValue(supportSummary?.openCount, 0)} note={`${statValue(supportSummary?.unreadCount, 0)} unread`} tone="info" />
        <StatCard icon="bx-rupee" label="Visible LTV" value={currency(totalLtv)} note="Current page sample" tone="warning" />
      </section>

      <section className="sneat-dashboard-grid">
        <article className="sneat-card">
          <div className="sneat-card-head">
            <div><small>Customers</small><h3>Recent customer activity</h3></div>
            <Link className="btn btn-sm btn-outline-primary" to="/app/crm/customers/overview">Open list</Link>
          </div>
          <div className="sneat-list">
            {customers.slice(0, 7).map((customer) => <CustomerRowCompact key={customer.id || customer.mobile} customer={customer} />)}
            {!customers.length ? <EmptyCard title="No customers loaded" description="Customer records will appear from the live customer API." /> : null}
          </div>
        </article>
        <article className="sneat-card">
          <div className="sneat-card-head"><div><small>Customer Health</small><h3>Segment readiness</h3></div></div>
          {['New Customer', 'Returning Customer', 'High Value Customer', 'Offer Interested Customer'].map((label, index) => (
            <div className="sneat-progress-row" key={label}>
              <span>{label}</span>
              <strong>{customers.filter((customer) => (customer.segments || []).includes(label)).length}</strong>
              <div><span style={{ width: `${Math.max(12, 76 - index * 14)}%` }} /></div>
            </div>
          ))}
        </article>
      </section>
    </div>
  );
}
