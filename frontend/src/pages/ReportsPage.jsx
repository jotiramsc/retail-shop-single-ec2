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

export default function ReportsPage() {
  const [daily, setDaily] = useState(null);
  const [lowStockPage, setLowStockPage] = useState({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [orderFeed, setOrderFeed] = useState({ orders: [] });
  const [reportFromDate, setReportFromDate] = useState(today);
  const [customerFilter, setCustomerFilter] = useState('');
  const [customerSuggestions, setCustomerSuggestions] = useState([]);
  const [customerSearchFocused, setCustomerSearchFocused] = useState(false);
  const [error, setError] = useState('');
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

  const loadReportOptions = async () => {
    try {
      const [categories, productsPage] = await Promise.all([
        retailService.getProductCategoryOptions(),
        retailService.getProducts({ page: 0, size: 500 })
      ]);
      setCategoryOptions(categories);
      setReportProducts(productsPage.items || []);
    } catch {
      setCategoryOptions([]);
      setReportProducts([]);
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
        productId: nextFilters.scope === 'PRODUCT' ? nextFilters.productId : undefined
      });
      setSalesReport(response);
    } catch (requestError) {
      setSalesError(getApiErrorMessage(requestError, 'Unable to load the sales report.'));
      setSalesReport(null);
    } finally {
      setSalesLoading(false);
    }
  };

  useEffect(() => {
    loadReports(reportFromDate);
    loadReportOptions();
    loadSalesReport();
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

      <Panel
        title="Monthly and annual sales report"
        subtitle="Generate a printable sales report for all items, one category, or a specific item."
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
              disabled={salesLoading}
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
