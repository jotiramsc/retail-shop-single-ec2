import { useEffect, useMemo, useState } from 'react';
import DataTable from '../components/DataTable';
import MetricCard from '../components/MetricCard';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import { retailService } from '../services/retailService';
import { currency, formatDate } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

const today = new Date().toISOString().slice(0, 10);
const monthStart = `${today.slice(0, 8)}01`;
const yearStart = `${today.slice(0, 4)}-01-01`;
const websiteOption = { id: 'WEBSITE', displayName: 'Website' };

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

  const trendMax = useMemo(
    () => Math.max(1, ...(report?.trend || []).map((row) => Number(row.totalSalesAmount || 0))),
    [report]
  );

  return (
    <div className="page">
      <PageHeader
        eyebrow="Salesperson Sales"
        title="Track sales performance by salesperson"
        description="See totals, trends, and bill-level detail for each salesperson, with admin-wide visibility and self-view for staff."
      />

      <Panel title="Filters" subtitle={isAdmin ? 'Filter across all salespersons or focus on one performer.' : `Showing only ${auth?.displayName || 'your'} sales.`}>
        <div className="salesperson-sales-filters">
          <label>
            <span>From</span>
            <input type="date" value={fromDate} onChange={(event) => setFromDate(event.target.value)} max={toDate} />
          </label>
          <label>
            <span>To</span>
            <input type="date" value={toDate} onChange={(event) => setToDate(event.target.value)} min={fromDate} max={today} />
          </label>
          {isAdmin ? (
            <label>
              <span>Salesperson</span>
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
            <div className="salesperson-sales-lockup">
              <span>Salesperson</span>
              <strong>{auth?.displayName || 'Current user'}</strong>
            </div>
          )}

          <div className="salesperson-sales-filter-group">
            <span>Quick filters</span>
            <div className="salesperson-sales-quick-actions">
              <button type="button" className="ghost-btn compact-btn" onClick={() => applyQuickFilter('TODAY')}>Today</button>
              <button type="button" className="ghost-btn compact-btn" onClick={() => applyQuickFilter('MONTH')}>This Month</button>
              <button type="button" className="ghost-btn compact-btn" onClick={() => applyQuickFilter('YEAR')}>This Year</button>
            </div>
          </div>

          <div className="salesperson-sales-filter-group">
            <span>Breakdown</span>
            <div className="salesperson-sales-view-switch">
              {['DAILY', 'MONTHLY', 'YEARLY'].map((type) => (
                <button
                  key={type}
                  type="button"
                  className={`ghost-btn compact-btn ${viewType === type ? 'active' : ''}`}
                  onClick={() => setViewType(type)}
                >
                  {type}
                </button>
              ))}
            </div>
          </div>
        </div>
      </Panel>

      {error ? <p className="error-text">{error}</p> : null}

      <div className="metrics-grid">
        <MetricCard label="Total Sales Amount" value={currency(report?.totalSalesAmount)} tone="highlight" />
        <MetricCard label="Total Orders" value={report?.totalOrders ?? 0} />
        <MetricCard label="Total Items Sold" value={report?.totalItemsSold ?? 0} />
        <MetricCard label="Average Order Value" value={currency(report?.averageOrderValue)} />
      </div>

      <div className="two-column">
        <Panel
          title={`${viewType.charAt(0)}${viewType.slice(1).toLowerCase()} trend`}
          subtitle={loading ? 'Refreshing sales trend...' : `Viewing ${report?.salespersonName || 'All salespersons'} from ${fromDate} to ${toDate}.`}
        >
          <div className="salesperson-sales-trend">
            {(report?.trend || []).length === 0 ? (
              <p className="empty-cell">No sales captured for this selection yet.</p>
            ) : (
              (report?.trend || []).map((row) => (
                <div key={row.label} className="salesperson-sales-trend-row">
                  <div className="salesperson-sales-trend-copy">
                    <strong>{row.label}</strong>
                    <span>{row.orderCount} orders · {row.itemsSold} items · {currency(row.totalSalesAmount)}</span>
                  </div>
                  <div className="salesperson-sales-trend-bar">
                    <span style={{ width: `${Math.max(6, (Number(row.totalSalesAmount || 0) / trendMax) * 100)}%` }} />
                  </div>
                </div>
              ))
            )}
          </div>
        </Panel>

        <Panel
          title="Sales snapshot"
          subtitle="A clean roll-up of the current filter selection."
        >
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
        </Panel>
      </div>

      <Panel title="Detailed sales records" subtitle="Bill-level detail for the active date range and salesperson filter.">
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
      </Panel>
    </div>
  );
}
