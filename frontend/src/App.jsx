import { NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useCallback, useEffect, useState } from 'react';
import BillingPage from './pages/BillingPage';
import ProductsPage from './pages/ProductsPage';
import CustomersPage from './pages/CustomersPage';
import CampaignsPage from './pages/CampaignsPage';
import ReportsPage from './pages/ReportsPage';
import SalespersonSalesPage from './pages/SalespersonSalesPage';
import SiteInteractionsPage from './pages/SiteInteractionsPage';
import LoginPage from './pages/LoginPage';
import ReceiptSettingsPage from './pages/ReceiptSettingsPage';
import UsersPage from './pages/UsersPage';
import PublicHomePage from './pages/PublicHomePage';
import PublicProductsPage from './pages/PublicProductsPage';
import CustomerLoginPage from './pages/CustomerLoginPage';
import CartPage from './pages/CartPage';
import CheckoutPage from './pages/CheckoutPage';
import OrdersPage from './pages/OrdersPage';
import CustomerProfilePage from './pages/CustomerProfilePage';
import WishlistPage from './pages/WishlistPage';
import PrivacyPolicyPage from './pages/PrivacyPolicyPage';
import { retailService } from './services/retailService';
import { clearAuthSession, getStoredAuthSession } from './utils/auth';
import { defaultBranding, getStoredBranding, normalizeBranding, storeBranding } from './utils/branding';
import { getStoredVisitCount, storeVisitCount, trackCurrentSiteVisit } from './utils/siteInteraction';

const resolvedApiBaseUrl = window.__APP_CONFIG__?.API_BASE_URL || import.meta.env.VITE_API_BASE_URL || '/api';

const navItems = [
  { to: '/app', label: 'Billing', permission: 'BILLING' },
  { to: '/app/products', label: 'Inventory', permission: 'PRODUCTS' },
  { to: '/app/customers', label: 'Customers', permission: 'CUSTOMERS' },
  { to: '/app/campaigns', label: 'Campaign Studio', permission: ['MARKETING_AUTOMATION', 'OFFERS'] },
  { to: '/app/reports', label: 'Reports', permission: 'REPORTS' },
  { to: '/app/salesperson-sales', label: 'Salesperson Sales', permission: 'SALESPERSON_SALES' },
  { to: '/app/site-interactions', label: 'Site Interaction', permission: 'SITE_INTERACTIONS' },
  { to: '/app/settings/receipt', label: 'Brand Configuration', permission: 'RECEIPT_SETTINGS' },
  { to: '/app/users', label: 'Users', permission: 'USER_MANAGEMENT' }
];

function ProtectedApp({ auth, onLogout, branding }) {
  const location = useLocation();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const permissions = Array.isArray(auth.permissions) ? auth.permissions : [];
  const canAccess = (permission) => {
    if (auth.role === 'ADMIN' || auth.role === 'OWNER') {
      return true;
    }
    if (Array.isArray(permission)) {
      return permission.some((entry) => permissions.includes(entry));
    }
    return permissions.includes(permission);
  };
  const firstAllowedRoute = navItems.find((item) => canAccess(item.permission))?.to || '/login';
  const visibleNavItems = navItems.filter((item) => canAccess(item.permission));

  useEffect(() => {
    setIsMenuOpen(false);
  }, [location.pathname, location.search]);

  return (
    <div className="app-shell">
      <aside className={`sidebar ${isMenuOpen ? 'is-open' : ''}`}>
        <div>
          <div className="sidebar-brand-row">
            <div className="brand-lockup">
              <img
                className="brand-logo"
                src={branding.media?.logo}
                alt={`${branding.shopName || 'Store'} logo`}
              />
              <div>
                <p className="sidebar-kicker">{branding.sidebarKicker || 'Store Dashboard'}</p>
                <h1>{branding.shopName || 'Store'}</h1>
              </div>
            </div>
            <button
              type="button"
              className="sidebar-menu-toggle"
              aria-expanded={isMenuOpen}
              aria-label={isMenuOpen ? 'Close admin menu' : 'Open admin menu'}
              onClick={() => setIsMenuOpen((current) => !current)}
            >
              <span className="sidebar-menu-toggle-bars" aria-hidden="true">
                <span />
                <span />
                <span />
              </span>
              <span>{isMenuOpen ? 'Close' : 'Menu'}</span>
            </button>
          </div>
          <p className="sidebar-copy">
            {branding.headerLine || 'Manage your retail operations from one place.'}
          </p>
        </div>

        <nav className="nav-list">
          {visibleNavItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/app'}
              className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}
              onClick={() => setIsMenuOpen(false)}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-footer">
          <span className="status-dot" />
          Signed in as <strong>{auth.displayName}</strong> ({auth.role})
          <br />
          <span>{branding.contact?.phoneLabel || 'No phone configured'}</span>
          <br />
          Backend base URL: <code>{resolvedApiBaseUrl}</code>
          <br />
          <button className="ghost-btn sidebar-logout" onClick={onLogout}>Sign Out</button>
        </div>
      </aside>

      <main className="content">
        <Routes>
          <Route index element={canAccess('BILLING') ? <BillingPage /> : <Navigate to={firstAllowedRoute} replace />} />
          <Route path="products" element={canAccess('PRODUCTS') ? <ProductsPage /> : <Navigate to={firstAllowedRoute} replace />} />
          <Route path="customers" element={canAccess('CUSTOMERS') ? <CustomersPage /> : <Navigate to={firstAllowedRoute} replace />} />
          <Route path="offers" element={canAccess(['MARKETING_AUTOMATION', 'OFFERS']) ? <Navigate to="/app/campaigns?tab=offers" replace /> : <Navigate to={firstAllowedRoute} replace />} />
          <Route path="campaigns" element={canAccess(['MARKETING_AUTOMATION', 'OFFERS']) ? <CampaignsPage /> : <Navigate to={firstAllowedRoute} replace />} />
          <Route path="reports" element={canAccess('REPORTS') ? <ReportsPage /> : <Navigate to={firstAllowedRoute} replace />} />
          <Route path="salesperson-sales" element={canAccess('SALESPERSON_SALES') ? <SalespersonSalesPage auth={auth} /> : <Navigate to={firstAllowedRoute} replace />} />
          <Route path="site-interactions" element={canAccess('SITE_INTERACTIONS') ? <SiteInteractionsPage /> : <Navigate to={firstAllowedRoute} replace />} />
          <Route path="settings/receipt" element={canAccess('RECEIPT_SETTINGS') ? <ReceiptSettingsPage /> : <Navigate to={firstAllowedRoute} replace />} />
          <Route path="users" element={canAccess('USER_MANAGEMENT') ? <UsersPage /> : <Navigate to={firstAllowedRoute} replace />} />
          <Route path="*" element={<Navigate to={firstAllowedRoute} replace />} />
        </Routes>
      </main>
    </div>
  );
}

