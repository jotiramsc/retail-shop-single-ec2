import { useEffect, useState } from 'react';
import { Link, Navigate, useSearchParams } from 'react-router-dom';
import { retailService } from '../services/retailService';
import {
  clearCustomerSession,
  getStoredCustomerSession,
  isCustomerAuthError
} from '../utils/auth';
import { currency } from '../utils/format';

export default function OrdersPage() {
  const [customerSession, setCustomerSession] = useState(() => getStoredCustomerSession());
  const [searchParams] = useSearchParams();
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    retailService.getOrders()
      .then((response) => {
        setOrders(response || []);
      })
      .catch((err) => {
        if (isCustomerAuthError(err)) {
          clearCustomerSession();
          setCustomerSession(null);
          return;
        }
        setError(err.response?.data?.message || 'Unable to load orders.');
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  if (!customerSession?.token) {
    return <Navigate to="/customer-login?redirect=/orders" replace />;
  }

  return (
    <main className="glow-site customer-flow-page">
      <section className="customer-flow-shell">
        <div className="customer-flow-head">
          <div>
            <p className="glow-kicker">Orders</p>
            <h1>Your recent orders</h1>
            <p>Track placed orders and review what was confirmed from the checkout flow.</p>
          </div>
          <Link className="ghost-btn compact-btn" to="/products">Shop more</Link>
        </div>

        {searchParams.get('placed') === '1' ? (
          <div className="customer-flow-panel customer-success-banner">
            <strong>Order placed successfully.</strong>
            <span>Your cart has been converted into an order and the latest status appears below.</span>
          </div>
        ) : null}

        {loading ? <p>Loading orders…</p> : null}
        {error ? <p className="error-text">{error}</p> : null}

        <div className="customer-orders-list">
          {orders.map((order) => (
            <article key={order.id} className="customer-flow-panel customer-order-card">
              <div className="customer-order-head">
                <div>
                  <p className="glow-kicker">{order.source === 'BILLING' ? 'Billing order' : 'Website order'}</p>
                  <h2>{order.orderNumber}</h2>
                  <span>{order.status}</span>
                </div>
                <strong>{currency(order.finalAmount)}</strong>
              </div>
              <div className="customer-order-meta">
                <span>Subtotal: {currency(order.subtotal)}</span>
                {order.couponCode ? <span>Coupon: {order.couponCode}</span> : null}
                <span>Discount: {currency(order.discount)}</span>
                <span>Tax: {currency(order.tax)}</span>
                <span>Delivery: {currency(order.delivery)}</span>
                <span>Payment: {order.paymentStatus}</span>
              </div>
              <div className="customer-checkout-items">
                {(order.items || []).map((item) => (
                  <div key={`${order.id}-${item.productId}`} className="customer-checkout-item">
                    <div>
                      <strong>{item.productName}</strong>
                      <span>{item.quantity} × {currency(item.price)}</span>
                    </div>
                    <strong>{currency(item.lineTotal)}</strong>
                  </div>
                ))}
              </div>
            </article>
          ))}
        </div>

        {!loading && !orders.length ? (
          <div className="customer-flow-panel customer-empty-state">
            <strong>No orders yet</strong>
            <span>Once you place an order, it will show up here.</span>
          </div>
        ) : null}
      </section>
    </main>
  );
}
