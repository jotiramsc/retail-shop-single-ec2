import axios from 'axios';
import {
  clearAuthSession,
  clearCustomerSession,
  getStoredAuthSession,
  getStoredCustomerSession
} from '../utils/auth';

const runtimeApiBaseUrl = window.__APP_CONFIG__?.API_BASE_URL;
const resolvedApiBaseUrl = runtimeApiBaseUrl || import.meta.env.VITE_API_BASE_URL || '/api';

const api = axios.create({
  baseURL: resolvedApiBaseUrl,
  headers: {
    'Content-Type': 'application/json'
  }
});

const isStaffAppRequest = () => window.location.pathname.startsWith('/app');
const CUSTOMER_PROTECTED_PREFIXES = [
  '/cart',
  '/address',
  '/order',
  '/orders',
  '/checkout',
  '/customer-profile',
  '/offers/applicable'
];

const isCustomerProtectedRequest = (config) => {
  const url = String(config.url || '');
  return CUSTOMER_PROTECTED_PREFIXES.some((prefix) => url.startsWith(prefix));
};

api.interceptors.request.use((config) => {
  const staffSession = getStoredAuthSession();
  const customerSession = getStoredCustomerSession();

  if (isStaffAppRequest()) {
    if (staffSession?.token) {
      config.headers.Authorization = staffSession.token;
    } else if (customerSession?.token) {
      config.headers.Authorization = customerSession.token;
    }
  } else if (customerSession?.token && isCustomerProtectedRequest(config)) {
    config.headers.Authorization = customerSession.token;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = Number(error.response?.status || 0);
    const staffSession = getStoredAuthSession();
    const customerSession = getStoredCustomerSession();

    if (status === 401 && window.location.pathname.startsWith('/app')) {
      clearAuthSession();
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }

    if (!staffSession?.token && customerSession?.token && [401, 403].includes(status)) {
      clearCustomerSession();
    }

    return Promise.reject(error);
  }
);

export default api;
