import { Link } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { retailService } from '../services/retailService';
import { currency } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

function DashboardMetric({ icon, label, value, note, tone = 'primary', trend }) {
  const trendValue = trend?.percentage ?? trend;
  const direction = trend?.direction || (String(trendValue || '').startsWith('-') ? 'down' : 'up');
  return (
    <article className={`sneat-stat-card is-${tone}`}>
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

function formatOrderDate(value) {
  if (!value) return 'No date';
  return new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}

export default function AdminDashboardPage({ branding, auth }) {
  const [analytics, setAnalytics] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
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
  }, []);

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

  return (
    <div className="sneat-module-page admin-dashboard-page">
      <section className="dashboard-search-strip">
        <div>
          <span className="sneat-eyebrow">Search</span>
          <h2>Find anything in Krishnai admin</h2>
        </div>
        <label className="dashboard-search-field">
          <i className="bx bx-search" />
          <input type="search" placeholder="Search products, customers, invoices, campaigns..." />
        </label>
      </section>

      <section className="sneat-dashboard-top">
        <article className="sneat-card sneat-congrats-card">
          <div>
            <span className="sneat-eyebrow">Central Dashboard</span>
            <h1>Congratulations {auth?.displayName || 'Admin'}!</h1>
            <p>Your Sneat admin workspace is connected to protected KPS analytics for customers, visits, orders, revenue, and stock signals.</p>
            <div className="sneat-hero-actions">
              <Link className="btn btn-primary" to="/app/billing"><i className="bx bx-receipt me-1" /> New sale</Link>
              <Link className="btn btn-outline-primary" to="/app/crm/dashboard"><i className="bx bx-user-voice me-1" /> Open CRM</Link>
            </div>
          </div>
          <span className="sneat-trophy-mark"><i className="bx bx-trophy" /></span>
        </article>
        <article className="sneat-card sneat-activity-card">
          <div className="sneat-card-head">
            <div><small>Customer Visits</small><h3>{analyticsValue(analytics?.customerVisits)}</h3></div>
            <span className="text-muted">This month</span>
          </div>
          <MiniBars values={weeklyBars} />
        </article>
        <article className="sneat-card sneat-activity-card">
          <div className="sneat-card-head">
            <div><small>Order Health</small><h3>{analyticsValue(analytics?.completedOrders)}/{analyticsValue(analytics?.totalOrders)}</h3></div>
            <span className="text-muted">Live</span>
          </div>
          <Sparkline />
        </article>
      </section>

      {loading ? <AnalyticsSkeleton /> : null}
      {error ? <div className="sneat-card dashboard-error-state"><i className="bx bx-error-circle" /><div><strong>Analytics unavailable</strong><span>{error}</span></div></div> : null}

      {!loading && !error ? (
      <section className="sneat-stat-grid dashboard-analytics-grid">
        <DashboardMetric icon="bx-group" label="Total customers" value={analyticsValue(analytics?.totalCustomers)} note="Live customer records" trend={analytics?.customerGrowth} tone="info" />
        <DashboardMetric icon="bx-show" label="Customer visits" value={analyticsValue(analytics?.customerVisits)} note="Current month visits" trend={analytics?.visitGrowth} />
        <DashboardMetric icon="bx-rupee" label="Total sales" value={currency(analyticsValue(analytics?.totalSales))} note="All captured revenue" trend={analytics?.salesGrowth} tone="success" />
        <DashboardMetric icon="bx-cart" label="Total orders" value={analyticsValue(analytics?.totalOrders)} note="Shop and website" trend={analytics?.orderGrowth} tone="warning" />
        <DashboardMetric icon="bx-time-five" label="Pending orders" value={analyticsValue(analytics?.pendingOrders)} note="Need action" tone="warning" />
        <DashboardMetric icon="bx-check-circle" label="Completed orders" value={analyticsValue(analytics?.completedOrders)} note="Fulfilled/sold" tone="success" />
        <DashboardMetric icon="bx-x-circle" label="Cancelled orders" value={analyticsValue(analytics?.cancelledOrders)} note="Cancelled/failed" tone="danger" />
        <DashboardMetric icon="bx-wallet" label="Revenue today" value={currency(analyticsValue(analytics?.revenueToday))} note="Compared with yesterday" trend={analytics?.todayRevenueGrowth} tone="success" />
        <DashboardMetric icon="bx-calendar-star" label="Revenue this month" value={currency(analyticsValue(analytics?.revenueThisMonth))} note="Compared with last month" trend={analytics?.monthRevenueGrowth} />
        <DashboardMetric icon="bx-error" label="Low-stock products" value={analyticsValue(analytics?.lowStockProducts)} note="At or below alert" tone="danger" />
      </section>
      ) : null}

      <section className="sneat-crm-dashboard-grid">
        <article className="sneat-card span-2">
          <div className="sneat-card-head">
            <div><small>Operations</small><h3>Admin modules</h3></div>
          </div>
          <div className="dashboard-module-grid">
            <QuickModule icon="bx-package" title="Inventory" description="Products, categories, collections, and brands." to="/app/inventory/products" />
            <QuickModule icon="bx-user-voice" title="Customer CRM" description="Dashboard, customers, leads, activity, reports, and settings." to="/app/crm/dashboard" />
            <QuickModule icon="bx-conversation" title="Support Inbox" description="Active chats, archived chats, and product sender." to="/app/support/active" />
            <QuickModule icon="bx-broadcast" title="Campaign Studio" description="Campaign dashboard, templates, audience, analytics, scheduler, reports, automation." to="/app/campaigns/dashboard" />
          </div>
        </article>
        <article className="sneat-card">
          <div className="sneat-card-head">
            <div><small>Growth</small><h3>Campaign pulse</h3></div>
            <span className="badge bg-label-primary">{analyticsValue(analytics?.totalOrders)} orders</span>
          </div>
          <div className="sneat-score-ring">{analyticsValue(analytics?.totalCustomers)}</div>
          <ProgressRow label="Completed orders" value={Math.min(100, Math.round((analyticsValue(analytics?.completedOrders) / Math.max(analyticsValue(analytics?.totalOrders), 1)) * 100))} />
          <ProgressRow label="Stock readiness" value={Math.max(0, 100 - Number(analytics?.lowStockProducts || 0) * 8)} tone="success" />
        </article>
        <article className="sneat-card">
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
        </article>
        <article className="sneat-card">
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
        </article>
        <article className="sneat-card">
          <div className="sneat-card-head">
            <div><small>Reports</small><h3>Business health</h3></div>
            <Link to="/app/reports/dashboard" className="btn btn-sm btn-outline-primary">Reports</Link>
          </div>
          <ProgressRow label="Sales capture" value={Math.min(100, Number(analytics?.totalOrders || 0) * 4)} />
          <ProgressRow label="Stock readiness" value={Math.max(0, 100 - Number(analytics?.lowStockProducts || 0) * 12)} tone="warning" />
          <ProgressRow label="Revenue momentum" value={Math.max(15, Math.min(100, 50 + Number(analytics?.monthRevenueGrowth?.percentage || 0)))} tone="success" />
        </article>
        <article className="sneat-card">
          <div className="sneat-card-head">
            <div><small>Team</small><h3>Active users</h3></div>
            <Link to="/app/users" className="btn btn-sm btn-outline-primary">Users</Link>
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
