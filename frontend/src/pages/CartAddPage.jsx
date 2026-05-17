import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { retailService } from '../services/retailService';
import { getStoredCustomerSession, isCustomerAuthError } from '../utils/auth';
import { addGuestCartItem } from '../utils/cart';
import { getApiErrorMessage } from '../utils/validation';

export default function CartAddPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const [message, setMessage] = useState('Adding product to cart...');
  const [error, setError] = useState('');

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const productId = params.get('productId');
    const customerSession = getStoredCustomerSession();

    const addProduct = async () => {
      if (!productId) {
        setError('Product link is missing.');
        setMessage('');
        return;
      }

      try {
        if (customerSession?.token) {
          try {
            await retailService.addToCart({ productId, quantity: 1 });
          } catch (requestError) {
            if (!isCustomerAuthError(requestError)) {
              throw requestError;
            }
            addGuestCartItem(productId, 1);
          }
        } else {
          addGuestCartItem(productId, 1);
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
