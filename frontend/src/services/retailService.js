import api from './api';

const buildPageParams = ({ page = 0, size = 10, ...rest } = {}) => {
  const params = new URLSearchParams();
  params.set('page', page);
  params.set('size', size);
  Object.entries(rest).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      params.set(key, value);
    }
  });
  return params.toString();
};

export const retailService = {
  login: (payload) => api.post('/auth/login', payload).then((res) => res.data),
  sendOtp: (payload) => api.post('/auth/send-otp', payload).then((res) => res.data),
  verifyOtp: (payload) => api.post('/auth/verify-otp', payload).then((res) => res.data),
  uploadImage: ({ file, category }) => {
    const formData = new FormData();
    formData.append('image', file);
    formData.append('category', category || 'general');
    return api.post('/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    }).then((res) => res.data);
  },
  getPublicCatalog: () => api.get('/products/catalog').then((res) => res.data),
  getPublicHomepageCatalog: () => api.get('/products/catalog/home').then((res) => res.data),
  getPublicTrendingCatalog: () => api.get('/products/catalog/trending').then((res) => res.data),
  getCart: () => api.get('/cart').then((res) => res.data),
  addToCart: (payload) => api.post('/cart/add', payload).then((res) => res.data),
  mergeCart: (payload) => api.post('/cart/merge', payload).then((res) => res.data),
  updateCart: (payload) => api.put('/cart/update', payload).then((res) => res.data),
  removeFromCart: (productId) => api.delete(`/cart/remove?productId=${productId}`).then((res) => res.data),
  getApplicableOffers: (couponCode) => api.get(`/offers/applicable${couponCode ? `?couponCode=${encodeURIComponent(couponCode)}` : ''}`).then((res) => res.data),
  applyCoupon: (payload) => api.post('/checkout/apply-coupon', payload).then((res) => res.data),
  createPaymentOrder: (payload) => api.post('/checkout/payment-order', payload || {}).then((res) => res.data),
  getPaymentStatus: (merchantOrderId) => api.get(`/checkout/payment-status?merchantOrderId=${encodeURIComponent(merchantOrderId)}`).then((res) => res.data),
  addAddress: (payload) => api.post('/address', payload).then((res) => res.data),
  getAddresses: () => api.get('/address').then((res) => res.data),
  deleteAddress: (id) => api.delete(`/address/${id}`).then((res) => res.data),
  getCustomerProfile: () => api.get('/customer-profile').then((res) => res.data),
  updateCustomerProfile: (payload) => api.put('/customer-profile', payload).then((res) => res.data),
  placeOrder: (payload) => api.post('/order/place', payload).then((res) => res.data),
  getOrders: () => api.get('/orders').then((res) => res.data),
  getProducts: (params) => api.get(`/products?${buildPageParams(params)}`).then((res) => res.data),
  getTrendingProducts: () => api.get('/products/trending').then((res) => res.data),
  createProduct: (payload) => api.post('/products', payload).then((res) => res.data),
  updateProduct: (id, payload) => api.put(`/products/${id}`, payload).then((res) => res.data),
  deleteProduct: (id) => api.delete(`/products/${id}`).then((res) => res.data),
  getProductCategories: (params) => api.get(`/product-categories?${buildPageParams(params)}`).then((res) => res.data),
  getProductCategoryOptions: () => api.get('/product-categories/options').then((res) => res.data),
  createProductCategory: (payload) => api.post('/product-categories', payload).then((res) => res.data),
  updateProductCategory: (id, payload) => api.put(`/product-categories/${id}`, payload).then((res) => res.data),
  getCustomers: (params) => api.get(`/customers?${buildPageParams(params)}`).then((res) => res.data),
  searchCustomers: (query) => api.get(`/customers/search?q=${encodeURIComponent(query)}`).then((res) => res.data),
  createCustomer: (payload) => api.post('/customers', payload).then((res) => res.data),
  lookupCustomer: (mobile) => api.get(`/customers/lookup?mobile=${mobile}`).then((res) => res.data),
  getCustomerHistory: (mobile) => api.get(`/customers/history?mobile=${mobile}`).then((res) => res.data),
  previewInvoice: (payload) => api.post('/billing/preview', payload).then((res) => res.data),
  createInvoice: (payload) => api.post('/billing/create', payload).then((res) => res.data),
  updateInvoice: (id, payload) => api.put(`/billing/${id}`, payload).then((res) => res.data),
  searchInvoices: ({ fromDate, toDate, customerName, page = 0, size = 10 }) => {
    const params = new URLSearchParams();
    params.set('page', page);
    params.set('size', size);
    if (fromDate) params.set('fromDate', fromDate);
    if (toDate) params.set('toDate', toDate);
    if (customerName) params.set('customerName', customerName);
    return api.get(`/billing/search?${params.toString()}`).then((res) => res.data);
  },
  getOffers: (params) => api.get(`/offers?${buildPageParams(params)}`).then((res) => res.data),
  createOffer: (payload) => api.post('/offers', payload).then((res) => res.data),
  updateOffer: (id, payload) => api.put(`/offers/${id}`, payload).then((res) => res.data),
  deleteOffer: (id) => api.delete(`/offers/${id}`).then((res) => res.data),
  getOfferSuggestions: () => api.get('/offers/suggested').then((res) => res.data),
  createCampaign: (payload) => api.post('/campaign', payload).then((res) => res.data),
  sendCampaign: (payload) => api.post('/campaign/send', payload).then((res) => res.data),
  publishCampaign: (campaignId) => api.post(`/campaign/${campaignId}/publish`).then((res) => res.data),
  retryCampaignLog: (campaignLogId) => api.post(`/campaign/history/${campaignLogId}/retry`).then((res) => res.data),
  getCampaignHistory: (params) => api.get(`/campaign/history?${buildPageParams(params)}`).then((res) => res.data),
  getDailyReport: ({ fromDate, toDate }) => {
    const params = new URLSearchParams();
    if (fromDate) params.set('fromDate', fromDate);
    if (toDate) params.set('toDate', toDate);
    return api.get(`/reports/daily?${params.toString()}`).then((res) => res.data);
  },
  getReportInvoices: ({ fromDate, toDate, customerName, page = 0, size = 10 }) => {
    const params = new URLSearchParams();
    params.set('page', page);
    params.set('size', size);
    if (fromDate) params.set('fromDate', fromDate);
    if (toDate) params.set('toDate', toDate);
    if (customerName) params.set('customerName', customerName);
    return api.get(`/reports/invoices?${params.toString()}`).then((res) => res.data);
  },
  getLowStock: (params) => api.get(`/reports/low-stock?${buildPageParams(params)}`).then((res) => res.data),
  getReceiptSettings: () => api.get('/settings/receipt').then((res) => res.data),
  updateReceiptSettings: (payload) => api.put('/settings/receipt', payload).then((res) => res.data),
  getUsers: (params) => api.get(`/users?${buildPageParams(params)}`).then((res) => res.data),
  createUser: (payload) => api.post('/users', payload).then((res) => res.data),
  updateUser: (id, payload) => api.put(`/users/${id}`, payload).then((res) => res.data)
};
