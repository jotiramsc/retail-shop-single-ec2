import { Link, NavLink, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { Fragment, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import BillingPage from './pages/BillingPage';
import AdminOrdersPage from './pages/AdminOrdersPage';
import AdminDashboardPage from './pages/AdminDashboardPage';
import ProductsPage from './pages/ProductsPage';
import CustomerCrmModulePage from './pages/CustomerCrmModulePage';
import CampaignStudioModulePage from './pages/CampaignStudioModulePage';
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

function isLocalDevAdminEnabled() {
  if (!import.meta.env.DEV || typeof window === 'undefined') return false;
  return ['localhost', '127.0.0.1', '::1'].includes(window.location.hostname);
}

function getLocalDevAdminSession() {
  if (!isLocalDevAdminEnabled()) return null;
  return {
    token: 'local-dev-admin',
    displayName: 'Local Admin',
    role: 'ADMIN',
    permissions: Array.from(new Set(navGroups.flatMap((group) => group.items.flatMap((item) => [
      ...(Array.isArray(item.permission) ? item.permission : [item.permission]),
      ...(item.children || []).map((child) => child.permission).filter(Boolean)
    ])))),
    expiresAt: new Date(Date.now() + 12 * 60 * 60 * 1000).toISOString()
  };
}

const navGroups = [
  {
    label: 'Store Operations',
    items: [
      { to: '/app/billing', label: 'Billing', icon: 'bx-receipt', permission: 'BILLING', children: [
        { to: '/app/billing', label: 'Checkout', permission: 'BILLING_CHECKOUT' },
        { to: '/app/billing/invoices', label: 'Invoices', permission: 'BILLING_INVOICES' },
        { to: '/app/billing/orders', label: 'Orders', permission: 'BILLING_ORDERS' }
      ] },
      { to: '/app/inventory/products', label: 'Inventory', icon: 'bx-package', permission: 'PRODUCTS', children: [
        { to: '/app/inventory/products', label: 'Products', permission: 'PRODUCTS_LIST' },
        { to: '/app/inventory/categories', label: 'Categories', permission: 'PRODUCTS_CATEGORIES' }
      ] },
      { to: '/app/crm/customers/overview', label: 'Customer CRM', icon: 'bx-user-voice', permission: 'CUSTOMERS', children: [
        { to: '/app/crm/dashboard', label: 'Dashboard', permission: 'CUSTOMERS_DASHBOARD' },
        { to: '/app/crm/customers/overview', label: 'Customer Info', permission: 'CUSTOMERS_OVERVIEW' },
        { to: '/app/crm/customers/search-activity', label: 'Search Activity', permission: 'CUSTOMERS_SEARCH_ACTIVITY' },
        { to: '/app/crm/customers/login-history', label: 'Login History', permission: 'CUSTOMERS_LOGIN_HISTORY' },
        { to: '/app/crm/customers/support-chat', label: 'Support Chat', permission: 'CUSTOMERS_SUPPORT_CHAT' },
        { to: '/app/crm/customers/ai-insights', label: 'AI Insights', permission: 'CUSTOMERS_AI_INSIGHTS' }
      ] },
      { to: '/app/support/active', label: 'Support', icon: 'bx-support', permission: 'CUSTOMERS', badgeKey: 'supportUnread', children: [
        { to: '/app/support/active', label: 'Active Conversations', permission: 'CUSTOMERS_SUPPORT_CHAT' },
        { to: '/app/support/archived', label: 'Archived Conversations', permission: 'CUSTOMERS_SUPPORT_CHAT' }
      ] }
    ]
  },
  {
    label: 'Growth',
    items: [
      { to: '/app/campaigns/dashboard', label: 'Campaign Studio', icon: 'bx-broadcast', permission: ['MARKETING_AUTOMATION', 'OFFERS'], children: [
        { to: '/app/campaigns/dashboard', label: 'Campaign Dashboard', permission: 'CAMPAIGNS_DASHBOARD' },
        { to: '/app/campaigns/list', label: 'Campaign List', permission: 'CAMPAIGNS_LIST' },
        { to: '/app/campaigns/create', label: 'Create Campaign', permission: 'CAMPAIGNS_CREATE' },
        { to: '/app/campaigns/templates', label: 'Templates', permission: 'CAMPAIGNS_TEMPLATES' },
        { to: '/app/campaigns/audience', label: 'Audience', permission: 'CAMPAIGNS_AUDIENCE' },
        { to: '/app/campaigns/offers', label: 'Offers', permission: 'CAMPAIGNS_OFFERS' },
        { to: '/app/campaigns/approval', label: 'Approval Queue', permission: 'CAMPAIGNS_APPROVAL' }
      ] },
      { to: '/app/reports', label: 'Reports', icon: 'bx-line-chart', permission: 'REPORTS', children: [
        { to: '/app/reports/dashboard', label: 'Dashboard', permission: 'REPORTS_DASHBOARD' },
        { to: '/app/reports/sales', label: 'Sales reports', permission: 'REPORTS_SALES' },
        { to: '/app/reports/payments', label: 'Razorpay diagnostics', permission: 'REPORTS_PAYMENTS' }
      ] },
      { to: '/app/salesperson-sales', label: 'Salesperson Sales', icon: 'bx-medal', permission: 'SALESPERSON_SALES' },
      { to: '/app/site-interactions', label: 'Site Interaction', icon: 'bx-map-alt', permission: 'SITE_INTERACTIONS' }
    ]
  },
  {
    label: 'Admin',
    items: [
      { to: '/app/settings/receipt', label: 'Brand Configuration', icon: 'bx-palette', permission: 'RECEIPT_SETTINGS', children: [
        { to: '/app/settings/receipt/business', label: 'Business details', permission: 'RECEIPT_SETTINGS_BUSINESS' },
        { to: '/app/settings/receipt/theme', label: 'Theme and media', permission: 'RECEIPT_SETTINGS_THEME' },
        { to: '/app/settings/receipt/social', label: 'Social links', permission: 'RECEIPT_SETTINGS_SOCIAL' },
        { to: '/app/settings/receipt/meta-catalog', label: 'Meta catalog', permission: 'RECEIPT_SETTINGS_META_CATALOG' }
      ] },
      { to: '/app/users', label: 'Users', icon: 'bx-lock-open-alt', permission: 'USER_MANAGEMENT', children: [
        { to: '/app/users', label: 'Team Accounts' }
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

function isRouteActive(location, to) {
  const target = pathFrom(to);
  if (target === '/app') {
    return location.pathname === '/app';
  }
  return location.pathname === target || location.pathname.startsWith(`${target}/`);
}

function isNavItemActive(location, item) {
  return isRouteActive(location, item.to) || Boolean(item.children?.some((child) => isRouteActive(location, child.to)));
}

function ProtectedApp({ auth, onLogout, branding }) {
  const location = useLocation();
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [supportSummary, setSupportSummary] = useState({ unreadCount: 0 });
  const [supportAlert, setSupportAlert] = useState(null);
  const [isMenuCollapsed, setIsMenuCollapsed] = useState(false);
  const [openMenuKey, setOpenMenuKey] = useState('');
  const [submenuHeights, setSubmenuHeights] = useState({});
  const submenuRefs = useRef({});
  const permissions = Array.isArray(auth.permissions) ? auth.permissions : [];

  const canAccess = (permission) => {
    if (auth.role === 'ADMIN' || auth.role === 'OWNER') return true;
    if (Array.isArray(permission)) return permission.some((entry) => permissions.includes(entry));
    return permissions.includes(permission);
  };
  const canAccessAny = (...entries) => entries.some((entry) => canAccess(entry));

  const visibleChildrenFor = (item) => {
    if (!item.children?.length) return [];
    return item.children.filter((child) => !child.permission || canAccess(child.permission) || canAccess(item.permission));
  };

  const visibleGroups = useMemo(() => navGroups
    .map((group) => ({
      ...group,
      items: group.items
        .map((item) => ({ ...item, children: visibleChildrenFor(item) }))
        .filter((item) => canAccess(item.permission) || item.children.length)
    }))
    .filter((group) => group.items.length), [auth.role, permissions]);
  const firstAllowedRoute = flattenNav(visibleGroups)[0]?.to || '/login';

  useEffect(() => {
    setIsMobileMenuOpen(false);
  }, [location.pathname, location.search]);

  useEffect(() => {
    const activeItem = visibleGroups
      .flatMap((group) => group.items)
      .find((item) => item.children?.length && isNavItemActive(location, item));
    setOpenMenuKey(activeItem?.to || '');
  }, [location, visibleGroups]);

  useLayoutEffect(() => {
    const next = {};
    Object.entries(submenuRefs.current).forEach(([key, node]) => {
      if (node) next[key] = node.scrollHeight;
    });
    setSubmenuHeights(next);
  }, [visibleGroups, openMenuKey, isMenuCollapsed]);

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

  const handleParentMenuToggle = (item) => {
    if (isMenuCollapsed) {
      setIsMenuCollapsed(false);
      setOpenMenuKey(item.to);
      return;
    }
    setOpenMenuKey((current) => (current === item.to ? '' : item.to));
  };

  const shellClasses = [
    'layout-wrapper layout-content-navbar kps-sneat-app',
    isMenuCollapsed ? 'kps-menu-collapsed' : '',
    'kps-fixed-navbar kps-rounded kps-menu-animated',
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
            <button type="button" className="layout-menu-toggle menu-link text-large ms-auto kps-icon-button" onClick={() => setIsMenuCollapsed((current) => !current)} aria-label={isMenuCollapsed ? 'Expand menu' : 'Collapse menu'}>
              <i className={`bx ${isMenuCollapsed ? 'bx-chevron-right' : 'bx-chevron-left'} bx-sm align-middle`} />
            </button>
          </div>
          {isMenuCollapsed ? (
            <button type="button" className="kps-menu-reopen" onClick={() => setIsMenuCollapsed(false)} aria-label="Open menu">
              <i className="bx bx-chevron-right" />
            </button>
          ) : null}

          <div className="menu-inner-shadow" />
          <ul className="menu-inner py-1">
            {visibleGroups.map((group) => (
              <Fragment key={group.label}>
                <li className="menu-header small text-uppercase kps-menu-group">
                  <span className="menu-header-text">{group.label}</span>
                </li>
                {group.items.map((item) => {
                  const isParentActive = isNavItemActive(location, item);
                  const hasChildren = Boolean(item.children?.length);
                  const isOpen = hasChildren && !isMenuCollapsed && openMenuKey === item.to;
                  return (
                    <li key={item.to} className={`menu-item ${isParentActive ? 'active' : ''} ${isOpen ? 'open' : ''}`}>
                      {hasChildren ? (
                        <div className="menu-link kps-parent-link kps-menu-parent-row">
                          <button
                            type="button"
                            className="kps-parent-icon-toggle"
                            onClick={() => handleParentMenuToggle(item)}
                            aria-expanded={isOpen}
                            aria-label={`${isOpen ? 'Collapse' : 'Expand'} ${item.label} submenu`}
                          >
                            <i className={`menu-icon tf-icons bx ${item.icon}`} />
                          </button>
                          <NavLink to={item.children[0]?.to || item.to} className="kps-menu-parent-target" aria-label={`Open ${item.label}`}>
                            <div>{item.label}</div>
                          </NavLink>
                          {item.badgeKey === 'supportUnread' && Number(supportSummary.unreadCount || 0) > 0 ? (
                            <span className="badge bg-label-warning rounded-pill ms-auto">{supportSummary.unreadCount}</span>
                          ) : null}
                        </div>
                      ) : (
                        <NavLink to={item.to} end={item.to === '/app'} className="menu-link kps-parent-link">
                          <i className={`menu-icon tf-icons bx ${item.icon}`} />
                          <div>{item.label}</div>
                        </NavLink>
                      )}
                      {hasChildren ? (
                        <ul
                          className="menu-sub"
                          ref={(node) => {
                            submenuRefs.current[item.to] = node;
                          }}
                          aria-hidden={!isOpen}
                          style={{
                            maxHeight: isOpen ? `${submenuHeights[item.to] || item.children.length * 42}px` : '0px',
                            pointerEvents: isOpen ? 'auto' : 'none',
                            visibility: isOpen ? 'visible' : 'hidden'
                          }}
                        >
                          {item.children.map((child) => (
                            <li className={`menu-item ${isRouteActive(location, child.to) ? 'active' : ''}`} key={`${item.to}-${child.label}`}>
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
              </Fragment>
            ))}
          </ul>
        </aside>

        <div className="layout-page">
          <nav className="layout-navbar container-fluid navbar navbar-expand-xl navbar-detached align-items-center bg-navbar-theme kps-sneat-navbar">
            <div className="layout-menu-toggle navbar-nav align-items-xl-center me-3 me-xl-0 d-xl-none">
              <button className="nav-item nav-link px-0 me-xl-4 kps-icon-button" type="button" onClick={() => setIsMobileMenuOpen((current) => !current)} aria-label="Toggle menu">
                <i className="bx bx-menu bx-sm" />
              </button>
            </div>
            <div className="navbar-nav-right d-flex align-items-center w-100">
              <div className="navbar-nav align-items-center me-auto">
                <div className="nav-item d-flex align-items-center kps-navbar-title">
                  <i className="bx bx-grid-alt fs-4 lh-0" />
                  <span>Admin Workspace</span>
                </div>
              </div>
              <ul className="navbar-nav flex-row align-items-center ms-auto">
                <li className="nav-item dropdown">
                  <button className="nav-link dropdown-toggle hide-arrow kps-avatar-button" type="button" data-bs-toggle="dropdown">
                    <span className="kps-avatar-initial">{String(auth.displayName || 'K').slice(0, 1).toUpperCase()}</span>
                    <span className="kps-user-chip">
                      <strong>{auth.displayName || auth.username || 'Admin user'}</strong>
                      <small>{auth.role || 'Team member'}</small>
                    </span>
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
                    <Link to="/app/support/active" className="btn btn-primary btn-sm" onClick={() => setSupportAlert(null)}>Open chat</Link>
                    <button type="button" className="btn btn-outline-secondary btn-sm" onClick={() => setSupportAlert(null)}>Dismiss</button>
                  </div>
                </div>
              ) : null}
              <div className="kps-route-transition" key={location.pathname}>
              <Routes>
                <Route index element={<AdminDashboardPage branding={branding} auth={auth} />} />
                <Route path="billing" element={canAccessAny('BILLING', 'BILLING_CHECKOUT') ? <BillingPage /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="billing/checkout" element={canAccessAny('BILLING', 'BILLING_CHECKOUT') ? <Navigate to="/app/billing" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="billing/invoices" element={canAccessAny('BILLING', 'BILLING_INVOICES') ? <AdminOrdersPage mode="invoices" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="billing/invoices/:orderId" element={canAccessAny('BILLING', 'BILLING_INVOICES') ? <AdminOrdersPage mode="invoice-details" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="billing/orders" element={canAccessAny('BILLING', 'BILLING_ORDERS') ? <AdminOrdersPage mode="orders" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="billing/orders/:orderId" element={canAccessAny('BILLING', 'BILLING_ORDERS') ? <AdminOrdersPage mode="order-details" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="products" element={canAccess('PRODUCTS') ? <Navigate to="/app/inventory/products" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="inventory" element={<Navigate to="/app/inventory/products" replace />} />
                <Route path="inventory/products" element={canAccessAny('PRODUCTS', 'PRODUCTS_LIST') ? <ProductsPage initialTab="products" hideTabs /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="inventory/categories" element={canAccessAny('PRODUCTS', 'PRODUCTS_CATEGORIES') ? <ProductsPage initialTab="categories" hideTabs /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="inventory/collections" element={canAccess('PRODUCTS') ? <Navigate to="/app/inventory/products" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="inventory/brands" element={canAccess('PRODUCTS') ? <Navigate to="/app/inventory/products" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="customers" element={canAccess('CUSTOMERS') ? <Navigate to="/app/crm/customers/overview" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="crm" element={<Navigate to="/app/crm/customers/overview" replace />} />
                <Route path="crm/dashboard" element={canAccessAny('CUSTOMERS', 'CUSTOMERS_DASHBOARD') ? <CustomerCrmModulePage screen="dashboard" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="crm/customers" element={canAccess('CUSTOMERS') ? <Navigate to="/app/crm/customers/overview" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="crm/customers/overview" element={canAccessAny('CUSTOMERS', 'CUSTOMERS_OVERVIEW') ? <CustomerCrmModulePage screen="customers" detailTab="Overview" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="crm/customers/timeline" element={canAccess('CUSTOMERS') ? <Navigate to="/app/crm/customers/overview" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="crm/customers/orders" element={canAccess('CUSTOMERS') ? <Navigate to="/app/crm/customers/overview" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="crm/customers/preferences" element={canAccess('CUSTOMERS') ? <Navigate to="/app/crm/customers/overview" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="crm/customers/search-activity" element={canAccessAny('CUSTOMERS', 'CUSTOMERS_SEARCH_ACTIVITY') ? <CustomerCrmModulePage screen="customers" detailTab="Search Activity" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="crm/customers/login-history" element={canAccessAny('CUSTOMERS', 'CUSTOMERS_LOGIN_HISTORY') ? <CustomerCrmModulePage screen="customers" detailTab="Login History" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="crm/customers/support-chat" element={canAccessAny('CUSTOMERS', 'CUSTOMERS_SUPPORT_CHAT') ? <CustomerCrmModulePage screen="customers" detailTab="Support Chat" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="crm/customers/ai-insights" element={canAccessAny('CUSTOMERS', 'CUSTOMERS_AI_INSIGHTS') ? <CustomerCrmModulePage screen="customers" detailTab="AI Insights" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="crm/leads" element={canAccess('CUSTOMERS') ? <Navigate to="/app/crm/customers/overview" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="crm/opportunities" element={canAccess('CUSTOMERS') ? <Navigate to="/app/crm/customers/overview" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="crm/activities" element={canAccess('CUSTOMERS') ? <Navigate to="/app/crm/customers/overview" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="crm/reports" element={canAccess('CUSTOMERS') ? <Navigate to="/app/crm/dashboard" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="crm/settings" element={canAccess('CUSTOMERS') ? <Navigate to="/app/crm/customers/overview" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="support" element={canAccess('CUSTOMERS') ? <Navigate to="/app/support/active" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="support/active" element={canAccessAny('CUSTOMERS', 'CUSTOMERS_SUPPORT_CHAT') ? <SupportInboxPage initialTab="ACTIVE" hideTabs /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="support/archived" element={canAccessAny('CUSTOMERS', 'CUSTOMERS_SUPPORT_CHAT') ? <SupportInboxPage initialTab="ARCHIVED" hideTabs /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="support/product-sender" element={canAccess('CUSTOMERS') ? <Navigate to="/app/support/active" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="whatsapp" element={canAccess('CUSTOMERS') ? <Navigate to="/app/support/active" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="whatsapp/*" element={canAccess('CUSTOMERS') ? <Navigate to="/app/support/active" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="offers" element={canAccess(['MARKETING_AUTOMATION', 'OFFERS']) ? <Navigate to="/app/campaigns/offers" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="campaigns" element={canAccess(['MARKETING_AUTOMATION', 'OFFERS']) ? <Navigate to="/app/campaigns/dashboard" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="campaigns/dashboard" element={canAccessAny(['MARKETING_AUTOMATION', 'OFFERS'], 'CAMPAIGNS_DASHBOARD') ? <CampaignStudioModulePage screen="dashboard" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="campaigns/list" element={canAccessAny(['MARKETING_AUTOMATION', 'OFFERS'], 'CAMPAIGNS_LIST') ? <CampaignStudioModulePage screen="list" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="campaigns/create" element={canAccessAny(['MARKETING_AUTOMATION', 'OFFERS'], 'CAMPAIGNS_CREATE') ? <CampaignStudioModulePage screen="create" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="campaigns/offers" element={canAccessAny(['MARKETING_AUTOMATION', 'OFFERS'], 'CAMPAIGNS_OFFERS') ? <CampaignStudioModulePage screen="offers" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="campaigns/approval" element={canAccessAny(['MARKETING_AUTOMATION', 'OFFERS'], 'CAMPAIGNS_APPROVAL') ? <CampaignStudioModulePage screen="approval" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="campaigns/templates" element={canAccessAny(['MARKETING_AUTOMATION', 'OFFERS'], 'CAMPAIGNS_TEMPLATES') ? <CampaignStudioModulePage screen="templates" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="campaigns/audience" element={canAccessAny(['MARKETING_AUTOMATION', 'OFFERS'], 'CAMPAIGNS_AUDIENCE') ? <CampaignStudioModulePage screen="audience" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="campaigns/analytics" element={canAccess(['MARKETING_AUTOMATION', 'OFFERS']) ? <CampaignStudioModulePage screen="analytics" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="campaigns/scheduler" element={canAccess(['MARKETING_AUTOMATION', 'OFFERS']) ? <CampaignStudioModulePage screen="scheduler" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="campaigns/reports" element={canAccess(['MARKETING_AUTOMATION', 'OFFERS']) ? <CampaignStudioModulePage screen="reports" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="campaigns/automation" element={canAccess(['MARKETING_AUTOMATION', 'OFFERS']) ? <CampaignStudioModulePage screen="automation" /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="reports" element={canAccess('REPORTS') ? <Navigate to="/app/reports/dashboard" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="reports/dashboard" element={canAccessAny('REPORTS', 'REPORTS_DASHBOARD') ? <ReportsPage initialTab="dashboard" hideTabs /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="reports/sales" element={canAccessAny('REPORTS', 'REPORTS_SALES') ? <ReportsPage initialTab="sales" hideTabs /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="reports/payments" element={canAccessAny('REPORTS', 'REPORTS_PAYMENTS') ? <ReportsPage initialTab="payments" hideTabs /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="reports/low-stock" element={canAccess('REPORTS') ? <Navigate to="/app/reports/dashboard" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="reports/website-orders" element={canAccess('REPORTS') ? <Navigate to="/app/reports/dashboard" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="salesperson-sales" element={canAccess('SALESPERSON_SALES') ? <SalespersonSalesPage auth={auth} /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="site-interactions" element={canAccess('SITE_INTERACTIONS') ? <SiteInteractionsPage /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="settings/receipt" element={canAccess('RECEIPT_SETTINGS') ? <Navigate to="/app/settings/receipt/business" replace /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="settings/receipt/business" element={canAccessAny('RECEIPT_SETTINGS', 'RECEIPT_SETTINGS_BUSINESS') ? <ReceiptSettingsPage initialTab="brand" hideTabs /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="settings/receipt/theme" element={canAccessAny('RECEIPT_SETTINGS', 'RECEIPT_SETTINGS_THEME') ? <ReceiptSettingsPage initialTab="theme" hideTabs /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="settings/receipt/social" element={canAccessAny('RECEIPT_SETTINGS', 'RECEIPT_SETTINGS_SOCIAL') ? <ReceiptSettingsPage initialTab="social" hideTabs /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="settings/receipt/meta-catalog" element={canAccessAny('RECEIPT_SETTINGS', 'RECEIPT_SETTINGS_META_CATALOG') ? <ReceiptSettingsPage initialTab="facebook" hideTabs /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="users" element={canAccess('USER_MANAGEMENT') ? <UsersPage /> : <Navigate to={firstAllowedRoute} replace />} />
                <Route path="*" element={<Navigate to={firstAllowedRoute} replace />} />
              </Routes>
              </div>
            </main>
            <footer className="content-footer footer bg-footer-theme">
              <div className="container-xxl d-flex flex-wrap justify-content-between py-2 flex-md-row flex-column">
                <div className="mb-2 mb-md-0">{branding.shopName || 'KPS Krishnai Pearl Shopee'} admin workspace.</div>
                <div><span className="text-muted">{branding.shopName || 'KPS Krishnai Pearl Shopee'}</span></div>
              </div>
            </footer>
          </div>
        </div>
      </div>
      <button type="button" className="layout-overlay layout-menu-toggle" onClick={() => setIsMobileMenuOpen(false)} aria-label="Close menu" />
    </div>
  );
}

export default function App() {
  const location = useLocation();
  const [auth, setAuth] = useState(() => getStoredAuthSession() || getLocalDevAdminSession());
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
