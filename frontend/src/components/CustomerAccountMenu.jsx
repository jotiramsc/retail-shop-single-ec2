import { Link, useNavigate } from 'react-router-dom';
import { clearCustomerSession, getStoredCustomerSession } from '../utils/auth';

function BagIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="glow-icon-svg">
      <path
        d="M7 9V7a5 5 0 0 1 10 0v2M6 9h12l-1 10H7L6 9Z"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="1.8"
      />
    </svg>
  );
}

function ProfileIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="glow-icon-svg">
      <path
        d="M12 12a4 4 0 1 0-4-4 4 4 0 0 0 4 4Zm-7 8a7 7 0 0 1 14 0"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="1.8"
      />
    </svg>
  );
}

export default function CustomerAccountMenu({ cartCount = 0 }) {
  const navigate = useNavigate();
  const customerSession = getStoredCustomerSession();
  const customerName = customerSession?.name?.trim() || 'Customer';
  const isLoggedIn = Boolean(customerSession?.token);
  const compactName = isLoggedIn ? customerName.split(/\s+/)[0] : 'Sign in';

  const logout = () => {
    clearCustomerSession();
    navigate('/');
    window.location.reload();
  };

  return (
    <div className="customer-site-actions">
      <Link to="/cart" className="glow-icon-btn" aria-label="Bag">
        <span className="glow-icon-badge bronze">{cartCount}</span>
        <BagIcon />
      </Link>

      <details className="customer-menu">
        <summary className="glow-account-btn glow-account-btn-compact customer-menu-trigger">
          <span className="glow-account-icon"><ProfileIcon /></span>
          <span className="customer-menu-trigger-copy">
            <strong>{compactName}</strong>
            <span>{isLoggedIn ? 'My account' : 'WhatsApp OTP'}</span>
          </span>
        </summary>

        <div className="customer-menu-popover">
          {isLoggedIn ? (
            <>
              <div className="customer-menu-profile">
                <strong>{customerName}</strong>
                <span>{customerSession?.mobile}</span>
              </div>
              <Link to="/account">My Profile</Link>
              <Link to="/orders">My Orders</Link>
              <Link to="/checkout">Checkout</Link>
              <button type="button" onClick={logout}>Logout</button>
            </>
          ) : (
            <>
              <Link to="/customer-login">Login with WhatsApp OTP</Link>
              <Link to="/cart">View Cart</Link>
            </>
          )}
        </div>
      </details>
    </div>
  );
}
