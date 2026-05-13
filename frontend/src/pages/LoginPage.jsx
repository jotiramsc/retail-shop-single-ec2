import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { retailService } from '../services/retailService';
import { storeAuthSession } from '../utils/auth';
import { defaultBranding } from '../utils/branding';
import { getApiErrorMessage } from '../utils/validation';

export default function LoginPage({ onLogin, branding = defaultBranding }) {
  const [form, setForm] = useState({ username: '', password: '' });
  const [customerAccessProducts, setCustomerAccessProducts] = useState([]);
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    retailService.getPublicCatalog()
      .then((catalog) => {
        setCustomerAccessProducts(
          catalog
            .filter((product) => product.showInCustomerAccess && product.imageDataUrl)
            .slice(0, 2)
        );
      })
      .catch(() => {
        setCustomerAccessProducts([]);
      });
  }, []);

  useEffect(() => {
    if (!error) {
      return undefined;
    }
    const timer = window.setTimeout(() => setError(''), 4200);
    return () => window.clearTimeout(timer);
  }, [error]);

  const customerAccessImages = customerAccessProducts.length
    ? customerAccessProducts.map((product) => product.imageDataUrl).filter(Boolean)
    : [branding.media?.heroPrimary, branding.media?.heroSecondary].filter(Boolean);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      const session = await retailService.login(form);
      storeAuthSession(session);
      onLogin(session);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to sign in.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-shell">
      <div className="login-card">
        <div className="login-forest">
          <div className="login-brand-row">
            {branding.media?.logo ? (
              <img
                className="login-brand-logo"
                src={branding.media.logo}
                alt={`${branding.shopName || 'Store'} logo`}
              />
            ) : null}
            <div>
              <p className="sidebar-kicker">{branding.loginKicker || 'Staff Access'}</p>
              <h1>{branding.shopName || 'Store'}</h1>
            </div>
          </div>
          <p className="login-copy">
            {branding.headerLine || 'Sign in to manage billing, inventory, customers, and campaigns.'}
          </p>
          <div className="login-hero-grid">
            <div className="login-hero-copy">
              <strong>{branding.homepageTitle || branding.shopName}</strong>
              <span>{branding.homepageSubtitle}</span>
              <div className="trust-chip-row">
                {(branding.trustPoints || []).map((point) => (
                  <span key={point} className="trust-chip">{point}</span>
                ))}
              </div>
            </div>
            <div className="login-hero-images">
              {customerAccessImages.map((image, index) => (
                <img
                  key={`${image}-${index}`}
                  src={image}
                  alt={`Featured ${branding.shopName || 'store'} product ${index + 1}`}
                />
              ))}
            </div>
          </div>
        </div>

        <div className="login-sheet">
          <div className="login-sheet-head">
            <p className="sidebar-kicker">Store access</p>
            <h2>Welcome back</h2>
            <span>Use assigned credentials to open billing, stock, campaigns, and reports.</span>
          </div>

          <form className="form-grid" onSubmit={handleSubmit}>
            <input
              placeholder="Username"
              value={form.username}
              onChange={(e) => setForm({ ...form, username: e.target.value })}
              required
            />
            <input
              type="password"
              placeholder="Password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              required
            />
            {error ? <p className="error-text storefront-feedback error" role="alert">{error}</p> : null}
            <button className="primary-btn" type="submit" disabled={submitting}>
              {submitting ? 'Signing in...' : 'Sign In'}
            </button>
          </form>

          <div className="login-hint">
            <strong>Need customer view?</strong>
            <span>Open the public website without entering admin mode.</span>
            <Link className="text-link" to="/">View customer website</Link>
          </div>

          <div className="login-contact-card">
            <strong>Customer contact</strong>
            {branding.contact?.address ? <span>{branding.contact.address}</span> : null}
            {branding.contact?.phoneHref && branding.contact?.phoneLabel ? (
              <a href={branding.contact.phoneHref}>{branding.contact.phoneLabel}</a>
            ) : null}
            {branding.contact?.email ? (
              <a href={`mailto:${branding.contact.email}`}>{branding.contact.email}</a>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}
