import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
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
  productImages: [],
  description: '',
  generateAiDescription: false,
  aiDescriptionStatus: '',
  aiDescription: '',
  aiDescriptionGeneratedAt: '',
  aiDescriptionError: '',
  showOnWebsite: true,
  useForBilling: true,
  showInEditorsPicks: false,
  showInNewRelease: false,
  showInCustomerAccess: false,
  showInShopCollection: false,
  showInFeaturedPieces: false,
  showInStory: false,
  showInCuratedSelections: false,
  facebookSyncEnabled: true,
  expiryDate: ''
});

const blankCategoryForm = {
  displayName: '',
  iconImageUrl: '',
  iconPrimaryColor: '#C97D3A',
  iconAccentColor: '#E8A44A',
  iconDetailColor: '#4A2C1A',
  facebookSyncEnabled: true,
  facebookCategory: '',
  facebookCollectionName: '',
  active: true
};

const skuFromProductName = (name) => String(name || '')
  .replace(/[^a-z0-9]/gi, '')
  .slice(0, 3)
  .toUpperCase();

const defaultFacebookCategoryFor = (name) => {
  const normalized = String(name || '').toLowerCase();
  if (normalized.includes('neck') || normalized.includes('mala') || normalized.includes('mangalsutra') || normalized.includes('pearl')) {
    return 'Apparel & Accessories > Jewelry > Necklaces';
  }
  if (normalized.includes('ear') || normalized.includes('jhum')) {
    return 'Apparel & Accessories > Jewelry > Earrings';
  }
  if (normalized.includes('bangle') || normalized.includes('bracelet') || normalized.includes('bali')) {
    return 'Apparel & Accessories > Jewelry > Bracelets';
  }
  if (normalized.includes('ring')) {
    return 'Apparel & Accessories > Jewelry > Rings';
  }
  if (normalized.includes('cosmetic') || normalized.includes('lip') || normalized.includes('kajal') || normalized.includes('makeup') || normalized.includes('beauty') || normalized.includes('skin')) {
    return 'Health & Beauty > Personal Care > Cosmetics';
  }
  return 'Apparel & Accessories > Jewelry';
};

const defaultFacebookCollectionFor = (name) => {
  const displayName = String(name || '').trim();
  return displayName ? `${displayName} Collection` : '';
};

