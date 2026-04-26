export const isValidMobile = (value) => /^[0-9]{10,15}$/.test(String(value || '').trim());

export const getApiErrorMessage = (error, fallbackMessage) =>
  error?.response?.data?.message ||
  error?.response?.data?.details?.join(', ') ||
  fallbackMessage;
