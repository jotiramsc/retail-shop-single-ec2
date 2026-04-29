import { Suspense, lazy, useEffect, useState } from 'react';
import DataTable from '../components/DataTable';
import MetricCard from '../components/MetricCard';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import { retailService } from '../services/retailService';
import { formatDate } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

const rangeOptions = [
  { value: '7', label: 'Last 7 days' },
  { value: '30', label: 'Last 30 days' },
  { value: '90', label: 'Last 90 days' },
  { value: '365', label: 'Last 365 days' }
];

const SiteVisitMapSection = lazy(() => import('../components/SiteVisitMapSection'));

function numberFormat(value) {
  return new Intl.NumberFormat('en-IN').format(Number(value || 0));
}

function formatVisitTimestamp(value) {
  if (!value) {
    return '-';
  }
  return `${new Intl.DateTimeFormat('en-IN', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
    timeZone: 'Asia/Kolkata'
  }).format(new Date(value))} IST`;
}

function formatSourceTypeLabel(value) {
  switch (value) {
    case 'CAMPAIGN':
      return 'Campaign';
    case 'SEARCH':
      return 'Search';
    case 'SOCIAL':
      return 'Social';
    case 'REFERRAL':
      return 'Referral';
    case 'DIRECT':
    default:
      return 'Direct';
  }
}

function formatSourceLabel(row) {
  const host = row.referrerHost?.toLowerCase();
  if (row.sourceType === 'DIRECT') {
    return 'Direct';
  }
  if (row.sourceType === 'SEARCH') {
    if (host?.includes('google')) {
      return 'Google Search';
    }
    if (host?.includes('bing')) {
      return 'Bing Search';
    }
    if (host?.includes('duckduckgo')) {
      return 'DuckDuckGo Search';
    }
  }
  if (row.sourceType === 'SOCIAL') {
    if (host?.includes('instagram')) {
      return 'Instagram';
    }
    if (host?.includes('facebook')) {
      return 'Facebook';
    }
    if (host?.includes('whatsapp')) {
      return 'WhatsApp';
    }
  }
  if (row.sourceType === 'CAMPAIGN') {
    return row.utmSource || row.utmCampaign || row.sourceLabel || 'Campaign';
  }
  return row.sourceLabel || row.referrerHost || formatSourceTypeLabel(row.sourceType);
}

function normalizeLanguage(value) {
  if (!value) {
    return null;
  }
  const primary = value
    .split(',')
    .map((part) => part.split(';')[0]?.trim())
    .find(Boolean);
  if (!primary) {
    return null;
  }
  try {
    return Intl.getCanonicalLocales(primary)[0] || primary;
  } catch {
    return primary;
  }
}

function formatVisitDetails(row) {
  const details = [];
  if (row.sourceType === 'CAMPAIGN' && row.utmCampaign) {
    details.push(`Campaign ${row.utmCampaign}`);
  } else if (row.referrerHost && row.sourceType !== 'DIRECT') {
    details.push(row.referrerHost);
  }
  const language = normalizeLanguage(row.acceptLanguage);
  if (language) {
    details.push(`Lang ${language}`);
  }
  return details.length ? details.join(' · ') : '—';
}

function locationLabel(row) {
  if (row.exactLocationName) {
    return row.exactLocationName;
  }
  const place = [row.city, row.region].filter(Boolean).join(', ');
  const country = [row.countryName, row.countryCode].filter(Boolean).join(' ');
  return [place, country].filter(Boolean).join(' · ') || 'Unknown';
}

