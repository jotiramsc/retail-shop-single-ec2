import { useEffect, useState } from 'react';
import DataTable from '../components/DataTable';
import MetricCard from '../components/MetricCard';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import { retailService } from '../services/retailService';
import { currency, formatDate } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

const today = new Date().toISOString().slice(0, 10);

export default function ReportsPage() {
  const [daily, setDaily] = useState(null);
  const [lowStockPage, setLowStockPage] = useState({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [orderFeed, setOrderFeed] = useState({ orders: [] });
  const [reportFromDate, setReportFromDate] = useState(today);
  const [customerFilter, setCustomerFilter] = useState('');
  const [customerSuggestions, setCustomerSuggestions] = useState([]);
  const [customerSearchFocused, setCustomerSearchFocused] = useState(false);
  const [error, setError] = useState('');

  const loadReports = async (fromDate = reportFromDate, lowStockPageNumber = 0, invoicePageNumber = 0) => {
    setError('');
    try {
      const [dailyData, lowStockData, orderData] = await Promise.all([
        retailService.getDailyReport({ fromDate, toDate: today }),
        retailService.getLowStock({ page: lowStockPageNumber, size: 10 }),
        retailService.getReportInvoices({ fromDate, toDate: today, customerName: customerFilter, page: invoicePageNumber, size: 10 })
      ]);
      setDaily(dailyData);
      setLowStockPage(lowStockData);
      setOrderFeed(orderData);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to load reports.'));
    }
  };

  useEffect(() => {
    loadReports(reportFromDate);
  }, []);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      loadReports(reportFromDate, lowStockPage.page || 0, 0);
    }, 250);

    return () => window.clearTimeout(timeoutId);
  }, [reportFromDate, customerFilter]);

  const loadLowStockPage = (page) => loadReports(reportFromDate, page, orderFeed.page || 0);
  const loadInvoicePage = (page) => loadReports(reportFromDate, lowStockPage.page || 0, page);

  useEffect(() => {
    const normalized = customerFilter.trim();
    if (!customerSearchFocused || normalized.length < 1) {
      setCustomerSuggestions([]);
      return;
    }

    const timeoutId = window.setTimeout(async () => {
      try {
        const matches = await retailService.searchCustomers(normalized);
        setCustomerSuggestions(matches);
      } catch {
        setCustomerSuggestions([]);
      }
    }, 200);

    return () => window.clearTimeout(timeoutId);
  }, [customerFilter, customerSearchFocused]);

  return (
    <div className="page">
      <PageHeader
        eyebrow="Reports"
        title="Sales and replenishment dashboard"
        description="Track today’s sales performance and quickly see which products need restocking attention."
        actions={(
          <div className="page-actions">
            <div className="search-box-wrap report-filter-search">
              <input
                placeholder="Search customer"
                value={customerFilter}
                onChange={(e) => setCustomerFilter(e.target.value)}
                onFocus={() => setCustomerSearchFocused(true)}
                onBlur={() => window.setTimeout(() => setCustomerSearchFocused(false), 120)}
                autoComplete="off"
              />
              {customerSearchFocused && customerSuggestions.length ? (
                <div className="autocomplete-menu">
                  {customerSuggestions.map((customer) => (
                    <button
                      key={customer.id}
                      type="button"
                      className="autocomplete-item"
                      onClick={() => {
                        setCustomerFilter(customer.name);
                        setCustomerSearchFocused(false);
                      }}
                    >
                      <strong>{customer.name}</strong>
                      <span>{customer.mobile}</span>
                    </button>
                  ))}
                </div>
              ) : null}
            </div>
            <label className="date-field">
              <span>From</span>
              <input
                type="date"
                max={today}
                value={reportFromDate}
                onChange={(e) => setReportFromDate(e.target.value)}
              />
            </label>
          </div>
        )}
      />

      {error ? <p className="error-text">{error}</p> : null}

      <div className="metric-grid">
        <MetricCard label="Orders In Range" value={daily?.invoiceCount || 0} />
        <MetricCard label="Sales Total" value={currency(daily?.totalSales)} tone="accent" />
        <MetricCard label="Discount Given" value={currency(daily?.totalDiscount)} />
      </div>

      <Panel title="Low stock products" subtitle="These items are at or below the operational threshold.">
        <DataTable
          columns={[
            { key: 'productName', label: 'Product' },
            { key: 'category', label: 'Category' },
            { key: 'sku', label: 'SKU' },
            { key: 'quantity', label: 'Quantity Left' },
            { key: 'threshold', label: 'Alert At' }
          ]}
          rows={lowStockPage.items || []}
          pagination={lowStockPage}
          onPageChange={loadLowStockPage}
        />
      </Panel>

      <Panel title="Orders in selected range" subtitle="Review both website orders and billing orders in the chosen date range, with live delivery status for online purchases.">
        <DataTable
          columns={[
            { key: 'referenceNumber', label: 'Reference No.' },
            { key: 'source', label: 'Source' },
            { key: 'status', label: 'Status' },
            { key: 'createdAt', label: 'Date', render: (row) => formatDate(row.createdAt) },
            { key: 'customerName', label: 'Customer' },
            { key: 'customerMobile', label: 'Mobile' },
            { key: 'paymentMode', label: 'Payment Mode' },
            { key: 'paymentStatus', label: 'Payment Status' },
            { key: 'finalAmount', label: 'Final Amount', render: (row) => currency(row.finalAmount) },
            { key: 'discount', label: 'Discount', render: (row) => currency(row.discount) },
            { key: 'couponCode', label: 'Coupon' }
          ]}
          rows={orderFeed.orders || []}
          emptyMessage="No billing or website orders matched this date range and customer filter."
          pagination={orderFeed}
          onPageChange={loadInvoicePage}
        />
      </Panel>
    </div>
  );
}
