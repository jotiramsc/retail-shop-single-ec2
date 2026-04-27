import { useEffect, useMemo, useState } from 'react';
import DataTable from '../components/DataTable';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import { retailService } from '../services/retailService';
import { currency } from '../utils/format';
import { getApiErrorMessage } from '../utils/validation';

const createBlankProduct = (defaultCategory = '') => ({
  name: '',
  category: defaultCategory,
  sku: '',
  costPrice: '',
  sellingPrice: '',
  websitePricePercentage: '',
  websitePrice: '',
  quantity: '',
  lowStockThreshold: '5',
  imageDataUrl: '',
  showInEditorsPicks: false,
  showInNewRelease: false,
  showInCustomerAccess: false,
  showInShopCollection: false,
  showInFeaturedPieces: false,
  showInStory: false,
  showInCuratedSelections: false,
  expiryDate: ''
});

const blankCategoryForm = {
  displayName: '',
  active: true
};

export default function ProductsPage() {
  const [productsPage, setProductsPage] = useState({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [categoriesPage, setCategoriesPage] = useState({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [categoryOptions, setCategoryOptions] = useState([]);
  const [form, setForm] = useState(createBlankProduct());
  const [categoryForm, setCategoryForm] = useState(blankCategoryForm);
  const [editingId, setEditingId] = useState(null);
  const [editingCategoryId, setEditingCategoryId] = useState(null);
  const [formMode, setFormMode] = useState('create');
  const [inventorySearch, setInventorySearch] = useState('');
  const [searchFocused, setSearchFocused] = useState(false);
  const [selectedProductSnapshot, setSelectedProductSnapshot] = useState(null);
  const [uploadingImage, setUploadingImage] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [categoryError, setCategoryError] = useState('');
  const [categorySuccess, setCategorySuccess] = useState('');

  const defaultCategoryCode = categoryOptions[0]?.code || '';

  const loadProducts = async (page = 0) => {
    try {
      setProductsPage(await retailService.getProducts({ page, size: 10 }));
    } catch {
      setError('Unable to load inventory.');
    }
  };

  const loadCategories = async (page = 0) => {
    try {
      const [categories, options] = await Promise.all([
        retailService.getProductCategories({ page, size: 10 }),
        retailService.getProductCategoryOptions()
      ]);
      setCategoriesPage(categories);
      setCategoryOptions(options);
    } catch (requestError) {
      setCategoryError(getApiErrorMessage(requestError, 'Unable to load product categories.'));
    }
  };

  useEffect(() => {
    loadProducts();
    loadCategories();
  }, []);

  useEffect(() => {
    if (!form.category && defaultCategoryCode) {
      setForm((current) => ({ ...current, category: defaultCategoryCode }));
    }
  }, [defaultCategoryCode, form.category]);

  const products = productsPage.items || [];
  const computedWebsitePrice = useMemo(() => {
    const shopPrice = Number(form.sellingPrice || 0);
    const websitePricePercentage = Number(form.websitePricePercentage || 0);
    if (!shopPrice) {
      return '';
    }
    if (!websitePricePercentage) {
      return shopPrice.toFixed(2);
    }
    return (shopPrice + (shopPrice * websitePricePercentage / 100)).toFixed(2);
  }, [form.sellingPrice, form.websitePricePercentage]);

  const resetForm = () => {
    setForm(createBlankProduct(defaultCategoryCode));
    setEditingId(null);
    setFormMode('create');
    setInventorySearch('');
    setSelectedProductSnapshot(null);
  };

  const resetCategoryForm = () => {
    setCategoryForm(blankCategoryForm);
    setEditingCategoryId(null);
  };

  const applyProductToForm = (product, mode = 'edit') => {
    setEditingId(product.id);
    setFormMode(mode);
    setSelectedProductSnapshot(product);
    setInventorySearch(product.name);
    setForm({
      name: product.name,
      category: product.category,
      sku: product.sku,
      costPrice: product.costPrice,
      sellingPrice: product.sellingPrice,
      websitePricePercentage: product.websitePricePercentage ?? '',
      websitePrice: product.websitePrice ?? '',
      quantity: mode === 'restock' ? '' : product.quantity,
      lowStockThreshold: product.lowStockThreshold,
      imageDataUrl: product.imageDataUrl || '',
      showInEditorsPicks: Boolean(product.showInEditorsPicks),
      showInNewRelease: Boolean(product.showInNewRelease),
      showInCustomerAccess: Boolean(product.showInCustomerAccess),
      showInShopCollection: Boolean(product.showInShopCollection),
      showInFeaturedPieces: Boolean(product.showInFeaturedPieces),
      showInStory: Boolean(product.showInStory),
      showInCuratedSelections: Boolean(product.showInCuratedSelections),
      expiryDate: product.expiryDate || ''
    });
    setSearchFocused(false);
  };

  const handleImageChange = async (event) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }

    if (!file.type.startsWith('image/')) {
      setError('Please choose a valid image file.');
      return;
    }

    setError('');
    setUploadingImage(true);
    try {
      const upload = await retailService.uploadImage({
        file,
        category: form.category || 'products'
      });
      setForm((current) => ({
        ...current,
        imageDataUrl: upload.cloudfrontUrl
      }));
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to upload image.'));
    } finally {
      setUploadingImage(false);
      event.target.value = '';
    }
  };

  const submit = async (event) => {
    event.preventDefault();
    setError('');
    setSuccess('');
    if (Number(form.sellingPrice) < Number(form.costPrice)) {
      setError('Shop price cannot be lower than cost price.');
      return;
    }
    const payload = {
      ...form,
      websitePricePercentage: Number(form.websitePricePercentage || 0) > 0 ? Number(form.websitePricePercentage) : null,
      quantity: Number(form.quantity),
      lowStockThreshold: Number(form.lowStockThreshold)
    };
    try {
      if (editingId) {
        const nextQuantity = formMode === 'restock'
          ? Number(selectedProductSnapshot?.quantity || 0) + Number(form.quantity)
          : payload.quantity;
        await retailService.updateProduct(editingId, {
          ...payload,
          quantity: nextQuantity,
        });
      } else {
        await retailService.createProduct(payload);
      }
      setSuccess(
        formMode === 'restock'
          ? `Inventory added successfully. New stock: ${Number(selectedProductSnapshot?.quantity || 0) + Number(form.quantity)}`
          : editingId
            ? 'Product updated successfully.'
            : 'Product added successfully.'
      );
      resetForm();
      loadProducts(productsPage.page || 0);
      loadCategories(categoriesPage.page || 0);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to save product.'));
    }
  };

  const submitCategory = async (event) => {
    event.preventDefault();
    setCategoryError('');
    setCategorySuccess('');
    try {
      if (editingCategoryId) {
        await retailService.updateProductCategory(editingCategoryId, categoryForm);
        setCategorySuccess('Category updated successfully.');
      } else {
        await retailService.createProductCategory(categoryForm);
        setCategorySuccess('Category created successfully.');
      }
      resetCategoryForm();
      await loadCategories(categoriesPage.page || 0);
    } catch (requestError) {
      setCategoryError(getApiErrorMessage(requestError, 'Unable to save category.'));
    }
  };

  const startEditCategory = (category) => {
    setEditingCategoryId(category.id);
    setCategoryForm({
      displayName: category.displayName,
      active: Boolean(category.active)
    });
  };

  const handleDeleteProduct = async (product) => {
    const confirmed = window.confirm(`Delete "${product.name}" from inventory? This cannot be undone.`);
    if (!confirmed) {
      return;
    }

    setError('');
    setSuccess('');

    try {
      await retailService.deleteProduct(product.id);
      if (editingId === product.id) {
        resetForm();
      }
      setSuccess('Product deleted successfully.');
      const nextPage = products.length === 1 && productsPage.page > 0 ? productsPage.page - 1 : productsPage.page;
      loadProducts(nextPage);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to delete product.'));
    }
  };

  const searchResults = inventorySearch
    ? products
      .filter((product) =>
        `${product.name} ${product.sku} ${product.category}`.toLowerCase().includes(inventorySearch.toLowerCase())
      )
      .slice(0, 8)
    : [];

  const handleInventorySearchChange = (value) => {
    setInventorySearch(value);
    const normalized = value.trim().toLowerCase();
    if (!normalized) {
      resetForm();
      return;
    }

    const exactMatch = products.find((product) =>
      product.name.toLowerCase() === normalized || product.sku.toLowerCase() === normalized
    );

    if (exactMatch) {
      applyProductToForm(exactMatch, 'edit');
      return;
    }

    setEditingId(null);
    setFormMode('create');
    setSelectedProductSnapshot(null);
    setForm((current) => ({
      ...createBlankProduct(defaultCategoryCode),
      ...current,
      name: value,
      category: current.category || defaultCategoryCode
    }));
  };

  const categoryTableRows = useMemo(() => categoriesPage.items || [], [categoriesPage.items]);

  return (
    <div className="page">
      <PageHeader
        eyebrow="Inventory"
        title="Product and stock management"
        description="Track cosmetics and jewellery stock, keep expiry dates visible, and update prices without mutating historical invoices."
      />

      <div className="inventory-stack">
        <Panel
          title={formMode === 'restock' ? 'Add inventory' : editingId ? 'Edit product' : 'Add product'}
          subtitle="Search an existing product to restock it quickly, or enter a brand-new product from scratch."
        >
          <form className="form-grid" onSubmit={submit}>
            <div className="search-box-wrap">
              <input
                placeholder="Search existing product to restock or edit"
                value={inventorySearch}
                onChange={(e) => handleInventorySearchChange(e.target.value)}
                onFocus={() => setSearchFocused(true)}
                onBlur={() => window.setTimeout(() => setSearchFocused(false), 120)}
                autoComplete="off"
              />
              <p className="field-note">Pick an existing item to auto-fill details and add more inventory, or leave this blank to create a new product.</p>
              {searchFocused && searchResults.length ? (
                <div className="autocomplete-menu">
                  {searchResults.map((product) => (
                    <button
                      key={product.id}
                      type="button"
                      className="autocomplete-item"
                      onClick={() => applyProductToForm(product, 'edit')}
                    >
                      <strong>{product.name}</strong>
                      <span>{product.sku} • Stock {product.quantity} • Shop {currency(product.sellingPrice)} • Web {currency(product.websitePrice ?? product.sellingPrice)}</span>
                    </button>
                  ))}
                </div>
              ) : null}
            </div>

            {editingId && selectedProductSnapshot ? (
              <div className="inventory-helper-card">
                <strong>Existing product found</strong>
                <span>Current stock: {selectedProductSnapshot.quantity}</span>
                <span>Selected SKU: {selectedProductSnapshot.sku}</span>
                <span>Website price: {currency(selectedProductSnapshot.websitePrice ?? selectedProductSnapshot.sellingPrice)}</span>
                <span>Update the fields below to edit it, or change the search text to start a new product.</span>
              </div>
            ) : null}

            <input placeholder="Product name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
            <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} required>
              {categoryOptions.map((category) => (
                <option key={category.id} value={category.code}>{category.displayName}</option>
              ))}
            </select>
            <input placeholder="SKU" value={form.sku} onChange={(e) => setForm({ ...form, sku: e.target.value })} required />
            <input type="number" step="0.01" min="0" placeholder="Cost Price" value={form.costPrice} onChange={(e) => setForm({ ...form, costPrice: e.target.value })} required />
            <input type="number" step="0.01" min="0" placeholder="Shop Price" value={form.sellingPrice} onChange={(e) => setForm({ ...form, sellingPrice: e.target.value })} required />
            <input
              type="number"
              step="0.01"
              min="0"
              placeholder="Website Price %"
              value={form.websitePricePercentage}
              onChange={(e) => setForm({ ...form, websitePricePercentage: e.target.value })}
            />
            <input
              type="number"
              step="0.01"
              min="0"
              placeholder="Website Price"
              value={computedWebsitePrice}
              readOnly
            />
            <input
              type="number"
              min="0"
              placeholder="Quantity"
              value={form.quantity}
              onChange={(e) => setForm({ ...form, quantity: e.target.value })}
              required
            />
            <input
              type="number"
              min="0"
              placeholder="Low stock alert threshold"
              value={form.lowStockThreshold}
              onChange={(e) => setForm({ ...form, lowStockThreshold: e.target.value })}
              required
            />
            <p className="field-note">Leave Website Price % blank or 0 to keep the website price equal to the shop price.</p>
            <div className="image-picker">
              <label className="image-picker-label" htmlFor="product-image">Product picture</label>
              <input
                id="product-image"
                type="file"
                accept="image/*"
                onChange={handleImageChange}
                disabled={uploadingImage}
              />
              {uploadingImage ? <p className="field-note">Uploading image to S3...</p> : null}
              {form.imageDataUrl ? (
                <div className="image-preview-card">
                  <img src={form.imageDataUrl} alt={form.name || 'Product preview'} className="image-preview" />
                  <button
                    type="button"
                    className="ghost-btn"
                    onClick={() => setForm({ ...form, imageDataUrl: '' })}
                  >
                    Remove picture
                  </button>
                </div>
              ) : null}
            </div>
            <label className="toggle-field">
              <input
                type="checkbox"
                checked={Boolean(form.showInShopCollection)}
                onChange={(e) => setForm({ ...form, showInShopCollection: e.target.checked })}
              />
              <span>Homepage: Shop the Collection (shows up to 3 products)</span>
            </label>
            <label className="toggle-field">
              <input
                type="checkbox"
                checked={Boolean(form.showInFeaturedPieces)}
                onChange={(e) => setForm({ ...form, showInFeaturedPieces: e.target.checked })}
              />
              <span>Homepage: Featured Pieces (shows up to 8 products)</span>
            </label>
            <label className="toggle-field">
              <input
                type="checkbox"
                checked={Boolean(form.showInStory)}
                onChange={(e) => setForm({ ...form, showInStory: e.target.checked })}
              />
              <span>Homepage: Our Story Crafted with Intention (shows 1 product)</span>
            </label>
            <label className="toggle-field">
              <input
                type="checkbox"
                checked={Boolean(form.showInCuratedSelections)}
                onChange={(e) => setForm({ ...form, showInCuratedSelections: e.target.checked })}
              />
              <span>Homepage: Curated Selections (shows up to 4 products)</span>
            </label>
            <label className="toggle-field">
              <input
                type="checkbox"
                checked={Boolean(form.showInNewRelease)}
                onChange={(e) => setForm({ ...form, showInNewRelease: e.target.checked })}
              />
              <span>Tag this product as New Release</span>
            </label>
            <label className="toggle-field">
              <input
                type="checkbox"
                checked={Boolean(form.showInCustomerAccess)}
                onChange={(e) => setForm({ ...form, showInCustomerAccess: e.target.checked })}
              />
              <span>Customer Access hero image (single product only)</span>
            </label>
            <input type="date" value={form.expiryDate} onChange={(e) => setForm({ ...form, expiryDate: e.target.value })} />
            {error ? <p className="error-text">{error}</p> : null}
            {success ? <p className="success-text">{success}</p> : null}
            <button className="primary-btn" type="submit">
              {editingId ? 'Update Product' : 'Add Product'}
            </button>
          </form>
        </Panel>

        <Panel title="Current stock" subtitle="Low stock visibility helps billing and automation stay ahead.">
            <DataTable
              columns={[
                {
                  key: 'image',
                  label: 'Photo',
                  render: (row) => row.imageDataUrl ? <img src={row.imageDataUrl} alt={row.name} className="table-thumb" /> : '—'
                },
                { key: 'name', label: 'Name' },
                { key: 'category', label: 'Category' },
                { key: 'sku', label: 'SKU' },
                { key: 'sellingPrice', label: 'Shop Price', render: (row) => currency(row.sellingPrice) },
                {
                  key: 'websitePricePercentage',
                  label: 'Web %',
                  render: (row) => row.websitePricePercentage ? `${row.websitePricePercentage}%` : 'Same'
                },
                {
                  key: 'websitePrice',
                  label: 'Website Price',
                  render: (row) => currency(row.websitePrice ?? row.sellingPrice)
                },
                { key: 'quantity', label: 'Stock' },
                {
                  key: 'showInShopCollection',
                  label: 'Collection',
                  render: (row) => row.showInShopCollection ? 'On' : 'Off'
                },
                {
                  key: 'showInFeaturedPieces',
                  label: 'Featured',
                  render: (row) => row.showInFeaturedPieces ? 'On' : 'Off'
                },
                {
                  key: 'showInStory',
                  label: 'Story',
                  render: (row) => row.showInStory ? 'On' : 'Off'
                },
                {
                  key: 'showInCuratedSelections',
                  label: 'Curated',
                  render: (row) => row.showInCuratedSelections ? 'On' : 'Off'
                },
                { key: 'lowStockThreshold', label: 'Alert At' },
                {
                  key: 'actions',
                  label: 'Actions',
                  render: (row) => (
                    <div className="table-action-group">
                      <button
                        type="button"
                        className="ghost-btn compact-btn table-action-btn"
                        onClick={() => applyProductToForm(row, 'edit')}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="ghost-btn compact-btn table-action-btn danger-btn"
                        onClick={() => handleDeleteProduct(row)}
                      >
                        Delete
                      </button>
                    </div>
                  )
                }
              ]}
              rows={products}
              pagination={productsPage}
              onPageChange={loadProducts}
            />
        </Panel>

        <Panel title="Product categories" subtitle="Add categories like Jewellery or Cosmetics and use them immediately while creating products.">
            <form className="form-grid compact-form" onSubmit={submitCategory}>
              <input
                placeholder="Category name"
                value={categoryForm.displayName}
                onChange={(e) => setCategoryForm({ ...categoryForm, displayName: e.target.value })}
                required
              />
              <label className="toggle-field">
                <input
                  type="checkbox"
                  checked={Boolean(categoryForm.active)}
                  onChange={(e) => setCategoryForm({ ...categoryForm, active: e.target.checked })}
                />
                <span>Available in product forms</span>
              </label>
              {categoryError ? <p className="error-text">{categoryError}</p> : null}
              {categorySuccess ? <p className="success-text">{categorySuccess}</p> : null}
              <div className="table-action-group">
                <button className="primary-btn" type="submit">
                  {editingCategoryId ? 'Update Category' : 'Add Category'}
                </button>
                {editingCategoryId ? (
                  <button type="button" className="ghost-btn" onClick={resetCategoryForm}>Cancel</button>
                ) : null}
              </div>
            </form>

            <DataTable
              columns={[
                { key: 'displayName', label: 'Name' },
                { key: 'code', label: 'Code' },
                { key: 'active', label: 'Status', render: (row) => row.active ? 'Active' : 'Hidden' },
                {
                  key: 'actions',
                  label: 'Actions',
                  render: (row) => (
                    <button type="button" className="ghost-btn compact-btn table-action-btn" onClick={() => startEditCategory(row)}>
                      Edit
                    </button>
                  )
                }
              ]}
              rows={categoryTableRows}
              pagination={categoriesPage}
              onPageChange={loadCategories}
            />
        </Panel>
      </div>
    </div>
  );
}
