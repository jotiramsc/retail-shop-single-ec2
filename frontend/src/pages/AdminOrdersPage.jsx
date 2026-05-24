import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import DataTable from '../components/DataTable';
import { retailService } from '../services/retailService';
import { getStoredAuthSession } from '../utils/auth';
import { currency, formatDate } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

const today = new Date().toISOString().slice(0, 10);
const lastThirtyDays = new Date(Date.now() - 29 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
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

const shippingTimelineTemplate = [
  { status: 'PENDING', title: 'Order was placed', description: 'Your order has been placed successfully' },
  { status: 'CONFIRMED', title: 'Pick-up', description: 'Pick-up scheduled with courier' },
  { status: 'SHIPPED', title: 'Dispatched', description: 'Item has been picked up by courier' },
  { status: 'SHIPPED', title: 'Package arrived', description: 'Package arrived at sorting facility' },
  { status: 'SHIPPED', title: 'Dispatched for delivery', description: 'Package has left the sorting facility' },
  { status: 'DELIVERED', title: 'Delivery', description: 'Package will be delivered by tomorrow' }
];

const previewOrderPage = {
  invoices: [
    {
      id: 'preview-order-1',
      invoiceNumber: 'KPS-1024',
      customerName: 'Snehal Chikane',
      customerMobile: '+91 9764909221',
      salesPersonName: 'Store Admin',
      paymentMode: 'CASH',
      createdAt: new Date().toISOString(),
      totalAmount: 4200,
      discount: 250,
      finalAmount: 3950
    },
    {
      id: 'preview-order-2',
      invoiceNumber: 'KPS-1023',
      customerName: 'Meena Bhosale',
      customerMobile: '+91 8888770948',
      salesPersonName: 'Billing Staff',
      paymentMode: 'UPI',
      createdAt: new Date(Date.now() - 86400000).toISOString(),
      totalAmount: 8999,
      discount: 500,
      finalAmount: 8499
    }
  ],
  page: 0,
  totalPages: 1,
  totalItems: 2,
  hasNext: false,
  hasPrevious: false
};

const customerCrmPath = (order = {}) => {
  if (order.customerId) {
    return `/app/crm/customers/overview?customerId=${encodeURIComponent(order.customerId)}`;
  }
  const mobile = order.customerMobile || order.mobile || order.phone;
  return `/app/crm/customers/overview${mobile ? `?mobile=${encodeURIComponent(mobile)}` : ''}`;
};

const couponOffersPath = (couponCode) => `/app/campaigns/offers${couponCode ? `?coupon=${encodeURIComponent(couponCode)}` : ''}`;

function isLocalPreviewEnabled() {
  if (!import.meta.env.DEV || typeof window === 'undefined') return false;
  return ['localhost', '127.0.0.1', '::1'].includes(window.location.hostname);
}

function OrderStat({ icon, label, value, note, tone = 'primary' }) {
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

function normalizeReference(row) {
  return row?.referenceNumber || row?.invoiceNumber || row?.orderNumber || 'KPS order';
}

function normalizeSource(row, mode) {
  return String(row?.source || (mode === 'invoices' || mode === 'invoice-details' ? 'BILLING' : 'BILLING')).toUpperCase();
}

function statusRank(status) {
  const normalized = String(status || '').toUpperCase();
  if (normalized === 'DELIVERED' || normalized === 'COMPLETED') return 5;
  if (normalized === 'SHIPPED') return 4;
  if (normalized === 'CONFIRMED') return 1;
  if (normalized === 'CANCELLED' || normalized === 'RETURNED' || normalized === 'REFUND_INITIATED' || normalized === 'PAYMENT_FAILED') return 0;
  return normalized ? 0 : -1;
}

function AdminOrderDetails({ mode }) {
  const { orderId } = useParams();
  const location = useLocation();
  const stateOrder = location.state?.order || null;
  const [invoice, setInvoice] = useState(null);
  const [currentStatus, setCurrentStatus] = useState(stateOrder?.status || '');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const [updatingStatus, setUpdatingStatus] = useState(false);
  const source = normalizeSource(stateOrder, mode);
  const isWebsiteOrder = source === 'WEBSITE';
  const reference = normalizeReference(invoice || stateOrder);
  const customerName = invoice?.customerName || stateOrder?.customerName || 'Walk-in customer';
  const customerMobile = invoice?.customerMobile || stateOrder?.customerMobile || 'No mobile captured';
  const customerLink = customerCrmPath(invoice || stateOrder || {});
  const couponCode = invoice?.couponCode || stateOrder?.couponCode || '';
  const items = invoice?.items || stateOrder?.items || [];
  const subtotal = invoice?.totalAmount ?? stateOrder?.subtotal ?? stateOrder?.finalAmount ?? 0;
  const discount = invoice?.discount ?? stateOrder?.discount ?? 0;
  const tax = invoice?.tax ?? stateOrder?.tax ?? 0;
  const delivery = invoice?.delivery ?? stateOrder?.delivery ?? 0;
  const total = invoice?.finalAmount ?? stateOrder?.finalAmount ?? 0;
  const activeShippingRank = statusRank(currentStatus);

  useEffect(() => {
    if (!orderId || isWebsiteOrder) return;
    setLoading(true);
    setError('');
    retailService.getInvoice(orderId)
      .then(setInvoice)
      .catch((requestError) => {
        if (!stateOrder) {
          setError(getApiErrorMessage(requestError, 'Unable to load order details.'));
        }
      })
      .finally(() => setLoading(false));
  }, [isWebsiteOrder, orderId, stateOrder]);

  const updateWebsiteStatus = async (nextStatus) => {
    if (!isWebsiteOrder || !orderId) return;
    setUpdatingStatus(true);
    setError('');
    setSuccess('');
    try {
      await retailService.updateOrderStatus(orderId, { status: nextStatus });
      setCurrentStatus(nextStatus);
      setSuccess(`Order status moved to ${nextStatus.replaceAll('_', ' ')}.`);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to update website order status.'));
    } finally {
      setUpdatingStatus(false);
    }
  };

  return (
    <div className="sneat-module-page admin-order-detail-page">
      <div className="sneat-detail-header">
        <div>
          <Link className="ghost-btn compact-btn" to={mode === 'invoice-details' ? '/app/billing/invoices' : '/app/billing/orders'}>
            <i className="bx bx-chevron-left me-1" /> Back
          </Link>
          <div className="sneat-order-title-row">
            <h1>{mode === 'invoice-details' ? 'Invoice' : 'Order'} {reference}</h1>
            <span className="badge bg-label-success">{invoice || !isWebsiteOrder ? 'Paid' : stateOrder?.paymentStatus || 'Payment'}</span>
            <span className={`badge ${isWebsiteOrder ? 'bg-label-info' : 'bg-label-primary'}`}>
              {isWebsiteOrder ? currentStatus || 'Website order' : 'Shop order'}
            </span>
          </div>
          <p>{formatDate(invoice?.createdAt || stateOrder?.createdAt)} · {invoice?.salesPersonName || stateOrder?.salesPersonName || (isWebsiteOrder ? 'Website' : 'Store')}</p>
        </div>
      </div>

      {error ? <p className="error-text">{error}</p> : null}
      {success ? <p className="success-text">{success}</p> : null}

      <div className="sneat-order-detail-grid">
        <section className="sneat-card sneat-order-items-card">
          <div className="sneat-card-head">
            <div><small>Order Details</small><h3>Products</h3></div>
            {loading ? <span className="badge bg-label-info">Loading</span> : null}
          </div>
          <div className="table-responsive text-nowrap">
            <table className="table">
              <thead>
                <tr><th>Products</th><th>Price</th><th>Qty</th><th>Total</th></tr>
              </thead>
              <tbody>
                {items.length ? items.map((item, index) => (
                  <tr key={`${item.productId || item.productName}-${index}`}>
                    <td>
                      <div className="sneat-product-cell">
                        <span className="sneat-product-thumb"><i className="bx bx-package" /></span>
                        <div><strong>{item.productName || 'Product'}</strong><small>{item.sku || item.category || 'KPS item'}</small></div>
                      </div>
                    </td>
                    <td>{currency(item.unitPrice || item.price || 0)}</td>
                    <td>{item.quantity || 1}</td>
                    <td>{currency(item.lineTotal || (Number(item.unitPrice || item.price || 0) * Number(item.quantity || 1)))}</td>
                  </tr>
                )) : (
                  <tr><td colSpan="4" className="empty-cell">Line items are not available for this {isWebsiteOrder ? 'website order preview' : 'order'}.</td></tr>
                )}
              </tbody>
            </table>
          </div>
          <div className="sneat-order-total-box">
            <span>Subtotal <strong>{currency(subtotal)}</strong></span>
            <span>Discount <strong>{currency(discount)}</strong></span>
            {Number(tax || 0) > 0 ? <span>Tax <strong>{currency(tax)}</strong></span> : null}
            {isWebsiteOrder && Number(delivery || 0) > 0 ? <span>Delivery <strong>{currency(delivery)}</strong></span> : null}
            <span className="is-total">Total <strong>{currency(total)}</strong></span>
          </div>
        </section>

        <aside className="sneat-order-side-stack">
          <section className="sneat-card">
            <div className="sneat-card-head"><div><small>Customer</small><h3>Customer details</h3></div></div>
            <Link className="sneat-side-customer sneat-clickable-customer" to={customerLink}>
              <span className="sneat-avatar">{String(customerName || 'C').slice(0, 1).toUpperCase()}</span>
              <div><strong>{customerName}</strong><span>{customerMobile}</span></div>
            </Link>
            <div className="sneat-detail-list">
              <span><b>Payment</b>{invoice?.paymentMode || stateOrder?.paymentMode || stateOrder?.paymentStatus || 'Paid'}</span>
              <span><b>Coupon</b>{couponCode ? <Link className="sneat-table-link" to={couponOffersPath(couponCode)}>{couponCode}</Link> : 'No coupon'}</span>
              <span><b>Source</b>{isWebsiteOrder ? 'Website order' : 'Shop billing'}</span>
            </div>
          </section>

          {isWebsiteOrder ? (
            <section className="sneat-card sneat-shipping-activity-card">
              <div className="sneat-card-head"><div><small>Delivery</small><h3>Shipping activity</h3></div></div>
              <label className="sneat-status-control">
                <span>Website order status</span>
                <select
                  className="order-status-select"
                  value={currentStatus}
                  disabled={updatingStatus}
                  onChange={(event) => updateWebsiteStatus(event.target.value)}
                >
                  {!currentStatus ? <option value="">Select status</option> : null}
                  {ORDER_STATUS_OPTIONS.map((status) => (
                    <option key={status} value={status}>{status.replaceAll('_', ' ')}</option>
                  ))}
                </select>
              </label>
              <div className="sneat-shipping-timeline">
                {shippingTimelineTemplate.map((step, index) => {
                  const isDone = index <= activeShippingRank;
                  return (
                    <div className={`sneat-shipping-row ${isDone ? 'is-done' : ''}`} key={`${step.title}-${index}`}>
                      <span className="sneat-shipping-dot" />
                      <div>
                        <strong>{index === 0 ? `${step.title} (Order ID: #${reference})` : step.title}</strong>
                        <p>{step.description}</p>
                      </div>
                      <time>{index === 0 ? formatDate(stateOrder?.createdAt) : isDone ? 'Updated' : 'Pending'}</time>
                    </div>
                  );
                })}
                {['CANCELLED', 'RETURNED', 'REFUND_INITIATED', 'PAYMENT_FAILED'].includes(String(currentStatus || '').toUpperCase()) ? (
                  <div className="sneat-shipping-row is-exception">
                    <span className="sneat-shipping-dot" />
                    <div>
                      <strong>{currentStatus.replaceAll('_', ' ')}</strong>
                      <p>This website order has moved out of the normal delivery flow.</p>
                    </div>
                    <time>Current</time>
                  </div>
                ) : null}
              </div>
            </section>
          ) : (
            <section className="sneat-card">
              <div className="sneat-card-head"><div><small>Fulfilment</small><h3>Shop order</h3></div></div>
              <p className="text-muted mb-0">Counter billing orders do not use website delivery status. Use this page for bill items, payment, salesperson, and customer details.</p>
              <Link className="btn btn-primary btn-sm mt-3" to={`/app/billing?editInvoiceId=${encodeURIComponent(orderId)}`}>
                <i className="bx bx-edit me-1" /> Edit in checkout
              </Link>
            </section>
          )}
        </aside>
      </div>
    </div>
  );
}

export default function AdminOrdersPage({ mode = 'orders' }) {
  const [fromDate, setFromDate] = useState(lastThirtyDays);
  const [toDate, setToDate] = useState(today);
  const [customerName, setCustomerName] = useState('');
  const [ordersPage, setOrdersPage] = useState({ invoices: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [sourceFilter, setSourceFilter] = useState(mode === 'invoices' ? 'BILLING' : 'ALL');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);
  const [updatingOrderId, setUpdatingOrderId] = useState('');

  const loadOrders = async (page = 0) => {
    setLoading(true);
    setError('');
    try {
      const response = await retailService.getReportInvoices({
        fromDate,
        toDate,
        customerName: customerName.trim(),
        page,
        size: 10
      });
      setOrdersPage(response || { invoices: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
    } catch (requestError) {
      if (isLocalPreviewEnabled()) {
        setOrdersPage(previewOrderPage);
      } else {
        setError(getApiErrorMessage(requestError, 'Unable to load orders.'));
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadOrders(0);
  }, []);

  if (mode === 'order-details' || mode === 'invoice-details') {
    return <AdminOrderDetails mode={mode} />;
  }

  const allOrders = ordersPage.orders || ordersPage.invoices || [];
  const orders = allOrders.filter((order) => sourceFilter === 'ALL' || normalizeSource(order, mode) === sourceFilter);
  const revenue = orders.reduce((sum, invoice) => sum + Number(invoice.finalAmount || 0), 0);
  const discount = orders.reduce((sum, invoice) => sum + Number(invoice.discount || 0), 0);
  const title = mode === 'invoices' ? 'Invoices' : 'Orders';
  const auth = getStoredAuthSession();
  const isAdminUser = ['ADMIN', 'OWNER'].includes(auth?.role);

  const updateOrderDeliveryStatus = async (row, nextStatus) => {
    const source = normalizeSource(row, mode);
    if (!row?.id || source !== 'WEBSITE') return;
    setUpdatingOrderId(row.id);
    setError('');
    setSuccess('');
    try {
      await retailService.updateOrderStatus(row.id, { status: nextStatus });
      setOrdersPage((current) => ({
        ...current,
        orders: (current.orders || current.invoices || []).map((order) => (
          order.id === row.id ? { ...order, status: nextStatus } : order
        )),
        invoices: (current.invoices || []).map((order) => (
          order.id === row.id ? { ...order, status: nextStatus } : order
        ))
      }));
      setSuccess(`${row.referenceNumber || 'Website order'} moved to ${nextStatus.replaceAll('_', ' ')}.`);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to update website order status.'));
    } finally {
      setUpdatingOrderId('');
    }
  };

  const deleteRecord = async (row) => {
    if (!isAdminUser || !row?.id) return;
    setError('');
    setSuccess('');
    try {
      if (mode === 'invoices') {
        await retailService.deleteInvoice(row.id);
      } else {
        await retailService.deleteOrder(row.id);
      }
      setSuccess(`${normalizeReference(row)} deleted.`);
      await loadOrders(Math.max(ordersPage.page || 0, 0));
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, `Unable to delete ${mode === 'invoices' ? 'invoice' : 'order'}.`));
    }
  };

  return (
    <div className="sneat-module-page admin-orders-page">
      <section className="sneat-page-title">
        <div>
          <span className="sneat-eyebrow">eCommerce</span>
          <h1>{title}</h1>
          <p>Sneat-style order list using existing billing/report invoice APIs.</p>
        </div>
        <button type="button" className="btn btn-primary" onClick={() => loadOrders(0)} disabled={loading}>
          <i className="bx bx-refresh me-1" /> Refresh
        </button>
      </section>

      <section className="sneat-stat-grid">
        <OrderStat icon="bx-receipt" label="Records" value={ordersPage.totalItems || orders.length} note="Filtered result" />
        <OrderStat icon="bx-rupee" label="Revenue" value={currency(revenue)} note="Current page total" tone="success" />
        <OrderStat icon="bx-purchase-tag" label="Discounts" value={currency(discount)} note="Offer/manual discount" tone="warning" />
        <OrderStat icon="bx-calendar" label="Range" value={`${fromDate} to ${toDate}`} note="Active filter" tone="info" />
      </section>

      <section className="sneat-card order-list-card">
        <div className="sneat-card-head">
          <div><small>Filters</small><h3>{title} list</h3></div>
        </div>
        <div className="sneat-filter-grid order-filter-grid">
          <label><span>Customer</span><input value={customerName} onChange={(event) => setCustomerName(event.target.value)} placeholder="Customer name" /></label>
          <label><span>From</span><input type="date" value={fromDate} max={toDate} onChange={(event) => setFromDate(event.target.value)} /></label>
          <label><span>To</span><input type="date" value={toDate} min={fromDate} max={today} onChange={(event) => setToDate(event.target.value)} /></label>
          <button type="button" className="btn btn-primary align-self-end" onClick={() => loadOrders(0)} disabled={loading}>
            <i className="bx bx-filter-alt me-1" /> Apply
          </button>
        </div>
        <div className="admin-order-source-tabs" role="group" aria-label="Order source">
          {[
            ['ALL', 'All orders'],
            ['BILLING', 'Shop orders'],
            ['WEBSITE', 'Website orders']
          ].map(([value, label]) => (
            <button
              key={value}
              type="button"
              className={sourceFilter === value ? 'is-active' : ''}
              onClick={() => setSourceFilter(value)}
            >
              {label}
            </button>
          ))}
        </div>
        {error ? <p className="error-text">{error}</p> : null}
        {success ? <p className="success-text">{success}</p> : null}
        <DataTable
          columns={[
            { key: 'referenceNumber', label: mode === 'invoices' ? 'Invoice' : 'Order', render: (row) => (
              <Link className="sneat-table-link" to={`/app/billing/${mode === 'invoices' ? 'invoices' : 'orders'}/${row.id}`} state={{ order: row }}>
                {normalizeReference(row)}
              </Link>
            ) },
            { key: 'createdAt', label: 'Date', render: (row) => formatDate(row.createdAt) },
            { key: 'customerName', label: 'Customer', render: (row) => (
              <Link className="sneat-table-link" to={customerCrmPath(row)}>
                {row.customerName || 'Customer'}
              </Link>
            ) },
            { key: 'customerMobile', label: 'Mobile', render: (row) => (
              <Link className="sneat-table-link" to={customerCrmPath(row)}>
                {row.customerMobile || 'No mobile'}
              </Link>
            ) },
            { key: 'source', label: 'Source', render: (row) => normalizeSource(row, mode) === 'WEBSITE' ? 'Website' : 'Shop' },
            { key: 'salesPersonName', label: 'Sales person', render: (row) => row.salesPersonName || 'Website' },
            { key: 'paymentMode', label: 'Payment' },
            { key: 'finalAmount', label: 'Total', render: (row) => currency(row.finalAmount) },
            { key: 'status', label: 'Status', render: (row) => {
              const source = normalizeSource(row, mode);
              if (source !== 'WEBSITE') {
                return <span className="badge bg-label-success">{row.status || 'Completed'}</span>;
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
                    <option key={status} value={status}>{status.replaceAll('_', ' ')}</option>
                  ))}
                </select>
              );
            } },
            { key: 'actions', label: 'Action', render: (row) => (
              <div className="table-action-group">
                <Link className="btn btn-sm btn-outline-primary" to={`/app/billing/${mode === 'invoices' ? 'invoices' : 'orders'}/${row.id}`} state={{ order: row }}>
                  View
                </Link>
                {normalizeSource(row, mode) !== 'WEBSITE' ? (
                  <Link className="btn btn-sm btn-primary" to={`/app/billing?editInvoiceId=${encodeURIComponent(row.id)}`}>
                    Edit
                  </Link>
                ) : null}
                {isAdminUser ? (
                  <button type="button" className="danger-btn compact-btn" onClick={() => deleteRecord(row)}>
                    Delete
                  </button>
                ) : null}
              </div>
            ) }
          ]}
          rows={orders}
          emptyMessage={`No ${title.toLowerCase()} found for this filter.`}
          pagination={ordersPage}
          onPageChange={loadOrders}
        />
      </section>
    </div>
  );
}
