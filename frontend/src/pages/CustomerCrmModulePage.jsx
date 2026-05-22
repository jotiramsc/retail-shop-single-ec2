import { Link } from 'react-router-dom';
import { useEffect, useMemo, useState } from 'react';
import CustomersPage from './CustomersPage';
import { retailService } from '../services/retailService';
import { currency, formatDate } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

const screenMeta = {
  dashboard: {
    eyebrow: 'Customer Intelligence',
    title: 'CRM Dashboard',
    description: 'A Sneat-style command center for customers, activity, revenue signals, and AI-assisted selling.'
  },
  leads: {
    eyebrow: 'Pipeline',
    title: 'Leads',
    description: 'Track new collectors, offer-interested customers, and warm customer signals from live customer data.'
  },
  opportunities: {
    eyebrow: 'Revenue',
    title: 'Opportunities',
    description: 'Focus on high-value customers, repeat buyers, wishlist intent, and offer opportunities.'
  },
  activities: {
    eyebrow: 'Customer Signals',
    title: 'Activities',
    description: 'A routed activity workspace for login, search, product, chat, and purchase signals.'
  },
  reports: {
    eyebrow: 'Insights',
    title: 'CRM Reports',
    description: 'Customer health, engagement, segment mix, lifetime value, and operational snapshots.'
  },
  settings: {
    eyebrow: 'Configuration',
    title: 'CRM Settings',
    description: 'Control customer intelligence defaults, tracking visibility, onboarding, and support handoff behavior.'
  }
};

function statValue(value, fallback = 0) {
  return value == null || value === '' ? fallback : value;
}

function customerInitial(customer) {
  return String(customer?.name || customer?.mobile || '?').slice(0, 1).toUpperCase();
}

