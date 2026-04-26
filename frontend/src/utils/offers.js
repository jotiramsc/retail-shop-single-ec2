const roundCurrency = (value) => Math.round((Number(value || 0) + Number.EPSILON) * 100) / 100;

const normalizeText = (value) => String(value || '').trim();

const normalizeDiscountType = (offer) => {
  if (offer?.discountType) {
    return String(offer.discountType).trim().toUpperCase();
  }
  const legacyType = String(offer?.type || '').trim().toUpperCase();
  return legacyType === 'FLAT' ? 'FLAT' : 'PERCENT';
};

export const getOfferDiscountMeta = (offer) => ({
  discountType: normalizeDiscountType(offer),
  discountValue: Number(offer?.discountValue ?? offer?.value ?? 0),
  maxDiscountAmount: offer?.maxDiscountAmount == null ? null : Number(offer.maxDiscountAmount)
});

export const getOfferDisplayLabel = (offer) => {
  const { discountType, discountValue } = getOfferDiscountMeta(offer);
  return {
    discountType,
    discountValue,
    isPercent: discountType === 'PERCENT'
  };
};

const offerAppliesToItem = (offer, item) => {
  const offerProductId = normalizeText(offer?.productId);
  const itemProductId = normalizeText(item?.productId);
  if (offerProductId) {
    return offerProductId === itemProductId;
  }

  const offerCategory = normalizeText(offer?.category).toLowerCase();
  if (offerCategory) {
    return offerCategory === normalizeText(item?.category).toLowerCase();
  }

  return true;
};

const calculateOfferDiscountAmount = (offer, baseAmount) => {
  const safeBaseAmount = Number(baseAmount || 0);
  if (safeBaseAmount <= 0) {
    return 0;
  }

  const { discountType, discountValue, maxDiscountAmount } = getOfferDiscountMeta(offer);
  let amount = discountType === 'PERCENT'
    ? roundCurrency((safeBaseAmount * discountValue) / 100)
    : roundCurrency(discountValue);

  if (discountType === 'PERCENT' && maxDiscountAmount != null) {
    amount = Math.min(amount, Number(maxDiscountAmount));
  }

  return roundCurrency(Math.min(amount, safeBaseAmount));
};

const buildItemCaption = (itemNames) => {
  if (!itemNames.length) {
    return '';
  }
  if (itemNames.length === 1) {
    return itemNames[0];
  }
  return `${itemNames[0]} + ${itemNames.length - 1} more`;
};

const buildCouponCaption = (offer) => {
  if (normalizeText(offer?.productName)) {
    return offer.productName;
  }
  if (normalizeText(offer?.category)) {
    return offer.category;
  }
  return 'Eligible cart items';
};

const getAutomaticOfferBreakdown = (quote) => {
  const items = quote?.cart?.items || [];
  const automaticOffers = (quote?.applicableOffers || []).filter((offer) => !normalizeText(offer?.couponCode));
  const totalsByOffer = new Map();

  items.forEach((item) => {
    const lineTotal = Number(item?.lineTotal || 0);
    if (lineTotal <= 0) {
      return;
    }

    const bestMatch = automaticOffers
      .filter((offer) => offerAppliesToItem(offer, item))
      .map((offer) => ({
        offer,
        amount: calculateOfferDiscountAmount(offer, lineTotal)
      }))
      .filter((match) => match.amount > 0)
      .sort((left, right) => right.amount - left.amount || normalizeText(left.offer?.name).localeCompare(normalizeText(right.offer?.name)))[0];

    if (!bestMatch) {
      return;
    }

    const offerId = normalizeText(bestMatch.offer?.id) || normalizeText(bestMatch.offer?.name);
    const current = totalsByOffer.get(offerId) || {
      id: offerId,
      offerId: normalizeText(bestMatch.offer?.id),
      name: normalizeText(bestMatch.offer?.name) || 'Automatic offer',
      amount: 0,
      itemNames: []
    };

    current.amount = roundCurrency(current.amount + bestMatch.amount);
    if (normalizeText(item?.name)) {
      current.itemNames.push(item.name);
    }
    totalsByOffer.set(offerId, current);
  });

  return Array.from(totalsByOffer.values())
    .map((entry) => ({
      ...entry,
      caption: buildItemCaption(entry.itemNames)
    }))
    .sort((left, right) => right.amount - left.amount || left.name.localeCompare(right.name));
};

export const getAppliedDiscountDetails = (quote) => {
  const totalDiscount = roundCurrency(Number(quote?.discount || 0));
  if (totalDiscount <= 0) {
    return {
      kind: 'none',
      label: '',
      title: '',
      totalDiscount: 0,
      entries: [],
      appliedOfferIds: new Set(),
      appliedCouponCode: ''
    };
  }

  const appliedCouponCode = normalizeText(quote?.appliedCouponCode).toUpperCase();
  if (appliedCouponCode) {
    const appliedCouponOffer = (quote?.applicableOffers || []).find(
      (offer) => normalizeText(offer?.couponCode).toUpperCase() === appliedCouponCode
    );

    return {
      kind: 'coupon',
      label: appliedCouponCode,
      title: normalizeText(appliedCouponOffer?.name) || appliedCouponCode,
      totalDiscount,
      entries: [{
        id: normalizeText(appliedCouponOffer?.id) || appliedCouponCode,
        offerId: normalizeText(appliedCouponOffer?.id),
        name: normalizeText(appliedCouponOffer?.name) || appliedCouponCode,
        amount: totalDiscount,
        caption: buildCouponCaption(appliedCouponOffer),
        couponCode: appliedCouponCode
      }],
      appliedOfferIds: new Set(normalizeText(appliedCouponOffer?.id) ? [normalizeText(appliedCouponOffer.id)] : []),
      appliedCouponCode
    };
  }

  const automaticEntries = getAutomaticOfferBreakdown(quote);
  const automaticDiscount = roundCurrency(Number(quote?.automaticDiscount || 0));
  const calculatedAutomaticDiscount = roundCurrency(
    automaticEntries.reduce((sum, entry) => sum + Number(entry.amount || 0), 0)
  );

  if (!automaticEntries.length || Math.abs(calculatedAutomaticDiscount - automaticDiscount) > 0.05) {
    return {
      kind: 'automatic',
      label: 'Automatic discount',
      title: 'Automatic discount',
      totalDiscount,
      entries: [{
        id: 'automatic-discount',
        offerId: '',
        name: 'Automatic discount',
        amount: totalDiscount,
        caption: 'Eligible offers applied automatically'
      }],
      appliedOfferIds: new Set(),
      appliedCouponCode: ''
    };
  }

  return {
    kind: 'automatic',
    label: automaticEntries.length === 1 ? automaticEntries[0].name : 'Offers applied',
    title: automaticEntries.length === 1 ? automaticEntries[0].name : 'Offers applied',
    totalDiscount,
    entries: automaticEntries,
    appliedOfferIds: new Set(automaticEntries.map((entry) => entry.offerId).filter(Boolean)),
    appliedCouponCode: ''
  };
};
