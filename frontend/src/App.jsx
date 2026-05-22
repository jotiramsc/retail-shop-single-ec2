import { Link, NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useCallback, useEffect, useMemo, useState } from 'react';
import BillingPage from './pages/BillingPage';
import ProductsPage from './pages/ProductsPage';
import CustomersPage from './pages/CustomersPage';
import CampaignsPage from './pages/CampaignsPage';
import ReportsPage from './pages/ReportsPage';
import SalespersonSalesPage from './pages/SalespersonSalesPage';
import SiteInteractionsPage from './pages/SiteInteractionsPage';
import SupportInboxPage from './pages/SupportInboxPage';
import LoginPage from './pages/LoginPage';
import ReceiptSettingsPage from './pages/ReceiptSettingsPage';
import UsersPage from './pages/UsersPage';
import PublicHomePage from './pages/PublicHomePage';
import PublicProductsPage from './pages/PublicProductsPage';
import PublicProductDetailPage from './pages/PublicProductDetailPage';
import CustomerLoginPage from './pages/CustomerLoginPage';
import CartPage from './pages/CartPage';
import CartAddPage from './pages/CartAddPage';
import CheckoutPage from './pages/CheckoutPage';
import OrdersPage from './pages/OrdersPage';
import CustomerProfilePage from './pages/CustomerProfilePage';
import WishlistPage from './pages/WishlistPage';
import PrivacyPolicyPage from './pages/PrivacyPolicyPage';
import { retailService } from './services/retailService';
import { clearAuthSession, getStoredAuthSession } from './utils/auth';
import { defaultBranding, getStoredBranding, normalizeBranding, storeBranding } from './utils/branding';
import { trackMetaEvent } from './utils/metaPixel';
import { getStoredVisitCount, storeVisitCount, trackCurrentSiteVisit } from './utils/siteInteraction';

const resolvedApiBaseUrl = window.__APP_CONFIG__?.API_BASE_URL || import.meta.env.VITE_API_BASE_URL || '/api';
const UI_SETTINGS_KEY = 'kps_sneat_ui_settings';

const defaultUiSettings = {
  collapsed: false,
  fixedNavbar: true,
  compact: false,
  rounded: true,
  menuAnimation: true
};

const readUiSettings = () => {
  try {
    return { ...defaultUiSettings, ...JSON.parse(window.localStorage.getItem(UI_SETTINGS_KEY) || '{}') };
  } catch {
    return defaultUiSettings;
  }
};

