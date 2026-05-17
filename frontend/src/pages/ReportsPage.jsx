import { useEffect, useMemo, useState } from 'react';
import DataTable from '../components/DataTable';
import MetricCard from '../components/MetricCard';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import { retailService } from '../services/retailService';
import { currency, formatDate } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

const today = new Date().toISOString().slice(0, 10);
const currentMonth = today.slice(0, 7);
const currentYear = Number(today.slice(0, 4));
const defaultPaymentFromDate = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
const websiteSalesPersonOption = { id: 'WEBSITE', displayName: 'Website', username: 'website' };
const ORDER_STATUS_OPTIONS = [
  'PENDING',
  'CONFIRMED',
  'SHIPPED',
  'DELIVERED',
  'COMPLETED',
  'CANCELLED',
  'RETURNED',
  'REFUND_INITIATED',
  'PAYMENT_FAILED'
];

const paymentStatusTone = (status) => {
  const normalized = String(status || '').toUpperCase();
  if (['SUCCESS', 'PAID', 'LOCAL_TEST', 'RECEIVED'].includes(normalized)) {
    return 'is-good';
  }
  if (normalized.includes('FAIL') || normalized.includes('INVALID') || normalized.includes('ERROR')) {
    return 'is-bad';
  }
  return 'is-warm';
};

const prettyPayload = (payload) => {
  if (!payload) {
    return 'No payload recorded.';
  }
  try {
    return JSON.stringify(JSON.parse(payload), null, 2);
  } catch {
    return payload;
  }
};

