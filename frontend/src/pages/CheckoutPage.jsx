import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { retailService } from '../services/retailService';
import {
  clearCustomerSession,
  getStoredCustomerSession,
  isCustomerAuthError
} from '../utils/auth';
import {
  clearStoredCheckoutCouponCode,
  getStoredCheckoutCouponCode,
  storeCheckoutCouponCode
} from '../utils/checkout';
import { currency } from '../utils/format';
import { getAppliedDiscountDetails, getOfferDisplayLabel } from '../utils/offers';

const initialAddress = {
  label: 'Home',
  recipientName: '',
  mobile: '',
  line1: '',
  line2: '',
  landmark: '',
  city: 'Pune',
  state: 'Maharashtra',
  pincode: '',
  latitude: '',
  longitude: ''
};

const PHONEPE_PENDING_ORDER_KEY = 'retail.phonepe.pending-order';

const readPendingPhonePeOrder = () => {
  if (typeof window === 'undefined') {
    return null;
  }
  const rawValue = window.sessionStorage.getItem(PHONEPE_PENDING_ORDER_KEY);
  if (!rawValue) {
    return null;
  }
  try {
    return JSON.parse(rawValue);
  } catch {
    window.sessionStorage.removeItem(PHONEPE_PENDING_ORDER_KEY);
    return null;
  }
};

const writePendingPhonePeOrder = (payload) => {
  if (typeof window === 'undefined') {
    return;
  }
  window.sessionStorage.setItem(PHONEPE_PENDING_ORDER_KEY, JSON.stringify(payload));
};

const clearPendingPhonePeOrder = () => {
  if (typeof window === 'undefined') {
    return;
  }
  window.sessionStorage.removeItem(PHONEPE_PENDING_ORDER_KEY);
};

const normalizePaymentProvider = (value) => String(value || 'PHONEPE').trim().toUpperCase();

const loadRazorpayCheckout = () => new Promise((resolve) => {
  if (window.Razorpay) {
    resolve(true);
    return;
  }

  const existing = document.querySelector('script[data-razorpay-checkout="true"]');
  if (existing) {
    existing.addEventListener('load', () => resolve(true), { once: true });
    existing.addEventListener('error', () => resolve(false), { once: true });
    return;
  }

  const script = document.createElement('script');
  script.src = 'https://checkout.razorpay.com/v1/checkout.js';
  script.async = true;
  script.dataset.razorpayCheckout = 'true';
  script.onload = () => resolve(true);
  script.onerror = () => resolve(false);
  document.body.appendChild(script);
});

const launchRazorpayPayment = ({ paymentOrder, customerSession }) => new Promise((resolve, reject) => {
  if (!window.Razorpay) {
    reject(new Error('Payment gateway is not available right now.'));
    return;
  }

  const razorpay = new window.Razorpay({
    key: paymentOrder.keyId,
    amount: paymentOrder.amountSubunits,
    currency: paymentOrder.currency || 'INR',
    name: 'Krishnai Pearl Shoppee',
    description: 'Customer order checkout',
    order_id: paymentOrder.orderId,
    prefill: {
      name: customerSession?.name || '',
      contact: customerSession?.mobile?.replace(/\D/g, '') || ''
    },
    notes: {
      receipt: paymentOrder.receipt || ''
    },
    theme: {
      color: '#c98342'
    },
    modal: {
      ondismiss: () => reject(new Error('Payment was cancelled before confirmation.'))
    },
    handler: (response) => resolve(response)
  });

  razorpay.on('payment.failed', (event) => {
    reject(new Error(event?.error?.description || 'Payment failed.'));
  });
  razorpay.open();
});