const navGroups = [
  {
    label: 'Store Operations',
    items: [
      { to: '/app', label: 'Billing', icon: 'bx-receipt', permission: 'BILLING', children: [
        { to: '/app', label: 'Product picker' },
        { to: '/app', label: 'Checkout' },
        { to: '/app', label: 'Latest invoices' }
      ] },
      { to: '/app/products', label: 'Inventory', icon: 'bx-package', permission: 'PRODUCTS', children: [
        { to: '/app/products?tab=products', label: 'Products' },
        { to: '/app/products?tab=categories', label: 'Categories' },
        { to: '/app/products?tab=collections', label: 'Collections' },
        { to: '/app/products?tab=brands', label: 'Brands' }
      ] },
      { to: '/app/customers', label: 'Customer CRM', icon: 'bx-user-voice', permission: 'CUSTOMERS', children: [
        { to: '/app/customers', label: 'Customer list' },
        { to: '/app/customers?tab=overview', label: 'Overview' },
        { to: '/app/customers?tab=timeline', label: 'Timeline' },
        { to: '/app/customers?tab=search', label: 'Search activity' },
        { to: '/app/customers?tab=login', label: 'Login history' },
        { to: '/app/customers?tab=ai', label: 'AI insights' }
      ] },
      { to: '/app/support', label: 'Support Inbox', icon: 'bx-conversation', permission: 'CUSTOMERS', badgeKey: 'supportUnread', children: [
        { to: '/app/support?tab=active', label: 'Active chats' },
        { to: '/app/support?tab=archived', label: 'Archived chats' },
        { to: '/app/support', label: 'Product sender' }
      ] }
    ]
  },
  {
    label: 'Growth',
    items: [
      { to: '/app/campaigns', label: 'Campaign Studio', icon: 'bx-broadcast', permission: ['MARKETING_AUTOMATION', 'OFFERS'], children: [
        { to: '/app/campaigns', label: 'Campaign list' },
        { to: '/app/campaigns?tab=create', label: 'Create campaign' },
        { to: '/app/campaigns?tab=offers', label: 'Offers' },
        { to: '/app/campaigns?tab=approval', label: 'Approval queue' },
        { to: '/app/campaigns?tab=schedule', label: 'Scheduled publishing' },
        { to: '/app/campaigns?tab=analytics', label: 'Analytics' }
      ] },
      { to: '/app/reports', label: 'Reports', icon: 'bx-line-chart', permission: 'REPORTS', children: [
        { to: '/app/reports?tab=dashboard', label: 'Dashboard' },
        { to: '/app/reports?tab=sales', label: 'Sales reports' },
        { to: '/app/reports?tab=payments', label: 'Razorpay diagnostics' },
        { to: '/app/reports', label: 'Low stock' },
        { to: '/app/reports', label: 'Website orders' }
      ] },
      { to: '/app/salesperson-sales', label: 'Salesperson Sales', icon: 'bx-medal', permission: 'SALESPERSON_SALES', children: [
        { to: '/app/salesperson-sales', label: 'Performance filters' },
        { to: '/app/salesperson-sales', label: 'Sales trend' },
        { to: '/app/salesperson-sales', label: 'Detailed records' }
      ] },
      { to: '/app/site-interactions', label: 'Site Interaction', icon: 'bx-map-alt', permission: 'SITE_INTERACTIONS', children: [
        { to: '/app/site-interactions', label: 'Traffic by day' },
        { to: '/app/site-interactions', label: 'Top sources' },
        { to: '/app/site-interactions', label: 'Maharashtra map' },
        { to: '/app/site-interactions', label: 'Recent visits' }
      ] }
    ]
  },
  {
    label: 'Admin',
    items: [
      { to: '/app/settings/receipt', label: 'Brand Configuration', icon: 'bx-palette', permission: 'RECEIPT_SETTINGS', children: [
        { to: '/app/settings/receipt?tab=brand', label: 'Business details' },
        { to: '/app/settings/receipt?tab=theme', label: 'Theme and media' },
        { to: '/app/settings/receipt?tab=social', label: 'Social links' },
        { to: '/app/settings/receipt?tab=facebook', label: 'Meta catalog' }
      ] },
      { to: '/app/users', label: 'Users', icon: 'bx-lock-open-alt', permission: 'USER_MANAGEMENT', children: [
        { to: '/app/users', label: 'Team accounts' },
        { to: '/app/users', label: 'Permissions' },
        { to: '/app/users', label: 'Access status' }
      ] }
    ]
  }
];

function flattenNav(groups) {
  return groups.flatMap((group) => group.items);
}

function pathFrom(to) {
  return to.split('?')[0];
}

