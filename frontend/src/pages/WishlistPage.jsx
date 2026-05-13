import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { retailService } from '../services/retailService';
import {
  clearCustomerSession,
  getStoredCustomerSession,
  isCustomerAuthError
} from '../utils/auth';
import { getApiErrorMessage } from '../utils/validation';
import { currency } from '../utils/format';

const fallbackImage = '/assets/glowjewels/no_image.png';

export default function WishlistPage() {
  const [customerSession, setCustomerSession] = useState(() => getStoredCustomerSession());
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busyProductId, setBusyProductId] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    const loadWishlist = async () => {
      setLoading(true);
      setError('');
      try {
        setItems(await retailService.getWishlist());
      } catch (err) {
        if (isCustomerAuthError(err)) {
          clearCustomerSession();
          setCustomerSession(null);
          return;
        }
        setError(getApiErrorMessage(err, 'Unable to load wishlist right now. Please login again if needed.'));
      } finally {
        setLoading(false);
      }
    };

    if (customerSession?.token) {
      loadWishlist();
    }
  }, [customerSession?.token]);

  useEffect(() => {
    if (!error) {
      return undefined;
    }
    const timer = window.setTimeout(() => setError(''), 4200);
    return () => window.clearTimeout(timer);
  }, [error]);

  useEffect(() => {
    if (!success) {
      return undefined;
    }
    const timer = window.setTimeout(() => setSuccess(''), 4200);
    return () => window.clearTimeout(timer);
  }, [success]);

  if (!customerSession?.token) {
    return <Navigate to="/customer-login?redirect=/wishlist" replace />;
  }

  const removeItem = async (productId) => {
    setBusyProductId(productId);
    setError('');
    setSuccess('');
    try {
      setItems(await retailService.removeFromWishlist(productId));
      setSuccess('Removed from wishlist.');
    } catch (err) {
      if (isCustomerAuthError(err)) {
        clearCustomerSession();
        setCustomerSession(null);
        return;
      }
      setError(getApiErrorMessage(err, 'Unable to update wishlist. Please try again.'));
    } finally {
      setBusyProductId('');
    }
  };

  const moveToCart = async (item) => {
    setBusyProductId(item.productId);
    setError('');
    setSuccess('');
    try {
      await retailService.moveWishlistToCart({ productId: item.productId, quantity: 1 });
      setItems((current) => current.filter((entry) => entry.productId !== item.productId));
      setSuccess(`${item.name} moved to cart.`);
    } catch (err) {
      if (isCustomerAuthError(err)) {
        clearCustomerSession();
        setCustomerSession(null);
        return;
      }
      setError(getApiErrorMessage(err, 'Unable to move item to cart. Please check stock and try again.'));
    } finally {
      setBusyProductId('');
    }
  };

  return (
    <main className="glow-site customer-flow-page">
      <section className="customer-flow-shell">
        <div className="customer-flow-head">
          <div>
            <p className="glow-kicker">Wishlist</p>
            <h1>Saved for later</h1>
            <p>Keep favourite products here, move them to cart, or remove them anytime.</p>
          </div>
          <Link className="ghost-btn compact-btn" to="/products">Continue shopping</Link>
        </div>

        {loading ? <p>Loading wishlist...</p> : null}
        {error ? <p className="error-text storefront-feedback error" role="alert">{error}</p> : null}
        {success ? <p className="success-text storefront-feedback" role="status" aria-live="polite">{success}</p> : null}

        {!loading && !items.length ? (
          <div className="customer-flow-panel customer-empty-state">
            <strong>Your wishlist is empty</strong>
            <span>Save products while browsing and they will appear here.</span>
            <Link className="glow-account-btn" to="/products">Browse products</Link>
          </div>
        ) : null}

        {items.length ? (
          <section className="customer-flow-panel customer-cart-list">
            {items.map((item) => (
              <article key={item.productId} className="customer-cart-item">
                <img src={item.imageDataUrl || fallbackImage} alt={item.name} />
                <div className="customer-cart-copy">
                  <p>{item.category}</p>
                  <h3>{item.name}</h3>
                  <span>{item.sku}</span>
                </div>
                <strong>{currency(item.price)}</strong>
                <div className="wishlist-actions">
                  <button
                    type="button"
                    className="primary-btn compact-btn"
                    onClick={() => moveToCart(item)}
                    disabled={busyProductId === item.productId || !item.inStock}
                  >
                    {item.inStock ? 'Move to cart' : 'Out of stock'}
                  </button>
                  <button
                    type="button"
                    className="ghost-btn compact-btn danger-btn"
                    onClick={() => removeItem(item.productId)}
                    disabled={busyProductId === item.productId}
                  >
                    Remove
                  </button>
                </div>
              </article>
            ))}
          </section>
        ) : null}
      </section>
    </main>
  );
}
