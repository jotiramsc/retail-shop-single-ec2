const AUTH_STORAGE_KEY = 'retail_shop_auth';
const CUSTOMER_AUTH_STORAGE_KEY = 'retail_shop_customer_auth';
const CHECKOUT_COUPON_KEY = 'retail_shop_checkout_coupon';

const isExpiredSession = (session) => {
  const expiresAt = Date.parse(session?.expiresAt || '');
  return !Number.isFinite(expiresAt) || expiresAt <= Date.now();
};

export const storeAuthSession = (session) => {
  window.localStorage.removeItem(AUTH_STORAGE_KEY);
  window.sessionStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
};

export const getStoredAuthSession = () => {
  window.localStorage.removeItem(AUTH_STORAGE_KEY);
  try {
    const raw = window.sessionStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const session = JSON.parse(raw);
    if (isExpiredSession(session)) {
      clearAuthSession();
      return null;
    }
    return {
      ...session,
      permissions: Array.isArray(session.permissions) ? session.permissions : []
    };
  } catch {
    return null;
  }
};

export const clearAuthSession = () => {
  window.localStorage.removeItem(AUTH_STORAGE_KEY);
  window.sessionStorage.removeItem(AUTH_STORAGE_KEY);
};

export const storeCustomerSession = (session) => {
  window.localStorage.setItem(CUSTOMER_AUTH_STORAGE_KEY, JSON.stringify(session));
};

export const getStoredCustomerSession = () => {
  try {
    const raw = window.localStorage.getItem(CUSTOMER_AUTH_STORAGE_KEY);
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
};

export const clearCustomerSession = () => {
  window.localStorage.removeItem(CUSTOMER_AUTH_STORAGE_KEY);
  window.localStorage.removeItem(CHECKOUT_COUPON_KEY);
};

export const isCustomerAuthError = (error) =>
  [401, 403].includes(Number(error?.response?.status || 0));