export default function ProductsPage({
  initialTab = 'products',
  hidePageHeader = false,
  hideTabs = false
}) {
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTab = searchParams.get('tab');
  const [productsPage, setProductsPage] = useState({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [inventoryDirectory, setInventoryDirectory] = useState([]);
  const [categoriesPage, setCategoriesPage] = useState({ items: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [categoryOptions, setCategoryOptions] = useState([]);
  const [form, setForm] = useState(createBlankProduct());
  const [categoryForm, setCategoryForm] = useState(blankCategoryForm);
  const [activeInventoryTab, setActiveInventoryTab] = useState(['products', 'categories', 'collections', 'brands'].includes(initialTab) ? initialTab : 'products');
  const [editingId, setEditingId] = useState(null);
  const [editingCategoryId, setEditingCategoryId] = useState(null);
  const [formMode, setFormMode] = useState('create');
  const [inventorySearch, setInventorySearch] = useState('');
  const [inventoryTableSearch, setInventoryTableSearch] = useState('');
  const [inventoryCategoryFilter, setInventoryCategoryFilter] = useState('');
  const [inventoryStockFilter, setInventoryStockFilter] = useState('ALL');
  const [inventoryWebsiteFilter, setInventoryWebsiteFilter] = useState('ALL');
  const [searchFocused, setSearchFocused] = useState(false);
  const [selectedProductSnapshot, setSelectedProductSnapshot] = useState(null);
  const [uploadingImage, setUploadingImage] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [categoryError, setCategoryError] = useState('');
  const [categorySuccess, setCategorySuccess] = useState('');
  const [categorySubmitting, setCategorySubmitting] = useState(false);
  const [iconGenerating, setIconGenerating] = useState(false);
  const [iconOptions, setIconOptions] = useState([]);
  const [categoryDrawerOpen, setCategoryDrawerOpen] = useState(false);

  const defaultCategoryCode = categoryOptions[0]?.code || '';

  useEffect(() => {
    if (hideTabs) return;
    if (['products', 'categories', 'collections', 'brands'].includes(requestedTab) && requestedTab !== activeInventoryTab) {
      setActiveInventoryTab(requestedTab);
    }
  }, [requestedTab, activeInventoryTab, hideTabs]);

  useEffect(() => {
    if (!hideTabs) return;
    const nextTab = ['products', 'categories', 'collections', 'brands'].includes(initialTab) ? initialTab : 'products';
    if (nextTab !== activeInventoryTab) {
      setActiveInventoryTab(nextTab);
    }
  }, [activeInventoryTab, hideTabs, initialTab]);

  const selectInventoryTab = (tab) => {
    setActiveInventoryTab(tab);
    if (!hideTabs) {
      setSearchParams(tab === 'products' ? {} : { tab });
    }
  };

  const loadProducts = async (page = 0) => {
    try {
      setProductsPage(await retailService.getProducts({ page, size: 10 }));
    } catch {
      setError('Unable to load inventory.');
    }
  };

  const loadProductDirectory = async () => {
    try {
      const directoryPage = await retailService.getProducts({ page: 0, size: 500 });
      setInventoryDirectory(directoryPage.items || []);
    } catch {
      setInventoryDirectory([]);
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
    loadProductDirectory();
    loadCategories();
  }, []);

  useEffect(() => {
    if (!form.category && defaultCategoryCode) {
      setForm((current) => ({ ...current, category: defaultCategoryCode }));
    }
  }, [defaultCategoryCode, form.category]);

  const products = productsPage.items || [];
  const searchableProducts = inventoryDirectory.length ? inventoryDirectory : products;
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
  const previewImage = form.productImages?.[0] || form.imageDataUrl || '';

  const resetForm = () => {
    setForm(createBlankProduct(defaultCategoryCode));
    setEditingId(null);
    setFormMode('create');
    setInventorySearch('');
    setSelectedProductSnapshot(null);
  };

  const handleProductNameChange = (value) => {
    setForm((current) => {
      const currentAutoSku = skuFromProductName(current.name);
      const nextAutoSku = skuFromProductName(value);
      const shouldAutoSku = formMode === 'create'
        && (!current.sku || current.sku === currentAutoSku);

      return {
        ...current,
        name: value,
        sku: shouldAutoSku ? nextAutoSku : current.sku
      };
    });
  };

  const handleCostPriceChange = (value) => {
    setForm((current) => {
      const shouldUseCostAsShopPrice = formMode === 'create'
        && (!current.sellingPrice || current.sellingPrice === current.costPrice);

      return {
        ...current,
        costPrice: value,
        sellingPrice: shouldUseCostAsShopPrice ? value : current.sellingPrice
      };
    });
  };

  const handleCategoryNameChange = (value) => {
    setCategoryForm((current) => {
      const previousAutoCategory = defaultFacebookCategoryFor(current.displayName);
      const previousAutoCollection = defaultFacebookCollectionFor(current.displayName);
      return {
        ...current,
        displayName: value,
        facebookCategory: !current.facebookCategory || current.facebookCategory === previousAutoCategory
          ? defaultFacebookCategoryFor(value)
          : current.facebookCategory,
        facebookCollectionName: !current.facebookCollectionName || current.facebookCollectionName === previousAutoCollection
          ? defaultFacebookCollectionFor(value)
          : current.facebookCollectionName
      };
    });
  };

  const resetCategoryForm = () => {
    setCategoryForm(blankCategoryForm);
    setEditingCategoryId(null);
    setIconOptions([]);
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
      productImages: product.productImages?.length ? product.productImages : (product.imageDataUrl ? [product.imageDataUrl] : []),
      description: product.description || '',
      generateAiDescription: false,
      aiDescriptionStatus: product.aiDescriptionStatus || '',
      aiDescription: product.aiDescription || '',
      aiDescriptionGeneratedAt: product.aiDescriptionGeneratedAt || '',
      aiDescriptionError: product.aiDescriptionError || '',
      showOnWebsite: product.showOnWebsite !== false,
      useForBilling: product.useForBilling !== false,
      showInEditorsPicks: Boolean(product.showInEditorsPicks),
      showInNewRelease: Boolean(product.showInNewRelease),
      showInCustomerAccess: Boolean(product.showInCustomerAccess),
      showInShopCollection: Boolean(product.showInShopCollection),
      showInFeaturedPieces: Boolean(product.showInFeaturedPieces),
      showInStory: Boolean(product.showInStory),
      showInCuratedSelections: Boolean(product.showInCuratedSelections),
      facebookSyncEnabled: product.facebookSyncEnabled !== false,
      expiryDate: product.expiryDate || ''
    });
    setSearchFocused(false);
  };

  const handleImageChange = async (event) => {
    const files = Array.from(event.target.files || []);
    if (!files.length) {
      return;
    }

    if (files.some((file) => !file.type.startsWith('image/'))) {
      setError('Please choose a valid image file.');
      return;
    }

    setError('');
    setUploadingImage(true);
    try {
      const uploads = await Promise.all(files.map((file) => retailService.uploadImage({
        file,
        category: form.category || 'products'
      })));
      const uploadedUrls = uploads.map((upload) => upload.cloudfrontUrl).filter(Boolean);
      setForm((current) => ({
        ...current,
        imageDataUrl: current.imageDataUrl || uploadedUrls[0] || '',
        productImages: [...(current.productImages || []), ...uploadedUrls].filter(Boolean)
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
    if (!form.name.trim()) {
      setError('Product name is required.');
      return;
    }
    if (!form.category) {
      setError('Choose a product category.');
      return;
    }
    if (!form.sku.trim()) {
      setError('SKU is required.');
      return;
    }
    if (Number(form.costPrice) < 0 || Number(form.sellingPrice) <= 0) {
      setError('Enter valid cost and shop prices.');
      return;
    }
    if (Number(form.sellingPrice) < Number(form.costPrice)) {
      setError('Shop price cannot be lower than cost price.');
      return;
    }
    if (Number(form.quantity) < 0 || Number(form.lowStockThreshold) < 0) {
      setError('Stock and low-stock threshold cannot be negative.');
      return;
    }
    const selectedCategory = categoryOptions.find((category) => category.code === form.category);
    const selectedImages = form.productImages?.length ? form.productImages : (form.imageDataUrl ? [form.imageDataUrl] : []);
    if (form.facebookSyncEnabled) {
      if (form.showOnWebsite === false) {
        setError('Facebook synced products must be visible on the website.');
        return;
      }
      if (!selectedImages.length) {
        setError('Facebook synced products need at least one public product image.');
        return;
      }
      if (selectedCategory?.facebookSyncEnabled === false) {
        setError('Enable Facebook Catalog sync on the selected category first.');
        return;
      }
    }
    const payload = {
      ...form,
      imageDataUrl: selectedImages[0] || '',
      productImages: selectedImages,
      description: form.description?.trim() || null,
      generateAiDescription: Boolean(form.generateAiDescription),
      facebookSyncEnabled: form.facebookSyncEnabled !== false,
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
            ? `Product updated successfully.${form.generateAiDescription ? ' AI description is generating in the background.' : ''}`
            : `Product added successfully.${form.generateAiDescription ? ' AI description is generating in the background.' : ''}`
      );
      resetForm();
      loadProducts(productsPage.page || 0);
      loadProductDirectory();
      loadCategories(categoriesPage.page || 0);
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to save product.'));
    }
  };

  const submitCategory = async (event) => {
    event.preventDefault();
    setCategoryError('');
    setCategorySuccess('');
    if (!categoryForm.displayName.trim()) {
      setCategoryError('Category name is required.');
      return;
    }
    setCategorySubmitting(true);
    const categoryPayload = {
      displayName: categoryForm.displayName.trim(),
      iconImageUrl: categoryForm.iconImageUrl || '',
      facebookSyncEnabled: categoryForm.facebookSyncEnabled !== false,
      facebookCategory: categoryForm.facebookCategory || defaultFacebookCategoryFor(categoryForm.displayName),
      facebookCollectionName: categoryForm.facebookCollectionName || defaultFacebookCollectionFor(categoryForm.displayName),
      active: Boolean(categoryForm.active)
    };
    try {
      if (editingCategoryId) {
        await retailService.updateProductCategory(editingCategoryId, categoryPayload);
        setCategorySuccess('Category updated successfully.');
      } else {
        await retailService.createProductCategory(categoryPayload);
        setCategorySuccess('Category created successfully.');
      }
      resetCategoryForm();
      setIconOptions([]);
      setCategoryDrawerOpen(false);
      await loadCategories(categoriesPage.page || 0);
    } catch (requestError) {
      setCategoryError(getApiErrorMessage(requestError, 'Unable to save category.'));
    } finally {
      setCategorySubmitting(false);
    }
  };

  const startEditCategory = (category) => {
    setEditingCategoryId(category.id);
    setCategoryForm({
      displayName: category.displayName,
      iconImageUrl: category.iconImageUrl || '',
      iconPrimaryColor: '#C97D3A',
      iconAccentColor: '#E8A44A',
      iconDetailColor: '#4A2C1A',
      facebookSyncEnabled: category.facebookSyncEnabled !== false,
      facebookCategory: category.facebookCategory || defaultFacebookCategoryFor(category.displayName),
      facebookCollectionName: category.facebookCollectionName || defaultFacebookCollectionFor(category.displayName),
      active: Boolean(category.active)
    });
    setIconOptions([]);
    setCategoryDrawerOpen(true);
  };

  const generateCategoryIcons = async () => {
    const categoryName = categoryForm.displayName.trim();
    if (!categoryName) {
      setCategoryError('Enter category name before generating icons.');
      return;
    }
    setCategoryError('');
    setIconGenerating(true);
    try {
      const options = await retailService.generateProductCategoryIcons({
        categoryName,
        primaryColor: categoryForm.iconPrimaryColor || '#C97D3A',
        accentColor: categoryForm.iconAccentColor || '#E8A44A',
        detailColor: categoryForm.iconDetailColor || '#4A2C1A'
      });
      if (Array.isArray(options) && options.length) {
        setCategoryForm((current) => ({ ...current, iconImageUrl: options[0].imageUrl }));
        setIconOptions([]);
        setCategorySuccess(editingCategoryId ? 'Category icon generated. Save the category to apply it.' : 'Category icon generated. Save the category to keep it.');
      } else {
        setCategoryError('OpenAI returned no category icon. Please try again.');
      }
    } catch (requestError) {
      setIconOptions([]);
      setCategoryError(getApiErrorMessage(requestError, 'Unable to generate category icon with OpenAI.'));
    } finally {
      setIconGenerating(false);
    }
  };

  const handleDeleteProduct = async (product) => {
    const confirmed = window.confirm(`Remove "${product.name}" from active inventory? Receipts and invoices will stay preserved.`);
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
      setSuccess('Product removed from active inventory. Existing receipts and invoices are preserved.');
      const nextPage = products.length === 1 && productsPage.page > 0 ? productsPage.page - 1 : productsPage.page;
      loadProducts(nextPage);
      loadProductDirectory();
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to delete product.'));
    }
  };

  const productUpdatePayload = (product, overrides = {}) => ({
    name: product.name,
    category: product.category,
    sku: product.sku,
    costPrice: product.costPrice,
    sellingPrice: product.sellingPrice,
    websitePricePercentage: product.websitePricePercentage ?? null,
    quantity: product.quantity,
    lowStockThreshold: product.lowStockThreshold,
    imageDataUrl: product.productImages?.[0] || product.imageDataUrl || '',
    productImages: product.productImages?.length ? product.productImages : (product.imageDataUrl ? [product.imageDataUrl] : []),
    description: product.description || null,
    showOnWebsite: product.showOnWebsite !== false,
    useForBilling: product.useForBilling !== false,
    showInEditorsPicks: Boolean(product.showInEditorsPicks),
    showInNewRelease: Boolean(product.showInNewRelease),
    showInCustomerAccess: Boolean(product.showInCustomerAccess),
    showInShopCollection: Boolean(product.showInShopCollection),
    showInFeaturedPieces: Boolean(product.showInFeaturedPieces),
    showInStory: Boolean(product.showInStory),
    showInCuratedSelections: Boolean(product.showInCuratedSelections),
    facebookSyncEnabled: product.facebookSyncEnabled !== false,
    expiryDate: product.expiryDate || null,
    ...overrides
  });

  const quickUpdateProduct = async (product, overrides, successMessage) => {
    setError('');
    setSuccess('');
    try {
      await retailService.updateProduct(product.id, productUpdatePayload(product, overrides));
      setSuccess(successMessage || `${product.name} updated.`);
      await loadProducts(productsPage.page || 0);
      await loadProductDirectory();
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to update product.'));
    }
  };

  const duplicateProduct = (product) => {
    setEditingId(null);
    setFormMode('create');
    setSelectedProductSnapshot(null);
    setInventorySearch('');
    setForm({
      ...createBlankProduct(defaultCategoryCode),
      ...productUpdatePayload(product),
      name: `${product.name} Copy`,
      sku: `${skuFromProductName(product.name)}${Date.now().toString().slice(-4)}`,
      quantity: product.quantity || 0,
      expiryDate: product.expiryDate || ''
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const searchResults = inventorySearch
    ? searchableProducts
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

    const exactMatch = searchableProducts.find((product) =>
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

  const inventoryRows = useMemo(() => {
    const normalized = inventoryTableSearch.trim().toLowerCase();
    const source = normalized ? searchableProducts : products;

    return source
      .filter((product) => !normalized || `${product.name} ${product.sku} ${product.category}`.toLowerCase().includes(normalized))
      .filter((product) => !inventoryCategoryFilter || product.category === inventoryCategoryFilter)
      .filter((product) => {
        if (inventoryWebsiteFilter === 'WEBSITE_ON') {
          return product.showOnWebsite !== false;
        }
        if (inventoryWebsiteFilter === 'WEBSITE_OFF') {
          return product.showOnWebsite === false;
        }
        return true;
      })
      .filter((product) => {
        if (inventoryStockFilter === 'LOW') {
          return Number(product.quantity || 0) <= Number(product.lowStockThreshold || 0);
        }
        if (inventoryStockFilter === 'OUT') {
          return Number(product.quantity || 0) <= 0;
        }
        return true;
      })
      .slice(0, normalized || inventoryCategoryFilter || inventoryStockFilter !== 'ALL' || inventoryWebsiteFilter !== 'ALL' ? 80 : products.length);
  }, [inventoryTableSearch, inventoryCategoryFilter, inventoryStockFilter, inventoryWebsiteFilter, products, searchableProducts]);

  const categoryTableRows = useMemo(() => categoriesPage.items || [], [categoriesPage.items]);

  return (
    <div className="page">
      {hidePageHeader ? null : (
        <PageHeader
          eyebrow="Inventory"
          title="Product and stock management"
          description="Track cosmetics and jewellery stock, keep expiry dates visible, and update prices without mutating historical invoices."
        />
      )}

      {hideTabs ? null : <div className="admin-tab-row inventory-tabs" role="tablist" aria-label="Inventory sections">
        {[
          ['products', 'Products'],
          ['categories', 'Categories'],
          ['collections', 'Collections'],
          ['brands', 'Brands']
        ].map(([value, label]) => (
          <button
            key={value}
            type="button"
            className={activeInventoryTab === value ? 'is-active' : ''}
            onClick={() => selectInventoryTab(value)}
          >
            {label}
          </button>
        ))}
      </div>}

      <div className="inventory-stack">
        {activeInventoryTab === 'products' ? (
          <>
        <Panel
          title={formMode === 'restock' ? 'Add inventory' : editingId ? 'Edit product' : 'Add product'}
          subtitle="Search an existing product to restock it quickly, or enter a brand-new product from scratch."
        >
          <form className="form-grid product-add-form" onSubmit={submit}>
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

            <input placeholder="Product name" value={form.name} onChange={(e) => handleProductNameChange(e.target.value)} required />
            <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} required>
              {categoryOptions.map((category) => (
                <option key={category.id} value={category.code}>{category.displayName}</option>
              ))}
            </select>
            <input placeholder="SKU" value={form.sku} onChange={(e) => setForm({ ...form, sku: e.target.value })} required />
            <input type="number" step="0.01" min="0" placeholder="Cost Price" value={form.costPrice} onChange={(e) => handleCostPriceChange(e.target.value)} required />
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
                multiple
                onChange={handleImageChange}
                disabled={uploadingImage}
              />
              {uploadingImage ? <p className="field-note">Uploading image to S3...</p> : null}
              {(form.productImages?.length || form.imageDataUrl) ? (
                <div className="image-preview-card">
                  <div className="product-image-strip">
                    {(form.productImages?.length ? form.productImages : [form.imageDataUrl]).map((imageUrl, index) => (
                      <div key={`${imageUrl}-${index}`} className={index === 0 ? 'product-image-thumb is-primary' : 'product-image-thumb'}>
                        <img src={imageUrl} alt={`${form.name || 'Product'} preview ${index + 1}`} className="image-preview" />
                        <span>{index === 0 ? 'Primary' : `Image ${index + 1}`}</span>
                        <button
                          type="button"
                          className="ghost-btn compact-btn"
                          onClick={() => {
                            const nextImages = (form.productImages?.length ? form.productImages : [form.imageDataUrl]).filter((_, imageIndex) => imageIndex !== index);
                            setForm({ ...form, productImages: nextImages, imageDataUrl: nextImages[0] || '' });
                          }}
                        >
                          Remove
                        </button>
                      </div>
                    ))}
                  </div>
                </div>
              ) : null}
            </div>
            <label className="toggle-field">
              <input
                type="checkbox"
                checked={Boolean(form.generateAiDescription)}
                onChange={(e) => setForm({ ...form, generateAiDescription: e.target.checked })}
              />
              <span>{editingId ? 'Regenerate AI description after save' : 'Generate AI description'}</span>
            </label>
            <textarea
              className="form-textarea"
              placeholder="Product description"
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
            />
            {editingId && (form.aiDescriptionStatus || form.aiDescription || form.aiDescriptionError) ? (
              <div className="admin-ai-description-card">
                <strong>AI description: {form.aiDescriptionStatus || 'Not requested'}</strong>
                {form.aiDescription ? <p>{form.aiDescription}</p> : null}
                {form.aiDescriptionError ? <small>{form.aiDescriptionError}</small> : null}
                {form.aiDescriptionStatus === 'FAILED' ? <small>Check “Regenerate AI description after save” and update the product to retry.</small> : null}
              </div>
            ) : null}
            <label className="toggle-field">
              <input
                type="checkbox"
                checked={form.showOnWebsite !== false}
                onChange={(e) => setForm({ ...form, showOnWebsite: e.target.checked })}
              />
              <span>Show on Website</span>
            </label>
            <label className="toggle-field">
              <input
                type="checkbox"
                checked={form.useForBilling !== false}
                onChange={(e) => setForm({ ...form, useForBilling: e.target.checked })}
              />
              <span>Use for Shop Billing</span>
            </label>
            <div className="facebook-catalog-box">
              <label className="toggle-field">
                <input
                  type="checkbox"
                  checked={Boolean(form.facebookSyncEnabled)}
                  onChange={(e) => setForm({ ...form, facebookSyncEnabled: e.target.checked })}
                />
                <span>Sync this product to Facebook Catalog</span>
              </label>
              <p className="field-note">
                Facebook Category: {(() => {
                  const selectedCategory = categoryOptions.find((category) => category.code === form.category);
                  if (!selectedCategory) return 'Select a category to auto-map';
                  return selectedCategory.facebookCategory || defaultFacebookCategoryFor(selectedCategory.displayName);
                })()}
              </p>
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
            {(form.name || previewImage || form.category || computedWebsitePrice) ? (
              <div className="admin-product-preview-card">
                {previewImage ? <img src={previewImage} alt={`${form.name || 'Product'} preview`} /> : <div className="support-product-placeholder" />}
                <div>
                  <span>{form.category || 'Category'}</span>
                  <strong>{form.name || 'Product preview'}</strong>
                  <p>{currency(computedWebsitePrice || form.sellingPrice || 0)}</p>
                  <small>{form.showOnWebsite !== false ? 'Visible on website' : 'Website hidden'} · {form.useForBilling !== false ? 'Billing enabled' : 'Billing hidden'}</small>
                </div>
              </div>
            ) : null}
            {error ? <p className="error-text">{error}</p> : null}
            {success ? <p className="success-text">{success}</p> : null}
            <button className="primary-btn" type="submit">
              {editingId ? 'Update Product' : 'Add Product'}
            </button>
          </form>
        </Panel>

        <Panel title="Current stock" subtitle="Low stock visibility helps billing and automation stay ahead.">
            <div className="inventory-table-toolbar">
              <div className="search-box-wrap">
                <input
                  placeholder="Search all inventory for quick edit"
                  value={inventoryTableSearch}
                  onChange={(e) => setInventoryTableSearch(e.target.value)}
                  autoComplete="off"
                />
                <p className="field-note">
                  {inventoryTableSearch.trim()
                    ? `Showing up to ${inventoryRows.length} matching products from the full inventory list.`
                    : 'Filter by name, SKU, or category to jump straight to the right product.'}
                </p>
              </div>
              <select value={inventoryCategoryFilter} onChange={(e) => setInventoryCategoryFilter(e.target.value)}>
                <option value="">All categories</option>
                {categoryOptions.map((category) => (
                  <option key={category.id} value={category.code}>{category.displayName}</option>
                ))}
              </select>
              <select value={inventoryStockFilter} onChange={(e) => setInventoryStockFilter(e.target.value)}>
                <option value="ALL">All stock</option>
                <option value="LOW">Low stock</option>
                <option value="OUT">Out of stock</option>
              </select>
              <select value={inventoryWebsiteFilter} onChange={(e) => setInventoryWebsiteFilter(e.target.value)}>
                <option value="ALL">Website: all</option>
                <option value="WEBSITE_ON">Website on</option>
                <option value="WEBSITE_OFF">Website off</option>
              </select>
            </div>
            <DataTable
              columns={[
                {
                  key: 'image',
                  label: 'Photo',
                  render: (row) => (row.productImages?.[0] || row.imageDataUrl) ? <img src={row.productImages?.[0] || row.imageDataUrl} alt={row.name} className="table-thumb" /> : '—'
                },
                { key: 'name', label: 'Name' },
                { key: 'category', label: 'Category' },
                { key: 'sellingPrice', label: 'Shop Price', render: (row) => currency(row.sellingPrice) },
                {
                  key: 'websitePrice',
                  label: 'Website Price',
                  render: (row) => currency(row.websitePrice ?? row.sellingPrice)
                },
                { key: 'quantity', label: 'Stock' },
                {
                  key: 'showOnWebsite',
                  label: 'Website',
                  render: (row) => (
                    <button type="button" className={row.showOnWebsite !== false ? 'mini-switch is-on' : 'mini-switch'} onClick={() => quickUpdateProduct(row, { showOnWebsite: row.showOnWebsite === false }, 'Website visibility updated.')}>
                      <span />
                    </button>
                  )
                },
                {
                  key: 'useForBilling',
                  label: 'Billing',
                  render: (row) => (
                    <button type="button" className={row.useForBilling !== false ? 'mini-switch is-on' : 'mini-switch'} onClick={() => quickUpdateProduct(row, { useForBilling: row.useForBilling === false }, 'Billing visibility updated.')}>
                      <span />
                    </button>
                  )
                },
                {
                  key: 'showInFeaturedPieces',
                  label: 'Featured',
                  render: (row) => (
                    <button type="button" className={row.showInFeaturedPieces ? 'mini-switch is-on' : 'mini-switch'} onClick={() => quickUpdateProduct(row, { showInFeaturedPieces: !row.showInFeaturedPieces }, 'Featured flag updated.')}>
                      <span />
                    </button>
                  )
                },
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
                        className="ghost-btn compact-btn table-action-btn"
                        onClick={() => duplicateProduct(row)}
                      >
                        Duplicate
                      </button>
                      <a className="ghost-btn compact-btn table-action-btn" href={`/product/${row.id}`} target="_blank" rel="noreferrer">
                        Preview
                      </a>
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
              rows={inventoryRows}
              emptyMessage="No inventory matched this search."
              pagination={inventoryTableSearch.trim() || inventoryCategoryFilter || inventoryStockFilter !== 'ALL' || inventoryWebsiteFilter !== 'ALL' ? null : productsPage}
              onPageChange={inventoryTableSearch.trim() || inventoryCategoryFilter || inventoryStockFilter !== 'ALL' || inventoryWebsiteFilter !== 'ALL' ? null : loadProducts}
            />
        </Panel>
          </>
        ) : null}

        {activeInventoryTab === 'categories' ? (
        <Panel title="Product categories" subtitle="Add categories like Jewellery or Cosmetics and use them immediately while creating products.">
            <div className="category-list-toolbar">
              <div>
                <strong>{categoriesPage.totalItems || categoryTableRows.length} categories</strong>
                <span>Transparent icons, catalog sync, and product form visibility.</span>
              </div>
              <button
                type="button"
                className="primary-btn compact-btn"
                onClick={() => {
                  resetCategoryForm();
                  setCategoryDrawerOpen(true);
                }}
              >
                Add Category
              </button>
            </div>

            <div className="category-table-card">
              <DataTable
                columns={[
                  {
                    key: 'iconImageUrl',
                    label: 'Category',
                    render: (row) => (
                      <div className="category-cell">
                        <span className="category-cell-icon">
                          {row.iconImageUrl ? <img src={row.iconImageUrl} alt={row.displayName} /> : <i className="bx bx-category" />}
                        </span>
                        <span>
                          <strong>{row.displayName}</strong>
                          <small>{row.code}</small>
                        </span>
                      </div>
                    )
                  },
                  {
                    key: 'productCount',
                    label: 'Products',
                    render: (row) => products.filter((product) => product.category === row.code).length
                  },
                  {
                    key: 'facebookSyncEnabled',
                    label: 'Catalog',
                    render: (row) => row.facebookSyncEnabled ? 'Synced' : 'Off'
                  },
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
            </div>

            {categoryDrawerOpen ? <button type="button" className="category-drawer-backdrop" aria-label="Close category drawer" onClick={() => setCategoryDrawerOpen(false)} /> : null}
            <aside className={`category-drawer ${categoryDrawerOpen ? 'is-open' : ''}`} aria-hidden={!categoryDrawerOpen}>
              <form className="category-drawer-form" onSubmit={submitCategory}>
                <div className="category-drawer-head">
                  <div>
                    <h3>{editingCategoryId ? 'Edit Category' : 'Add Category'}</h3>
                    <span>Use transparent jewellery-style icons with your brand colors.</span>
                  </div>
                  <button type="button" className="ghost-btn compact-btn" onClick={() => setCategoryDrawerOpen(false)}>Close</button>
                </div>

                <label>
                  <span>Title</span>
                  <input
                    placeholder="Enter category title"
                    value={categoryForm.displayName}
                    onChange={(e) => handleCategoryNameChange(e.target.value)}
                    required
                  />
                </label>
                <label>
                  <span>Slug</span>
                  <input
                    placeholder="Auto generated"
                    value={String(categoryForm.displayName || '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')}
                    readOnly
                  />
                </label>
                <label>
                  <span>Attachment / icon URL</span>
                  <input
                    placeholder="Generated icon URL or uploaded image URL"
                    value={categoryForm.iconImageUrl}
                    onChange={(e) => setCategoryForm({ ...categoryForm, iconImageUrl: e.target.value })}
                  />
                </label>
                <div className="category-icon-builder">
                  <div className="category-icon-preview">
                    {categoryForm.iconImageUrl ? <img src={categoryForm.iconImageUrl} alt="Selected category icon" /> : <span>Transparent icon</span>}
                  </div>
                  <button type="button" className="ghost-btn compact-btn" onClick={generateCategoryIcons} disabled={iconGenerating}>
                    {iconGenerating ? 'Generating...' : categoryForm.iconImageUrl ? 'Regenerate Icon' : 'Generate Icon'}
                  </button>
                </div>
                <div className="category-color-grid">
                  {[
                    ['iconPrimaryColor', 'Primary gold'],
                    ['iconAccentColor', 'Gem light'],
                    ['iconDetailColor', 'Dark detail']
                  ].map(([field, label]) => (
                    <label key={field}>
                      <span>{label}</span>
                      <input
                        type="color"
                        value={categoryForm[field]}
                        onChange={(e) => setCategoryForm({ ...categoryForm, [field]: e.target.value })}
                      />
                    </label>
                  ))}
                </div>
                {iconOptions.length ? (
                  <div className="category-icon-options">
                    {iconOptions.map((option) => (
                      <button
                        key={option.label}
                        type="button"
                        className={categoryForm.iconImageUrl === option.imageUrl ? 'is-selected' : ''}
                        onClick={() => setCategoryForm({ ...categoryForm, iconImageUrl: option.imageUrl })}
                      >
                        <img src={option.imageUrl} alt={option.label} />
                        <span>{option.label}</span>
                      </button>
                    ))}
                  </div>
                ) : null}
                <label>
                  <span>Parent category</span>
                  <select value="" disabled>
                    <option>Select parent category</option>
                  </select>
                </label>
                <label>
                  <span>Description</span>
                  <textarea placeholder="Optional category notes for store team" rows={4} disabled />
                </label>
                <label>
                  <span>Select category status</span>
                  <select
                    value={categoryForm.active ? 'active' : 'hidden'}
                    onChange={(e) => setCategoryForm({ ...categoryForm, active: e.target.value === 'active' })}
                  >
                    <option value="active">Active</option>
                    <option value="hidden">Hidden</option>
                  </select>
                </label>
                <div className="facebook-catalog-box">
                  <label className="toggle-field">
                    <input
                      type="checkbox"
                      checked={Boolean(categoryForm.facebookSyncEnabled)}
                      onChange={(e) => setCategoryForm((current) => ({
                        ...current,
                        facebookSyncEnabled: e.target.checked,
                        facebookCategory: e.target.checked && !current.facebookCategory ? defaultFacebookCategoryFor(current.displayName) : current.facebookCategory,
                        facebookCollectionName: e.target.checked && !current.facebookCollectionName ? defaultFacebookCollectionFor(current.displayName) : current.facebookCollectionName
                      }))}
                    />
                    <span>Sync this category to Facebook</span>
                  </label>
                  {categoryForm.facebookSyncEnabled ? (
                    <div className="settings-two-column">
                      <input
                        placeholder="Facebook Product Category"
                        value={categoryForm.facebookCategory || ''}
                        onChange={(e) => setCategoryForm({ ...categoryForm, facebookCategory: e.target.value })}
                      />
                      <input
                        placeholder="Facebook Collection Name"
                        value={categoryForm.facebookCollectionName || ''}
                        onChange={(e) => setCategoryForm({ ...categoryForm, facebookCollectionName: e.target.value })}
                      />
                    </div>
                  ) : null}
                </div>
                {categoryError ? <p className="error-text">{categoryError}</p> : null}
                {categorySuccess ? <p className="success-text">{categorySuccess}</p> : null}
                <div className="table-action-group">
                  <button className="primary-btn" type="submit" disabled={categorySubmitting}>
                    {categorySubmitting ? 'Saving...' : editingCategoryId ? 'Update' : 'Add'}
                  </button>
                  <button type="button" className="ghost-btn" onClick={() => {
                    resetCategoryForm();
                    setCategoryDrawerOpen(false);
                  }}>
                    Discard
                  </button>
                </div>
              </form>
            </aside>
        </Panel>
        ) : null}
        {activeInventoryTab === 'collections' ? (
          <Panel title="Collections" subtitle="Collection controls reuse the product homepage flags today. Use product quick toggles for Featured, Shop Collection, Story, and Curated sections.">
            <div className="empty-state-card">
              <strong>Collection controls are available on each product row.</strong>
              <span>Dedicated collection grouping can be added later without changing product visibility behavior.</span>
            </div>
          </Panel>
        ) : null}
        {activeInventoryTab === 'brands' ? (
          <Panel title="Brands" subtitle="Brand management is reserved for future cosmetics and jewellery vendor grouping.">
            <div className="empty-state-card">
              <strong>No brand records yet.</strong>
              <span>Products continue to work with existing category and SKU fields.</span>
            </div>
          </Panel>
        ) : null}
      </div>
    </div>
  );
}
