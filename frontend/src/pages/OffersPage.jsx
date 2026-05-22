import { useEffect, useMemo, useState } from 'react';
import DataTable from '../components/DataTable';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import { retailService } from '../services/retailService';
import { getApiErrorMessage } from '../utils/validation';

const formatDateInput = (date) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const defaultOfferDates = () => {
  const start = new Date();
  const end = new Date(start);
  end.setMonth(end.getMonth() + 1);
  return {
    startDate: formatDateInput(start),
    endDate: formatDateInput(end)
  };
};

const generateCouponCode = (name) => String(name || '')
  .toUpperCase()
  .replace(/[^A-Z0-9]/g, '')
  .slice(0, 6);

const createBlankOffer = () => {
  const dates = defaultOfferDates();
  return {
  name: '',
  targetType: 'STORE',
  scheduleType: 'DATE_RANGE',
  specificDays: [],
  type: 'PERCENT',
  value: '',
  couponCode: '',
  couponManuallyEdited: false,
  maxDiscountAmount: '',
  minOrderValue: '',
  category: '',
  productId: '',
  buyTargetType: 'CATEGORY',
  buyCategory: '',
  buyProductId: '',
  buyQuantity: 1,
  getTargetType: 'CATEGORY',
  getCategory: '',
  getProductId: '',
  getQuantity: 1,
  rewardMode: 'FREE',
  rewardDiscountPercent: 100,
  startDate: dates.startDate,
  endDate: dates.endDate,
  active: true
};
};

const BUY_GET_TYPES = new Set(['BUY_ONE_GET_ONE', 'BUY_X_GET_Y', 'COMBO']);
const WEEKDAYS = [
  ['MON', 'Mon'],
  ['TUE', 'Tue'],
  ['WED', 'Wed'],
  ['THU', 'Thu'],
  ['FRI', 'Fri'],
  ['SAT', 'Sat'],
  ['SUN', 'Sun']
];