export default function App() {
  const location = useLocation();
  const [auth, setAuth] = useState(getStoredAuthSession());
  const [branding, setBranding] = useState(() => getStoredBranding());
  const [siteVisitCount, setSiteVisitCount] = useState(() => getStoredVisitCount());

  const handleLogout = useCallback(() => {
    clearAuthSession();
    setAuth(null);
  }, []);

  useEffect(() => {
    if (!auth) {
      return undefined;
    }
    const expiresAt = Date.parse(auth.expiresAt || '');
    if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
      handleLogout();
      return undefined;
    }
    const timeoutId = window.setTimeout(handleLogout, Math.min(expiresAt - Date.now(), 2_147_483_647));
    return () => window.clearTimeout(timeoutId);
  }, [auth, handleLogout]);

  useEffect(() => {
    document.title = branding.shopName || defaultBranding.shopName;
  }, [branding.shopName]);

  useEffect(() => {
    retailService.getReceiptSettings()
      .then((settings) => {
        setBranding(storeBranding(normalizeBranding(settings)));
      })
      .catch(() => {
        setBranding(defaultBranding);
      });
  }, []);

  useEffect(() => {
    if (location.pathname.startsWith('/app') || location.pathname === '/login') {
      return;
    }

    let cancelled = false;

    trackCurrentSiteVisit()
      .then((response) => {
        if (!cancelled && typeof response?.totalVisits === 'number') {
          setSiteVisitCount(storeVisitCount(response.totalVisits));
        }
      })
      .catch(() => {});

    return () => {
      cancelled = true;
    };
  }, [location.pathname, location.search]);

  return (
    <Routes>
      <Route
        path="/login"
        element={auth ? <Navigate to="/app" replace /> : <LoginPage onLogin={setAuth} branding={branding} />}
      />
      <Route path="/customer-login" element={<CustomerLoginPage />} />
      <Route path="/" element={<PublicHomePage branding={branding} siteVisitCount={siteVisitCount} />} />
      <Route path="/products" element={<PublicProductsPage branding={branding} />} />
      <Route path="/cart" element={<CartPage />} />
      <Route path="/wishlist" element={<WishlistPage />} />
      <Route path="/checkout" element={<CheckoutPage />} />
      <Route path="/orders" element={<OrdersPage />} />
      <Route path="/account" element={<CustomerProfilePage />} />
      <Route path="/privacy-policy" element={<PrivacyPolicyPage branding={branding} />} />
      <Route
        path="/app/*"
        element={auth ? <ProtectedApp auth={auth} onLogout={handleLogout} branding={branding} /> : <Navigate to="/login" replace />}
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
