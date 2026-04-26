import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import { useEffect, useState } from 'react';
import BillingPage from './pages/BillingPage';
import ProductsPage from './pages/ProductsPage';
import CustomersPage from './pages/CustomersPage';
import OffersPage from './pages/OffersPage';
import CampaignsPage from './pages/CampaignsPage';
import ReportsPage from './pages/ReportsPage';
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
import { retailService } from './services/retailService';
import { clearAuthSession, getStoredAuthSession } from './utils/auth';

const resolvedApiBaseUrl = window.__APP_CONFIG__?.API_BASE_URL || import.meta.env.VITE_API_BASE_URL || '/api';

const navItems = [
  { to: '/app', label: 'Billing', permission: 'BILLING' },
  { to: '/app/products', label: 'Inventory', permission: 'PRODUCTS' },
  { to: '/app/customers', label: 'Customers', permission: 'CUSTOMERS' },
  { to: '/app/offers', label: 'Offers', permission: 'OFFERS' },
  { to: '/app/campaigns', label: 'Marketing', permission: 'CAMPAIGNS' },
  { to: '/app/reports', label: 'Reports', permission: 'REPORTS' },
  { to: '/app/settings/receipt', label: 'Receipt Settings', permission: 'RECEIPT_SETTINGS' },
  { to: '/app/users', label: 'Users', permission: 'USER_MANAGEMENT' }
];

const emptyBranding = {
  shopName: '',
  headerLine: '',
  sidebarKicker: '',
  loginKicker: '',
  homepageTitle: '',
  homepageSubtitle: '',
  trustPoints: [],
  featuredCollections: [],
  contact: {
    phoneLabel: '',
    phoneHref: '',
    email: '',
    address: ''
  },
  media: {
    logo: '',
    heroPrimary: '',
    heroSecondary: ''
  }
};

function ProtectedApp({ auth, onLogout, branding }) {
  const permissions = Array.isArray(auth.permissions) ? auth.permissions : [];
  const canAccess = (permission) => auth.role === 'ADMIN' || permissions.includes(permission);
  const firstAllowedRoute = navItems.find((item) => canAccess(item.permission))?.to || '/login';
  const visibleNavItems = navItems.filter((item) => canAccess(item.permission));

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div>
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
          <Route path="offers" element={canAccess('OFFERS') ? <OffersPage /> : <Navigate to={firstAllowedRoute} replace />} />
          <Route path="campaigns" element={canAccess('CAMPAIGNS') ? <CampaignsPage /> : <Navigate to={firstAllowedRoute} replace />} />
          <Route path="reports" element={canAccess('REPORTS') ? <ReportsPage /> : <Navigate to={firstAllowedRoute} replace />} />
          <Route path="settings/receipt" element={canAccess('RECEIPT_SETTINGS') ? <ReceiptSettingsPage /> : <Navigate to={firstAllowedRoute} replace />} />
          <Route path="users" element={canAccess('USER_MANAGEMENT') ? <UsersPage /> : <Navigate to={firstAllowedRoute} replace />} />
          <Route path="*" element={<Navigate to={firstAllowedRoute} replace />} />
        </Routes>
      </main>
    </div>
  );
}

export default function App() {
  const [auth, setAuth] = useState(getStoredAuthSession());
  const [branding, setBranding] = useState(emptyBranding);

  useEffect(() => {
    retailService.getReceiptSettings()
      .then((settings) => {
        setBranding({
          shopName: settings.shopName || '',
          headerLine: settings.headerLine || '',
          sidebarKicker: settings.loginKicker || '',
          loginKicker: settings.loginKicker || '',
          homepageTitle: settings.homepageTitle || '',
          homepageSubtitle: settings.homepageSubtitle || '',
          trustPoints: [
            settings.trustBadgeOne,
            settings.trustBadgeTwo,
            settings.trustBadgeThree,
            settings.trustBadgeFour
          ].filter(Boolean),
          featuredCollections: [],
          contact: {
            phoneLabel: settings.phoneNumber || '',
            phoneHref: settings.phoneNumber
              ? `tel:${String(settings.phoneNumber).replace(/[^+\d]/g, '')}`
              : '',
            email: '',
            address: settings.address || ''
          },
          media: {
            logo: settings.logoUrl || '',
            heroPrimary: settings.heroPrimaryImageUrl || '',
            heroSecondary: settings.heroSecondaryImageUrl || ''
          }
        });
      })
      .catch(() => {
        setBranding(emptyBranding);
      });
  }, []);

  const handleLogout = () => {
    clearAuthSession();
    setAuth(null);
  };

  return (
    <Routes>
      <Route
        path="/login"
        element={auth ? <Navigate to="/app" replace /> : <LoginPage onLogin={setAuth} branding={branding} />}
      />
      <Route path="/customer-login" element={<CustomerLoginPage />} />
      <Route path="/" element={<PublicHomePage branding={branding} />} />
      <Route path="/products" element={<PublicProductsPage branding={branding} />} />
      <Route path="/cart" element={<CartPage />} />
      <Route path="/checkout" element={<CheckoutPage />} />
      <Route path="/orders" element={<OrdersPage />} />
      <Route path="/account" element={<CustomerProfilePage />} />
      <Route
        path="/app/*"
        element={auth ? <ProtectedApp auth={auth} onLogout={handleLogout} branding={branding} /> : <Navigate to="/login" replace />}
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
