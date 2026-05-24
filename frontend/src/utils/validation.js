import { isValidIndianMobile } from './mobile';

export const isValidMobile = (value) => isValidIndianMobile(value);

const fieldLabels = {
  recipientName: 'Recipient name',
  mobile: 'Mobile number',
  line1: 'Address line 1',
  city: 'City',
  state: 'State',
  pincode: 'Pincode',
  name: 'Name',
  email: 'Email address'
};

const cleanValidationDetail = (detail) => {
  const [field, ...rest] = String(detail || '').split(':');
  if (!rest.length) {
    return detail;
  }
  const message = rest.join(':').trim().replace('must not be blank', 'is required');
  return `${fieldLabels[field.trim()] || field.trim()}: ${message}`;
};

export const getApiErrorMessage = (error, fallbackMessage) => {
  const message = error?.response?.data?.message;
  const details = Array.isArray(error?.response?.data?.details)
    ? error.response.data.details.map(cleanValidationDetail).filter(Boolean)
    : [];

  if (details.length && (!message || /validation failed|constraint violation/i.test(message))) {
    return details.join(', ');
  }

  return message || details.join(', ') || fallbackMessage;
};
