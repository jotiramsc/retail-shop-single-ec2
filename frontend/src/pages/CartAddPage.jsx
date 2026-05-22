import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { retailService } from '../services/retailService';
import { getStoredCustomerSession, isCustomerAuthError } from '../utils/auth';
import { addGuestCartItem } from '../utils/cart';
import { getApiErrorMessage } from '../utils/validation';

function isUuid(value) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value || '');
}

function safeQuantity(value) {
  const parsed = Number.parseInt(value || '1', 10);
  if (!Number.isFinite(parsed)) {
    return 1;
  }
  return Math.min(Math.max(parsed, 1), 10);
}

function rememberAttribution({ source, campaign, product }) {
  if (typeof window === 'undefined') {
    return;
  }
  const attribution = {
    source: source || '',
    campaign: campaign || '',
    product: product || '',
    capturedAt: new Date().toISOString()
  };
  window.sessionStorage.setItem('krishnai_cart_attribution', JSON.stringify(attribution));
}

export default function CartAddPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const [message, setMessage] = useState('Adding product to cart...');
  const [error, setError] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const productParam = params.get('product') || params.get('productId');
    const quantity = safeQuantity(params.get('qty') || params.get('quantity'));
    const source = params.get('source') || '';
    const campaign = params.get('campaign') || '';
    const customerSession = getStoredCustomerSession();

    const addProduct = async () => {
      if (!productParam) {
        setError('Product link is missing.');
        setMessage('');
        return;
      }

      try {
        let productId = productParam;
        if (!isUuid(productId)) {
          const catalog = await retailService.getPublicCatalog();
          const product = (catalog || []).find((item) => String(item.sku || '').toLowerCase() === productParam.toLowerCase());
          if (!product?.id) {
            setError('This catalog product is no longer available.');
            setMessage('');
            return;
          }
          productId = product.id;
        }
        rememberAttribution({ source, campaign, product: productParam });
        if (customerSession?.token) {
          try {
            await retailService.addToCart({ productId, quantity });
          } catch (requestError) {
            if (!isCustomerAuthError(requestError)) {
              throw requestError;
            }
            addGuestCartItem(productId, quantity);
          }
        } else {
          addGuestCartItem(productId, quantity);
        }
        setMessage('Product added to cart. Opening your cart...');
        window.setTimeout(() => navigate('/cart', { replace: true }), 700);
      } catch (requestError) {
        setError(getApiErrorMessage(requestError, 'Unable to add this product to cart.'));
        setMessage('');
      }
    };

    addProduct();
  }, [location.search, navigate]);

  return (
    <main className="glow-site customer-flow-page">
      <section className="customer-flow-shell">
        <div className="customer-flow-panel customer-empty-state">
          <strong>{error ? 'Cart link needs attention' : 'Krishnai Cart'}</strong>
          {message ? <span>{message}</span> : null}
          {error ? <span className="error-text">{error}</span> : null}
          <Link className="glow-account-btn" to="/cart">Open cart</Link>
        </div>
      </section>
    </main>
  );
}
