const CHECKOUT_COUPON_KEY = 'retail_shop_checkout_coupon';

export const getStoredCheckoutCouponCode = () => {
  try {
    const rawValue = window.localStorage.getItem(CHECKOUT_COUPON_KEY);
    return rawValue ? rawValue.trim().toUpperCase() : '';
  } catch {
    return '';
  }
};

export const storeCheckoutCouponCode = (couponCode) => {
  if (!couponCode || !couponCode.trim()) {
    clearStoredCheckoutCouponCode();
    return;
  }
  window.localStorage.setItem(CHECKOUT_COUPON_KEY, couponCode.trim().toUpperCase());
};

export const clearStoredCheckoutCouponCode = () => {
  window.localStorage.removeItem(CHECKOUT_COUPON_KEY);
};
