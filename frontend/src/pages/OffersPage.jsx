import { useEffect, useState } from 'react';
import DataTable from '../components/DataTable';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import { retailService } from '../services/retailService';
import { getApiErrorMessage } from '../utils/validation';

const blankOffer = {
  name: '',
  type: 'PERCENT',
  value: '',
  couponCode: '',
  maxDiscountAmount: '',
  minOrderValue: '',
  category: '',
  productId: '',
  startDate: '',
  endDate: '',
  active: true
};

export default function OffersPage() {
  const [products, setProducts] = useState([]);
  const [offersPage, setOffersPage] = useState({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [categoryOptions, setCategoryOptions] = useState([]);
  const [suggestions, setSuggestions] = useState([]);
  const [form, setForm] = useState(blankOffer);
  const [editingOfferId, setEditingOfferId] = useState(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const appliesByCategory = !form.productId;

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
      setForm((current) => ({ ...current, category: current.category || categoryOptions[0].code }));
    }
  }, [categoryOptions, form.category]);

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
    try {
      const payload = {
        ...form,
        value: Number(form.value),
        couponCode: form.couponCode.trim() || null,
        maxDiscountAmount: form.type === 'PERCENT' && form.maxDiscountAmount !== ''
          ? Number(form.maxDiscountAmount)
          : null,
        minOrderValue: form.minOrderValue !== '' ? Number(form.minOrderValue) : null,
        productId: form.productId || null,
        category: form.productId ? null : form.category
      };
      if (editingOfferId) {
        await retailService.updateOffer(editingOfferId, payload);
      } else {
        await retailService.createOffer(payload);
      }
      setForm(blankOffer);
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
      type: offer.type,
      value: offer.value,
      couponCode: offer.couponCode || '',
      maxDiscountAmount: offer.maxDiscountAmount ?? '',
      minOrderValue: offer.minOrderValue ?? '',
      category: offer.category || categoryOptions[0]?.code || '',
      productId: offer.productId || '',
      startDate: offer.startDate,
      endDate: offer.endDate,
      active: offer.active
    });
  };

  const cancelEdit = () => {
    setEditingOfferId(null);
    setForm(blankOffer);
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
    <div className="page">
      <PageHeader
        eyebrow="Offers"
        title="Promotions and smart suggestions"
        description="Only one best valid offer applies per product. Suggestions are generated from stock depth and low sales velocity."
      />

      <div className="two-column">
        <Panel title={editingOfferId ? 'Edit offer' : 'Create offer'} subtitle="Product-specific offers override by being more relevant, but discounts still never stack. Category means the offer applies to every product in that selected category.">
          <form className="form-grid" onSubmit={submit}>
            <input placeholder="Offer name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
            <select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}>
              <option value="FLAT">Flat</option>
              <option value="PERCENT">Percent</option>
            </select>
            <input type="number" min="0" step="0.01" placeholder="Value" value={form.value} onChange={(e) => setForm({ ...form, value: e.target.value })} required />
            <input
              placeholder="Coupon code (optional)"
              value={form.couponCode}
              onChange={(e) => setForm({ ...form, couponCode: e.target.value.toUpperCase() })}
            />
            <select value={form.productId} onChange={(e) => setForm({ ...form, productId: e.target.value })}>
              <option value="">Apply by category</option>
              {products.map((product) => (
                <option key={product.id} value={product.id}>
                  {product.name}
                </option>
              ))}
            </select>
            {appliesByCategory ? (
              <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
                {categoryOptions.map((category) => (
                  <option key={category.id} value={category.code}>{category.displayName}</option>
                ))}
              </select>
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
            <input type="date" value={form.startDate} onChange={(e) => setForm({ ...form, startDate: e.target.value })} required />
            <input type="date" value={form.endDate} onChange={(e) => setForm({ ...form, endDate: e.target.value })} required />
            {form.type === 'PERCENT' ? (
              <p className="field-note">
                Example: 50% off with max cap 400 means the discount stops at Rs. 400 even if 50% is higher.
              </p>
            ) : null}
            <p className="field-note">
              Category means the coupon or offer works for every product in that selected category. Pick a specific product above if it should apply to only one item.
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

        <Panel title="Offer suggestions" subtitle="Recommended by the automation engine for slow-moving stock.">
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
