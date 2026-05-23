import { Link, useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { retailService } from '../services/retailService';
import { currency } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

function DashboardMetric({ icon, label, value, note, tone = 'primary', trend, to }) {
  const trendValue = trend?.percentage ?? trend;
  const direction = trend?.direction || (String(trendValue || '').startsWith('-') ? 'down' : 'up');
  const content = (
    <>
      <span className="sneat-stat-icon"><i className={`bx ${icon}`} /></span>
      <div>
        <small>{label}</small>
        <strong>{value}</strong>
        {note ? <span>{note}</span> : null}
        {trendValue !== undefined && trendValue !== null ? (
          <em className={direction === 'down' ? 'text-danger' : direction === 'flat' ? 'text-muted' : 'text-success'}>
            {direction === 'down' ? '' : direction === 'flat' ? '' : '+'}{trendValue}%
          </em>
        ) : null}
      </div>
    </>
  );
  if (to) {
    return (
      <Link className={`sneat-stat-card dashboard-click-card is-${tone}`} to={to}>
        {content}
        <i className="bx bx-chevron-right dashboard-card-arrow" />
      </Link>
    );
  }
  return (
    <article className={`sneat-stat-card is-${tone}`}>
      {content}
    </article>
  );
}

function QuickModule({ icon, title, description, to }) {
  return (
    <Link className="sneat-card dashboard-module-card" to={to}>
      <span className="sneat-stat-icon"><i className={`bx ${icon}`} /></span>
      <div>
        <h3>{title}</h3>
        <p>{description}</p>
      </div>
      <i className="bx bx-chevron-right" />
    </Link>
  );
}

function MiniBars({ values = [] }) {
  const safeValues = values.length ? values : [24, 42, 31, 56, 38, 72, 64];
  const max = Math.max(...safeValues, 1);
  return (
    <div className="sneat-mini-bars" aria-label="Weekly activity chart">
      {safeValues.map((value, index) => (
        <span
          key={`${value}-${index}`}
          style={{ '--bar-height': `${Math.max(18, (value / max) * 100)}%` }}
          className={index === safeValues.length - 2 ? 'is-active' : ''}
        />
      ))}
    </div>
  );
}

function Sparkline() {
  return (
    <svg className="sneat-sparkline" viewBox="0 0 220 86" role="img" aria-label="Activity trend">
      <defs>
        <linearGradient id="dashboardSparkFill" x1="0" x2="0" y1="0" y2="1">
          <stop offset="0%" stopColor="#2fbf8f" stopOpacity="0.24" />
          <stop offset="100%" stopColor="#2fbf8f" stopOpacity="0" />
        </linearGradient>
      </defs>
      <path d="M0 64 C24 60 30 38 56 42 C83 46 78 72 106 68 C132 64 124 26 151 22 C181 17 176 58 207 45 L220 38 L220 86 L0 86 Z" fill="url(#dashboardSparkFill)" />
      <path d="M0 64 C24 60 30 38 56 42 C83 46 78 72 106 68 C132 64 124 26 151 22 C181 17 176 58 207 45 L220 38" fill="none" stroke="#2fbf8f" strokeWidth="5" strokeLinecap="round" />
    </svg>
  );
}

function ProgressRow({ label, value, tone = 'primary' }) {
  return (
    <div className={`sneat-progress-row is-${tone}`}>
      <span>{label}</span>
      <strong>{value}%</strong>
      <div><span style={{ width: `${value}%` }} /></div>
    </div>
  );
}

function DashboardListRow({ avatar, title, subtitle, badge, tone = 'primary' }) {
  return (
    <div className="sneat-list-row dashboard-list-row">
      <span className={`sneat-avatar is-${tone}`}>{avatar}</span>
      <div>
        <strong>{title}</strong>
        <small>{subtitle}</small>
      </div>
      {badge ? <span className={`badge bg-label-${tone === 'primary' ? 'primary' : tone}`}>{badge}</span> : null}
    </div>
  );
}

function AnalyticsSkeleton() {
  return (
    <section className="sneat-stat-grid dashboard-skeleton-grid" aria-label="Loading analytics">
      {Array.from({ length: 12 }).map((_, index) => <span key={index} className="dashboard-skeleton-card" />)}
    </section>
  );
}

function analyticsValue(value, fallback = 0) {
  return value === undefined || value === null ? fallback : value;
}

function canUsePermission(auth, permission) {
  if (auth?.role === 'ADMIN' || auth?.role === 'OWNER') return true;
  const permissions = Array.isArray(auth?.permissions) ? auth.permissions : [];
  if (Array.isArray(permission)) return permission.some((entry) => permissions.includes(entry));
  return permissions.includes(permission);
}

function formatOrderDate(value) {
  if (!value) return 'No date';
  return new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}

export default function AdminDashboardPage({ branding, auth }) {
  const navigate = useNavigate();
  const [analytics, setAnalytics] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [dashboardSearch, setDashboardSearch] = useState('');
  const [searchOptions, setSearchOptions] = useState([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const canViewAnalytics = canUsePermission(auth, ['REPORTS', 'REPORTS_DASHBOARD', 'CUSTOMERS_DASHBOARD']);
  const canSearchBilling = canUsePermission(auth, ['BILLING', 'BILLING_CHECKOUT', 'BILLING_INVOICES', 'BILLING_ORDERS']);
  const canSearchProducts = canUsePermission(auth, ['PRODUCTS', 'PRODUCTS_LIST', 'PRODUCTS_CATEGORIES']);
  const canSearchCustomers = canUsePermission(auth, ['CUSTOMERS', 'CUSTOMERS_OVERVIEW', 'CUSTOMERS_DASHBOARD']);
  const canSearchCampaigns = canUsePermission(auth, ['MARKETING_AUTOMATION', 'CAMPAIGNS', 'CAMPAIGNS_LIST', 'CAMPAIGNS_TEMPLATES', 'OFFERS', 'CAMPAIGNS_OFFERS']);
  const canSearchSupport = canUsePermission(auth, ['CUSTOMERS', 'CUSTOMERS_SUPPORT_CHAT']);
  const canSearchUsers = canUsePermission(auth, 'USER_MANAGEMENT');
  const canSearchVisits = canUsePermission(auth, 'SITE_INTERACTIONS');

  useEffect(() => {
    if (!canViewAnalytics) {
      setAnalytics(null);
      setError('');
      setLoading(false);
      return undefined;
    }
    let cancelled = false;
    setLoading(true);
    retailService.getDashboardAnalytics()
      .then((data) => {
        if (cancelled) return;
        setAnalytics(data || {});
        setError('');
      })
      .catch((requestError) => {
        if (cancelled) return;
        setError(getApiErrorMessage(requestError, 'Unable to load dashboard analytics.'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [canViewAnalytics]);

  const topProducts = analytics?.topSellingProducts || [];
  const recentOrders = analytics?.recentOrders || [];
  const weeklyBars = [
    Number(analytics?.totalOrders || 1),
    Number(analytics?.customerVisits || 1),
    Number(analytics?.totalCustomers || 1),
    Number(analytics?.completedOrders || 1),
    Number(analytics?.pendingOrders || 1),
    Math.max(30, Math.min(96, Number(analytics?.revenueToday || 0) / 100)),
    Math.max(22, Number(analytics?.lowStockProducts || 1) * 18)
  ];
  const dashboardSearchTargets = [
    canSearchCustomers ? { keywords: ['customer', 'crm', 'mobile', 'profile'], to: '/app/crm/customers/overview' } : null,
    canUsePermission(auth, ['REPORTS', 'REPORTS_SALES']) ? { keywords: ['sale', 'sales', 'revenue', 'report'], to: '/app/reports/sales' } : null,
    canSearchBilling ? { keywords: ['order', 'orders', 'pending', 'completed', 'cancelled'], to: '/app/billing/orders' } : null,
    canSearchBilling ? { keywords: ['invoice', 'bill', 'billing', 'checkout'], to: '/app/billing' } : null,
    canSearchProducts ? { keywords: ['product', 'inventory', 'stock', 'low stock'], to: '/app/inventory/products' } : null,
    canSearchProducts ? { keywords: ['category', 'categories'], to: '/app/inventory/categories' } : null,
    canSearchCampaigns ? { keywords: ['campaign', 'template', 'offer', 'coupon'], to: '/app/campaigns/templates' } : null,
    canSearchSupport ? { keywords: ['chat', 'support', 'whatsapp'], to: '/app/support/active' } : null,
    canSearchUsers ? { keywords: ['user', 'team', 'staff'], to: '/app/users' } : null,
    canSearchVisits ? { keywords: ['visit', 'website'], to: '/app/site-interactions' } : null
  ].filter(Boolean);

  useEffect(() => {
    const query = dashboardSearch.trim();
    if (query.length < 2) {
      setSearchOptions([]);
      setSearchLoading(false);
      return undefined;
    }

    let cancelled = false;
    const timeoutId = window.setTimeout(async () => {
      setSearchLoading(true);
      const lowerQuery = query.toLowerCase();
      const fromDate = new Date(Date.now() - 90 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
      const toDate = new Date().toISOString().slice(0, 10);
      const matchesText = (...values) => values.some((value) => String(value || '').toLowerCase().includes(lowerQuery));
      try {
        const [customers, products, categories, offers, campaigns, orders] = await Promise.allSettled([
          canSearchCustomers || canSearchBilling ? retailService.searchCustomers(query) : Promise.resolve([]),
          canSearchProducts || canSearchBilling ? retailService.getProducts({ page: 0, size: 40 }) : Promise.resolve({ items: [] }),
          canSearchProducts ? retailService.getProductCategoryOptions() : Promise.resolve([]),
          canSearchCampaigns || canSearchBilling ? retailService.getOffers({ page: 0, size: 40 }) : Promise.resolve({ items: [] }),
          canSearchCampaigns ? retailService.getMarketingCampaigns({ page: 0, size: 40 }) : Promise.resolve({ items: [] }),
          canSearchBilling ? retailService.getReportInvoices({ fromDate, toDate, customerName: query, page: 0, size: 12 }) : Promise.resolve({ orders: [], invoices: [] })
        ]);
        if (cancelled) return;
        const nextOptions = [];
        if (customers.status === 'fulfilled') {
          (customers.value || []).slice(0, 6).forEach((customer) => nextOptions.push({
            key: `customer-${customer.id || customer.mobile}`,
            type: 'Customer',
            icon: 'bx-user',
            title: customer.name || customer.mobile || 'Customer',
            subtitle: customer.mobile || customer.email || 'Open CRM details',
            to: customer.id
              ? `/app/crm/customers/overview?customerId=${encodeURIComponent(customer.id)}`
              : `/app/crm/customers/overview?mobile=${encodeURIComponent(customer.mobile || query)}`
          }));
        }
        if (products.status === 'fulfilled') {
          (products.value?.items || [])
            .filter((product) => matchesText(product.name, product.sku, product.category))
            .slice(0, 6)
            .forEach((product) => nextOptions.push({
              key: `product-${product.id || product.sku}`,
              type: 'Product',
              icon: 'bx-package',
              title: product.name || product.sku || 'Product',
              subtitle: `${product.sku || 'SKU'} · ${product.category || 'Category'} · ${currency(product.sellingPrice || product.websitePrice || 0)}`,
              to: `/app/inventory/products?q=${encodeURIComponent(product.name || product.sku || query)}`
            }));
        }
        if (categories.status === 'fulfilled') {
          (categories.value || [])
            .filter((category) => matchesText(category.displayName, category.name, category.code))
            .slice(0, 6)
            .forEach((category) => nextOptions.push({
              key: `category-${category.id || category.code}`,
              type: 'Category',
              icon: 'bx-category',
              title: category.displayName || category.name || category.code,
              subtitle: category.code || 'Open category list',
              to: `/app/inventory/categories?q=${encodeURIComponent(category.displayName || category.name || category.code || query)}`
            }));
        }
        if (offers.status === 'fulfilled') {
          (offers.value?.items || [])
            .filter((offer) => matchesText(offer.name, offer.couponCode, offer.category))
            .slice(0, 5)
            .forEach((offer) => nextOptions.push({
              key: `offer-${offer.id || offer.couponCode}`,
              type: 'Offer',
              icon: 'bx-purchase-tag',
              title: offer.name || offer.couponCode || 'Offer',
              subtitle: offer.couponCode ? `Coupon ${offer.couponCode}` : 'Open offers',
              to: `/app/campaigns/offers${offer.couponCode ? `?coupon=${encodeURIComponent(offer.couponCode)}` : ''}`
            }));
        }
        if (campaigns.status === 'fulfilled') {
          (campaigns.value?.items || [])
            .filter((campaign) => matchesText(campaign.campaignName, campaign.offerTitle, campaign.status, campaign.campaignType))
            .slice(0, 5)
            .forEach((campaign) => nextOptions.push({
              key: `campaign-${campaign.id}`,
              type: 'Campaign',
              icon: 'bx-broadcast',
              title: campaign.campaignName || 'Campaign',
              subtitle: `${campaign.status || 'Draft'} · ${campaign.campaignType || 'Marketing'}`,
              to: '/app/campaigns/list'
            }));
        }
        if (orders.status === 'fulfilled') {
          (orders.value?.orders || orders.value?.invoices || [])
            .filter((order) => matchesText(order.referenceNumber, order.customerName, order.customerMobile, order.couponCode))
            .slice(0, 5)
            .forEach((order) => nextOptions.push({
              key: `order-${order.id || order.referenceNumber}`,
              type: String(order.source || '').toUpperCase() === 'BILLING' ? 'Invoice' : 'Order',
              icon: 'bx-receipt',
              title: order.referenceNumber || order.orderNumber || 'Order',
              subtitle: `${order.customerName || order.customerMobile || 'Customer'} · ${currency(order.finalAmount || 0)}`,
              to: String(order.source || '').toUpperCase() === 'BILLING'
                ? `/app/billing/invoices/${encodeURIComponent(order.id)}`
                : `/app/billing/orders/${encodeURIComponent(order.id)}`
            }));
        }
        setSearchOptions(nextOptions.slice(0, 18));
      } catch {
        if (!cancelled) setSearchOptions([]);
      } finally {
        if (!cancelled) setSearchLoading(false);
      }
    }, 240);

    return () => {
      cancelled = true;
      window.clearTimeout(timeoutId);
    };
  }, [dashboardSearch, canSearchBilling, canSearchCampaigns, canSearchCustomers, canSearchProducts]);

  const submitDashboardSearch = (event) => {
    event.preventDefault();
    const query = dashboardSearch.trim().toLowerCase();
    if (!query) return;
    const match = dashboardSearchTargets.find((target) => target.keywords.some((keyword) => (
      query === keyword || query.includes(keyword) || keyword.includes(query)
    )));
    navigate(match?.to || (canSearchCustomers
      ? `/app/crm/customers/overview?search=${encodeURIComponent(dashboardSearch.trim())}`
      : firstSearchRoute));
  };

  const openSearchTarget = (target) => {
    setDashboardSearch(target.keywords[0]);
    navigate(target.to);
  };

  const openSearchOption = (option) => {
    setDashboardSearch('');
    setSearchOptions([]);
    navigate(option.to);
  };
  const firstSearchRoute = dashboardSearchTargets[0]?.to || '/app';
  const accessibleModules = [
    canSearchBilling ? { icon: 'bx-receipt', title: 'Billing', description: 'Checkout, invoices, and orders available to this user.', to: '/app/billing' } : null,
    canSearchProducts ? { icon: 'bx-package', title: 'Inventory', description: 'Products, categories, collections, and brands.', to: '/app/inventory/products' } : null,
    canSearchCustomers ? { icon: 'bx-user-voice', title: 'Customer CRM', description: 'Customer details, activity, support, and insights.', to: '/app/crm/dashboard' } : null,
    canSearchSupport ? { icon: 'bx-conversation', title: 'Support Inbox', description: 'Active chats, archived chats, and product sender.', to: '/app/support/active' } : null,
    canSearchCampaigns ? { icon: 'bx-broadcast', title: 'Campaign Studio', description: 'Campaigns, templates, offers, and approvals.', to: '/app/campaigns/dashboard' } : null,
    canUsePermission(auth, ['REPORTS', 'REPORTS_DASHBOARD', 'REPORTS_SALES']) ? { icon: 'bx-line-chart', title: 'Reports', description: 'Sales, payments, and operations reporting.', to: '/app/reports/dashboard' } : null,
    canUsePermission(auth, 'SALESPERSON_SALES') ? { icon: 'bx-medal', title: 'Salesperson Sales', description: 'Salesperson targets and performance.', to: '/app/salesperson-sales' } : null,
    canSearchVisits ? { icon: 'bx-map-alt', title: 'Site Interaction', description: 'Website visits and customer activity.', to: '/app/site-interactions' } : null,
    canSearchUsers ? { icon: 'bx-lock-open-alt', title: 'Users', description: 'Team accounts and menu access.', to: '/app/users' } : null
  ].filter(Boolean);

  return (
    <div className="sneat-module-page admin-dashboard-page">
      <section className="dashboard-search-strip">
        <div>
          <span className="sneat-eyebrow">Search</span>
          <h2>Find anything in Krishnai admin</h2>
        </div>
        <form className="dashboard-search-field" onSubmit={submitDashboardSearch}>
          <i className="bx bx-search" />
          <input
            type="search"
            value={dashboardSearch}
            onChange={(event) => setDashboardSearch(event.target.value)}
            placeholder="Search products, customers, invoices, campaigns..."
          />
          <button type="submit">Open</button>
          {dashboardSearch.trim().length >= 2 ? (
            <div className="dashboard-search-results" role="listbox" aria-label="Admin search results">
              {searchLoading ? <span className="dashboard-search-state">Searching admin records...</span> : null}
              {!searchLoading && searchOptions.length ? searchOptions.map((option) => (
                <button key={option.key} type="button" onClick={() => openSearchOption(option)} title={`Open ${option.type}: ${option.title}`}>
                  <i className={`bx ${option.icon}`} />
                  <span>
                    <strong>{option.title}</strong>
                    <small>{option.type} · {option.subtitle}</small>
                  </span>
                  <i className="bx bx-chevron-right" />
                </button>
              )) : null}
              {!searchLoading && !searchOptions.length ? <span className="dashboard-search-state">No exact match yet. Press Open to use your first available module.</span> : null}
            </div>
          ) : null}
        </form>
        <div className="dashboard-search-shortcuts" aria-label="Quick admin search destinations">
          {dashboardSearchTargets.slice(0, 8).map((target) => (
            <button
              key={target.to}
              type="button"
              title={`Open ${target.keywords[0]}`}
              onClick={() => openSearchTarget(target)}
            >
              {target.keywords[0]}
            </button>
          ))}
        </div>
      </section>

      <section className="sneat-dashboard-top">
        <article className="sneat-card sneat-congrats-card">
          <div>
            <span className="sneat-eyebrow">Central Dashboard</span>
            <h1>Congratulations {auth?.displayName || 'Admin'}!</h1>
            <p>{canViewAnalytics
              ? 'Your Sneat admin workspace is connected to protected KPS analytics for customers, visits, orders, revenue, and stock signals.'
              : 'Your workspace shows the modules assigned to your account. Ask an admin if another section is needed.'}</p>
            <div className="sneat-hero-actions">
              {canSearchBilling ? <Link className="btn btn-primary" to="/app/billing"><i className="bx bx-receipt me-1" /> New sale</Link> : null}
              {canSearchCustomers ? <Link className="btn btn-outline-primary" to="/app/crm/dashboard"><i className="bx bx-user-voice me-1" /> Open CRM</Link> : null}
            </div>
          </div>
          <span className="sneat-trophy-mark"><i className="bx bx-trophy" /></span>
        </article>
        {canViewAnalytics ? <article className="sneat-card sneat-activity-card">
          <div className="sneat-card-head">
            <div><small>Customer Visits</small><h3>{analyticsValue(analytics?.customerVisits)}</h3></div>
            <span className="text-muted">This month</span>
          </div>
          <MiniBars values={weeklyBars} />
        </article> : null}
        {canViewAnalytics ? <article className="sneat-card sneat-activity-card">
          <div className="sneat-card-head">
            <div><small>Order Health</small><h3>{analyticsValue(analytics?.completedOrders)}/{analyticsValue(analytics?.totalOrders)}</h3></div>
            <span className="text-muted">Live</span>
          </div>
          <Sparkline />
        </article> : null}
      </section>

      {loading && canViewAnalytics ? <AnalyticsSkeleton /> : null}
      {error ? <div className="sneat-card dashboard-error-state"><i className="bx bx-error-circle" /><div><strong>Analytics unavailable</strong><span>{error}</span></div></div> : null}

      {!loading && !error && canViewAnalytics ? (
      <section className="sneat-stat-grid dashboard-analytics-grid">
        <DashboardMetric icon="bx-group" label="Total customers" value={analyticsValue(analytics?.totalCustomers)} note="Live customer records" trend={analytics?.customerGrowth} tone="info" to="/app/crm/customers/overview" />
        <DashboardMetric icon="bx-show" label="Customer visits" value={analyticsValue(analytics?.customerVisits)} note="Current month visits" trend={analytics?.visitGrowth} to="/app/site-interactions" />
        <DashboardMetric icon="bx-rupee" label="Total sales" value={currency(analyticsValue(analytics?.totalSales))} note="All captured revenue" trend={analytics?.salesGrowth} tone="success" to="/app/reports/sales" />
        <DashboardMetric icon="bx-cart" label="Total orders" value={analyticsValue(analytics?.totalOrders)} note="Shop and website" trend={analytics?.orderGrowth} tone="warning" to="/app/billing/orders" />
        <DashboardMetric icon="bx-time-five" label="Pending orders" value={analyticsValue(analytics?.pendingOrders)} note="Need action" tone="warning" to="/app/billing/orders?status=PENDING" />
        <DashboardMetric icon="bx-check-circle" label="Completed orders" value={analyticsValue(analytics?.completedOrders)} note="Fulfilled/sold" tone="success" to="/app/billing/orders?status=COMPLETED" />
        <DashboardMetric icon="bx-x-circle" label="Cancelled orders" value={analyticsValue(analytics?.cancelledOrders)} note="Cancelled/failed" tone="danger" to="/app/billing/orders?status=CANCELLED" />
        <DashboardMetric icon="bx-wallet" label="Revenue today" value={currency(analyticsValue(analytics?.revenueToday))} note="Compared with yesterday" trend={analytics?.todayRevenueGrowth} tone="success" to="/app/reports/sales" />
        <DashboardMetric icon="bx-calendar-star" label="Revenue this month" value={currency(analyticsValue(analytics?.revenueThisMonth))} note="Compared with last month" trend={analytics?.monthRevenueGrowth} to="/app/reports/sales" />
        <DashboardMetric icon="bx-error" label="Low-stock products" value={analyticsValue(analytics?.lowStockProducts)} note="At or below alert" tone="danger" to="/app/reports/low-stock" />
      </section>
      ) : null}

      <section className="sneat-crm-dashboard-grid">
        <article className="sneat-card span-2">
          <div className="sneat-card-head">
            <div><small>Operations</small><h3>Available modules</h3></div>
          </div>
          <div className="dashboard-module-grid">
            {accessibleModules.map((module) => <QuickModule key={module.to} {...module} />)}
            {!accessibleModules.length ? <p className="dashboard-empty-state">No modules are assigned to this user yet.</p> : null}
          </div>
        </article>
        {canViewAnalytics ? <article className="sneat-card">
          <div className="sneat-card-head">
            <div><small>Growth</small><h3>Campaign pulse</h3></div>
            <span className="badge bg-label-primary">{analyticsValue(analytics?.totalOrders)} orders</span>
          </div>
          <div className="sneat-score-ring">{analyticsValue(analytics?.totalCustomers)}</div>
          <ProgressRow label="Completed orders" value={Math.min(100, Math.round((analyticsValue(analytics?.completedOrders) / Math.max(analyticsValue(analytics?.totalOrders), 1)) * 100))} />
          <ProgressRow label="Stock readiness" value={Math.max(0, 100 - Number(analytics?.lowStockProducts || 0) * 8)} tone="success" />
        </article> : null}
        {canViewAnalytics ? <article className="sneat-card">
          <div className="sneat-card-head">
            <div><small>Sales</small><h3>Top-selling products</h3></div>
            <Link to="/app/inventory/products" className="btn btn-sm btn-outline-primary">View all</Link>
          </div>
          <div className="sneat-list">
            {topProducts.length ? topProducts.map((product) => (
              <DashboardListRow
                key={product.productId || product.sku || product.name}
                avatar={String(product.name || 'P').slice(0, 1).toUpperCase()}
                title={product.name || 'Unnamed product'}
                subtitle={`${Number(product.quantitySold || 0)} sold - ${currency(product.revenue || 0)}`}
                badge={product.sku || product.category || 'Product'}
                tone="primary"
              />
            )) : <p className="dashboard-empty-state">No top-selling product data yet.</p>}
          </div>
        </article> : null}
        {canViewAnalytics ? <article className="sneat-card">
          <div className="sneat-card-head">
            <div><small>Orders</small><h3>Recent orders</h3></div>
            <Link to="/app/billing/orders" className="btn btn-sm btn-outline-primary">Open orders</Link>
          </div>
          <div className="sneat-list">
            {recentOrders.length ? recentOrders.map((order) => (
              <DashboardListRow
                key={`${order.source}-${order.id}`}
                avatar={String(order.customerName || order.referenceNumber || 'O').slice(0, 1).toUpperCase()}
                title={order.referenceNumber || 'Order'}
                subtitle={`${order.customerName || order.customerMobile || 'Customer'} - ${formatOrderDate(order.createdAt)}`}
                badge={currency(order.finalAmount || 0)}
                tone={order.status === 'CANCELLED' || order.status === 'PAYMENT_FAILED' ? 'warning' : 'info'}
              />
            )) : <p className="dashboard-empty-state">No recent orders yet.</p>}
          </div>
        </article> : null}
        {canViewAnalytics ? <article className="sneat-card">
          <div className="sneat-card-head">
            <div><small>Reports</small><h3>Business health</h3></div>
            <Link to="/app/reports/dashboard" className="btn btn-sm btn-outline-primary">Reports</Link>
          </div>
          <ProgressRow label="Sales capture" value={Math.min(100, Number(analytics?.totalOrders || 0) * 4)} />
          <ProgressRow label="Stock readiness" value={Math.max(0, 100 - Number(analytics?.lowStockProducts || 0) * 12)} tone="warning" />
          <ProgressRow label="Revenue momentum" value={Math.max(15, Math.min(100, 50 + Number(analytics?.monthRevenueGrowth?.percentage || 0)))} tone="success" />
        </article> : null}
        <article className="sneat-card">
          <div className="sneat-card-head">
            <div><small>Team</small><h3>Active users</h3></div>
            {canSearchUsers ? <Link to="/app/users" className="btn btn-sm btn-outline-primary">Users</Link> : null}
          </div>
          <div className="sneat-list">
            <DashboardListRow
              avatar={String(auth?.displayName || 'A').slice(0, 1).toUpperCase()}
              title={auth?.displayName || 'Signed-in admin'}
              subtitle={auth?.role || 'Current session'}
              badge="Online"
              tone="success"
            />
            <DashboardListRow
              avatar="R"
              title="Reports access"
              subtitle="Protected by admin/dashboard permissions"
              badge="Secure"
              tone="primary"
            />
          </div>
        </article>
      </section>
    </div>
  );
}
