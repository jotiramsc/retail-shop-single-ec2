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
  verifyProfileMobileOtp: (payload) => api.post('/customer-profile/mobile/verify-otp', payload).then((res) => res.data),
  loginWithGoogle: (payload) => api.post('/auth/google', payload).then((res) => res.data),
  verifyGoogleMobileOtp: (payload) => api.post('/auth/google/verify-mobile', payload).then((res) => res.data),
  recordSiteVisit: (payload) => api.post('/site-interactions/visit', payload || {}).then((res) => res.data),
  getSiteInteractionReport: (days = 30) => api.get(`/site-interactions/report?days=${days}`).then((res) => res.data),
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
  getWishlist: () => api.get('/wishlist').then((res) => res.data),
  addToWishlist: (payload) => api.post('/wishlist', payload).then((res) => res.data),
  removeFromWishlist: (productId) => api.delete(`/wishlist?productId=${productId}`).then((res) => res.data),
  moveWishlistToCart: (payload) => api.post('/wishlist/move-to-cart', payload).then((res) => res.data),
  getApplicableOffers: (couponCode) => api.get(`/offers/applicable${couponCode ? `?couponCode=${encodeURIComponent(couponCode)}` : ''}`).then((res) => res.data),
  applyCoupon: (payload) => api.post('/checkout/apply-coupon', payload).then((res) => res.data),
  createPaymentOrder: (payload) => api.post('/checkout/payment-order', payload || {}).then((res) => res.data),
  getPaymentStatus: (merchantOrderId) => api.get(`/checkout/payment-status?merchantOrderId=${encodeURIComponent(merchantOrderId)}`).then((res) => res.data),
  getPaymentTransactions: ({ fromDate, toDate, provider, operation, status, search, page = 0, size = 12 } = {}) => {
    const params = new URLSearchParams();
    params.set('page', page);
    params.set('size', size);
    if (fromDate) params.set('fromDate', fromDate);
    if (toDate) params.set('toDate', toDate);
    if (provider) params.set('provider', provider);
    if (operation) params.set('operation', operation);
    if (status) params.set('status', status);
    if (search) params.set('search', search);
    return api.get(`/payments/transactions?${params.toString()}`).then((res) => res.data);
  },
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
  getMarketingCampaigns: ({ status, platform, type, fromDate, toDate, page = 0, size = 10 } = {}) => {
    const params = new URLSearchParams();
    params.set('page', page);
    params.set('size', size);
    if (status) params.set('status', status);
    if (platform) params.set('platform', platform);
    if (type) params.set('type', type);
    if (fromDate) params.set('fromDate', fromDate);
    if (toDate) params.set('toDate', toDate);
    return api.get(`/marketing/campaigns?${params.toString()}`).then((res) => res.data);
  },
  getMarketingSuggestions: ({ daysAhead = 20 } = {}) => api.get(`/marketing/suggestions?daysAhead=${encodeURIComponent(daysAhead)}`).then((res) => res.data),
  getMarketingCampaign: (campaignId) => api.get(`/marketing/campaigns/${campaignId}`).then((res) => res.data),
  createMarketingCampaign: (payload) => api.post('/marketing/campaigns', payload).then((res) => res.data),
  generateMarketingCampaign: (campaignId) => api.post(`/marketing/campaigns/${campaignId}/generate`).then((res) => res.data),
  generateMarketingCampaignAsync: (campaignId) => api.post(`/marketing/campaigns/${campaignId}/generate-async`).then((res) => res.data),
  deleteMarketingCampaign: (campaignId) => api.delete(`/marketing/campaigns/${campaignId}`).then((res) => res.data),
  updateMarketingContent: (contentId, payload) => api.put(`/marketing/content/${contentId}`, payload).then((res) => res.data),
  approveMarketingContent: (contentId, payload = {}) => api.post(`/marketing/content/${contentId}/approve`, payload).then((res) => res.data),
  rejectMarketingContent: (contentId, payload) => api.post(`/marketing/content/${contentId}/reject`, payload).then((res) => res.data),
  scheduleMarketingContent: (contentId, payload) => api.post(`/marketing/content/${contentId}/schedule`, payload).then((res) => res.data),
  publishMarketingContentNow: (contentId) => api.post(`/marketing/content/${contentId}/publish-now`).then((res) => res.data),
  getMarketingApprovalQueue: () => api.get('/marketing/approval-queue').then((res) => res.data),
  getMarketingAnalytics: ({ campaignId, platform, fromDate, toDate } = {}) => {
    const params = new URLSearchParams();
    if (campaignId) params.set('campaignId', campaignId);
    if (platform) params.set('platform', platform);
    if (fromDate) params.set('fromDate', fromDate);
    if (toDate) params.set('toDate', toDate);
    return api.get(`/marketing/analytics?${params.toString()}`).then((res) => res.data);
  },
  getDailyReport: ({ fromDate, toDate, salesPersonName }) => {
    const params = new URLSearchParams();
    if (fromDate) params.set('fromDate', fromDate);
    if (toDate) params.set('toDate', toDate);
    if (salesPersonName) params.set('salesPersonName', salesPersonName);
    return api.get(`/reports/daily?${params.toString()}`).then((res) => res.data);
  },
  getReportInvoices: ({ fromDate, toDate, customerName, salesPersonName, page = 0, size = 10 }) => {
    const params = new URLSearchParams();
    params.set('page', page);
    params.set('size', size);
    if (fromDate) params.set('fromDate', fromDate);
    if (toDate) params.set('toDate', toDate);
    if (customerName) params.set('customerName', customerName);
    if (salesPersonName) params.set('salesPersonName', salesPersonName);
    return api.get(`/reports/invoices?${params.toString()}`).then((res) => res.data);
  },
  getSalespersonSales: ({ salespersonId, fromDate, toDate, viewType = 'DAILY' } = {}) => {
    const params = new URLSearchParams();
    params.set('viewType', viewType);
    if (salespersonId) params.set('salespersonId', salespersonId);
    if (fromDate) params.set('fromDate', fromDate);
    if (toDate) params.set('toDate', toDate);
    return api.get(`/salesperson-sales?${params.toString()}`).then((res) => res.data);
  },
  getSalesReport: ({ period = 'MONTHLY', month, year, scope = 'ALL', category, productId, salesPersonName }) => {
    const params = new URLSearchParams();
    params.set('period', period);
    params.set('scope', scope);
    if (month) params.set('month', month);
    if (year) params.set('year', year);
    if (category) params.set('category', category);
    if (productId) params.set('productId', productId);
    if (salesPersonName) params.set('salesPersonName', salesPersonName);
    return api.get(`/reports/sales?${params.toString()}`).then((res) => res.data);
  },
  getLowStock: (params) => api.get(`/reports/low-stock?${buildPageParams(params)}`).then((res) => res.data),
  getReceiptSettings: () => api.get('/settings/receipt').then((res) => res.data),
  updateReceiptSettings: (payload) => api.put('/settings/receipt', payload).then((res) => res.data),
  getUsers: (params) => api.get(`/users?${buildPageParams(params)}`).then((res) => res.data),
  getSalesPeople: () => api.get('/users/salespeople').then((res) => res.data),
  createUser: (payload) => api.post('/users', payload).then((res) => res.data),
  updateUser: (id, payload) => api.put(`/users/${id}`, payload).then((res) => res.data)
};
