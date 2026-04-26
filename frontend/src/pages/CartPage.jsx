import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { retailService } from '../services/retailService';
import { currency } from '../utils/format';
import {
  clearCustomerSession,
  getStoredCustomerSession,
  isCustomerAuthError
} from '../utils/auth';
import {
  getGuestCartItems,
  removeGuestCartItem,
  updateGuestCartItem
} from '../utils/cart';
import {
  clearStoredCheckoutCouponCode,
  getStoredCheckoutCouponCode,
  storeCheckoutCouponCode
} from '../utils/checkout';
import { getAppliedDiscountDetails } from '../utils/offers';

const fallbackImage = '/assets/glowjewels/no_image.png';

export default function CartPage() {
  const navigate = useNavigate();
  const [customerSession, setCustomerSession] = useState(() => getStoredCustomerSession());
  const [items, setItems] = useState([]);
  const [quote, setQuote] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const syncCustomerCartQuote = async (preferredCouponCode = getStoredCheckoutCouponCode()) => {
    const normalizedCouponCode = String(preferredCouponCode || '').trim().toUpperCase();

    try {
      const nextQuote = await retailService.getApplicableOffers(normalizedCouponCode || undefined);
      setItems(nextQuote?.cart?.items || []);
      setQuote(nextQuote || null);
      if (nextQuote?.appliedCouponCode) {
        storeCheckoutCouponCode(nextQuote.appliedCouponCode);
      } else {
        clearStoredCheckoutCouponCode();
      }
      return nextQuote;
    } catch (err) {
      const status = Number(err?.response?.status || 0);
      if (normalizedCouponCode && !isCustomerAuthError(err) && status >= 400 && status < 500) {
        clearStoredCheckoutCouponCode();
        const fallbackQuote = await retailService.getApplicableOffers();
        setItems(fallbackQuote?.cart?.items || []);
        setQuote(fallbackQuote || null);
        return fallbackQuote;
      }
      throw err;
    }
  };

  useEffect(() => {
    const loadCart = async () => {
      setLoading(true);
      setError('');
      try {
        if (customerSession?.token) {
          try {
            await syncCustomerCartQuote();
            return;
          } catch (err) {
            if (!isCustomerAuthError(err)) {
              throw err;
            }
            clearCustomerSession();
            clearStoredCheckoutCouponCode();
            setCustomerSession(null);
            setQuote(null);
          }
        }

        const [catalog, guestItems] = await Promise.all([
          retailService.getPublicCatalog(),
          Promise.resolve(getGuestCartItems())
        ]);
        const catalogMap = new Map((catalog || []).map((product) => [product.id, product]));
        setItems(guestItems
          .map((item) => {
            const product = catalogMap.get(item.productId);
            if (!product) {
              return null;
            }
            const price = Number(product.sellingPrice || 0);
            const quantity = Number(item.quantity || 1);
            return {
              productId: product.id,
              name: product.name,
              sku: product.sku,
              category: product.category,
              imageDataUrl: product.imageDataUrl,
              price,
              quantity,
              stockAvailable: product.quantity,
              lineTotal: price * quantity
            };
          })
          .filter(Boolean));
        setQuote(null);
      } catch (err) {
        setError(err.response?.data?.message || 'Unable to load the cart right now.');
      } finally {
        setLoading(false);
      }
    };

    loadCart();
  }, [customerSession?.token]);

  const subtotal = useMemo(
    () => Number(quote?.subtotal ?? items.reduce((sum, item) => sum + Number(item.lineTotal || Number(item.price || 0) * Number(item.quantity || 0)), 0)),
    [items, quote]
  );
  const appliedDiscountDetails = useMemo(() => getAppliedDiscountDetails(quote), [quote]);
  const appliedDiscount = appliedDiscountDetails.totalDiscount;
  const cartTotal = Number(quote?.finalTotal ?? subtotal);
  const taxAmount = Number(quote?.tax ?? 0);
  const deliveryAmount = Number(quote?.delivery ?? 0);
  const shouldShowDiscountBreakdown = appliedDiscountDetails.entries.length > 1
    || appliedDiscountDetails.entries.some((entry) => entry.caption);

  const updateQuantity = async (productId, quantity) => {
    const safeQuantity = Math.max(1, Number(quantity || 1));
    setError('');
    try {
      if (customerSession?.token) {
        try {
          await retailService.updateCart({ productId, quantity: safeQuantity });
          await syncCustomerCartQuote();
        } catch (err) {
          if (!isCustomerAuthError(err)) {
            throw err;
          }
          clearCustomerSession();
          clearStoredCheckoutCouponCode();
          setCustomerSession(null);
          setQuote(null);
          updateGuestCartItem(productId, safeQuantity);
          setItems((current) => current.map((item) => (
            item.productId === productId
              ? { ...item, quantity: safeQuantity, lineTotal: Number(item.price || 0) * safeQuantity }
              : item
          )));
          setError('Customer session expired. The item quantity was updated in your guest cart.');
        }
      } else {
        updateGuestCartItem(productId, safeQuantity);
        setItems((current) => current.map((item) => (
          item.productId === productId
            ? { ...item, quantity: safeQuantity, lineTotal: Number(item.price || 0) * safeQuantity }
            : item
        )));
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to update cart item.');
    }
  };

  const removeItem = async (productId) => {
    setError('');
    try {
      if (customerSession?.token) {
        try {
          await retailService.removeFromCart(productId);
          await syncCustomerCartQuote();
        } catch (err) {
          if (!isCustomerAuthError(err)) {
            throw err;
          }
          clearCustomerSession();
          clearStoredCheckoutCouponCode();
          setCustomerSession(null);
          setQuote(null);
          removeGuestCartItem(productId);
          setItems((current) => current.filter((item) => item.productId !== productId));
          setError('Customer session expired. The item was removed from your guest cart.');
        }
      } else {
        removeGuestCartItem(productId);
        setItems((current) => current.filter((item) => item.productId !== productId));
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to remove cart item.');
    }
  };

  const proceedToCheckout = () => {
    if (!items.length) {
      return;
    }
    if (!customerSession?.token) {
      navigate('/customer-login?redirect=/checkout');
      return;
    }
    navigate('/checkout');
  };

  return (
    <main className="glow-site customer-flow-page">
      <section className="customer-flow-shell">
        <div className="customer-flow-head">
          <div>
            <p className="glow-kicker">Cart</p>
            <h1>Your selected pieces</h1>
            <p>Review quantities, keep your guest cart safe, and continue to checkout when you are ready.</p>
          </div>
          <Link className="ghost-btn compact-btn" to="/products">Continue shopping</Link>
        </div>

        {loading ? <p>Loading cart…</p> : null}
        {error ? <p className="error-text">{error}</p> : null}

        {!loading && !items.length ? (
          <div className="customer-flow-panel customer-empty-state">
            <strong>Your cart is empty</strong>
            <span>Add products from the storefront and they will appear here.</span>
            <Link className="glow-account-btn" to="/products">Browse products</Link>
          </div>
        ) : null}

        {items.length ? (
          <div className="customer-cart-layout">
            <section className="customer-flow-panel customer-cart-list">
              {items.map((item) => (
                <article key={item.productId} className="customer-cart-item">
                  <img src={item.imageDataUrl || fallbackImage} alt={item.name} />
                  <div className="customer-cart-copy">
                    <p>{item.category}</p>
                    <h3>{item.name}</h3>
                    <span>{item.sku}</span>
                  </div>
                  <label className="customer-qty-field">
                    Qty
                    <input
                      type="number"
                      min="1"
                      max={item.stockAvailable || 99}
                      value={item.quantity}
                      onChange={(event) => updateQuantity(item.productId, event.target.value)}
                    />
                  </label>
                  <strong>{currency(item.lineTotal)}</strong>
                  <button type="button" className="ghost-btn compact-btn danger-btn" onClick={() => removeItem(item.productId)}>
                    Remove
                  </button>
                </article>
              ))}
            </section>

            <aside className="customer-flow-panel customer-cart-summary">
              <p className="glow-kicker">Summary</p>
              <div className="customer-summary-row">
                <span>Items</span>
                <strong>{items.length}</strong>
              </div>
              <div className="customer-summary-row">
                <span>Subtotal</span>
                <strong>{currency(subtotal)}</strong>
              </div>
              {quote?.appliedCouponCode ? (
                <div className="customer-summary-row">
                  <div className="customer-summary-copy">
                    <strong>Applied Coupon: {quote.appliedCouponCode}</strong>
                  </div>
                </div>
              ) : appliedDiscount > 0 ? (
                <div className="customer-summary-row">
                  <div className="customer-summary-copy">
                    <strong>Offer Applied: {appliedDiscountDetails.title}</strong>
                  </div>
                </div>
              ) : null}
              {appliedDiscount > 0 ? (
                <>
                  <div className="customer-summary-row">
                    <span>Discount</span>
                    <strong>- {currency(appliedDiscount)}</strong>
                  </div>
                  {shouldShowDiscountBreakdown && !quote?.appliedCouponCode ? (
                    <div className="customer-discount-breakdown">
                      {appliedDiscountDetails.entries.map((entry) => (
                        <div key={entry.id} className="customer-discount-breakdown-row">
                          <div>
                            <strong>{entry.name}</strong>
                            {entry.caption ? <span>{entry.caption}</span> : null}
                          </div>
                          <strong>- {currency(entry.amount)}</strong>
                        </div>
                      ))}
                    </div>
                  ) : null}
                </>
              ) : null}
              <div className="customer-summary-row">
                <span>Tax</span>
                <strong>{currency(taxAmount)}</strong>
              </div>
              <div className="customer-summary-row">
                <span>Delivery</span>
                <strong>{currency(deliveryAmount)}</strong>
              </div>
              <div className="customer-summary-row total">
                <span>Cart total</span>
                <strong>{currency(cartTotal)}</strong>
              </div>
              <button type="button" className="glow-account-btn customer-full-btn" onClick={proceedToCheckout}>
                {customerSession?.token ? 'Proceed to checkout' : 'Login with OTP to checkout'}
              </button>
            </aside>
          </div>
        ) : null}
      </section>
    </main>
  );
}