export default function OffersPage({ embedded = false }) {
  const [products, setProducts] = useState([]);
  const [offersPage, setOffersPage] = useState({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [categoryOptions, setCategoryOptions] = useState([]);
  const [suggestions, setSuggestions] = useState([]);
  const [form, setForm] = useState(createBlankOffer);
  const [editingOfferId, setEditingOfferId] = useState(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const appliesByCategory = form.targetType === 'CATEGORY';
  const sortedCategories = useMemo(() => [...categoryOptions].sort((a, b) => (a.displayName || '').localeCompare(b.displayName || '')), [categoryOptions]);
  const sortedProducts = useMemo(() => [...products].sort((a, b) => {
    const categoryCompare = (a.category || '').localeCompare(b.category || '');
    if (categoryCompare !== 0) return categoryCompare;
    return (a.name || '').localeCompare(b.name || '');
  }), [products]);
  const selectedProduct = sortedProducts.find((product) => product.id === form.productId);
  const selectedCategory = sortedCategories.find((category) => category.code === form.category);
  const isBuyGetOffer = BUY_GET_TYPES.has(form.type);

  const changeScheduleType = (scheduleType) => {
    setForm((current) => {
      const dates = defaultOfferDates();
      return {
        ...current,
        scheduleType,
        specificDays: scheduleType === 'WEEKEND_ONLY' ? ['SAT', 'SUN'] : scheduleType === 'SPECIFIC_DAYS' ? current.specificDays || [] : [],
        startDate: scheduleType === 'ALWAYS_ACTIVE' ? current.startDate : current.startDate || dates.startDate,
        endDate: scheduleType === 'ALWAYS_ACTIVE' ? current.endDate : current.endDate || dates.endDate
      };
    });
  };

  const toggleSpecificDay = (dayCode) => {
    if (form.scheduleType !== 'SPECIFIC_DAYS') return;
    setForm((current) => {
      const selected = new Set(current.specificDays || []);
      if (selected.has(dayCode)) {
        selected.delete(dayCode);
      } else {
        selected.add(dayCode);
      }
      return { ...current, specificDays: Array.from(selected) };
    });
  };

  const changeOfferName = (name) => {
    setForm((current) => ({
      ...current,
      name,
      couponCode: current.couponManuallyEdited ? current.couponCode : generateCouponCode(name)
    }));
  };

  const load = async (page = 0) => {
    const [productsData, offersData, suggestionsData, categoriesData] = await Promise.all([
      retailService.getProducts({ page: 0, size: 250 }),
      retailService.getOffers({ page, size: 10 }),
      retailService.getOfferSuggestions(),
      retailService.getProductCategoryOptions()
    ]);
    setProducts(productsData.items || []);
    setOffersPage(offersData);
    setSuggestions(suggestionsData);
    setCategoryOptions(categoriesData);
  };

  useEffect(() => {
    load().catch(() => setError('Unable to load offers.'));
  }, []);

  useEffect(() => {
    if (!form.category && categoryOptions.length) {
      setForm((current) => ({
        ...current,
        category: current.category || sortedCategories[0]?.code || categoryOptions[0].code,
        buyCategory: current.buyCategory || sortedCategories[0]?.code || categoryOptions[0].code,
        getCategory: current.getCategory || sortedCategories[0]?.code || categoryOptions[0].code
      }));
    }
  }, [categoryOptions, sortedCategories, form.category]);

  const submit = async (event) => {
    event.preventDefault();
    setError('');
    setSuccess('');
    if ((form.type === 'PERCENT' || form.type === 'CATEGORY') && Number(form.value) > 100) {
      setError('Percent-based offers cannot exceed 100.');
      return;
    }
    if (form.endDate && form.startDate && form.endDate < form.startDate) {
      setError('Offer end date cannot be earlier than start date.');
      return;
    }
    if (form.scheduleType === 'SPECIFIC_DAYS' && !(form.specificDays || []).length) {
      setError('Please select at least one day for this offer.');
      return;
    }
    try {
      const scheduleType = form.scheduleType || 'DATE_RANGE';
      const dates = defaultOfferDates();
      const payload = {
        ...form,
        type: form.type || 'PERCENT',
        active: form.active !== false,
        value: isBuyGetOffer ? 0 : Number(form.value),
        couponCode: form.couponCode.trim().toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6) || null,
        scheduleType,
        specificDays: scheduleType === 'WEEKEND_ONLY' ? ['SAT', 'SUN'] : scheduleType === 'SPECIFIC_DAYS' ? form.specificDays || [] : [],
        startDate: scheduleType === 'ALWAYS_ACTIVE' ? null : form.startDate || dates.startDate,
        endDate: scheduleType === 'ALWAYS_ACTIVE' ? null : form.endDate || dates.endDate,
        maxDiscountAmount: (form.type || 'PERCENT') === 'PERCENT' && form.maxDiscountAmount !== ''
          ? Number(form.maxDiscountAmount)
          : null,
        minOrderValue: form.minOrderValue !== '' ? Number(form.minOrderValue) : null,
        productId: !isBuyGetOffer && form.targetType === 'PRODUCT' ? form.productId || null : null,
        category: !isBuyGetOffer && form.targetType === 'CATEGORY' ? form.category || null : null,
        applicableOn: isBuyGetOffer ? 'BUY_GET' : form.targetType,
        buyProductId: isBuyGetOffer && form.buyTargetType === 'PRODUCT' ? form.buyProductId || null : null,
        buyCategory: isBuyGetOffer && form.buyTargetType === 'CATEGORY' ? form.buyCategory || null : null,
        buyQuantity: isBuyGetOffer ? Number(form.buyQuantity || 1) : null,
        getProductId: isBuyGetOffer && form.getTargetType === 'PRODUCT' ? form.getProductId || null : null,
        getCategory: isBuyGetOffer && form.getTargetType === 'CATEGORY' ? form.getCategory || null : null,
        getQuantity: isBuyGetOffer ? Number(form.getQuantity || 1) : null,
        rewardMode: isBuyGetOffer ? form.rewardMode : null,
        rewardDiscountPercent: isBuyGetOffer ? Number(form.rewardDiscountPercent || 100) : null
      };
      if (editingOfferId) {
        await retailService.updateOffer(editingOfferId, payload);
      } else {
        await retailService.createOffer(payload);
      }
      setForm(createBlankOffer());
      setEditingOfferId(null);
      setSuccess(editingOfferId
        ? 'Offer updated successfully.'
        : 'Offer created and automation broadcast triggered.');
      await load(offersPage.page || 0);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, editingOfferId ? 'Unable to update offer.' : 'Unable to create offer.'));
    }
  };

  const beginEdit = (offer) => {
    setError('');
    setSuccess('');
    setEditingOfferId(offer.id);
    setForm({
      name: offer.name,
      targetType: offer.productId ? 'PRODUCT' : offer.category ? 'CATEGORY' : 'STORE',
      scheduleType: offer.scheduleType || 'DATE_RANGE',
      specificDays: offer.specificDays || [],
      type: offer.type,
      value: offer.value,
      couponCode: offer.couponCode || '',
      couponManuallyEdited: true,
      maxDiscountAmount: offer.maxDiscountAmount ?? '',
      minOrderValue: offer.minOrderValue ?? '',
      category: offer.category || categoryOptions[0]?.code || '',
      productId: offer.productId || '',
      buyTargetType: offer.buyProductId ? 'PRODUCT' : 'CATEGORY',
      buyCategory: offer.buyCategory || categoryOptions[0]?.code || '',
      buyProductId: offer.buyProductId || '',
      buyQuantity: offer.buyQuantity || 1,
      getTargetType: offer.getProductId ? 'PRODUCT' : 'CATEGORY',
      getCategory: offer.getCategory || categoryOptions[0]?.code || '',
      getProductId: offer.getProductId || '',
      getQuantity: offer.getQuantity || 1,
      rewardMode: offer.rewardMode || 'FREE',
      rewardDiscountPercent: offer.rewardDiscountPercent ?? 100,
      startDate: offer.startDate,
      endDate: offer.endDate,
      active: offer.active
    });
  };

  const cancelEdit = () => {
    setEditingOfferId(null);
    setForm(createBlankOffer());
    setError('');
    setSuccess('');
  };

  const removeOffer = async (offer) => {
    setError('');
    setSuccess('');
    try {
      await retailService.deleteOffer(offer.id);
      if (editingOfferId === offer.id) {
        cancelEdit();
      }
      setSuccess(`Offer ${offer.name} deleted.`);
      await load(offersPage.page || 0);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to delete offer.'));
    }
  };

  return (
    <div className={embedded ? 'offers-embedded-stack' : 'page'}>
      {!embedded ? (
        <PageHeader
          eyebrow="Offers"
          title="Promotions and smart suggestions"
          description="Only one best valid offer applies per product. Suggestions are generated from stock depth and low sales velocity."
        />
      ) : null}

      <div className="two-column">
        <Panel title={editingOfferId ? 'Edit offer' : 'Create offer'} subtitle="Choose whether the offer applies store-wide, to one category, or to a specific product. Only the best valid discount is applied.">
          <form className="form-grid" onSubmit={submit}>
            <input placeholder="Offer name" value={form.name} onChange={(e) => changeOfferName(e.target.value)} required />
            {!isBuyGetOffer ? (
              <>
                <div className="offer-choice-block">
                  <span className="input-label">Offer applies to</span>
                  <div className="segmented-control">
                    {[
                      ['STORE', 'Entire Store'],
                      ['CATEGORY', 'Specific Category'],
                      ['PRODUCT', 'Specific Product']
                    ].map(([value, label]) => (
                      <button
                        key={value}
                        type="button"
                        className={form.targetType === value ? 'is-selected' : ''}
                        onClick={() => setForm((current) => ({
                          ...current,
                          targetType: value,
                          productId: value === 'PRODUCT' ? current.productId : '',
                          category: value === 'CATEGORY' ? current.category || categoryOptions[0]?.code || '' : ''
                        }))}
                      >
                        {label}
                      </button>
                    ))}
                  </div>
                </div>
                {form.targetType === 'PRODUCT' ? (
                  <select value={form.productId} onChange={(e) => setForm({ ...form, productId: e.target.value })} required>
                    <option value="">Choose product</option>
                    {sortedProducts.map((product) => (
                      <option key={product.id} value={product.id}>
                        {product.category} · {product.name} · {product.sku} · Rs. {product.websitePrice ?? product.sellingPrice}
                      </option>
                    ))}
                  </select>
                ) : null}
                {appliesByCategory ? (
                  <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
                    {sortedCategories.map((category) => (
                      <option key={category.id} value={category.code}>{category.displayName}</option>
                    ))}
                  </select>
                ) : null}
                {(selectedProduct || selectedCategory || form.targetType === 'STORE') ? (
                  <div className="offer-target-preview">
                    {selectedProduct?.imageDataUrl ? <img src={selectedProduct.imageDataUrl} alt={selectedProduct.name} /> : null}
                    <div>
                      <span>{form.targetType === 'STORE' ? 'Entire Store' : form.targetType === 'CATEGORY' ? 'Category offer' : 'Product offer'}</span>
                      <strong>{selectedProduct?.name || selectedCategory?.displayName || 'All active products'}</strong>
                      <small>{selectedProduct ? `${selectedProduct.sku} · Rs. ${selectedProduct.websitePrice ?? selectedProduct.sellingPrice}` : 'Best valid offer will be applied at checkout.'}</small>
                    </div>
                  </div>
                ) : null}
              </>
            ) : null}
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
              <option value="PERCENT">Percentage Discount</option>
              <option value="FLAT">Flat Discount</option>
              <option value="BUY_ONE_GET_ONE">Buy One Get One</option>
              <option value="BUY_X_GET_Y">Buy X Get Y</option>
              <option value="COMBO">Combo Offer</option>
            </select>
            {!isBuyGetOffer ? (
              <input type="number" min="0" step="0.01" placeholder="Value" value={form.value} onChange={(e) => setForm({ ...form, value: e.target.value })} required />
            ) : null}
            <input
              maxLength={6}
              placeholder="Coupon code (optional)"
              value={form.couponCode}
              onChange={(e) => setForm({
                ...form,
                couponCode: e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 6),
                couponManuallyEdited: true
              })}
            />
            {isBuyGetOffer ? (
              <div className="bogo-builder">
                <div className="bogo-row">
                  <strong>Buy</strong>
                  <select value={form.buyTargetType} onChange={(e) => setForm({ ...form, buyTargetType: e.target.value })}>
                    <option value="CATEGORY">Category</option>
                    <option value="PRODUCT">Product</option>
                  </select>
                  {form.buyTargetType === 'CATEGORY' ? (
                    <select value={form.buyCategory} onChange={(e) => setForm({ ...form, buyCategory: e.target.value })} required>
                      {sortedCategories.map((category) => (
                        <option key={category.id} value={category.code}>{category.displayName}</option>
                      ))}
                    </select>
                  ) : (
                    <select value={form.buyProductId} onChange={(e) => setForm({ ...form, buyProductId: e.target.value })} required>
                      <option value="">Choose buy product</option>
                      {sortedProducts.map((product) => (
                        <option key={product.id} value={product.id}>{product.category} · {product.name} · Rs. {product.websitePrice ?? product.sellingPrice}</option>
                      ))}
                    </select>
                  )}
                  <input type="number" min="1" step="1" value={form.buyQuantity} onChange={(e) => setForm({ ...form, buyQuantity: e.target.value })} aria-label="Buy quantity" />
                </div>
                <div className="bogo-row">
                  <strong>Get</strong>
                  <select value={form.getTargetType} onChange={(e) => setForm({ ...form, getTargetType: e.target.value })}>
                    <option value="CATEGORY">Category</option>
                    <option value="PRODUCT">Product</option>
                  </select>
                  {form.getTargetType === 'CATEGORY' ? (
                    <select value={form.getCategory} onChange={(e) => setForm({ ...form, getCategory: e.target.value })} required>
                      {sortedCategories.map((category) => (
                        <option key={category.id} value={category.code}>{category.displayName}</option>
                      ))}
                    </select>
                  ) : (
                    <select value={form.getProductId} onChange={(e) => setForm({ ...form, getProductId: e.target.value })} required>
                      <option value="">Choose reward product</option>
                      {sortedProducts.map((product) => (
                        <option key={product.id} value={product.id}>{product.category} · {product.name} · Rs. {product.websitePrice ?? product.sellingPrice}</option>
                      ))}
                    </select>
                  )}
                  <input type="number" min="1" step="1" value={form.getQuantity} onChange={(e) => setForm({ ...form, getQuantity: e.target.value })} aria-label="Reward quantity" />
                </div>
                <div className="bogo-row">
                  <strong>Reward</strong>
                  <select value={form.rewardMode} onChange={(e) => setForm({ ...form, rewardMode: e.target.value })}>
                    <option value="FREE">Free item</option>
                    <option value="PERCENT_OFF">Percentage off reward</option>
                  </select>
                  <input
                    type="number"
                    min="0"
                    max="100"
                    step="0.01"
                    value={form.rewardDiscountPercent}
                    onChange={(e) => setForm({ ...form, rewardDiscountPercent: e.target.value })}
                    placeholder="Reward discount %"
                  />
                </div>
              </div>
            ) : null}
            {form.type === 'PERCENT' ? (
              <input
                type="number"
                min="0"
                step="0.01"
                placeholder="Max discount amount cap"
                value={form.maxDiscountAmount}
                onChange={(e) => setForm({ ...form, maxDiscountAmount: e.target.value })}
              />
            ) : null}
            <input
              type="number"
              min="0"
              step="0.01"
              placeholder="Minimum order value (optional)"
              value={form.minOrderValue}
              onChange={(e) => setForm({ ...form, minOrderValue: e.target.value })}
            />
            <div className="offer-choice-block">
              <span className="input-label">Offer schedule</span>
              <div className="segmented-control">
                {[
                  ['ALWAYS_ACTIVE', 'Always Active'],
                  ['DATE_RANGE', 'Date Range'],
                  ['WEEKEND_ONLY', 'Weekend Only'],
                  ['SPECIFIC_DAYS', 'Specific Days']
                ].map(([value, label]) => (
                  <button
                    key={value}
                    type="button"
                    className={form.scheduleType === value ? 'is-selected' : ''}
                    onClick={() => changeScheduleType(value)}
                  >
                    {label}
                  </button>
                ))}
              </div>
            </div>
            {form.scheduleType !== 'ALWAYS_ACTIVE' ? (
              <>
                <input type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} required />
                <input type="date" value={form.endDate} onChange={(e) => setForm({ ...form, endDate: e.target.value })} required />
              </>
            ) : null}
            {form.scheduleType === 'WEEKEND_ONLY' || form.scheduleType === 'SPECIFIC_DAYS' ? (
              <div className="offer-days-row">
                <small>Select the days when this offer should be active.</small>
                {WEEKDAYS.map(([day, label]) => (
                  <button
                    key={day}
                    type="button"
                    disabled={form.scheduleType !== 'SPECIFIC_DAYS'}
                    className={(form.scheduleType === 'WEEKEND_ONLY' && ['SAT', 'SUN'].includes(day)) || (form.specificDays || []).includes(day) ? 'is-selected' : ''}
                    onClick={() => toggleSpecificDay(day)}
                  >
                    {label}
                  </button>
                ))}
              </div>
            ) : null}
            <label className="toggle-field">
              <input
                type="checkbox"
                checked={form.active !== false}
                onChange={(e) => setForm({ ...form, active: e.target.checked })}
              />
              <span>Active offer</span>
            </label>
            {form.type === 'PERCENT' ? (
              <p className="field-note">
                Example: 50% off with max cap 400 means the discount stops at Rs. 400 even if 50% is higher.
              </p>
            ) : null}
            <p className="field-note">
              Category and product selectors are sorted alphabetically. Buy/Get offers are stored with buy and reward targets; same-product rewards apply during line pricing.
            </p>
            {error ? <p className="error-text">{error}</p> : null}
            {success ? <p className="success-text">{success}</p> : null}
            <div className="page-actions">
              <button className="primary-btn" type="submit">{editingOfferId ? 'Update Offer' : 'Create Offer'}</button>
              {editingOfferId ? (
                <button className="ghost-btn" type="button" onClick={cancelEdit}>Cancel</button>
              ) : null}
            </div>
          </form>
        </Panel>

        {!embedded ? (
          <Panel title="Offer suggestions" subtitle="Recommended from stock depth, demand signals, and sales velocity.">
            <DataTable
              columns={[
                { key: 'productName', label: 'Product' },
                { key: 'category', label: 'Category' },
                { key: 'currentQuantity', label: 'Stock' },
                { key: 'suggestedDiscountPercent', label: 'Suggested %' },
                { key: 'reason', label: 'Reason' }
              ]}
              rows={suggestions}
            />
          </Panel>
        ) : null}
      </div>

      <Panel title="Active offers" subtitle="Live and date-valid promotions currently used by billing.">
        <DataTable
          columns={[
            { key: 'name', label: 'Offer' },
            { key: 'type', label: 'Type' },
            { key: 'couponCode', label: 'Coupon' },
            { key: 'value', label: 'Value' },
            { key: 'maxDiscountAmount', label: 'Max Cap' },
            { key: 'minOrderValue', label: 'Min Order' },
            { key: 'category', label: 'Category' },
            { key: 'productName', label: 'Product' },
            { key: 'endDate', label: 'Ends' },
            {
              key: 'actions',
              label: 'Actions',
              render: (row) => (
                <div className="table-actions">
                  <button className="ghost-btn compact-btn" type="button" onClick={() => beginEdit(row)}>Edit</button>
                  <button className="ghost-btn compact-btn danger-btn" type="button" onClick={() => removeOffer(row)}>Delete</button>
                </div>
              )
            }
          ]}
          rows={offersPage.items || []}
          pagination={offersPage}
          onPageChange={load}
        />
      </Panel>
    </div>
  );
}