export default function CheckoutPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [customerSession, setCustomerSession] = useState(() => getStoredCustomerSession());
  const [cart, setCart] = useState({ items: [], subtotal: 0 });
  const [quote, setQuote] = useState(null);
  const [couponCode, setCouponCode] = useState(() => getStoredCheckoutCouponCode());
  const [addresses, setAddresses] = useState([]);
  const [selectedAddressId, setSelectedAddressId] = useState('');
  const [addressForm, setAddressForm] = useState(initialAddress);
  const [loading, setLoading] = useState(true);
  const [savingAddress, setSavingAddress] = useState(false);
  const [placingOrder, setPlacingOrder] = useState(false);
  const [verifyingPhonePe, setVerifyingPhonePe] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const handledPhonePeReturnRef = useRef('');

  const phonepeMerchantOrderId = useMemo(
    () => new URLSearchParams(location.search).get('phonepeMerchantOrderId') || '',
    [location.search]
  );

  const syncCheckoutQuote = async (preferredCouponCode = getStoredCheckoutCouponCode()) => {
    const normalizedCouponCode = String(preferredCouponCode || '').trim().toUpperCase();

    try {
      const nextQuote = await retailService.getApplicableOffers(normalizedCouponCode || undefined);
      setQuote(nextQuote || null);
      setCart(nextQuote?.cart || { items: [], subtotal: 0 });
      if (nextQuote?.appliedCouponCode) {
        setCouponCode(nextQuote.appliedCouponCode);
        storeCheckoutCouponCode(nextQuote.appliedCouponCode);
      } else {
        setCouponCode(normalizedCouponCode);
        clearStoredCheckoutCouponCode();
      }
      return nextQuote;
    } catch (err) {
      const status = Number(err?.response?.status || 0);
      if (normalizedCouponCode && !isCustomerAuthError(err) && status >= 400 && status < 500) {
        clearStoredCheckoutCouponCode();
        setCouponCode('');
        const fallbackQuote = await retailService.getApplicableOffers();
        setQuote(fallbackQuote || null);
        setCart(fallbackQuote?.cart || { items: [], subtotal: 0 });
        return fallbackQuote;
      }
      throw err;
    }
  };

  useEffect(() => {
    const loadCheckout = async () => {
      setLoading(true);
      setError('');
      try {
        const [addressResponse] = await Promise.all([
          retailService.getAddresses(),
          syncCheckoutQuote()
        ]);
        setAddresses(addressResponse || []);
        setSelectedAddressId(addressResponse?.[0]?.id || '');
      } catch (err) {
        if (isCustomerAuthError(err)) {
          clearCustomerSession();
          clearStoredCheckoutCouponCode();
          setCustomerSession(null);
          return;
        }
        setError(err.response?.data?.message || 'Unable to load checkout right now.');
      } finally {
        setLoading(false);
      }
    };

    loadCheckout();
  }, []);

  useEffect(() => {
    if (!customerSession?.token || !phonepeMerchantOrderId) {
      return;
    }
    if (handledPhonePeReturnRef.current === phonepeMerchantOrderId) {
      return;
    }

    handledPhonePeReturnRef.current = phonepeMerchantOrderId;
    const pendingOrder = readPendingPhonePeOrder();

    if (!pendingOrder?.addressId) {
      clearPendingPhonePeOrder();
      setError('PhonePe returned, but the checkout session expired before we could confirm the order.');
      navigate('/checkout', { replace: true });
      return;
    }
    if (pendingOrder.merchantOrderId && pendingOrder.merchantOrderId !== phonepeMerchantOrderId) {
      handledPhonePeReturnRef.current = '';
      clearPendingPhonePeOrder();
      setError('PhonePe returned with a different checkout reference than the order we started.');
      navigate('/checkout', { replace: true });
      return;
    }

    const finalizePhonePeOrder = async () => {
      setVerifyingPhonePe(true);
      setError('');
      setSuccess('');
      try {
        const paymentStatus = await retailService.getPaymentStatus(phonepeMerchantOrderId);
        if (!paymentStatus?.success) {
          throw new Error(
            paymentStatus?.paymentState
              ? `PhonePe payment is ${String(paymentStatus.paymentState).toLowerCase()}.`
              : 'PhonePe payment is not confirmed yet.'
          );
        }

        await retailService.placeOrder({
          addressId: pendingOrder.addressId,
          couponCode: pendingOrder.couponCode || undefined,
          paymentProvider: normalizePaymentProvider(paymentStatus.provider),
          phonepeMerchantOrderId,
          phonepeTransactionId: paymentStatus.transactionId
        });

        clearStoredCheckoutCouponCode();
        clearPendingPhonePeOrder();
        navigate('/orders?placed=1', { replace: true });
      } catch (err) {
        if (isCustomerAuthError(err)) {
          clearCustomerSession();
          clearStoredCheckoutCouponCode();
          setCustomerSession(null);
          return;
        }
        handledPhonePeReturnRef.current = '';
        clearPendingPhonePeOrder();
        setError(err.response?.data?.message || err.message || 'Unable to confirm the PhonePe payment.');
        navigate('/checkout', { replace: true });
      } finally {
        setVerifyingPhonePe(false);
      }
    };

    finalizePhonePeOrder();
  }, [customerSession?.token, navigate, phonepeMerchantOrderId]);

  const activeFinalTotal = useMemo(
    () => quote?.finalTotal ?? cart?.subtotal ?? 0,
    [quote, cart]
  );
  const appliedDiscountDetails = useMemo(() => getAppliedDiscountDetails(quote), [quote]);
  const appliedDiscount = appliedDiscountDetails.totalDiscount;
  const taxAmount = Number(quote?.tax ?? 0);
  const deliveryAmount = Number(quote?.delivery ?? 0);
  const shouldShowDiscountBreakdown = appliedDiscountDetails.entries.length > 1
    || appliedDiscountDetails.entries.some((entry) => entry.caption);

  if (!customerSession?.token) {
    return <Navigate to="/customer-login?redirect=/checkout" replace />;
  }

  const refreshQuote = async (code = '') => {
    return syncCheckoutQuote(code);
  };

  const isOfferApplied = (offer) => {
    const offerCouponCode = String(offer?.couponCode || '').trim().toUpperCase();
    if (offerCouponCode) {
      return offerCouponCode === appliedDiscountDetails.appliedCouponCode;
    }
    return appliedDiscountDetails.kind === 'automatic'
      && appliedDiscountDetails.appliedOfferIds.has(String(offer?.id || '').trim());
  };
  const visibleOffers = (quote?.applicableOffers || []).filter((offer) => !isOfferApplied(offer));

  const saveAddress = async (event) => {
    event.preventDefault();
    setSavingAddress(true);
    setError('');
    setSuccess('');
    try {
      const saved = await retailService.addAddress(addressForm);
      const nextAddresses = [saved, ...addresses];
      setAddresses(nextAddresses);
      setSelectedAddressId(saved.id);
      setAddressForm(initialAddress);
      setSuccess('Address saved for delivery.');
    } catch (err) {
      if (isCustomerAuthError(err)) {
        clearCustomerSession();
        clearStoredCheckoutCouponCode();
        setCustomerSession(null);
        return;
      }
      setError(err.response?.data?.message || 'Unable to save address.');
    } finally {
      setSavingAddress(false);
    }
  };

  const removeCoupon = async () => {
    setCouponCode('');
    setError('');
    setSuccess('');
    try {
      await refreshQuote('');
      clearStoredCheckoutCouponCode();
      setSuccess('Coupon removed.');
    } catch (err) {
      if (isCustomerAuthError(err)) {
        clearCustomerSession();
        clearStoredCheckoutCouponCode();
        setCustomerSession(null);
        return;
      }
      setError(err.response?.data?.message || 'Unable to remove coupon.');
    }
  };

  const captureLocation = () => {
    setError('');
    if (!navigator.geolocation) {
      setError('Browser geolocation is not available on this device.');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setAddressForm((current) => ({
          ...current,
          latitude: position.coords.latitude.toFixed(6),
          longitude: position.coords.longitude.toFixed(6)
        }));
      },
      () => {
        setError('Unable to capture current location. You can still enter the address manually.');
      }
    );
  };

  const applyCoupon = async (event) => {
    event.preventDefault();
    setError('');
    setSuccess('');
    try {
      await refreshQuote(couponCode.trim());
      setSuccess('Coupon applied.');
    } catch (err) {
      if (isCustomerAuthError(err)) {
        clearCustomerSession();
        clearStoredCheckoutCouponCode();
        setCustomerSession(null);
        return;
      }
      setError(err.response?.data?.message || 'Unable to apply coupon.');
    }
  };

  const selectCoupon = async (code) => {
    setCouponCode(code);
    setError('');
    setSuccess('');
    try {
      await refreshQuote(code);
      setSuccess(`${code} applied.`);
    } catch (err) {
      if (isCustomerAuthError(err)) {
        clearCustomerSession();
        clearStoredCheckoutCouponCode();
        setCustomerSession(null);
        return;
      }
      setError(err.response?.data?.message || 'Unable to apply coupon.');
    }
  };

  const placeOrder = async () => {
    if (!selectedAddressId) {
      setError('Please select a Pune delivery address before placing the order.');
      return;
    }

    const resolvedCouponCode = quote?.appliedCouponCode || couponCode || undefined;
    setPlacingOrder(true);
    setError('');
    setSuccess('');

    try {
      const paymentOrder = await retailService.createPaymentOrder({
        couponCode: resolvedCouponCode
      });

      const paymentProvider = normalizePaymentProvider(paymentOrder?.provider);

      if (!paymentOrder?.configured) {
        const orderPayload = {
          addressId: selectedAddressId,
          couponCode: resolvedCouponCode,
          paymentProvider
        };
        if (paymentProvider === 'PHONEPE') {
          orderPayload.phonepeMerchantOrderId = paymentOrder?.orderId;
        } else {
          orderPayload.razorpayOrderId = paymentOrder?.orderId;
        }
        await retailService.placeOrder({
          ...orderPayload
        });
        clearStoredCheckoutCouponCode();
        navigate('/orders?placed=1');
        return;
      }

      if (paymentProvider === 'PHONEPE') {
        if (!paymentOrder?.paymentUrl) {
          throw new Error('Unable to start PhonePe checkout right now.');
        }
        writePendingPhonePeOrder({
          addressId: selectedAddressId,
          couponCode: resolvedCouponCode || '',
          merchantOrderId: paymentOrder.orderId
        });
        window.location.assign(paymentOrder.paymentUrl);
        return;
      }

      const isGatewayReady = await loadRazorpayCheckout();
      if (!isGatewayReady) {
        throw new Error('Unable to load the payment gateway.');
      }

      const paymentResult = await launchRazorpayPayment({
        paymentOrder,
        customerSession
      });

      await retailService.placeOrder({
        addressId: selectedAddressId,
        couponCode: resolvedCouponCode,
        razorpayOrderId: paymentResult.razorpay_order_id,
        razorpayPaymentId: paymentResult.razorpay_payment_id,
        razorpaySignature: paymentResult.razorpay_signature
      });
      clearStoredCheckoutCouponCode();
      navigate('/orders?placed=1');
    } catch (err) {
      if (isCustomerAuthError(err)) {
        clearCustomerSession();
        clearStoredCheckoutCouponCode();
        setCustomerSession(null);
        return;
      }
      setError(err.response?.data?.message || err.message || 'Unable to place the order.');
    } finally {
      setPlacingOrder(false);
    }
  };

  return (
    <main className="glow-site customer-flow-page">
      <section className="customer-flow-shell">
        <div className="customer-flow-head">
          <div>
            <p className="glow-kicker">Checkout</p>
            <h1>Complete your order</h1>
            <p>Review the cart, apply the best offer, choose a Pune address, and confirm payment.</p>
          </div>
          <Link className="ghost-btn compact-btn" to="/cart">Back to cart</Link>
        </div>

        {loading ? <p>Loading checkout…</p> : null}
        {verifyingPhonePe ? <p>Confirming PhonePe payment…</p> : null}
        {error ? <p className="error-text">{error}</p> : null}
        {success ? <p className="success-text">{success}</p> : null}

        {!loading ? (
          <div className="customer-checkout-layout">
            <div className="customer-checkout-main">
              <section className="customer-flow-panel">
                <div className="customer-section-title">
                  <div>
                    <p className="glow-kicker">Delivery address</p>
                    <h2>Pune deliveries only</h2>
                  </div>
                </div>

                <div className="customer-address-list">
                  {addresses.map((address) => (
                    <label key={address.id} className={`customer-address-card ${selectedAddressId === address.id ? 'is-selected' : ''}`}>
                      <input
                        type="radio"
                        name="address"
                        checked={selectedAddressId === address.id}
                        onChange={() => setSelectedAddressId(address.id)}
                      />
                      <div>
                        <strong>{address.label || address.recipientName}</strong>
                        <p>{address.line1}, {address.city}, {address.state} {address.pincode}</p>
                        <span>{address.mobile}</span>
                      </div>
                    </label>
                  ))}
                </div>

                <form className="customer-address-form" onSubmit={saveAddress}>
                  <div className="customer-form-grid">
                    <input value={addressForm.label} onChange={(event) => setAddressForm((current) => ({ ...current, label: event.target.value }))} placeholder="Label" />
                    <input value={addressForm.recipientName} onChange={(event) => setAddressForm((current) => ({ ...current, recipientName: event.target.value }))} placeholder="Recipient name" />
                    <input value={addressForm.mobile} onChange={(event) => setAddressForm((current) => ({ ...current, mobile: event.target.value }))} placeholder="Mobile number" />
                    <input value={addressForm.line1} onChange={(event) => setAddressForm((current) => ({ ...current, line1: event.target.value }))} placeholder="Address line 1" />
                    <input value={addressForm.line2} onChange={(event) => setAddressForm((current) => ({ ...current, line2: event.target.value }))} placeholder="Address line 2" />
                    <input value={addressForm.landmark} onChange={(event) => setAddressForm((current) => ({ ...current, landmark: event.target.value }))} placeholder="Landmark" />
                    <input value={addressForm.city} onChange={(event) => setAddressForm((current) => ({ ...current, city: event.target.value }))} placeholder="City" />
                    <input value={addressForm.state} onChange={(event) => setAddressForm((current) => ({ ...current, state: event.target.value }))} placeholder="State" />
                    <input value={addressForm.pincode} onChange={(event) => setAddressForm((current) => ({ ...current, pincode: event.target.value }))} placeholder="Pincode" />
                    <input value={addressForm.latitude} onChange={(event) => setAddressForm((current) => ({ ...current, latitude: event.target.value }))} placeholder="Latitude" />
                    <input value={addressForm.longitude} onChange={(event) => setAddressForm((current) => ({ ...current, longitude: event.target.value }))} placeholder="Longitude" />
                  </div>
                  <div className="checkout-actions">
                    <button type="button" className="ghost-btn compact-btn" onClick={captureLocation}>Use current location</button>
                    <button type="submit" className="primary-btn compact-btn" disabled={savingAddress}>
                      {savingAddress ? 'Saving...' : 'Save address'}
                    </button>
                  </div>
                </form>
              </section>

              <section className="customer-flow-panel">
                <div className="customer-section-title">
                  <div>
                    <p className="glow-kicker">Cart items</p>
                    <h2>{cart.items?.length || 0} pieces ready</h2>
                  </div>
                </div>
                <div className="customer-checkout-items">
                  {(cart.items || []).map((item) => (
                    <div key={item.productId} className="customer-checkout-item">
                      <div>
                        <strong>{item.name}</strong>
                        <span>{item.quantity} × {currency(item.price)}</span>
                      </div>
                      <strong>{currency(item.lineTotal)}</strong>
                    </div>
                  ))}
                </div>
              </section>

              <section className="customer-flow-panel">
                <div className="customer-section-title">
                  <div>
                    <p className="glow-kicker">Coupons and offers</p>
                    <h2>Apply or replace one coupon</h2>
                  </div>
                </div>
                <form className="customer-inline-form" onSubmit={applyCoupon}>
                  <input
                    value={couponCode}
                    onChange={(event) => setCouponCode(event.target.value.toUpperCase())}
                    placeholder="Enter coupon code"
                  />
                  <button type="submit" className="primary-btn compact-btn">Apply coupon</button>
                </form>
                <div className="customer-offer-list">
                  {visibleOffers.map((offer) => {
                    const offerDisplay = getOfferDisplayLabel(offer);

                    return (
                      <div key={offer.id} className="customer-offer-card">
                        <div className="customer-offer-meta">
                          <strong>{offer.name}</strong>
                          <span>
                            {offer.couponCode ? `${offer.couponCode} · ` : ''}
                            {offerDisplay.isPercent
                              ? `${offerDisplay.discountValue}% off`
                              : `${currency(offerDisplay.discountValue)} off`}
                          </span>
                        </div>
                        <div className="customer-offer-actions">
                          <span className="customer-offer-status">
                            {offer.couponCode
                              ? (quote?.appliedCouponCode ? 'Available to replace' : 'Available')
                              : 'Auto when best'}
                          </span>
                          {offer.couponCode ? (
                            <button
                              type="button"
                              className="ghost-btn compact-btn"
                              onClick={() => selectCoupon(offer.couponCode)}
                            >
                              {quote?.appliedCouponCode ? `Replace with ${offer.couponCode}` : `Use ${offer.couponCode}`}
                            </button>
                          ) : null}
                        </div>
                      </div>
                    );
                  })}
                  {!visibleOffers.length ? (
                    <div className="customer-offer-empty">
                      <strong>No other coupons to replace right now.</strong>
                      <span>The current total already reflects the best eligible pricing.</span>
                    </div>
                  ) : null}
                </div>
              </section>
            </div>

            <aside className="customer-flow-panel customer-order-summary">
              <p className="glow-kicker">Order summary</p>
              <div className="customer-summary-row">
                <span>Subtotal</span>
                <strong>{currency(quote?.subtotal ?? cart.subtotal)}</strong>
              </div>
              {quote?.appliedCouponCode ? (
                <div className="customer-summary-row">
                  <div className="customer-summary-copy">
                    <strong>Applied Coupon: {quote.appliedCouponCode}</strong>
                    <span>Only one coupon stays active at a time.</span>
                  </div>
                  <button type="button" className="ghost-btn compact-btn" onClick={removeCoupon}>Remove</button>
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
                <span>Final total</span>
                <strong>{currency(activeFinalTotal)}</strong>
              </div>
              <button
                type="button"
                className="glow-account-btn customer-full-btn"
                onClick={placeOrder}
                disabled={placingOrder || !(cart.items || []).length}
              >
                {placingOrder ? 'Opening payment...' : 'Pay and place order'}
              </button>
              <small className="customer-helper-copy">
                PhonePe opens when the gateway is configured. Local environments still fall back safely for test checkout.
              </small>
            </aside>
          </div>
        ) : null}
      </section>
    </main>
  );
}