export default function SiteInteractionsPage() {
  const [days, setDays] = useState('30');
  const [report, setReport] = useState(null);
  const [error, setError] = useState('');

  const loadReport = async (nextDays = days) => {
    setError('');
    try {
      setReport(await retailService.getSiteInteractionReport(nextDays));
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to load site interactions.'));
    }
  };

  useEffect(() => {
    loadReport(days);
  }, []);

  const handleRangeChange = async (value) => {
    setDays(value);
    await loadReport(value);
  };

  return (
    <div className="page">
      <PageHeader
        eyebrow="Site Interaction"
        title="Website visits and source details"
        description="See how many shoppers are landing on the storefront, where they came from, and which pages are pulling them in first."
        actions={(
          <label className="date-field site-interaction-range">
            <span>Reporting window</span>
            <select value={days} onChange={(e) => handleRangeChange(e.target.value)}>
              {rangeOptions.map((option) => (
                <option key={option.value} value={option.value}>{option.label}</option>
              ))}
            </select>
          </label>
        )}
      />

      {error ? <p className="error-text">{error}</p> : null}

      <div className="metric-grid report-metric-grid">
        <MetricCard label="Total visits" value={numberFormat(report?.totalVisits)} tone="accent" />
        <MetricCard label="Visits in range" value={numberFormat(report?.visitsInRange)} />
        <MetricCard label="Campaign visits" value={numberFormat(report?.campaignVisits)} />
        <MetricCard label="Search visits" value={numberFormat(report?.searchVisits)} />
        <MetricCard label="Social visits" value={numberFormat(report?.socialVisits)} />
        <MetricCard label="Direct visits" value={numberFormat(report?.directVisits)} />
      </div>

      <div className="two-column site-interaction-grid">
        <Panel
          title="Traffic by day"
          subtitle={`Daily unique visits from ${report?.fromDate || '—'} to ${report?.toDate || '—'}. Each browser is counted once per day.`}
        >
          <DataTable
            columns={[
              { key: 'date', label: 'Date' },
              { key: 'visits', label: 'Visits', render: (row) => numberFormat(row.visits) }
            ]}
            rows={(report?.dailyVisits || []).map((row) => ({
              ...row,
              date: formatDate(row.date)
            }))}
            emptyMessage="No visits recorded in this window yet."
          />
        </Panel>

        <Panel title="Top sources" subtitle="Campaign, search, social, referral, and direct breakdown for the selected range.">
          <DataTable
            columns={[
              {
                key: 'sourceType',
                label: 'Source type',
                render: (row) => <span className="trust-chip small-chip site-source-chip">{formatSourceTypeLabel(row.sourceType)}</span>
              },
              { key: 'sourceLabel', label: 'Source detail' },
              { key: 'visits', label: 'Visits', render: (row) => numberFormat(row.visits) }
            ]}
            rows={report?.sourceBreakdown || []}
            emptyMessage="No source data recorded yet."
          />
        </Panel>
      </div>

      <div className="two-column site-interaction-grid">
        <Panel title="Top referrers" subtitle="Domains that most often sent visitors to the site.">
          <DataTable
            columns={[
              { key: 'label', label: 'Referrer' },
              { key: 'visits', label: 'Visits', render: (row) => numberFormat(row.visits) }
            ]}
            rows={report?.topReferrers || []}
            emptyMessage="No external referrers recorded yet."
          />
        </Panel>

        <Panel title="Top landing pages" subtitle="Where a visit first started on the storefront.">
          <DataTable
            columns={[
              { key: 'label', label: 'Landing page' },
              { key: 'visits', label: 'Visits', render: (row) => numberFormat(row.visits) }
            ]}
            rows={report?.topLandingPages || []}
            emptyMessage="No landing-page data recorded yet."
          />
        </Panel>
      </div>

      <div className="two-column site-interaction-grid">
        <Panel title="Visit location map" subtitle="Interactive hotspots grouped by nearby visit coordinates. Higher-traffic places are larger and darker.">
          <Suspense fallback={<div className="site-map-empty"><strong>Loading map...</strong><span>Preparing hotspot locations for this reporting window.</span></div>}>
            <SiteVisitMapSection points={report?.mapPoints || []} />
          </Suspense>
        </Panel>

        <Panel title="Top countries" subtitle="Approximate visitor location by IP geolocation for the selected range.">
          <DataTable
            columns={[
              {
                key: 'country',
                label: 'Country',
                render: (row) => (
                  <div>
                    <strong>{row.countryName || 'Unknown'}</strong>
                    <div className="table-subcopy">{row.countryCode || '—'}</div>
                  </div>
                )
              },
              { key: 'visits', label: 'Visits', render: (row) => numberFormat(row.visits) }
            ]}
            rows={report?.topCountries || []}
            emptyMessage="No country data recorded yet."
          />
        </Panel>
      </div>

      <div className="two-column site-interaction-grid">
        <Panel title="What we capture" subtitle="Each visit stores the key source signals we can reliably use inside the admin view.">
          <div className="trust-chip-row site-capture-chip-row">
            {['IP address', 'Exact location name', 'Latitude / longitude', 'Accuracy (when allowed)', 'Country', 'City / region', 'Timezone', 'Organization', 'Referrer', 'UTM source', 'UTM campaign', 'Landing page', 'Browser language'].map((item) => (
              <span key={item} className="trust-chip small-chip site-source-chip">{item}</span>
            ))}
          </div>
        </Panel>
      </div>

      <Panel title="Recent visits" subtitle="Latest recorded interactions with cleaner source details. All timestamps are shown in IST.">
        <DataTable
          columns={[
            { key: 'createdAt', label: 'When', render: (row) => formatVisitTimestamp(row.createdAt) },
            { key: 'landingPath', label: 'Landing page' },
            {
              key: 'ipAddress',
              label: 'IP / Network',
              render: (row) => (
                <div>
                  <strong>{row.ipAddress || 'Unknown'}</strong>
                  <div className="table-subcopy">{row.organization || '—'}</div>
                </div>
              )
            },
            {
              key: 'location',
              label: 'Location',
              render: (row) => (
                <div>
                  <strong>{locationLabel(row)}</strong>
                  <div className="table-subcopy">
                    {[
                      row.locationSource ? `${row.locationSource === 'BROWSER' ? 'Browser precise' : 'IP approximate'}` : null,
                      row.postalCode ? `PIN ${row.postalCode}` : null,
                      row.latitude != null && row.longitude != null ? `${row.latitude.toFixed(5)}, ${row.longitude.toFixed(5)}` : null,
                      row.locationAccuracyMeters != null ? `±${Math.round(row.locationAccuracyMeters)}m` : null
                    ].filter(Boolean).join(' · ')}
                  </div>
                </div>
              )
            },
            {
              key: 'source',
              label: 'Source',
              render: (row) => (
                <div>
                  <strong>{formatSourceLabel(row)}</strong>
                  <div className="table-subcopy">{formatSourceTypeLabel(row.sourceType)}</div>
                </div>
              )
            },
            {
              key: 'detail',
              label: 'Details',
              render: (row) => (
                <div>
                  <strong>{formatVisitDetails(row)}</strong>
                  <div className="table-subcopy">{row.timezone || 'Timezone unavailable'}</div>
                </div>
              )
            }
          ]}
          rows={report?.recentVisits || []}
          emptyMessage="Recent visits will appear here once traffic starts landing."
        />
      </Panel>
    </div>
  );
}
