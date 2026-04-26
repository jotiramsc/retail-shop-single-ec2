const AUTH_STORAGE_KEY = 'retail_shop_auth';
const CUSTOMER_AUTH_STORAGE_KEY = 'retail_shop_customer_auth';
const CHECKOUT_COUPON_KEY = 'retail_shop_checkout_coupon';

export const buildBasicToken = (username, password) =>
  `Basic ${window.btoa(`${username}:${password}`)}`;

export const storeAuthSession = (session) => {
  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session));
};

export const getStoredAuthSession = () => {
  try {
    const raw = window.localStorage.getItem(AUTH_STORAGE_KEY);
    if (!raw) {
      return null;
    }
    const session = JSON.parse(raw);
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
