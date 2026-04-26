import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import CustomerAccountMenu from './CustomerAccountMenu';

function HeartIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="glow-icon-svg">
      <path
        d="M12 20.3 4.9 13.7a4.7 4.7 0 0 1 6.6-6.7l.5.5.5-.5a4.7 4.7 0 1 1 6.6 6.7L12 20.3Z"
        fill="none"
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="1.8"
      />
    </svg>
  );
}

export default function StorefrontHeader({ logo, shopName, navLinks = [], cartCount = 0 }) {
  const uniqueLinks = useMemo(() => {
    const seen = new Set();
    return navLinks.filter((item) => {
      const key = `${item?.to || ''}::${item?.label || ''}`;
      if (!item?.to || !item?.label || seen.has(key)) {
        return false;
      }
      seen.add(key);
      return true;
    });
  }, [navLinks]);

  return (
    <header className="glow-header">
      <div className="glow-header-inner">
        <Link to="/" className="glow-brand">
          <div className="glow-brand-mark">
            <img src={logo} alt={`${shopName} logo`} className="glow-brand-logo" />
            <span className="glow-brand-line" />
          </div>
          <div className="glow-brand-copy">
            <strong>{shopName}</strong>
          </div>
        </Link>

        {uniqueLinks.length > 0 ? (
          <nav className="glow-nav" aria-label="Store sections">
            {uniqueLinks.map((link) => (
              <Link key={`${link.to}-${link.label}`} to={link.to}>
                {link.label}
              </Link>
            ))}
          </nav>
        ) : null}

        <div className="glow-header-actions">
          <Link to="/products" className="glow-icon-btn" aria-label="Wishlist">
            <HeartIcon />
          </Link>
          <CustomerAccountMenu cartCount={cartCount} />
        </div>
      </div>
    </header>
  );
}
