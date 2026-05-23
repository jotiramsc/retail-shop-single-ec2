import { Link } from 'react-router-dom';
import { useEffect, useMemo, useState } from 'react';
import CampaignsPage from './CampaignsPage';
import { retailService } from '../services/retailService';
import { formatDate } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

const screenMeta = {
  dashboard: {
    eyebrow: 'Growth',
    title: 'Campaign Dashboard',
    description: 'A Sneat-native control room for social campaigns, offers, approvals, schedules, and AI creatives.'
  },
  templates: {
    eyebrow: 'Creative Library',
    title: 'Templates',
    description: 'Use live marketing suggestions as reusable campaign starting points for KPS jewellery and cosmetics promotions.'
  },
  audience: {
    eyebrow: 'Targeting',
    title: 'Audience',
    description: 'Segment customers for campaigns while preserving the existing customer and marketing API contracts.'
  },
  reports: {
    eyebrow: 'Measurement',
    title: 'Campaign Reports',
    description: 'Review campaign status, approval health, publishing readiness, and growth operations.'
  },
  list: {
    eyebrow: 'Campaigns',
    title: 'Campaign List',
    description: 'Manage existing campaigns as a dedicated routed Sneat screen backed by the current marketing APIs.'
  },
  create: {
    eyebrow: 'Create',
    title: 'Create Campaign',
    description: 'Launch campaign creation without tab switching while preserving the existing AI generation workflow.'
  },
  offers: {
    eyebrow: 'Offers',
    title: 'Offers',
    description: 'Create and manage offers from a separate campaign submenu screen.'
  },
  approval: {
    eyebrow: 'Approval',
    title: 'Approval Queue',
    description: 'Review campaign creatives, approvals, and publishing readiness in a dedicated routed module.'
  }
};

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

function CampaignRow({ campaign }) {
  return (
    <div className="sneat-list-row">
      <span className="sneat-stat-icon mini"><i className="bx bx-broadcast" /></span>
      <div>
        <strong>{campaign.campaignName || campaign.name || 'Untitled campaign'}</strong>
        <small>{campaign.campaignType || 'Campaign'} · {campaign.status || 'DRAFT'}</small>
      </div>
      <span className="badge bg-label-primary">{campaign.targetPlatforms?.[0] || campaign.platform || 'SOCIAL'}</span>
    </div>
  );
}

function SuggestionCard({ suggestion }) {
  return (
    <article className="sneat-card template-card">
      <span className="badge bg-label-info">{suggestion.kind || 'Suggested'}</span>
      <h3>{suggestion.campaignName || suggestion.occasionName || 'Suggested campaign'}</h3>
      <p>{suggestion.windowLabel || suggestion.offerTitle || 'Ready for a new branded creative.'}</p>
      <Link className="btn btn-outline-primary btn-sm" to="/app/campaigns/create">Use template</Link>
    </article>
  );
}