function ProtectedApp({ auth, onLogout, branding }) {
  const location = useLocation();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [supportSummary, setSupportSummary] = useState({ unreadCount: 0 });
  const [supportAlert, setSupportAlert] = useState(null);
  const [uiSettings, setUiSettings] = useState(readUiSettings);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const permissions = Array.isArray(auth.permissions) ? auth.permissions : [];

  const canAccess = (permission) => {
    if (auth.role === 'ADMIN' || auth.role === 'OWNER') return true;
    if (Array.isArray(permission)) return permission.some((entry) => permissions.includes(entry));
    return permissions.includes(permission);
  };

  const visibleGroups = useMemo(() => navGroups
    .map((group) => ({ ...group, items: group.items.filter((item) => canAccess(item.permission)) }))
    .filter((group) => group.items.length), [auth.role, permissions]);
  const firstAllowedRoute = flattenNav(visibleGroups)[0]?.to || '/login';

  useEffect(() => {
    window.localStorage.setItem(UI_SETTINGS_KEY, JSON.stringify(uiSettings));
  }, [uiSettings]);

  useEffect(() => {
    setIsMobileMenuOpen(false);
  }, [location.pathname, location.search]);

  useEffect(() => {
    let cancelled = false;
    const loadSupportSummary = () => {
      retailService.getSupportSummary()
        .then((summary) => {
          if (cancelled) return;
          const nextSummary = summary || { unreadCount: 0 };
          setSupportSummary((current) => {
            if (Number(nextSummary.unreadCount || 0) > Number(current?.unreadCount || 0)
                && !location.pathname.startsWith('/app/support')) {
              setSupportAlert({ unreadCount: nextSummary.unreadCount, timestamp: new Date().toLocaleString() });
            }
            return nextSummary;
          });
        })
        .catch(() => {});
    };
    loadSupportSummary();
    const timer = window.setInterval(loadSupportSummary, 8000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [location.pathname]);

  const toggleSetting = (key) => {
    setUiSettings((current) => ({ ...current, [key]: !current[key] }));
  };

  const shellClasses = [
    'layout-wrapper layout-content-navbar kps-sneat-app',
    uiSettings.collapsed ? 'kps-menu-collapsed' : '',
    uiSettings.fixedNavbar ? 'kps-fixed-navbar' : '',
    uiSettings.compact ? 'kps-compact' : '',
    uiSettings.rounded ? 'kps-rounded' : 'kps-square',
    uiSettings.menuAnimation ? 'kps-menu-animated' : 'kps-no-animation',
    isMobileMenuOpen ? 'layout-menu-expanded' : ''
  ].filter(Boolean).join(' ');

  return (
    <div className={shellClasses}>
      <div className="layout-container">
        <aside id="layout-menu" className="layout-menu menu-vertical menu bg-menu-theme kps-sneat-menu">
          <div className="app-brand demo">
            <NavLink to="/app" className="app-brand-link">
              <span className="app-brand-logo demo">
                <img className="kps-sneat-logo" src={branding.media?.logo || '/assets/glowjewels/app_logo.png'} alt={`${branding.shopName || 'Store'} logo`} />
              </span>
              <span className="app-brand-text demo menu-text fw-bolder ms-2">{branding.shopName || 'KPS Admin'}</span>
            </NavLink>
            <button type="button" className="layout-menu-toggle menu-link text-large ms-auto kps-icon-button" onClick={() => toggleSetting('collapsed')} aria-label={uiSettings.collapsed ? 'Expand menu' : 'Collapse menu'}>
              <i className={`bx ${uiSettings.collapsed ? 'bx-chevron-right' : 'bx-chevron-left'} bx-sm align-middle`} />
            </button>
          </div>

          <div className="menu-inner-shadow" />
          <ul className="menu-inner py-1">
            {visibleGroups.map((group) => (
              <li className="kps-menu-group" key={group.label}>
                <div className="menu-header small text-uppercase">
                  <span className="menu-header-text">{group.label}</span>
                </div>
                {group.items.map((item) => {
                  const isParentActive = location.pathname === pathFrom(item.to) || (item.to !== '/app' && location.pathname.startsWith(pathFrom(item.to)));
                  return (
                    <li key={item.to} className={`menu-item ${isParentActive ? 'active open' : ''}`}>
                      <NavLink to={item.to} end={item.to === '/app'} className="menu-link menu-toggle kps-parent-link">
                        <i className={`menu-icon tf-icons bx ${item.icon}`} />
                        <div>{item.label}</div>
                        {item.badgeKey === 'supportUnread' && Number(supportSummary.unreadCount || 0) > 0 ? (
                          <div className="badge bg-label-warning rounded-pill ms-auto">{supportSummary.unreadCount}</div>
                        ) : null}
                      </NavLink>
                      {item.children?.length ? (
                        <ul className="menu-sub">
                          {item.children.map((child) => (
                            <li className="menu-item" key={`${item.to}-${child.label}`}>
                              <NavLink to={child.to} className="menu-link">
                                <div>{child.label}</div>
                              </NavLink>
                            </li>
                          ))}
                        </ul>
                      ) : null}
                    </li>
                  );
                })}
              </li>
            ))}
          </ul>
        </aside>

        <div className="layout-page">
          <nav className="layout-navbar container-xxl navbar navbar-expand-xl navbar-detached align-items-center bg-navbar-theme kps-sneat-navbar">
            <div className="layout-menu-toggle navbar-nav align-items-xl-center me-3 me-xl-0 d-xl-none">
              <button className="nav-item nav-link px-0 me-xl-4 kps-icon-button" type="button" onClick={() => setIsMobileMenuOpen((current) => !current)} aria-label="Toggle menu">
                <i className="bx bx-menu bx-sm" />
              </button>
            </div>
            <div className="navbar-nav-right d-flex align-items-center w-100">
              <div className="navbar-nav align-items-center w-100">
                <div className="nav-item d-flex align-items-center w-100">
                  <i className="bx bx-search fs-4 lh-0" />
                  <input type="text" className="form-control border-0 shadow-none" placeholder="Search products, customers, invoices, campaigns..." />
                </div>
              </div>
              <ul className="navbar-nav flex-row align-items-center ms-auto">
                <li className="nav-item me-3 d-none d-md-block">
                  <span className="badge bg-label-success">Live APIs via {resolvedApiBaseUrl}</span>
                </li>
                <li className="nav-item me-2">
                  <button type="button" className="btn btn-icon btn-outline-primary" onClick={() => setSettingsOpen(true)} aria-label="Open UI settings">
                    <i className="bx bx-cog" />
                  </button>
                </li>
                <li className="nav-item dropdown">
                  <button className="nav-link dropdown-toggle hide-arrow kps-avatar-button" type="button" data-bs-toggle="dropdown">
                    <div className="avatar avatar-online">
                      <span className="avatar-initial rounded-circle bg-label-primary">{String(auth.displayName || 'K').slice(0, 1).toUpperCase()}</span>
                    </div>
                  </button>
                  <ul className="dropdown-menu dropdown-menu-end">
                    <li><span className="dropdown-item"><i className="bx bx-user me-2" />{auth.displayName} ({auth.role})</span></li>
                    <li><span className="dropdown-item"><i className="bx bx-phone me-2" />{branding.contact?.phoneLabel || 'No phone configured'}</span></li>
                    <li><div className="dropdown-divider" /></li>
                    <li><button className="dropdown-item" type="button" onClick={onLogout}><i className="bx bx-power-off me-2" />Log Out</button></li>
                  </ul>
                </li>
              </ul>
            </div>
          </nav>

          <div className="content-wrapper">
            <main className="container-xxl flex-grow-1 container-p-y kps-sneat-content">
              {supportAlert ? (
                <div className="alert alert-warning d-flex align-items-center justify-content-between gap-3 kps-support-alert" role="alert">
                  <div>
                    <strong>New support chat</strong>
                    <span className="d-block">{supportAlert.unreadCount} unread WhatsApp message{Number(supportAlert.unreadCount) === 1 ? '' : 's'} · {supportAlert.timestamp}</span>
                  </div>
                  <div className="d-flex gap-2">
                    <Link to="/app/support" className="btn btn-primary btn-sm" onClick={() => setSupportAlert(null)}>Open chat</Link>
                    <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setSupportAlert(null)}>Dismiss</button>
                  </div>
                </div>
              ) : null}
              <Routes>
                <Route index element={canAccess('BILLING') ? <BillingPage /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="products" element={canAccess('PRODUCTS') ? <ProductsPage /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="customers" element={canAccess('CUSTOMERS') ? <CustomersPage /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="support" element={canAccess('CUSTOMERS') ? <SupportInboxPage /> : <Navigate to={firstAllowedRoute} replace />} />
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
            <footer className="content-footer footer bg-footer-theme">
              <div className="container-xxl d-flex flex-wrap justify-content-between py-2 flex-md-row flex-column">
                <div className="mb-2 mb-md-0">Sneat admin migration shell connected to existing live APIs.</div>
                <div><span className="text-muted">{branding.shopName || 'KPS Krishnai Pearl Shopee'}</span></div>
              </div>
            </footer>
          </div>
        </div>
      </div>
      <button type="button" className="layout-overlay layout-menu-toggle" onClick={() => setIsMobileMenuOpen(false)} aria-label="Close menu" />
      {settingsOpen ? (
        <div className="kps-settings-backdrop" role="presentation" onClick={() => setSettingsOpen(false)}>
          <aside className="kps-settings-panel" role="dialog" aria-modal="true" aria-label="UI settings" onClick={(event) => event.stopPropagation()}>
            <div className="d-flex justify-content-between align-items-start mb-4">
              <div>
                <h5 className="mb-1">UI Settings</h5>
                <p className="text-muted mb-0">Preview menu and layout options before migration.</p>
              </div>
              <button type="button" className="btn btn-icon btn-outline-secondary" onClick={() => setSettingsOpen(false)} aria-label="Close UI settings"><i className="bx bx-x" /></button>
            </div>
            {[
              ['collapsed', 'Collapsed side menu', 'Use compact icon rail for dense admin work.'],
              ['fixedNavbar', 'Fixed navbar', 'Keep search and settings visible while scrolling.'],
              ['compact', 'Compact density', 'Reduce spacing for faster data scanning.'],
              ['rounded', 'Rounded Sneat cards', 'Use softer card corners and inputs.'],
              ['menuAnimation', 'Menu animations', 'Animate submenu open, hover, and transitions.']
            ].map(([key, title, description]) => (
              <label className="kps-setting-row" key={key}>
                <span>
                  <strong>{title}</strong>
                  <small>{description}</small>
                </span>
                <input className="form-check-input" type="checkbox" checked={Boolean(uiSettings[key])} onChange={() => toggleSetting(key)} />
              </label>
            ))}
          </aside>
        </div>
      ) : null}
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
    if (!auth) return undefined;
    const expiresAt = Date.parse(auth.expiresAt || '');
    if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
      handleLogout();
      return undefined;
    }
    const timeoutId = window.setTimeout(handleLogout, Math.min(expiresAt - Date.now(), 2_147_483_647));
    return () => window.clearTimeout(timeoutId);
  }, [auth, handleLogout]);

  useEffect(() => {
    document.title = branding.shopName ? `${branding.shopName} - Admin` : 'KPS Admin';
  }, [branding.shopName]);

  useEffect(() => {
    if (location.pathname.startsWith('/app') || location.pathname === '/login') return;
    trackMetaEvent(branding.metaPixelId, 'PageView');
  }, [branding.metaPixelId, location.pathname, location.search]);

  useEffect(() => {
    retailService.getReceiptSettings()
      .then((settings) => setBranding(storeBranding(normalizeBranding(settings))))
      .catch(() => setBranding(defaultBranding));
  }, []);

  useEffect(() => {
    if (location.pathname.startsWith('/app') || location.pathname === '/login') return undefined;
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
      <Route path="/login" element={auth ? <Navigate to="/app" replace /> : <LoginPage onLogin={setAuth} branding={branding} />} />
      <Route path="/customer-login" element={<CustomerLoginPage />} />
      <Route path="/" element={<PublicHomePage branding={branding} siteVisitCount={siteVisitCount} />} />
      <Route path="/products" element={<PublicProductsPage branding={branding} />} />
      <Route path="/product/:productId" element={<PublicProductDetailPage branding={branding} />} />
      <Route path="/products/:productId" element={<PublicProductDetailPage branding={branding} />} />
      <Route path="/cart/add" element={<CartAddPage />} />
      <Route path="/cart" element={<CartPage />} />
      <Route path="/wishlist" element={<WishlistPage />} />
      <Route path="/checkout" element={<CheckoutPage branding={branding} />} />
      <Route path="/orders" element={<OrdersPage />} />
      <Route path="/account" element={<CustomerProfilePage />} />
      <Route path="/privacy-policy" element={<PrivacyPolicyPage branding={branding} />} />
      <Route path="/app/*" element={auth ? <ProtectedApp auth={auth} onLogout={handleLogout} branding={branding} /> : <Navigate to="/login" replace />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
