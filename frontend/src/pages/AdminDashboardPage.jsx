import { Link } from 'react-router-dom';
import { useEffect, useMemo, useState } from 'react';
import { retailService } from '../services/retailService';
import { currency } from '../utils/format';

function DashboardMetric({ icon, label, value, note, tone = 'primary', trend }) {
  return (
    <article className={`sneat-stat-card is-${tone}`}>
      <span className="sneat-stat-icon"><i className={`bx ${icon}`} /></span>
      <div>
        <small>{label}</small>
        <strong>{value}</strong>
        {note ? <span>{note}</span> : null}
        {trend ? <em className={trend.startsWith('+') ? 'text-success' : 'text-danger'}>{trend}</em> : null}
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

export default function AdminDashboardPage({ branding, auth }) {
  const [daily, setDaily] = useState(null);
  const [productsPage, setProductsPage] = useState({ items: [], totalItems: 0 });
  const [customersPage, setCustomersPage] = useState({ items: [], totalItems: 0 });
  const [supportSummary, setSupportSummary] = useState({ openCount: 0, unreadCount: 0 });
  const [campaignsPage, setCampaignsPage] = useState({ items: [], totalItems: 0 });
  const [usersPage, setUsersPage] = useState({ items: [], totalItems: 0 });

  useEffect(() => {
    const today = new Date().toISOString().slice(0, 10);
    Promise.allSettled([
      retailService.getDailyReport({ fromDate: today, toDate: today }),
      retailService.getProducts({ page: 0, size: 6 }),
      retailService.getCustomers({ page: 0, size: 6 }),
      retailService.getSupportSummary(),
      retailService.getMarketingCampaigns({ page: 0, size: 6 }),
      retailService.getUsers({ page: 0, size: 5 })
    ]).then(([dailyResult, productsResult, customersResult, supportResult, campaignsResult, usersResult]) => {
      if (dailyResult.status === 'fulfilled') setDaily(dailyResult.value);
      if (productsResult.status === 'fulfilled') setProductsPage(productsResult.value || { items: [], totalItems: 0 });
      if (customersResult.status === 'fulfilled') setCustomersPage(customersResult.value || { items: [], totalItems: 0 });
      if (supportResult.status === 'fulfilled') setSupportSummary(supportResult.value || { openCount: 0, unreadCount: 0 });
      if (campaignsResult.status === 'fulfilled') setCampaignsPage(campaignsResult.value || { items: [], totalItems: 0 });
      if (usersResult.status === 'fulfilled') setUsersPage(usersResult.value || { items: [], totalItems: 0 });
    });
  }, []);

  const lowStockCount = useMemo(
    () => (productsPage.items || []).filter((product) => Number(product.quantity || 0) <= Number(product.lowStockThreshold || 0)).length,
    [productsPage.items]
  );
  const stockValue = useMemo(
    () => (productsPage.items || []).reduce((total, product) => total + (Number(product.sellingPrice || product.price || 0) * Number(product.quantity || 0)), 0),
    [productsPage.items]
  );
  const newCustomers = useMemo(
    () => (customersPage.items || []).filter((customer) => String(customer.segment || customer.customerType || '').toLowerCase().includes('new')).length,
    [customersPage.items]
  );
  const activeCampaigns = useMemo(
    () => (campaignsPage.items || []).filter((campaign) => String(campaign.status || '').toUpperCase() !== 'ARCHIVED').length,
    [campaignsPage.items]
  );
  const topProducts = (productsPage.items || []).slice(0, 5);
  const recentCustomers = (customersPage.items || []).slice(0, 5);
  const team = (usersPage.items || []).slice(0, 4);
  const weeklyBars = [
    Number(daily?.ordersInRange || daily?.invoiceCount || 1) * 12,
    Number(productsPage.totalItems || productsPage.items?.length || 1),
    Number(customersPage.totalItems || customersPage.items?.length || 1),
    Number(supportSummary.openCount || 1) * 18,
    Number(activeCampaigns || 1) * 22,
    Math.max(30, Math.min(96, Number(daily?.salesTotal || daily?.totalSales || 0) / 100)),
    Math.max(22, lowStockCount * 18)
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
            <p>Your Sneat admin workspace is connected to live KPS APIs for sales, products, customers, support, and campaigns.</p>
            <div className="sneat-hero-actions">
              <Link className="btn btn-primary" to="/app/billing"><i className="bx bx-receipt me-1" /> New sale</Link>
              <Link className="btn btn-outline-primary" to="/app/crm/dashboard"><i className="bx bx-user-voice me-1" /> Open CRM</Link>
            </div>
          </div>
          <span className="sneat-trophy-mark"><i className="bx bx-trophy" /></span>
        </article>
        <article className="sneat-card sneat-activity-card">
          <div className="sneat-card-head">
            <div><small>New Visitors</small><h3>{customersPage.totalItems || customersPage.items?.length || 0}</h3></div>
            <span className="text-muted">This week</span>
          </div>
          <MiniBars values={weeklyBars} />
        </article>
        <article className="sneat-card sneat-activity-card">
          <div className="sneat-card-head">
            <div><small>Activity</small><h3>{Math.min(98, 60 + Number(activeCampaigns || 0) + Number(supportSummary.openCount || 0))}%</h3></div>
            <span className="text-muted">Live</span>
          </div>
          <Sparkline />
        </article>
      </section>

      <section className="sneat-stat-grid">
        <DashboardMetric icon="bx-rupee" label="Sales today" value={currency(daily?.salesTotal || daily?.totalSales || 0)} note={`${daily?.ordersInRange || daily?.invoiceCount || 0} orders`} trend="+ live" />
        <DashboardMetric icon="bx-package" label="Inventory value" value={currency(stockValue)} note={`${productsPage.totalItems || productsPage.items?.length || 0} products`} tone="warning" />
        <DashboardMetric icon="bx-group" label="Customers" value={customersPage.totalItems || customersPage.items?.length || 0} note={`${newCustomers} new loaded`} tone="info" />
        <DashboardMetric icon="bx-conversation" label="Support" value={supportSummary.openCount || 0} note={`${supportSummary.unreadCount || 0} unread`} tone="success" />
      </section>

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
            <span className="badge bg-label-primary">{campaignsPage.totalItems || campaignsPage.items?.length || 0} campaigns</span>
          </div>
          <div className="sneat-score-ring">{activeCampaigns}</div>
          <ProgressRow label="Audience readiness" value={Math.min(100, 48 + newCustomers * 8)} />
          <ProgressRow label="Automation coverage" value={Math.min(100, 55 + activeCampaigns * 10)} tone="success" />
        </article>
        <article className="sneat-card">
          <div className="sneat-card-head">
            <div><small>Inventory</small><h3>Top stock signals</h3></div>
            <Link to="/app/inventory/products" className="btn btn-sm btn-outline-primary">View all</Link>
          </div>
          <div className="sneat-list">
            {topProducts.length ? topProducts.map((product) => (
              <DashboardListRow
                key={product.id || product.sku || product.name}
                avatar={String(product.name || 'P').slice(0, 1).toUpperCase()}
                title={product.name || 'Unnamed product'}
                subtitle={`${Number(product.quantity || 0)} in stock - ${currency(product.sellingPrice || product.price || 0)}`}
                badge={Number(product.quantity || 0) <= Number(product.lowStockThreshold || 0) ? 'Low stock' : 'Active'}
                tone={Number(product.quantity || 0) <= Number(product.lowStockThreshold || 0) ? 'warning' : 'primary'}
              />
            )) : <p className="text-muted mb-0">No product records loaded yet.</p>}
          </div>
        </article>
        <article className="sneat-card">
          <div className="sneat-card-head">
            <div><small>Customers</small><h3>Recent CRM records</h3></div>
            <Link to="/app/crm/customers" className="btn btn-sm btn-outline-primary">Open CRM</Link>
          </div>
          <div className="sneat-list">
            {recentCustomers.length ? recentCustomers.map((customer) => (
              <DashboardListRow
                key={customer.id || customer.mobile || customer.name}
                avatar={String(customer.name || customer.mobile || 'C').slice(0, 1).toUpperCase()}
                title={customer.name || 'Customer'}
                subtitle={`${customer.mobile || 'No mobile'} - ${customer.segment || customer.customerType || 'Retail customer'}`}
                badge={customer.city || 'CRM'}
                tone="info"
              />
            )) : <p className="text-muted mb-0">No customer records loaded yet.</p>}
          </div>
        </article>
        <article className="sneat-card">
          <div className="sneat-card-head">
            <div><small>Reports</small><h3>Business health</h3></div>
            <Link to="/app/reports/dashboard" className="btn btn-sm btn-outline-primary">Reports</Link>
          </div>
          <ProgressRow label="Sales capture" value={Math.min(100, Number(daily?.ordersInRange || daily?.invoiceCount || 0) * 12)} />
          <ProgressRow label="Stock readiness" value={Math.max(0, 100 - lowStockCount * 12)} tone="warning" />
          <ProgressRow label="Support response" value={Math.max(15, 100 - Number(supportSummary.unreadCount || 0) * 10)} tone="success" />
        </article>
        <article className="sneat-card">
          <div className="sneat-card-head">
            <div><small>Team</small><h3>Active users</h3></div>
            <Link to="/app/users" className="btn btn-sm btn-outline-primary">Users</Link>
          </div>
          <div className="sneat-list">
            {team.length ? team.map((user) => (
              <DashboardListRow
                key={user.id || user.username || user.mobile}
                avatar={String(user.displayName || user.username || 'U').slice(0, 1).toUpperCase()}
                title={user.displayName || user.username || 'Team user'}
                subtitle={user.role || 'Store role'}
                badge={user.enabled === false ? 'Disabled' : 'Active'}
                tone={user.enabled === false ? 'warning' : 'success'}
              />
            )) : (
              <DashboardListRow
                avatar={String(auth?.displayName || 'A').slice(0, 1).toUpperCase()}
                title={auth?.displayName || 'Signed-in admin'}
                subtitle={auth?.role || 'Current session'}
                badge="Online"
                tone="success"
              />
            )}
          </div>
        </article>
      </section>
    </div>
  );
}
