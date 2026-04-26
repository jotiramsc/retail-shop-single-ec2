const GUEST_CART_KEY = 'retail_shop_guest_cart';

export const getGuestCartItems = () => {
  try {
    return JSON.parse(window.localStorage.getItem(GUEST_CART_KEY) || '[]');
  } catch {
    return [];
  }
};

export const addGuestCartItem = (productId, quantity = 1) => {
  const items = getGuestCartItems();
  const existing = items.find((item) => item.productId === productId);
  if (existing) {
    existing.quantity += quantity;
  } else {
    items.push({ productId, quantity });
  }
  window.localStorage.setItem(GUEST_CART_KEY, JSON.stringify(items));
  return items;
};

export const updateGuestCartItem = (productId, quantity) => {
  const items = getGuestCartItems()
    .map((item) => (item.productId === productId ? { ...item, quantity } : item))
    .filter((item) => item.quantity > 0);
  window.localStorage.setItem(GUEST_CART_KEY, JSON.stringify(items));
  return items;
};

export const removeGuestCartItem = (productId) => {
  const items = getGuestCartItems().filter((item) => item.productId !== productId);
  window.localStorage.setItem(GUEST_CART_KEY, JSON.stringify(items));
  return items;
};

export const getGuestCartCount = () =>
  getGuestCartItems().reduce((total, item) => total + Number(item.quantity || 0), 0);

export const setGuestCartItems = (items) => {
  window.localStorage.setItem(GUEST_CART_KEY, JSON.stringify(items || []));
};

export const clearGuestCart = () => {
  window.localStorage.removeItem(GUEST_CART_KEY);
};