export default function CampaignStudioModulePage({ screen = 'dashboard' }) {
  const [campaignsPage, setCampaignsPage] = useState({ items: [], totalItems: 0 });
  const [suggestions, setSuggestions] = useState([]);
  const [approvalQueue, setApprovalQueue] = useState([]);
  const [customersPage, setCustomersPage] = useState({ items: [], totalItems: 0 });
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    Promise.allSettled([
      retailService.getMarketingCampaigns({ page: 0, size: 8 }),
      retailService.getMarketingSuggestions({ daysAhead: 20 }),
      retailService.getMarketingApprovalQueue(),
      retailService.getCustomers({ page: 0, size: 8 })
    ]).then(([campaignsResult, suggestionsResult, approvalResult, customersResult]) => {
      if (cancelled) return;
      if (campaignsResult.status === 'fulfilled') setCampaignsPage(campaignsResult.value || { items: [], totalItems: 0 });
      else setError(getApiErrorMessage(campaignsResult.reason, 'Unable to load campaign data.'));
      if (suggestionsResult.status === 'fulfilled') setSuggestions(suggestionsResult.value || []);
      if (approvalResult.status === 'fulfilled') setApprovalQueue(approvalResult.value || []);
      if (customersResult.status === 'fulfilled') setCustomersPage(customersResult.value || { items: [], totalItems: 0 });
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const campaigns = campaignsPage.items || [];
  const scheduledCount = campaigns.filter((campaign) => campaign.status === 'SCHEDULED').length;
  const publishedCount = campaigns.filter((campaign) => campaign.status === 'PUBLISHED').length;
  const draftCount = campaigns.filter((campaign) => !campaign.status || campaign.status === 'DRAFT').length;
  const customerSegments = useMemo(() => {
    const counts = {};
    (customersPage.items || []).forEach((customer) => {
      (customer.segments || ['Customer']).forEach((segment) => {
        counts[segment] = (counts[segment] || 0) + 1;
      });
    });
    return Object.entries(counts).slice(0, 6);
  }, [customersPage.items]);

  if (screen === 'analytics') {
    return (
      <div className="sneat-module-page">
        <SneatHero meta={{ eyebrow: 'Analytics', title: 'Campaign Analytics', description: 'A dedicated routed analytics workspace using the existing marketing analytics APIs.' }} />
        <CampaignsPage initialTab="analytics" hidePageHeader hideTabs />
      </div>
    );
  }

  if (screen === 'list') {
    return (
      <div className="sneat-module-page">
        <SneatHero meta={screenMeta.list} />
        <CampaignsPage initialTab="campaigns" hidePageHeader hideTabs />
      </div>
    );
  }

  if (screen === 'create') {
    return (
      <div className="sneat-module-page">
        <SneatHero meta={screenMeta.create} />
        <CampaignsPage initialTab="create" hidePageHeader hideTabs />
      </div>
    );
  }

  if (screen === 'templates') {
    return (
      <div className="sneat-module-page">
        <SneatHero meta={screenMeta.templates} />
        <CampaignsPage initialTab="templates" hidePageHeader hideTabs />
      </div>
    );
  }

  if (screen === 'offers') {
    return (
      <div className="sneat-module-page">
        <SneatHero meta={screenMeta.offers} />
        <CampaignsPage initialTab="offers" hidePageHeader hideTabs />
      </div>
    );
  }

  if (screen === 'approval') {
    return (
      <div className="sneat-module-page">
        <SneatHero meta={screenMeta.approval} />
        <CampaignsPage initialTab="approval" hidePageHeader hideTabs />
      </div>
    );
  }

  if (screen === 'scheduler') {
    return (
      <div className="sneat-module-page">
        <SneatHero meta={{ eyebrow: 'Publishing', title: 'Scheduler', description: 'Review scheduled content and publishing state in its own Sneat module.' }} />
        <CampaignsPage initialTab="schedule" hidePageHeader hideTabs />
      </div>
    );
  }

  if (screen === 'automation') {
    return (
      <div className="sneat-module-page">
        <SneatHero meta={{ eyebrow: 'Automation', title: 'AI Campaign Automation', description: 'Create, generate, approve, and publish campaigns with the current live integrations.' }} />
        <CampaignsPage initialTab="create" hidePageHeader hideTabs />
      </div>
    );
  }

  const meta = screenMeta[screen] || screenMeta.dashboard;

  const stats = (
    <section className="sneat-stat-grid">
      <StatCard icon="bx-broadcast" label="Campaigns" value={campaignsPage.totalItems || campaigns.length} note="Live marketing records" />
      <StatCard icon="bx-time-five" label="Scheduled" value={scheduledCount} note="Publishing queue" tone="info" />
      <StatCard icon="bx-check-circle" label="Published" value={publishedCount} note="Completed work" tone="success" />
      <StatCard icon="bx-edit" label="Drafts" value={draftCount} note={`${approvalQueue.length} pending approval`} tone="warning" />
    </section>
  );

  const renderScreen = () => {
    if (screen === 'audience') {
      return (
        <section className="sneat-two-column">
          <article className="sneat-card">
            <div className="sneat-card-head"><div><small>Audience segments</small><h3>Live customer groups</h3></div></div>
            {customerSegments.map(([label, count], index) => (
              <div className="sneat-progress-row" key={label}>
                <span>{label}</span>
                <strong>{count}</strong>
                <div><span style={{ width: `${Math.max(14, 80 - index * 12)}%` }} /></div>
              </div>
            ))}
            {!customerSegments.length ? <p className="text-muted mb-0">No customer segment data returned yet.</p> : null}
          </article>
          <article className="sneat-card">
            <div className="sneat-card-head"><div><small>Targeting workflow</small><h3>Campaign-ready audience</h3></div></div>
            {['New collectors', 'Returning customers', 'High value buyers', 'Offer interested customers'].map((label) => (
              <div className="sneat-check-row" key={label}><i className="bx bx-user-check" />{label}</div>
            ))}
          </article>
        </section>
      );
    }

    if (screen === 'reports') {
      return (
        <section className="sneat-two-column">
          <article className="sneat-card">
            <div className="sneat-card-head"><div><small>Status report</small><h3>Campaign pipeline</h3></div></div>
            {['DRAFT', 'PENDING_APPROVAL', 'SCHEDULED', 'PUBLISHED'].map((status, index) => (
              <div className="sneat-progress-row" key={status}>
                <span>{status}</span>
                <strong>{campaigns.filter((campaign) => campaign.status === status).length}</strong>
                <div><span style={{ width: `${Math.max(18, 74 - index * 13)}%` }} /></div>
              </div>
            ))}
          </article>
          <article className="sneat-card">
            <div className="sneat-card-head"><div><small>Latest activity</small><h3>Publishing history</h3></div></div>
            {campaigns.slice(0, 5).map((campaign) => (
              <div className="sneat-list-row" key={campaign.id || campaign.campaignName}>
                <span className="sneat-stat-icon mini"><i className="bx bx-calendar" /></span>
                <div>
                  <strong>{campaign.campaignName || 'Campaign'}</strong>
                  <small>{campaign.updatedAt ? formatDate(campaign.updatedAt) : campaign.status || 'Status pending'}</small>
                </div>
              </div>
            ))}
          </article>
        </section>
      );
    }

    return (
      <section className="sneat-dashboard-grid">
        <article className="sneat-card span-2">
          <div className="sneat-card-head">
            <div><small>Campaigns</small><h3>Recent marketing work</h3></div>
            <Link className="btn btn-primary btn-sm" to="/app/campaigns/automation">Create campaign</Link>
          </div>
          <div className="sneat-list">
            {campaigns.slice(0, 6).map((campaign) => <CampaignRow key={campaign.id || campaign.campaignName} campaign={campaign} />)}
            {!campaigns.length ? <article className="sneat-empty-card"><i className="bx bx-broadcast" /><h3>No campaigns loaded</h3><p>Campaigns will appear from the live marketing API.</p></article> : null}
          </div>
        </article>
        <article className="sneat-card">
          <div className="sneat-card-head"><div><small>Creative readiness</small><h3>AI campaign flow</h3></div></div>
          <div className="sneat-score-ring">76%</div>
          <p>Campaign creation, approval, scheduler, analytics, and reports now have independent routed modules.</p>
        </article>
      </section>
    );
  };

  return (
    <div className="sneat-module-page">
      <SneatHero
        meta={meta}
        actions={<Link className="btn btn-primary" to="/app/campaigns/automation"><i className="bx bx-magic-wand me-1" /> Create Campaign</Link>}
      />
      {error ? <div className="alert alert-danger">{error}</div> : null}
      {stats}
      {renderScreen()}
    </div>
  );
}