export default function ReportsPage() {
  const [activeReportTab, setActiveReportTab] = useState('dashboard');
  const [daily, setDaily] = useState(null);
  const [lowStockPage, setLowStockPage] = useState({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [orderFeed, setOrderFeed] = useState({ orders: [] });
  const [reportFromDate, setReportFromDate] = useState(today);
  const [customerFilter, setCustomerFilter] = useState('');
  const [customerSuggestions, setCustomerSuggestions] = useState([]);
  const [customerSearchFocused, setCustomerSearchFocused] = useState(false);
  const [salesPeople, setSalesPeople] = useState([websiteSalesPersonOption]);
  const [salesPersonFilter, setSalesPersonFilter] = useState('');
  const [error, setError] = useState('');
  const [orderStatusFeedback, setOrderStatusFeedback] = useState('');
  const [updatingOrderId, setUpdatingOrderId] = useState('');
  const [salesError, setSalesError] = useState('');
  const [salesLoading, setSalesLoading] = useState(false);
  const [salesReport, setSalesReport] = useState(null);
  const [salesPeriod, setSalesPeriod] = useState('MONTHLY');
  const [salesMonth, setSalesMonth] = useState(currentMonth);
  const [salesYear, setSalesYear] = useState(String(currentYear));
  const [salesScope, setSalesScope] = useState('ALL');
  const [salesCategory, setSalesCategory] = useState('');
  const [salesProductId, setSalesProductId] = useState('');
  const [categoryOptions, setCategoryOptions] = useState([]);
  const [reportProducts, setReportProducts] = useState([]);
  const [paymentFilters, setPaymentFilters] = useState({
    fromDate: defaultPaymentFromDate,
    toDate: today,
    provider: 'RAZORPAY',
    operation: '',
    status: '',
    search: ''
  });
  const [paymentPage, setPaymentPage] = useState({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [paymentError, setPaymentError] = useState('');
  const [paymentLoading, setPaymentLoading] = useState(false);
  const [selectedPayment, setSelectedPayment] = useState(null);

  const salesPersonValue = (salesPerson) => salesPerson.id === 'WEBSITE' ? 'WEBSITE' : salesPerson.displayName;
  const isWebsiteOrderView = salesPersonFilter === 'WEBSITE';

  const loadReports = async (fromDate = reportFromDate, lowStockPageNumber = 0, invoicePageNumber = 0) => {
    setError('');
    try {
      const [dailyData, lowStockData, orderData] = await Promise.all([
        retailService.getDailyReport({ fromDate, toDate: today, salesPersonName: salesPersonFilter }),
        retailService.getLowStock({ page: lowStockPageNumber, size: 10 }),
        retailService.getReportInvoices({ fromDate, toDate: today, customerName: customerFilter, salesPersonName: salesPersonFilter, page: invoicePageNumber, size: 10 })
      ]);
      setDaily(dailyData);
      setLowStockPage(lowStockData);
      setOrderFeed(orderData);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to load reports.'));
    }
  };

  const loadReportOptions = async () => {
    try {
      const [categories, productsPage, salesPeopleList] = await Promise.all([
        retailService.getProductCategoryOptions(),
        retailService.getProducts({ page: 0, size: 500 }),
        retailService.getSalesPeople()
      ]);
      setCategoryOptions(categories);
      setReportProducts(productsPage.items || []);
      setSalesPeople([websiteSalesPersonOption, ...(salesPeopleList || [])]);
    } catch {
      setCategoryOptions([]);
      setReportProducts([]);
      setSalesPeople([websiteSalesPersonOption]);
    }
  };

  const loadSalesReport = async (overrides = {}) => {
    const nextFilters = {
      period: salesPeriod,
      month: salesMonth,
      year: salesYear,
      scope: salesScope,
      category: salesCategory,
      productId: salesProductId,
      ...overrides
    };

    setSalesLoading(true);
    setSalesError('');
    try {
      const response = await retailService.getSalesReport({
        period: nextFilters.period,
        month: nextFilters.period === 'MONTHLY' ? nextFilters.month : undefined,
        year: nextFilters.period === 'ANNUAL' ? nextFilters.year : undefined,
        scope: nextFilters.scope,
        category: nextFilters.scope === 'CATEGORY' ? nextFilters.category : undefined,
        productId: nextFilters.scope === 'PRODUCT' ? nextFilters.productId : undefined,
        salesPersonName: salesPersonFilter || undefined
      });
      setSalesReport(response);
    } catch (requestError) {
      setSalesError(getApiErrorMessage(requestError, 'Unable to load the sales report.'));
      setSalesReport(null);
    } finally {
      setSalesLoading(false);
    }
  };

  const loadPaymentTransactions = async (page = 0, overrides = {}) => {
    const nextFilters = {
      ...paymentFilters,
      ...overrides
    };
    setPaymentLoading(true);
    setPaymentError('');
    try {
      const response = await retailService.getPaymentTransactions({
        ...nextFilters,
        page,
        size: 12
      });
      setPaymentPage(response);
      setSelectedPayment((current) => {
        const items = response.items || [];
        if (current) {
          const refreshed = items.find((item) => item.id === current.id);
          if (refreshed) {
            return refreshed;
          }
        }
        return items[0] || null;
      });
    } catch (requestError) {
      setPaymentError(getApiErrorMessage(requestError, 'Unable to load Razorpay diagnostics.'));
      setPaymentPage({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
      setSelectedPayment(null);
    } finally {
      setPaymentLoading(false);
    }
  };

  useEffect(() => {
    loadReports(reportFromDate);
    loadReportOptions();
    loadSalesReport();
  }, []);

  useEffect(() => {
    if (salesPeriod === 'MONTHLY' && !salesMonth) {
      return;
    }
    if (salesPeriod === 'ANNUAL' && !salesYear) {
      return;
    }
    if (salesScope === 'CATEGORY' && !salesCategory) {
      setSalesReport(null);
      return;
    }
    if (salesScope === 'PRODUCT' && !salesProductId) {
      setSalesReport(null);
      return;
    }

    const timeoutId = window.setTimeout(() => {
      loadSalesReport();
    }, 180);

    return () => window.clearTimeout(timeoutId);
  }, [salesPeriod, salesMonth, salesYear, salesScope, salesCategory, salesProductId, salesPersonFilter]);

  useEffect(() => {
    const timeoutId = window.setTimeout(() => {
      loadReports(reportFromDate, lowStockPage.page || 0, 0);
    }, 250);

    return () => window.clearTimeout(timeoutId);
  }, [reportFromDate, customerFilter, salesPersonFilter]);

  useEffect(() => {
    if (activeReportTab !== 'payments') {
      return undefined;
    }

    const timeoutId = window.setTimeout(() => {
      loadPaymentTransactions(0);
    }, 220);

    return () => window.clearTimeout(timeoutId);
  }, [activeReportTab, paymentFilters]);

  const loadLowStockPage = (page) => loadReports(reportFromDate, page, orderFeed.page || 0);
  const loadInvoicePage = (page) => loadReports(reportFromDate, lowStockPage.page || 0, page);

  const updateOrderDeliveryStatus = async (row, nextStatus) => {
    const source = String(row?.source || '').toUpperCase();
    if (!row?.id || source !== 'WEBSITE') {
      return;
    }
    setUpdatingOrderId(row.id);
    setOrderStatusFeedback('');
    setError('');
    try {
      await retailService.updateOrderStatus(row.id, { status: nextStatus });
      setOrderFeed((current) => ({
        ...current,
        orders: (current.orders || []).map((order) => (
          order.id === row.id ? { ...order, status: nextStatus } : order
        ))
      }));
      setOrderStatusFeedback(
        `Order ${row.referenceNumber || row.id} moved to ${nextStatus.replaceAll('_', ' ')}. WhatsApp update has been triggered.`
      );
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to update order delivery status.'));
    } finally {
      setUpdatingOrderId('');
    }
  };

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

  useEffect(() => {
    if (salesScope !== 'CATEGORY' && salesCategory) {
      setSalesCategory('');
    }
    if (salesScope !== 'PRODUCT' && salesProductId) {
      setSalesProductId('');
    }
  }, [salesScope, salesCategory, salesProductId]);

  const salesYearOptions = useMemo(
    () => Array.from({ length: 6 }, (_, index) => String(currentYear - index)),
    []
  );

  const printSalesReport = () => {
    if (!salesReport) {
      return;
    }

    const printWindow = window.open('', '_blank', 'width=1080,height=760');
    if (!printWindow) {
      return;
    }

    const rows = (salesReport.rows || [])
      .map((row) => `
        <tr>
          <td>${row.productName}</td>
          <td>${row.category || '—'}</td>
          <td>${row.sku || '—'}</td>
          <td>${row.quantitySold}</td>
          <td>${currency(row.grossSales)}</td>
          <td>${currency(row.discount)}</td>
          <td>${currency(row.netSales)}</td>
        </tr>
      `)
      .join('');

    printWindow.document.write(`
      <html>
        <head>
          <title>${salesReport.reportLabel}</title>
          <style>
            body { font-family: Arial, sans-serif; padding: 24px; color: #1f1714; }
            h1, p { margin: 0; }
            .meta { margin-top: 8px; color: #5f5249; }
            .summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin: 24px 0; }
            .card { border: 1px solid #e7d8c9; border-radius: 12px; padding: 12px; background: #fffaf4; }
            table { width: 100%; border-collapse: collapse; margin-top: 18px; }
            th, td { border-bottom: 1px solid #eadfce; padding: 10px 8px; text-align: left; font-size: 13px; }
            th { background: #f7efe4; }
          </style>
        </head>
        <body>
          <h1>${salesReport.reportLabel}</h1>
          <p class="meta">Range: ${salesReport.fromDate} to ${salesReport.toDate}</p>
          <p class="meta">Scope: ${salesReport.scope}${salesReport.category ? ` · ${salesReport.category}` : ''}</p>
          <p class="meta">Sales Person: ${salesReport.salesPersonName || 'All sales persons'}</p>
          <div class="summary">
            <div class="card"><strong>Orders</strong><div>${salesReport.orderCount}</div></div>
            <div class="card"><strong>Units Sold</strong><div>${salesReport.quantitySold}</div></div>
            <div class="card"><strong>Gross Sales</strong><div>${currency(salesReport.grossSales)}</div></div>
            <div class="card"><strong>Net Sales</strong><div>${currency(salesReport.netSales)}</div></div>
          </div>
          <table>
            <thead>
              <tr>
                <th>Product</th>
                <th>Category</th>
                <th>SKU</th>
                <th>Units</th>
                <th>Gross</th>
                <th>Discount</th>
                <th>Net</th>
              </tr>
            </thead>
            <tbody>${rows}</tbody>
          </table>
        </body>
      </html>
    `);
    printWindow.document.close();
    printWindow.focus();
    printWindow.print();
  };

  return (
    <div className="page">
      <PageHeader
        eyebrow="Reports"
        title="Sales and replenishment dashboard"
        description="Track today’s sales performance and quickly see which products need restocking attention."
      />

      <div className="report-tabs" role="tablist" aria-label="Report sections">
        <button
          type="button"
          role="tab"
          aria-selected={activeReportTab === 'dashboard'}
          className={`report-tab-btn ${activeReportTab === 'dashboard' ? 'is-active' : ''}`}
          onClick={() => setActiveReportTab('dashboard')}
        >
          Dashboard
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeReportTab === 'sales'}
          className={`report-tab-btn ${activeReportTab === 'sales' ? 'is-active' : ''}`}
          onClick={() => setActiveReportTab('sales')}
        >
          Sales report
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={activeReportTab === 'payments'}
          className={`report-tab-btn ${activeReportTab === 'payments' ? 'is-active' : ''}`}
          onClick={() => setActiveReportTab('payments')}
        >
          Payments
        </button>
      </div>

      {activeReportTab === 'sales' ? (
        <Panel
          title="Monthly and annual sales report"
          subtitle="Filter by period, category, or item and print the current sales view."
        >
          <div className="report-builder-grid">
            <label className="date-field">
              <span>Report period</span>
              <select value={salesPeriod} onChange={(e) => setSalesPeriod(e.target.value)}>
                <option value="MONTHLY">Monthly</option>
                <option value="ANNUAL">Annual</option>
              </select>
            </label>

            {salesPeriod === 'MONTHLY' ? (
              <label className="date-field">
                <span>Month</span>
                <input
                  type="month"
                  max={currentMonth}
                  value={salesMonth}
                  onChange={(e) => setSalesMonth(e.target.value)}
                />
              </label>
            ) : (
              <label className="date-field">
                <span>Year</span>
                <select value={salesYear} onChange={(e) => setSalesYear(e.target.value)}>
                  {salesYearOptions.map((yearOption) => (
                    <option key={yearOption} value={yearOption}>{yearOption}</option>
                  ))}
                </select>
              </label>
            )}

            <label className="date-field">
              <span>Sales scope</span>
              <select value={salesScope} onChange={(e) => setSalesScope(e.target.value)}>
                <option value="ALL">All items</option>
                <option value="CATEGORY">Category</option>
                <option value="PRODUCT">Item</option>
              </select>
            </label>

            <label className="date-field">
              <span>Sales person</span>
              <select value={salesPersonFilter} onChange={(e) => setSalesPersonFilter(e.target.value)}>
                <option value="">All sales persons</option>
                {salesPeople.map((salesPerson) => (
                  <option key={salesPerson.id} value={salesPersonValue(salesPerson)}>{salesPerson.displayName}</option>
                ))}
              </select>
            </label>

            {salesScope === 'CATEGORY' ? (
              <label className="date-field">
                <span>Category</span>
                <select value={salesCategory} onChange={(e) => setSalesCategory(e.target.value)}>
                  <option value="">Choose category</option>
                  {categoryOptions.map((category) => (
                    <option key={category.id} value={category.code}>{category.displayName}</option>
                  ))}
                </select>
              </label>
            ) : null}

            {salesScope === 'PRODUCT' ? (
              <label className="date-field">
                <span>Item</span>
                <select value={salesProductId} onChange={(e) => setSalesProductId(e.target.value)}>
                  <option value="">Choose item</option>
                  {reportProducts.map((product) => (
                    <option key={product.id} value={product.id}>{product.name} · {product.sku}</option>
                  ))}
                </select>
              </label>
            ) : null}

            <div className="report-builder-actions">
              <button
                type="button"
                className="primary-btn"
                onClick={() => loadSalesReport()}
                disabled={
                  salesLoading
                  || (salesScope === 'CATEGORY' && !salesCategory)
                  || (salesScope === 'PRODUCT' && !salesProductId)
                }
              >
                {salesLoading ? 'Generating...' : 'Generate report'}
              </button>
              <button
                type="button"
                className="ghost-btn"
                onClick={printSalesReport}
                disabled={!salesReport}
              >
                Print report
              </button>
            </div>
          </div>

          {salesError ? <p className="error-text">{salesError}</p> : null}

          {(salesScope === 'CATEGORY' && !salesCategory) ? (
            <p className="field-note">Choose a category to load the category sales report.</p>
          ) : null}

          {(salesScope === 'PRODUCT' && !salesProductId) ? (
            <p className="field-note">Choose an item to load the item sales report.</p>
          ) : null}

          {salesReport ? (
            <>
              <div className="metric-grid report-metric-grid">
                <MetricCard label="Orders" value={salesReport.orderCount} />
                <MetricCard label="Units Sold" value={salesReport.quantitySold} />
                <MetricCard label="Gross Sales" value={currency(salesReport.grossSales)} tone="accent" />
                <MetricCard label="Net Sales" value={currency(salesReport.netSales)} />
              </div>
              <DataTable
                columns={[
                  { key: 'productName', label: 'Product' },
                  { key: 'category', label: 'Category' },
                  { key: 'sku', label: 'SKU' },
                  { key: 'quantitySold', label: 'Units' },
                  { key: 'grossSales', label: 'Gross', render: (row) => currency(row.grossSales) },
                  { key: 'discount', label: 'Discount', render: (row) => currency(row.discount) },
                  { key: 'netSales', label: 'Net', render: (row) => currency(row.netSales) }
                ]}
                rows={salesReport.rows || []}
                emptyMessage="No sales matched this report."
              />
            </>
          ) : null}
        </Panel>
      ) : activeReportTab === 'payments' ? (
        <Panel
          title="Razorpay diagnostics"
          subtitle="Inspect payment order creation, verification, status checks, and webhooks without opening server logs."
        >
          <div className="report-builder-grid">
            <label className="date-field">
              <span>From</span>
              <input
                type="date"
                max={today}
                value={paymentFilters.fromDate}
                onChange={(e) => setPaymentFilters((filters) => ({ ...filters, fromDate: e.target.value }))}
              />
            </label>
            <label className="date-field">
              <span>To</span>
              <input
                type="date"
                max={today}
                value={paymentFilters.toDate}
                onChange={(e) => setPaymentFilters((filters) => ({ ...filters, toDate: e.target.value }))}
              />
            </label>
            <label className="date-field">
              <span>Operation</span>
              <select
                value={paymentFilters.operation}
                onChange={(e) => setPaymentFilters((filters) => ({ ...filters, operation: e.target.value }))}
              >
                <option value="">All operations</option>
                <option value="CREATE_ORDER">Create order</option>
                <option value="VERIFY_PAYMENT">Verify payment</option>
                <option value="STATUS_CHECK">Status check</option>
                <option value="WEBHOOK">Webhook</option>
              </select>
            </label>
            <label className="date-field">
              <span>Status</span>
              <select
                value={paymentFilters.status}
                onChange={(e) => setPaymentFilters((filters) => ({ ...filters, status: e.target.value }))}
              >
                <option value="">All statuses</option>
                <option value="SUCCESS">Success</option>
                <option value="FAILED">Failed</option>
                <option value="PENDING">Pending</option>
                <option value="LOCAL_TEST">Local test</option>
                <option value="RECEIVED">Webhook received</option>
              </select>
            </label>
            <label className="date-field">
              <span>Search</span>
              <input
                placeholder="Order id, payment id, receipt, error"
                value={paymentFilters.search}
                onChange={(e) => setPaymentFilters((filters) => ({ ...filters, search: e.target.value }))}
              />
            </label>
            <div className="report-builder-actions">
              <button
                type="button"
                className="primary-btn"
                onClick={() => loadPaymentTransactions(0)}
                disabled={paymentLoading}
              >
                {paymentLoading ? 'Refreshing...' : 'Refresh diagnostics'}
              </button>
            </div>
          </div>

          {paymentError ? <p className="error-text">{paymentError}</p> : null}

          <div className="payment-debug-layout">
            <DataTable
              columns={[
                { key: 'createdAt', label: 'Created', render: (row) => formatDate(row.createdAt) },
                { key: 'operation', label: 'Operation' },
                {
                  key: 'status',
                  label: 'Status',
                  render: (row) => <span className={`marketing-status-badge ${paymentStatusTone(row.status)}`}>{row.status}</span>
                },
                { key: 'orderNumber', label: 'Order' },
                { key: 'gatewayOrderId', label: 'Razorpay Order' },
                { key: 'gatewayPaymentId', label: 'Payment ID' },
                { key: 'amount', label: 'Amount', render: (row) => currency(row.amount) },
                { key: 'gatewayStatus', label: 'Gateway status' },
                { key: 'errorMessage', label: 'Error' },
                {
                  key: 'actions',
                  label: 'Action',
                  render: (row) => (
                    <button
                      type="button"
                      className="ghost-btn compact-btn"
                      onClick={() => setSelectedPayment(row)}
                    >
                      View
                    </button>
                  )
                }
              ]}
              rows={paymentPage.items || []}
              emptyMessage="No payment diagnostics match these filters."
              pagination={paymentPage}
              onPageChange={loadPaymentTransactions}
            />

            <aside className="payment-debug-detail">
              <div className="panel-head">
                <h3>Selected event</h3>
                <p>{selectedPayment ? `${selectedPayment.operation} · ${formatDate(selectedPayment.createdAt)}` : 'Choose a payment row to inspect payloads.'}</p>
              </div>
              {selectedPayment ? (
                <>
                  <div className="payment-debug-meta">
                    <span>Provider</span>
                    <strong>{selectedPayment.provider}</strong>
                    <span>Status</span>
                    <strong>{selectedPayment.status}</strong>
                    <span>Signature</span>
                    <strong>{selectedPayment.signatureStatus || '—'}</strong>
                    <span>Order</span>
                    <strong>{selectedPayment.orderNumber || selectedPayment.gatewayOrderId || '—'}</strong>
                    <span>Gateway payment</span>
                    <strong>{selectedPayment.gatewayPaymentId || '—'}</strong>
                  </div>
                  {selectedPayment.errorMessage ? <p className="error-text">{selectedPayment.errorMessage}</p> : null}
                  <h4>Request payload</h4>
                  <pre className="payment-debug-pre">{prettyPayload(selectedPayment.requestPayload)}</pre>
                  <h4>Response / webhook payload</h4>
                  <pre className="payment-debug-pre">{prettyPayload(selectedPayment.responsePayload)}</pre>
                </>
              ) : null}
            </aside>
          </div>
        </Panel>
      ) : (
        <>
          <div className="page-actions report-dashboard-toolbar">
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
              <span>Sales person</span>
              <select value={salesPersonFilter} onChange={(e) => setSalesPersonFilter(e.target.value)}>
                <option value="">All sales persons</option>
                {salesPeople.map((salesPerson) => (
                  <option key={salesPerson.id} value={salesPersonValue(salesPerson)}>{salesPerson.displayName}</option>
                ))}
              </select>
            </label>
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

          <Panel
            title={isWebsiteOrderView ? 'Website order priority queue' : 'Orders in selected range'}
            subtitle={isWebsiteOrderView
              ? "Showing pending website orders first. If none are pending, today's website orders are shown."
              : 'Review both website orders and billing orders in the chosen date range, with live delivery status for online purchases.'}
          >
            {orderStatusFeedback ? (
              <p className="storefront-feedback" role="status" aria-live="polite">
                {orderStatusFeedback}
              </p>
            ) : null}
            <DataTable
              columns={[
                { key: 'referenceNumber', label: 'Reference No.' },
                { key: 'source', label: 'Source' },
                {
                  key: 'status',
                  label: 'Delivery Status',
                  render: (row) => {
                    const source = String(row.source || '').toUpperCase();
                    if (source !== 'WEBSITE' || !row.id) {
                      return row.status || '-';
                    }
                    return (
                      <select
                        className="order-status-select"
                        value={row.status || ''}
                        disabled={updatingOrderId === row.id}
                        onChange={(event) => updateOrderDeliveryStatus(row, event.target.value)}
                      >
                        {!row.status ? <option value="">Select status</option> : null}
                        {ORDER_STATUS_OPTIONS.map((status) => (
                          <option key={status} value={status}>
                            {status.replaceAll('_', ' ')}
                          </option>
                        ))}
                      </select>
                    );
                  }
                },
                { key: 'createdAt', label: 'Date', render: (row) => formatDate(row.createdAt) },
                { key: 'customerName', label: 'Customer' },
                { key: 'customerMobile', label: 'Mobile' },
                { key: 'salesPersonName', label: 'Sales Person' },
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
        </>
      )}
    </div>
  );
}
