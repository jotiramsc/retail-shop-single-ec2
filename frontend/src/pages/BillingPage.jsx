import { useEffect, useRef, useState } from 'react';
import DataTable from '../components/DataTable';
import MetricCard from '../components/MetricCard';
import PageHeader from '../components/PageHeader';
import Panel from '../components/Panel';
import { retailService } from '../services/retailService';
import { currency, formatDate } from '../utils/format';
import { getApiErrorMessage, isValidMobile } from '../utils/validation';

const initialForm = {
  customerName: '',
  customerMobile: '',
  paymentMode: 'CASH',
  couponCode: '',
  manualDiscount: 0,
  manualDiscountType: 'FIXED'
};

const today = new Date().toISOString().slice(0, 10);

export default function BillingPage() {
  const latestInvoiceRef = useRef(null);
  const [products, setProducts] = useState([]);
  const [offers, setOffers] = useState([]);
  const [trendingProducts, setTrendingProducts] = useState([]);
  const [cart, setCart] = useState([]);
  const [editingInvoiceOriginalQuantities, setEditingInvoiceOriginalQuantities] = useState({});
  const [searchPickedProducts, setSearchPickedProducts] = useState([]);
  const [invoiceSearchName, setInvoiceSearchName] = useState('');
  const [invoiceSearchFromDate, setInvoiceSearchFromDate] = useState(today);
  const [invoiceSuggestions, setInvoiceSuggestions] = useState({ invoices: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
  const [editingInvoiceId, setEditingInvoiceId] = useState(null);
  const [form, setForm] = useState(initialForm);
  const [invoice, setInvoice] = useState(null);
  const [receiptSettings, setReceiptSettings] = useState(null);
  const [customerSnapshot, setCustomerSnapshot] = useState(null);
  const [customerSuggestions, setCustomerSuggestions] = useState([]);
  const [customerSearchFocused, setCustomerSearchFocused] = useState(false);
  const [productSearch, setProductSearch] = useState('');
  const [searchFocused, setSearchFocused] = useState(false);
  const [previewInvoice, setPreviewInvoice] = useState(null);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submittingAndPrinting, setSubmittingAndPrinting] = useState(false);

  useEffect(() => {
    Promise.all([
      retailService.getProducts({ page: 0, size: 250 }),
      retailService.getTrendingProducts(),
      retailService.getReceiptSettings(),
      retailService.getOffers({ page: 0, size: 250 })
    ])
      .then(([allProducts, trending, settings, offersPage]) => {
        setProducts(allProducts.items || []);
        setTrendingProducts(trending);
        setReceiptSettings(settings);
        setOffers(offersPage.items || []);
      })
      .catch(() => setError('Unable to load billing data.'));
  }, []);

  useEffect(() => {
    if (!isValidMobile(form.customerMobile)) {
      setCustomerSnapshot(null);
      return;
    }

    const timeoutId = window.setTimeout(async () => {
      try {
        const customer = await retailService.lookupCustomer(form.customerMobile);
        setCustomerSnapshot(customer);
        setForm((current) => ({
          ...current,
          customerName: current.customerName || customer.name
        }));
      } catch {
        setCustomerSnapshot(null);
      }
    }, 350);

    return () => window.clearTimeout(timeoutId);
  }, [form.customerMobile]);

  useEffect(() => {
    const normalizedName = form.customerName.trim();
    if (normalizedName.length < 1 || !customerSearchFocused) {
      setCustomerSuggestions([]);
      return;
    }

    const timeoutId = window.setTimeout(async () => {
      try {
        const matches = await retailService.searchCustomers(normalizedName);
        setCustomerSuggestions(
          matches.filter(
            (customer) =>
              customer.mobile !== form.customerMobile ||
              customer.name.toLowerCase() !== normalizedName.toLowerCase()
          )
        );
      } catch {
        setCustomerSuggestions([]);
      }
    }, 250);

    return () => window.clearTimeout(timeoutId);
  }, [form.customerName, form.customerMobile, customerSearchFocused]);

  useEffect(() => {
    if (!cart.length) {
      setPreviewInvoice(null);
      return;
    }

    const timeoutId = window.setTimeout(async () => {
      try {
        const preview = await retailService.previewInvoice({
          customerName: form.customerName || 'Walk-in Customer',
          customerMobile: isValidMobile(form.customerMobile) ? form.customerMobile : '9999999999',
          paymentMode: form.paymentMode,
          couponCode: form.couponCode || null,
          manualDiscount: Number(form.manualDiscount || 0),
          manualDiscountType: form.manualDiscountType,
          items: cart.map((item) => ({ productId: item.productId, quantity: Number(item.quantity) }))
        });
        setPreviewInvoice(preview);
      } catch {
        setPreviewInvoice(null);
      }
    }, 250);

    return () => window.clearTimeout(timeoutId);
  }, [cart, form.customerName, form.customerMobile, form.paymentMode, form.couponCode, form.manualDiscount, form.manualDiscountType]);

  useEffect(() => {
    const normalized = invoiceSearchName.trim();
    if (!normalized) {
      setInvoiceSuggestions([]);
      return;
    }

    const timeoutId = window.setTimeout(async () => {
      try {
        const result = await retailService.searchInvoices({
          fromDate: invoiceSearchFromDate,
          toDate: today,
          customerName: normalized,
          page: 0,
          size: 10
        });
        setInvoiceSuggestions(result);
      } catch {
        setInvoiceSuggestions({ invoices: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
      }
    }, 250);

    return () => window.clearTimeout(timeoutId);
  }, [invoiceSearchName, invoiceSearchFromDate]);

  useEffect(() => {
    if (!invoice || !latestInvoiceRef.current) {
      return;
    }

    latestInvoiceRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }, [invoice]);

  const getOriginalInvoiceQuantity = (productId) => Number(editingInvoiceOriginalQuantities[productId] || 0);

  const getEditableStock = (product) => Number(product.quantity || 0) + getOriginalInvoiceQuantity(product.id);

  const addToCart = (product, source = 'catalog') => {
    const editableStock = getEditableStock(product);
    const originalQuantity = getOriginalInvoiceQuantity(product.id);
    setCart((current) => {
      const existing = current.find((item) => item.productId === product.id);
      if (existing) {
        if (existing.quantity >= existing.stock) {
          setError(`Only ${existing.stock} units available for ${product.name}${editingInvoiceId ? ' while editing this invoice' : ''}.`);
          return current;
        }
        return current.map((item) =>
          item.productId === product.id ? { ...item, quantity: item.quantity + 1 } : item
        );
      }
      return [
        ...current,
        {
          productId: product.id,
          name: product.name,
          price: product.sellingPrice,
          stock: editableStock,
          currentStock: product.quantity,
          originalQuantity,
          lowStockThreshold: product.lowStockThreshold,
          imageDataUrl: product.imageDataUrl,
          quantity: 1
        }
      ];
    });
    if (source === 'search') {
      setSearchPickedProducts((current) => [
        product,
        ...current.filter((item) => item.id !== product.id)
      ].slice(0, 6));
    }
    setProductSearch('');
    setSearchFocused(false);
  };

  const updateQuantity = (productId, quantity) => {
    const parsedQuantity = Number(quantity);
    setCart((current) =>
      current
        .map((item) => {
          if (item.productId !== productId) {
            return item;
          }
          const nextQuantity = Math.max(1, Math.min(item.stock, parsedQuantity || 0));
          return { ...item, quantity: nextQuantity };
        })
        .filter((item) => item.quantity > 0)
    );
  };

  const subtotal = cart.reduce((sum, item) => sum + Number(item.price) * item.quantity, 0);

  const printInvoice = (invoiceToPrint) => {
    if (!invoiceToPrint) {
      return;
    }
    const printableInvoice = invoiceToPrint;
    const receiptWindow = window.open('', '_blank', 'width=900,height=700');
    if (!receiptWindow) {
      return;
    }

    const rows = (printableInvoice.items || [])
      .map(
        (item) => `
          <tr>
            <td>${item.productName}</td>
            <td>${item.quantity}</td>
            <td>${currency(item.unitPrice)}</td>
            <td>${currency(item.discount)}</td>
            <td>${currency(item.lineTotal)}</td>
          </tr>
        `
      )
      .join('');

    receiptWindow.document.write(`
      <html>
        <head>
          <title>${printableInvoice.invoiceNumber}</title>
          <style>
            body { font-family: Arial, sans-serif; padding: 8px; color: #222; width: 72mm; font-size: 12px; }
            h1, h2, p { margin: 0; }
            .center { text-align: center; margin-bottom: 8px; }
            .meta { margin: 10px 0; }
            table { width: 100%; border-collapse: collapse; margin-top: 20px; }
            th, td { border-bottom: 1px dashed #aaa; padding: 6px 2px; text-align: left; font-size: 11px; }
            .totals { margin-top: 12px; font-size: 12px; }
            @page { size: 80mm auto; margin: 4mm; }
          </style>
        </head>
        <body>
          <div class="center">
            <h1>${receiptSettings?.shopName || 'Store'}</h1>
            ${receiptSettings?.headerLine ? `<p>${receiptSettings.headerLine}</p>` : ''}
            ${receiptSettings?.showAddress ? `<p>${receiptSettings.address || ''}</p>` : ''}
            ${receiptSettings?.showPhoneNumber ? `<p>${receiptSettings.phoneNumber || ''}</p>` : ''}
            ${receiptSettings?.showGstNumber ? `<p>GST: ${receiptSettings.gstNumber || ''}</p>` : ''}
          </div>
          <div class="meta">
            <div><strong>Invoice:</strong> ${printableInvoice.invoiceNumber}</div>
            <div><strong>Customer:</strong> ${printableInvoice.customerName} (${printableInvoice.customerMobile})</div>
            <div><strong>Created:</strong> ${formatDate(printableInvoice.createdAt)}</div>
            <div><strong>Payment:</strong> ${printableInvoice.paymentMode}</div>
          </div>
          <table>
            <thead>
              <tr>
                <th>Product</th>
                <th>Qty</th>
                <th>Unit Price</th>
                <th>Discount</th>
                <th>Net Line</th>
              </tr>
            </thead>
            <tbody>${rows}</tbody>
          </table>
          <div class="totals">
            <div><strong>Total:</strong> ${currency(printableInvoice.totalAmount)}</div>
            <div><strong>Discount:</strong> ${currency(printableInvoice.discount)}</div>
            <div><strong>Final Amount:</strong> ${currency(printableInvoice.finalAmount)}</div>
            ${receiptSettings?.footerNote ? `<div style="margin-top:10px;text-align:center;">${receiptSettings.footerNote}</div>` : ''}
          </div>
        </body>
      </html>
    `);
    receiptWindow.document.close();
    receiptWindow.focus();
    receiptWindow.print();
  };

  const saveInvoice = async (shouldPrintAfterSave = false) => {
    setError('');
    setSuccess('');
    if (!isValidMobile(form.customerMobile)) {
      setError('Enter a valid mobile number with 10 to 15 digits.');
      return false;
    }
    if (!cart.length) {
      setError('Add at least one product to the cart.');
      return false;
    }
    setSubmitting(!shouldPrintAfterSave);
    setSubmittingAndPrinting(shouldPrintAfterSave);
    try {
      const payload = {
        ...form,
        couponCode: form.couponCode || null,
        manualDiscount: Number(form.manualDiscount || 0),
        manualDiscountType: form.manualDiscountType,
        items: cart.map((item) => ({ productId: item.productId, quantity: Number(item.quantity) }))
      };
      const response = editingInvoiceId
        ? await retailService.updateInvoice(editingInvoiceId, payload)
        : await retailService.createInvoice(payload);
      setInvoice(response);
      if (shouldPrintAfterSave) {
        window.setTimeout(() => printInvoice(response), 120);
      }
      setCart([]);
      setForm(initialForm);
      setCustomerSnapshot(null);
      setEditingInvoiceId(null);
      setEditingInvoiceOriginalQuantities({});
      setInvoiceSearchName('');
      setInvoiceSearchFromDate(today);
      setSuccess(editingInvoiceId
        ? `Invoice ${response.invoiceNumber} updated successfully.`
        : `Invoice ${response.invoiceNumber} created successfully.`);
      const [allProducts, trending, offersPage] = await Promise.all([
        retailService.getProducts({ page: 0, size: 250 }),
        retailService.getTrendingProducts(),
        retailService.getOffers({ page: 0, size: 250 })
      ]);
      setProducts(allProducts.items || []);
      setTrendingProducts(trending);
      setOffers(offersPage.items || []);
      return true;
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Unable to generate bill.'));
      return false;
    } finally {
      setSubmitting(false);
      setSubmittingAndPrinting(false);
    }
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    await saveInvoice(false);
  };

  const handleGenerateAndPrint = async () => {
    await saveInvoice(true);
  };

  const handlePrint = () => printInvoice(invoice);

  const filteredProducts = products.filter((product) =>
    `${product.name} ${product.sku} ${product.category}`.toLowerCase().includes(productSearch.toLowerCase())
  );
  const searchSuggestions = productSearch
    ? filteredProducts.filter((product) => !cart.some((item) => item.productId === product.id)).slice(0, 8)
    : [];
  const searchTileProducts = productSearch ? filteredProducts.slice(0, 6) : [];
  const offerDiscount = (previewInvoice?.items || []).reduce((sum, item) => sum + Number(item.discount || 0), 0);
  const previewTotalAmount = Number(previewInvoice?.totalAmount ?? subtotal);
  const couponSelected = Boolean(form.couponCode);
  const customDiscount = form.manualDiscountType === 'PERCENT'
    ? Number((previewTotalAmount * Number(form.manualDiscount || 0)) / 100)
    : Number(form.manualDiscount || 0);
  const previewFinalAmount = Number(previewInvoice?.finalAmount ?? Math.max(subtotal - customDiscount, 0));
  const autoOrCouponDiscount = Math.max(Number(previewInvoice?.discount ?? 0) - customDiscount, 0);
  const previewTotalDiscount = Number(autoOrCouponDiscount + customDiscount);
  const remainingStockAfterEdit = (item) => Number(item.currentStock || 0) + Number(item.originalQuantity || 0) - Number(item.quantity || 0);
  const lowStockBillingAlerts = cart.filter(
    (item) => Number(item.currentStock || 0) <= item.lowStockThreshold || remainingStockAfterEdit(item) <= item.lowStockThreshold
  );
  const isLowStockProduct = (product) => Number(product.quantity || 0) <= product.lowStockThreshold;
  const isLowStockCartItem = (item) => Number(item.currentStock || 0) <= item.lowStockThreshold || remainingStockAfterEdit(item) <= item.lowStockThreshold;
  const productStockLabel = (product) => {
    const originalQuantity = getOriginalInvoiceQuantity(product.id);
    if (editingInvoiceId && originalQuantity > 0) {
      return `Available in edit: ${getEditableStock(product)} (${product.quantity} on shelf + ${originalQuantity} already billed)`;
    }
    return `Stock: ${product.quantity}`;
  };
  const selectCustomer = (customer) => {
    setForm((current) => ({
      ...current,
      customerName: customer.name,
      customerMobile: customer.mobile
    }));
    setCustomerSearchFocused(false);
    setCustomerSuggestions([]);
  };

  const productVisualStyle = (product) =>
    product.imageDataUrl ? { backgroundImage: `url(${product.imageDataUrl})` } : undefined;

  const validCouponOffers = offers.filter((offer) => {
    if (!offer.active || !offer.couponCode || !cart.length) {
      return false;
    }

    const todayDate = today;
    const validFrom = offer.validFrom || offer.startDate;
    const validTo = offer.validTo || offer.endDate;
    if ((validFrom && validFrom > todayDate) || (validTo && validTo < todayDate)) {
      return false;
    }

    if (offer.minOrderValue != null && Number(offer.minOrderValue) > previewTotalAmount) {
      return false;
    }

    return cart.some((item) => {
      const product = products.find((entry) => entry.id === item.productId);
      if (!product) {
        return false;
      }
      if (offer.productId) {
        return offer.productId === product.id;
      }
      if (offer.category) {
        return String(offer.category).toLowerCase() === String(product.category).toLowerCase();
      }
      return true;
    });
  });

  const loadInvoiceForEdit = (selectedInvoice) => {
    const originalQuantities = Object.fromEntries(
      (selectedInvoice.items || []).map((item) => [item.productId, item.quantity])
    );
    setEditingInvoiceId(selectedInvoice.id);
    setEditingInvoiceOriginalQuantities(originalQuantities);
    setInvoiceSuggestions([]);
    setInvoiceSearchName(selectedInvoice.customerName);
      setForm({
        customerName: selectedInvoice.customerName,
        customerMobile: selectedInvoice.customerMobile,
        paymentMode: selectedInvoice.paymentMode,
        couponCode: selectedInvoice.couponCode || '',
        manualDiscount: 0,
        manualDiscountType: 'FIXED'
      });
    setCart(
      (selectedInvoice.items || []).map((item) => {
        const product = products.find((entry) => entry.id === item.productId);
        return {
          productId: item.productId,
          name: item.productName,
          price: item.unitPrice,
          stock: (product?.quantity ?? 0) + item.quantity,
          currentStock: product?.quantity ?? 0,
          originalQuantity: item.quantity,
          lowStockThreshold: product?.lowStockThreshold ?? 0,
          imageDataUrl: product?.imageDataUrl,
          quantity: item.quantity
        };
      })
    );
  };

  const loadInvoiceSuggestionPage = async (page) => {
    try {
      const result = await retailService.searchInvoices({
        fromDate: invoiceSearchFromDate,
        toDate: today,
        customerName: invoiceSearchName.trim(),
        page,
        size: 10
      });
      setInvoiceSuggestions(result);
    } catch {
      setInvoiceSuggestions({ invoices: [], page: 0, totalPages: 0, totalItems: 0, hasNext: false, hasPrevious: false });
    }
  };

  return (
    <div className="page">
      <PageHeader
        eyebrow="POS Billing"
        title="Fast billing for the counter"
        description="Build the cart, validate stock, auto-apply the best live offer, and close the sale in one move."
      />

      <div className="metric-grid">
        <MetricCard label="Cart Items" value={cart.length} />
        <MetricCard label="Subtotal" value={currency(subtotal)} tone="accent" />
        <MetricCard label="Products Ready" value={products.length} />
      </div>

      <div className="two-column">
        <div className="inventory-stack">
        <Panel title="Product picker" subtitle="Top 10 frequently bought items stay visible here. Use search if the item is not in quick picks.">
          <div className="catalog-hero">
            <div className="catalog-hero-copy">
              <p className="eyebrow">Curated Counter</p>
              <h4>Jewellery highlights and beauty essentials in one polished counter view.</h4>
              <p>Search fast, keep premium picks visible, and catch low-stock items before they disappoint a customer at checkout.</p>
            </div>
            <div className="catalog-hero-art">
              <div className="hero-image hero-image-jewel" />
              <div className="hero-image hero-image-vanity" />
            </div>
          </div>

          {lowStockBillingAlerts.length ? (
            <div className="billing-alert">
              <strong>Low stock alert</strong>
              <span>
                {lowStockBillingAlerts.map((item) => `${item.name} (${item.stock} in stock, after bill ${item.stock - item.quantity}, alert at ${item.lowStockThreshold})`).join(' • ')}
              </span>
            </div>
          ) : null}
          <div className="toolbar-row search-box-wrap">
            <input
              placeholder="Search products by name, SKU, or category"
              value={productSearch}
              onChange={(e) => setProductSearch(e.target.value)}
              onFocus={() => setSearchFocused(true)}
              onBlur={() => window.setTimeout(() => setSearchFocused(false), 120)}
            />
            {searchFocused && searchSuggestions.length ? (
              <div className="autocomplete-menu">
                {searchSuggestions.map((product) => (
                  <button
                    key={product.id}
                    type="button"
                    className="autocomplete-item"
                    onClick={() => addToCart(product, 'search')}
                  >
                    <strong>{product.name}</strong>
                    <span>{product.sku} • {product.category} • {currency(product.sellingPrice)}</span>
                    {isLowStockProduct(product) ? <span className="inline-alert">Low stock: {product.quantity} left</span> : null}
                  </button>
                ))}
              </div>
            ) : null}
          </div>

          {searchTileProducts.length ? (
            <>
              <h4 className="subsection-title">Search matches</h4>
              <div className="product-grid compact-grid">
                {searchTileProducts.map((product) => (
                  <button
                    key={product.id}
                    className={`product-card spotlight-card ${cart.some((item) => item.productId === product.id) ? 'selected' : ''}`}
                    onClick={() => addToCart(product, 'search')}
                    disabled={getEditableStock(product) === 0}
                  >
                    <span className="product-badge">{product.category}</span>
                    {isLowStockProduct(product) ? <span className="product-alert-badge">Low stock</span> : null}
                    <div
                      className={`product-media ${product.imageDataUrl ? 'with-image' : product.category.toLowerCase()}`}
                      style={productVisualStyle(product)}
                    />
                    <strong>{product.name}</strong>
                    <em>{currency(product.sellingPrice)}</em>
                    <small>{productStockLabel(product)}</small>
                    {isLowStockProduct(product) ? <small className="stock-alert-text">Alert at {product.lowStockThreshold} • Act now</small> : null}
                  </button>
                ))}
              </div>
            </>
          ) : null}

          {searchPickedProducts.length ? (
            <>
              <h4 className="subsection-title">Picked from search</h4>
              <div className="product-grid compact-grid">
                {searchPickedProducts.map((product) => (
                  <button
                    key={product.id}
                    className={`product-card remembered-card ${cart.some((item) => item.productId === product.id) ? 'selected' : ''}`}
                    onClick={() => addToCart(product, 'search')}
                    disabled={getEditableStock(product) === 0}
                  >
                    <span className="product-badge">{product.category}</span>
                    <div
                      className={`product-media ${product.imageDataUrl ? 'with-image' : product.category.toLowerCase()}`}
                      style={productVisualStyle(product)}
                    />
                    <strong>{product.name}</strong>
                    <em>{currency(product.sellingPrice)}</em>
                    <small>{productStockLabel(product)}</small>
                    {isLowStockProduct(product) ? <small className="stock-alert-text">Alert at {product.lowStockThreshold} • Restock soon</small> : null}
                  </button>
                ))}
              </div>
            </>
          ) : null}

          <h4 className="subsection-title">Trending quick picks</h4>
          <div className="product-grid">
            {trendingProducts.map((product) => (
              <button
                key={product.id}
                className={`product-card ${cart.some((item) => item.productId === product.id) ? 'selected' : ''}`}
                onClick={() => addToCart(product)}
                disabled={getEditableStock(product) === 0}
              >
                <span className="product-badge">{product.category}</span>
                {isLowStockProduct(product) ? <span className="product-alert-badge">Low stock</span> : null}
                <div
                  className={`product-media ${product.imageDataUrl ? 'with-image' : product.category.toLowerCase()}`}
                  style={productVisualStyle(product)}
                />
                <strong>{product.name}</strong>
                <em>{currency(product.sellingPrice)}</em>
                <small>{productStockLabel(product)}</small>
                {isLowStockProduct(product) ? <small className="stock-alert-text">Alert at {product.lowStockThreshold} • Restock soon</small> : null}
              </button>
            ))}
          </div>
        </Panel>

        <Panel
          title="Find Invoice To Edit"
          subtitle={editingInvoiceId
            ? 'An invoice is already loaded into checkout. Search here any time to replace it with another bill.'
            : 'Search by customer name, review matching invoices, and load the right bill back into checkout.'}
        >
          <div className="invoice-search-panel">
            <div className="search-box-wrap">
              <label className="input-label" htmlFor="invoice-search-name">Customer name</label>
              <input
                id="invoice-search-name"
                placeholder="Search customer name for invoice correction"
                value={invoiceSearchName}
                onChange={(e) => setInvoiceSearchName(e.target.value)}
                autoComplete="off"
              />
              <p className="field-note">Pick the invoice below and the cart, customer, payment mode, and coupon will load into billing automatically.</p>
            </div>
            <label className="date-field">
              <span>From date</span>
              <input
                type="date"
                max={today}
                value={invoiceSearchFromDate}
                onChange={(e) => setInvoiceSearchFromDate(e.target.value)}
              />
            </label>
          </div>

          {invoiceSearchName.trim() ? (
            <DataTable
              columns={[
                { key: 'invoiceNumber', label: 'Invoice' },
                { key: 'customerName', label: 'Customer' },
                { key: 'customerMobile', label: 'Mobile' },
                { key: 'createdAt', label: 'Date', render: (row) => formatDate(row.createdAt) },
                { key: 'finalAmount', label: 'Amount', render: (row) => currency(row.finalAmount) },
                {
                  key: 'load',
                  label: 'Action',
                  render: (row) => (
                    <button
                      className="ghost-btn compact-btn"
                      type="button"
                      onClick={() => loadInvoiceForEdit(row)}
                    >
                      Edit
                    </button>
                  )
                }
              ]}
              rows={invoiceSuggestions.invoices || []}
              emptyMessage="No matching invoices found for this customer and date."
              pagination={invoiceSuggestions}
              onPageChange={loadInvoiceSuggestionPage}
            />
          ) : null}
        </Panel>
        </div>

        <Panel title="Checkout" subtitle="Customer mobile is mandatory and old data is preserved through new invoices.">
          <form className="form-grid" onSubmit={handleSubmit}>
            <div className="search-box-wrap">
              <label className="input-label" htmlFor="billing-customer-name">Customer name</label>
              <input
                id="billing-customer-name"
                placeholder="Type customer name to autofill existing customer"
                value={form.customerName}
                onChange={(e) => setForm({ ...form, customerName: e.target.value })}
                onFocus={() => setCustomerSearchFocused(true)}
                onBlur={() => window.setTimeout(() => setCustomerSearchFocused(false), 120)}
                autoComplete="off"
                required
              />
              <p className="field-note">Start typing and choose an existing customer to fill mobile number and billing details automatically.</p>
              {customerSearchFocused && customerSuggestions.length ? (
                <div className="autocomplete-menu">
                  {customerSuggestions.map((customer) => (
                    <button
                      key={customer.id}
                      type="button"
                      className="autocomplete-item"
                      onClick={() => selectCustomer(customer)}
                    >
                      <strong>{customer.name}</strong>
                      <span>Mobile: {customer.mobile}</span>
                    </button>
                  ))}
                </div>
              ) : null}
            </div>
            <label className="input-label" htmlFor="billing-customer-mobile">Customer mobile number</label>
            <input
              id="billing-customer-mobile"
              placeholder="Customer mobile"
              value={form.customerMobile}
              onChange={(e) => setForm({ ...form, customerMobile: e.target.value })}
              inputMode="numeric"
              autoComplete="off"
              required
            />
            {customerSnapshot ? (
              <div className="customer-card">
                <strong>Existing customer found</strong>
                <span>Name: {customerSnapshot.name}</span>
                <span>Total visits: {customerSnapshot.totalInvoices}</span>
                <span>Total spent: {currency(customerSnapshot.totalSpent)}</span>
                <span>Last purchase: {customerSnapshot.lastPurchaseAt ? formatDate(customerSnapshot.lastPurchaseAt) : 'N/A'}</span>
              </div>
            ) : null}
            <label className="input-label" htmlFor="billing-payment-mode">Payment method</label>
            <select
              id="billing-payment-mode"
              value={form.paymentMode}
              onChange={(e) => setForm({ ...form, paymentMode: e.target.value })}
            >
              <option value="CASH">Cash</option>
              <option value="CARD">Card</option>
              <option value="UPI">UPI</option>
            </select>
            <div className="label-with-note">
              <span className="input-label">Coupon selection</span>
              <span className="field-note inline-field-note">Choose a valid coupon for the current cart. Coupon and custom discount cannot be combined.</span>
            </div>
            <select
              value={form.couponCode}
              onChange={(e) => setForm((current) => ({
                ...current,
                couponCode: e.target.value,
                manualDiscount: e.target.value ? 0 : current.manualDiscount
              }))}
              disabled={!cart.length}
            >
              <option value="">No coupon</option>
              {validCouponOffers.map((offer) => (
                <option key={offer.id} value={offer.couponCode}>
                  {offer.couponCode} · {offer.name}
                </option>
              ))}
            </select>
            <div className="label-with-note">
              <span className="input-label">Custom discount type</span>
              <span className="field-note inline-field-note">
                {couponSelected
                  ? 'Custom discount is locked because a coupon is selected.'
                  : 'Choose whether the discount value is a fixed amount or a percentage.'}
              </span>
            </div>
            <div className="discount-mode-toggle" role="group" aria-label="Custom discount type">
              <button
                type="button"
                className={form.manualDiscountType === 'FIXED' ? 'toggle-active' : ''}
                onClick={() => setForm({ ...form, manualDiscountType: 'FIXED' })}
                disabled={couponSelected}
              >
                Fixed
              </button>
              <button
                type="button"
                className={form.manualDiscountType === 'PERCENT' ? 'toggle-active' : ''}
                onClick={() => setForm({ ...form, manualDiscountType: 'PERCENT' })}
                disabled={couponSelected}
              >
                Percent
              </button>
            </div>
            <label className="input-label" htmlFor="billing-custom-discount">
              {form.manualDiscountType === 'PERCENT' ? 'Custom discount percentage' : 'Custom discount amount'}
            </label>
            <input
              id="billing-custom-discount"
              type="number"
              min="0"
              step="0.01"
              max={form.manualDiscountType === 'PERCENT' ? '100' : undefined}
              placeholder={form.manualDiscountType === 'PERCENT' ? 'Custom bill discount (%)' : 'Custom bill discount'}
              value={form.manualDiscount}
              onChange={(e) => setForm({ ...form, manualDiscount: e.target.value })}
              disabled={couponSelected}
            />
            {cart.length ? (
              <div className="discount-preview">
                <strong>Bill summary before save</strong>
                <span>Total bill: <strong>{currency(previewTotalAmount)}</strong></span>
                <span>{couponSelected ? `Coupon (${form.couponCode})` : 'Offer discount'}: <strong>{currency(autoOrCouponDiscount || offerDiscount)}</strong></span>
                <span>
                  Custom discount{form.manualDiscountType === 'PERCENT' ? ` (${Number(form.manualDiscount || 0)}%)` : ''}: <strong>{currency(customDiscount)}</strong>
                </span>
                <span>Total discount: <strong>{currency(previewTotalDiscount)}</strong></span>
                <span>Discounted bill: <strong>{currency(previewFinalAmount)}</strong></span>
              </div>
            ) : null}

            <DataTable
              columns={[
                {
                  key: 'name',
                  label: 'Product',
                  render: (row) => (
                    <div className="cart-product-cell">
                      <strong>{row.name}</strong>
                      {isLowStockCartItem(row) ? (
                        <small className="stock-alert-text">
                          Low stock. On shelf: {row.currentStock}, after bill: {remainingStockAfterEdit(row)}, alert at {row.lowStockThreshold}
                        </small>
                      ) : (
                        <small>
                          {editingInvoiceId && row.originalQuantity > 0
                            ? `Available in edit: ${row.stock} (${row.currentStock} on shelf + ${row.originalQuantity} already billed)`
                            : `In stock: ${row.currentStock}`}
                        </small>
                      )}
                    </div>
                  )
                },
                { key: 'price', label: 'Price', render: (row) => currency(row.price) },
                {
                  key: 'quantity',
                  label: 'Qty',
                  render: (row) => (
                    <input
                      className="quantity-input"
                      type="number"
                      min="1"
                      max={row.stock}
                      value={row.quantity}
                      onChange={(e) => updateQuantity(row.productId, e.target.value)}
                    />
                  )
                },
                {
                  key: 'offerDiscount',
                  label: 'Offer Discount',
                  render: (row) => {
                    const previewRow = previewInvoice?.items?.find((item) => item.productId === row.productId);
                    return currency(previewRow?.discount || 0);
                  }
                },
                {
                  key: 'lineTotal',
                  label: 'Net Line',
                  render: (row) => {
                    const previewRow = previewInvoice?.items?.find((item) => item.productId === row.productId);
                    return currency(previewRow?.lineTotal ?? row.price * row.quantity);
                  }
                },
                {
                  key: 'remove',
                  label: '',
                  render: (row) => (
                    <button
                      className="ghost-btn"
                      type="button"
                      onClick={() => setCart((current) => current.filter((item) => item.productId !== row.productId))}
                    >
                      Remove
                    </button>
                  )
                }
              ]}
              rows={cart}
              emptyMessage="Add products to start a bill."
            />

            {error ? <p className="error-text">{error}</p> : null}
            {success ? <p className="success-text">{success}</p> : null}
            <div className="checkout-actions">
              <button className="primary-btn" type="submit" disabled={!cart.length || submitting || submittingAndPrinting}>
                {submitting ? 'Saving...' : editingInvoiceId ? 'Update Invoice' : 'Generate Bill'}
              </button>
              <button
                className="ghost-btn"
                type="button"
                disabled={!cart.length || submitting || submittingAndPrinting}
                onClick={handleGenerateAndPrint}
              >
                {submittingAndPrinting ? 'Saving & Printing...' : editingInvoiceId ? 'Update & Print' : 'Generate & Print'}
              </button>
            </div>
          </form>
        </Panel>
      </div>

      {invoice ? (
        <div ref={latestInvoiceRef}>
          <Panel title="Latest invoice" subtitle="The final amount reflects the best non-stacked offer plus any manual discount.">
          <div className="invoice-summary">
            <MetricCard label="Invoice No." value={invoice.invoiceNumber} />
            <MetricCard label="Customer" value={invoice.customerName} />
            <MetricCard label="Final Amount" value={currency(invoice.finalAmount)} tone="accent" />
            <MetricCard label="Created" value={formatDate(invoice.createdAt)} />
          </div>
          <div className="toolbar-row">
            <button className="ghost-btn" type="button" onClick={handlePrint}>
              Print Receipt
            </button>
          </div>
          <DataTable
            columns={[
              { key: 'productName', label: 'Product' },
              { key: 'quantity', label: 'Qty' },
              { key: 'unitPrice', label: 'Unit Price', render: (row) => currency(row.unitPrice) },
              { key: 'discount', label: 'Discount', render: (row) => currency(row.discount) },
              { key: 'lineTotal', label: 'Net Line', render: (row) => currency(row.lineTotal) }
            ]}
            rows={invoice.items || []}
          />
          </Panel>
        </div>
      ) : null}
    </div>
  );
}