function SneatHero({ meta, actions }) {
  return (
    <section className="sneat-hero kps-jewelry-hero">
      <div>
        <span className="sneat-eyebrow">{meta.eyebrow}</span>
        <h1>{meta.title}</h1>
        <p>{meta.description}</p>
      </div>
      {actions ? <div className="sneat-hero-actions">{actions}</div> : null}
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

function CustomerRow({ customer }) {
  return (
    <div className="sneat-list-row">
      <span className="sneat-avatar">{customerInitial(customer)}</span>
      <div>
        <strong>{customer.name || 'Unnamed customer'}</strong>
        <small>{customer.mobile || customer.email || 'No contact captured'}</small>
      </div>
      <span className="badge bg-label-primary">{customer.segment || customer.segments?.[0] || 'Customer'}</span>
    </div>
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
        setError(getApiErrorMessage(customersResult.reason, 'Unable to load customer CRM data.'));
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
  const newLeads = useMemo(
    () => customers.filter((customer) => (customer.segments || []).some((segment) => /new|offer|interested|viewer/i.test(segment))),
    [customers]
  );
  const totalLtv = customers.reduce((sum, customer) => sum + Number(customer.totalSpent || 0), 0);

  const stats = (
    <section className="sneat-stat-grid">
      <StatCard icon="bx-group" label="Customers" value={statValue(customersPage.totalItems, customers.length)} note={loading ? 'Loading live API' : 'Live customer records'} />
      <StatCard icon="bx-crown" label="High value" value={highValueCustomers.length} note="Revenue signals" tone="success" />
      <StatCard icon="bx-conversation" label="Open support" value={statValue(supportSummary?.openCount, 0)} note={`${statValue(supportSummary?.unreadCount, 0)} unread`} tone="info" />
      <StatCard icon="bx-rupee" label="Visible LTV" value={currency(totalLtv)} note="Current page sample" tone="warning" />
    </section>
  );

  if (screen === 'customers') {
    return (
      <div className="sneat-module-page">
        <SneatHero
          meta={{ eyebrow: 'Customer Intelligence', title: 'Customers', description: 'Search, segment, select, and inspect customer 360 records using live CRM APIs.' }}
          actions={<Link className="btn btn-primary" to="/app/crm/dashboard"><i className="bx bx-grid-alt me-1" /> CRM Dashboard</Link>}
        />
        <CustomersPage hidePageHeader hideInternalTabs initialTab={detailTab} />
      </div>
    );
  }

  const renderScreen = () => {
    if (screen === 'leads') {
      return (
        <section className="sneat-two-column">
          <article className="sneat-card">
            <div className="sneat-card-head">
              <div><small>Lead queue</small><h3>New and warm customers</h3></div>
              <span className="badge bg-label-success">{newLeads.length} leads</span>
            </div>
            <div className="sneat-list">
              {(newLeads.length ? newLeads : customers.slice(0, 6)).map((customer) => <CustomerRow key={customer.id || customer.mobile} customer={customer} />)}
              {!customers.length ? <EmptyCard title="No leads loaded" description="Lead data will appear here when customers are returned by the live API." /> : null}
            </div>
          </article>
          <article className="sneat-card">
            <div className="sneat-card-head">
              <div><small>Lead source</small><h3>Qualification checklist</h3></div>
            </div>
            {['Mobile captured', 'Profile enrichment pending', 'Offer interest visible', 'Support handoff available'].map((item) => (
              <div className="sneat-check-row" key={item}><i className="bx bx-check-circle" />{item}</div>
            ))}
          </article>
        </section>
      );
    }

    if (screen === 'opportunities') {
      return (
        <section className="sneat-grid-3">
          {(highValueCustomers.length ? highValueCustomers : customers.slice(0, 6)).map((customer) => (
            <article className="sneat-card opportunity-card" key={customer.id || customer.mobile}>
              <span className="sneat-avatar lg">{customerInitial(customer)}</span>
              <h3>{customer.name || 'Unnamed customer'}</h3>
              <p>{customer.mobile || customer.email || 'Contact not captured'}</p>
              <div className="sneat-chip-row">
                <span>{currency(customer.totalSpent || 0)} LTV</span>
                <span>{customer.lastActiveAt ? formatDate(customer.lastActiveAt) : 'Recent status pending'}</span>
              </div>
            </article>
          ))}
          {!customers.length ? <EmptyCard title="No opportunities yet" description="Opportunities are computed from customer revenue and segment signals." icon="bx-line-chart" /> : null}
        </section>
      );
    }

    if (screen === 'activities') {
      return (
        <section className="sneat-card">
          <div className="sneat-card-head">
            <div><small>Timeline</small><h3>Customer activity stream</h3></div>
            <Link className="btn btn-outline-primary btn-sm" to="/app/crm/customers/overview">Open customer 360</Link>
          </div>
          <div className="sneat-timeline">
            {customers.slice(0, 7).map((customer, index) => (
              <div className="sneat-timeline-item" key={customer.id || customer.mobile}>
                <span />
                <div>
                  <strong>{customer.name || customer.mobile || 'Customer'} activity ready</strong>
                  <p>{index % 2 === 0 ? 'Search/login intelligence available in customer details.' : 'Support and order signals connected through live APIs.'}</p>
                </div>
              </div>
            ))}
            {!customers.length ? <EmptyCard title="No recent activity" description="Activity events will populate from login, search, product, chat, and order tracking." icon="bx-pulse" /> : null}
          </div>
        </section>
      );
    }

    if (screen === 'reports') {
      return (
        <section className="sneat-two-column">
          <article className="sneat-card">
            <div className="sneat-card-head"><div><small>Customer health</small><h3>Segment mix</h3></div></div>
            {['New Customer', 'Returning Customer', 'High Value Customer', 'Offer Interested Customer'].map((label, index) => (
              <div className="sneat-progress-row" key={label}>
                <span>{label}</span>
                <strong>{customers.filter((customer) => (customer.segments || []).includes(label)).length}</strong>
                <div><span style={{ width: `${Math.max(12, 76 - index * 14)}%` }} /></div>
              </div>
            ))}
          </article>
          <article className="sneat-card">
            <div className="sneat-card-head"><div><small>Revenue</small><h3>LTV snapshot</h3></div></div>
            <div className="sneat-big-metric">{currency(totalLtv)}</div>
            <p className="text-muted mb-0">This report is built from the same live customer endpoint and keeps the existing backend contract unchanged.</p>
          </article>
        </section>
      );
    }

    if (screen === 'settings') {
      return (
        <section className="sneat-grid-3">
          {[
            ['Conversational onboarding', 'Birthday, anniversary, language, budget, and preferences are collected through chat-first customer 360.'],
            ['Activity tracking', 'Login, search, product view, cart, offer, chat, and order events remain connected to existing APIs.'],
            ['Permissions', 'CRM access continues to use the current authentication, authorization, and RBAC rules.']
          ].map(([title, description]) => (
            <article className="sneat-card" key={title}>
              <span className="sneat-stat-icon"><i className="bx bx-slider-alt" /></span>
              <h3>{title}</h3>
              <p>{description}</p>
            </article>
          ))}
        </section>
      );
    }

    return (
      <section className="sneat-dashboard-grid">
        <article className="sneat-card span-2">
          <div className="sneat-card-head">
            <div><small>Customer list</small><h3>Fast customer visibility</h3></div>
            <Link className="btn btn-primary btn-sm" to="/app/crm/customers">Open customers</Link>
          </div>
          <div className="sneat-list">
            {customers.slice(0, 6).map((customer) => <CustomerRow key={customer.id || customer.mobile} customer={customer} />)}
            {!customers.length ? <EmptyCard title="No customers loaded" description="The dashboard is connected to the customer API and will fill when data is available." /> : null}
          </div>
        </article>
        <article className="sneat-card">
          <div className="sneat-card-head"><div><small>AI readiness</small><h3>Customer intelligence</h3></div></div>
          <div className="sneat-score-ring">82%</div>
          <p>Profile, support, and behavior signals are routed into dedicated CRM workspaces.</p>
        </article>
      </section>
    );
  };

  return (
    <div className="sneat-module-page">
      <SneatHero
        meta={meta}
        actions={<Link className="btn btn-primary" to="/app/crm/customers"><i className="bx bx-user me-1" /> Open Customers</Link>}
      />
      {error ? <div className="alert alert-danger">{error}</div> : null}
      {stats}
      {renderScreen()}
    </div>
  );
}
