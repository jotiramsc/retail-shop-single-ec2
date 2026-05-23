import { useEffect, useMemo, useState } from 'react';
import DataTable from '../components/DataTable';
import { retailService } from '../services/retailService';
import { currency, formatDate } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

const today = new Date().toISOString().slice(0, 10);
const monthStart = `${today.slice(0, 8)}01`;
const yearStart = `${today.slice(0, 4)}-01-01`;
const websiteOption = { id: 'WEBSITE', displayName: 'Website' };

function SalesStat({ icon, label, value, note, tone = 'primary' }) {
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

function SalesTrendTile({ row, index, total }) {
  const share = total > 0 ? Math.round((Number(row.totalSalesAmount || 0) / total) * 100) : 0;
  const tones = ['primary', 'success', 'info', 'warning'];
  return (
    <article className={`salesperson-trend-tile is-${tones[index % tones.length]}`}>
      <span className="salesperson-trend-rank">#{index + 1}</span>
      <div>
        <small>{row.label}</small>
        <strong>{currency(row.totalSalesAmount)}</strong>
      </div>
      <div className="salesperson-trend-metrics">
        <span><i className="bx bx-receipt" /> {row.orderCount} orders</span>
        <span><i className="bx bx-package" /> {row.itemsSold} items</span>
      </div>
      <span className="salesperson-trend-share">{share}% share</span>
    </article>
  );
}

export default function SalespersonSalesPage({ auth }) {
  const isAdmin = auth?.role === 'ADMIN';
  const [fromDate, setFromDate] = useState(monthStart);
  const [toDate, setToDate] = useState(today);
  const [viewType, setViewType] = useState('DAILY');
  const [salespersonId, setSalespersonId] = useState('');
  const [salesPeople, setSalesPeople] = useState([]);
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const loadSalesPeople = async () => {
    if (!isAdmin) {
      setSalesPeople([]);
      return;
    }
    try {
      const list = await retailService.getSalesPeople();
      setSalesPeople([websiteOption, ...(list || [])]);
    } catch {
      setSalesPeople([websiteOption]);
    }
  };

  const loadReport = async (overrides = {}) => {
    const nextFilters = {
      fromDate,
      toDate,
      viewType,
      salespersonId,
      ...overrides
    };

    setLoading(true);
    setError('');
    try {
      const response = await retailService.getSalespersonSales({
        fromDate: nextFilters.fromDate,
        toDate: nextFilters.toDate,
        viewType: nextFilters.viewType,
        salespersonId: isAdmin ? nextFilters.salespersonId || undefined : undefined
      });
      setReport(response);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to load salesperson sales.'));
      setReport(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSalesPeople();
  }, [isAdmin]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      loadReport();
    }, 180);
    return () => window.clearTimeout(timeoutId);
  }, [fromDate, toDate, viewType, salespersonId, isAdmin]);

  const applyQuickFilter = (preset) => {
    if (preset === 'TODAY') {
      setFromDate(today);
      setToDate(today);
      setViewType('DAILY');
      return;
    }
    if (preset === 'MONTH') {
      setFromDate(monthStart);
      setToDate(today);
      setViewType('MONTHLY');
      return;
    }
    setFromDate(yearStart);
    setToDate(today);
    setViewType('YEARLY');
  };

  const trendTotal = useMemo(
    () => (report?.trend || []).reduce((sum, row) => sum + Number(row.totalSalesAmount || 0), 0),
    [report]
  );
  const topTrendRows = useMemo(() => (report?.trend || []).slice(0, 8), [report]);

  return (
    <div className="sneat-module-page salesperson-sales-page">
      <section className="sneat-page-title">
        <div>
          <span className="sneat-eyebrow">eCommerce sales</span>
          <h1>Salesperson performance</h1>
          <p>Track counter billing and website sales with clean filters, trend bars, and bill-level records.</p>
        </div>
        <div className="sneat-page-actions">
          <button type="button" className="btn btn-outline-primary" onClick={() => applyQuickFilter('TODAY')}>Today</button>
          <button type="button" className="btn btn-primary" onClick={() => loadReport()} disabled={loading}>
            <i className="bx bx-refresh me-1" /> Refresh
          </button>
        </div>
      </section>

      <section className="sneat-card sneat-filter-card">
        <div className="sneat-card-head">
          <div>
            <small>Filters</small>
            <h3>{isAdmin ? 'All salespersons' : auth?.displayName || 'Current user'}</h3>
          </div>
          <span className="badge bg-label-primary">{viewType}</span>
        </div>
        <div className="sneat-filter-grid salesperson-filter-grid">
          <label><span>From</span><input type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} max={toDate} /></label>
          <label><span>To</span><input type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} min={fromDate} max={today} /></label>
          {isAdmin ? (
            <label><span>Salesperson</span>
              <select value={salespersonId} onChange={(event) => setSalespersonId(event.target.value)}>
                <option value="">All salespersons</option>
                {salesPeople.map((salesPerson) => (
                  <option key={salesPerson.id} value={salesPerson.id}>
                    {salesPerson.displayName}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <div className="sneat-lockup-field">
              <span>Salesperson</span>
              <strong>{auth?.displayName || 'Current user'}</strong>
            </div>
          )}
        </div>
        <div className="sneat-toolbar-row">
          <div className="btn-group" role="group" aria-label="Quick date filters">
            <button type="button" className="btn btn-outline-primary" onClick={() => applyQuickFilter('TODAY')}>Today</button>
            <button type="button" className="btn btn-outline-primary" onClick={() => applyQuickFilter('MONTH')}>This Month</button>
            <button type="button" className="btn btn-outline-primary" onClick={() => applyQuickFilter('YEAR')}>This Year</button>
          </div>
          <div className="btn-group" role="group" aria-label="Breakdown">
            {['DAILY', 'MONTHLY', 'YEARLY'].map((type) => (
              <button key={type} type="button" className={`btn ${viewType === type ? 'btn-primary' : 'btn-outline-primary'}`} onClick={() => setViewType(type)}>
                {type}
              </button>
            ))}
          </div>
        </div>
      </section>

      {error ? <p className="error-text">{error}</p> : null}

      <section className="sneat-stat-grid">
        <SalesStat icon="bx-rupee" label="Total Sales Amount" value={currency(report?.totalSalesAmount)} note="Current filter" />
        <SalesStat icon="bx-receipt" label="Total Orders" value={report?.totalOrders ?? 0} note="Bills and website orders" tone="info" />
        <SalesStat icon="bx-package" label="Total Items Sold" value={report?.totalItemsSold ?? 0} note="Units sold" tone="success" />
        <SalesStat icon="bx-trending-up" label="Average Order Value" value={currency(report?.averageOrderValue)} note="AOV" tone="warning" />
      </section>

      <section className="sneat-dashboard-grid">
        <article className="sneat-card">
          <div className="sneat-card-head">
            <div><small>Trend</small><h3>{`${viewType.charAt(0)}${viewType.slice(1).toLowerCase()} trend`}</h3></div>
            <span className="badge bg-label-info">{fromDate} to {toDate}</span>
          </div>
          <div className="salesperson-trend-summary">
            <span><i className="bx bx-line-chart" /> {topTrendRows.length} periods</span>
            <span><i className="bx bx-wallet" /> {currency(trendTotal)} total</span>
          </div>
          <div className="salesperson-trend-grid">
            {(report?.trend || []).length === 0 ? (
              <p className="empty-cell">No sales captured for this selection yet.</p>
            ) : (
              topTrendRows.map((row, index) => (
                <SalesTrendTile key={row.label} row={row} index={index} total={trendTotal} />
              ))
            )}
          </div>
        </article>

        <article className="sneat-card">
          <div className="sneat-card-head"><div><small>Snapshot</small><h3>Sales snapshot</h3></div></div>
          <div className="salesperson-sales-snapshot">
            <div>
              <span>Viewing</span>
              <strong>{report?.salespersonName || (isAdmin ? 'All salespersons' : auth?.displayName || 'Current user')}</strong>
            </div>
            <div>
              <span>Date range</span>
              <strong>{fromDate} to {toDate}</strong>
            </div>
            <div>
              <span>Breakdown mode</span>
              <strong>{viewType}</strong>
            </div>
            <div>
              <span>Records</span>
              <strong>{report?.records?.length ?? 0}</strong>
            </div>
          </div>
        </article>
      </section>

      <section className="sneat-card">
        <div className="sneat-card-head"><div><small>Records</small><h3>Detailed sales records</h3></div></div>
        <DataTable
          columns={[
            { key: 'date', label: 'Date', render: (row) => formatDate(row.date) },
            { key: 'billNo', label: 'Bill No' },
            { key: 'customerName', label: 'Customer Name' },
            { key: 'salespersonName', label: 'Salesperson Name' },
            { key: 'totalAmount', label: 'Total Amount', render: (row) => currency(row.totalAmount) },
            { key: 'paymentMode', label: 'Payment Mode' }
          ]}
          rows={report?.records || []}
          emptyMessage={loading ? 'Loading salesperson sales...' : 'No sales records found for the current filter.'}
        />
      </section>
    </div>
  );
}
