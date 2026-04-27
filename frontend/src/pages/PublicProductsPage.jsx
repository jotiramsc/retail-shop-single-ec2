import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { retailService } from '../services/retailService';
import { currency } from '../utils/format';
import {
  clearCustomerSession,
  getStoredCustomerSession,
  isCustomerAuthError
} from '../utils/auth';
import { addGuestCartItem, getGuestCartCount } from '../utils/cart';
import StorefrontHeader from '../components/StorefrontHeader';

const templateImages = {
  logo: '/assets/glowjewels/app_logo.png',
  hero: '/assets/glowjewels/maharashtrian_bridal_jewelry_hero.png',
  fullLook: '/assets/glowjewels/maharashtrian_lady_full_jewelry.png',
  flatlay: '/assets/glowjewels/maharashtrian_jewelry_flatlay.png',
  bangles: '/assets/glowjewels/maharashtrian_bangdi_bangles.png',
  earrings: '/assets/glowjewels/maharashtrian_chandrakor_earrings.png',
  necklace: '/assets/glowjewels/maharashtrian_necklace_kolhapuri.png',
  empty: '/assets/glowjewels/no_image.png'
};

function titleCaseCategory(category) {
  if (!category) {
    return 'Jewellery';
  }

  return String(category)
    .toLowerCase()
    .split(/[_\s]+/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function normalizeId(value) {
  return String(value || 'all')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
}

function productImage(product, fallback) {
  return product?.imageDataUrl || fallback || templateImages.empty;
}

function productFallback(index) {
  const fallbacks = [
    templateImages.flatlay,
    templateImages.fullLook,
    templateImages.bangles,
    templateImages.earrings,
    templateImages.necklace
  ];
  return fallbacks[index % fallbacks.length];
}

function buildPublicCategories(categoryOptions, products) {
  const byId = new Map();

  (categoryOptions || []).forEach((option) => {
    const name = titleCaseCategory(option.displayName || option.code);
    const id = normalizeId(name);
    if (!byId.has(id)) {
      byId.set(id, { id, name });
    }
  });

  products.forEach((product) => {
    const name = titleCaseCategory(product.category);
    const id = normalizeId(name);
    if (!byId.has(id)) {
      byId.set(id, { id, name });
    }
  });

  return [{ id: 'all', name: 'All' }, ...Array.from(byId.values())];
}

function priceRangeLabel(price) {
  if (price < 500) return 'Under Rs. 500';
  if (price <= 1000) return 'Rs. 500 - Rs. 1,000';
  if (price <= 2000) return 'Rs. 1,000 - Rs. 2,000';
  return 'Above Rs. 2,000';
}

export default function PublicProductsPage({ branding }) {
  const [searchParams, setSearchParams] = useSearchParams();
  const [products, setProducts] = useState([]);
  const [categoryOptions, setCategoryOptions] = useState([]);
  const [searchQuery, setSearchQuery] = useState(searchParams.get('q') || '');
  const [activeSort, setActiveSort] = useState('Newest');
  const [activePriceRange, setActivePriceRange] = useState('All Prices');
  const [error, setError] = useState('');
  const [cartMessage, setCartMessage] = useState('');
  const [cartCount, setCartCount] = useState(0);
  const [motionReady, setMotionReady] = useState(false);
  const [customerSession, setCustomerSession] = useState(() => getStoredCustomerSession());

  useEffect(() => {
    Promise.all([
      retailService.getPublicCatalog(),
      retailService.getProductCategoryOptions()
    ])
      .then(([catalog, options]) => {
        setProducts(catalog || []);
        setCategoryOptions(options || []);
      })
      .catch(() => {
        setError('Unable to load the collection right now.');
      });
  }, []);

  useEffect(() => {
    if (customerSession?.token) {
      retailService.getCart()
        .then((cart) => {
          setCartCount((cart.items || []).reduce((total, item) => total + Number(item.quantity || 0), 0));
        })
        .catch((err) => {
          if (isCustomerAuthError(err)) {
            clearCustomerSession();
            setCustomerSession(null);
            setCartCount(getGuestCartCount());
            return;
          }
          setCartCount(0);
        });
      return;
    }

    setCartCount(getGuestCartCount());
  }, [customerSession?.token]);

  useEffect(() => {
    const timer = window.setTimeout(() => setMotionReady(true), 120);
    return () => window.clearTimeout(timer);
  }, []);

  const categories = useMemo(
    () => buildPublicCategories(categoryOptions, products),
    [categoryOptions, products]
  );
  const headerLinks = useMemo(
    () => [
      { to: '/', label: 'Home' },
      { to: '/products', label: 'Collections' },
      ...categories.slice(1, 3).map((category) => ({
        to: `/products?category=${category.id}`,
        label: category.name
      }))
    ],
    [categories]
  );

  const activeCategory = searchParams.get('category') || 'all';

  const filteredProducts = useMemo(() => {
    return products
      .filter((product) => {
        const categoryId = normalizeId(titleCaseCategory(product.category));
        const matchesCategory = activeCategory === 'all' || categoryId === activeCategory;
        const matchesSearch = !searchQuery.trim()
          || `${product.name} ${product.sku} ${product.category}`.toLowerCase().includes(searchQuery.trim().toLowerCase());
        const matchesPrice = activePriceRange === 'All Prices'
          || priceRangeLabel(Number(product.sellingPrice || 0)) === activePriceRange;

        return matchesCategory && matchesSearch && matchesPrice;
      })
      .sort((left, right) => {
        if (activeSort === 'Price: Low to High') {
          return Number(left.sellingPrice || 0) - Number(right.sellingPrice || 0);
        }

        if (activeSort === 'Price: High to Low') {
          return Number(right.sellingPrice || 0) - Number(left.sellingPrice || 0);
        }

        if (activeSort === 'Name') {
          return left.name.localeCompare(right.name);
        }

        return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
      });
  }, [products, activeCategory, searchQuery, activePriceRange, activeSort]);

  const heroTitle = activeCategory === 'all'
    ? 'All Collections'
    : `${categories.find((item) => item.id === activeCategory)?.name || 'Collection'} Collection`;

  const priceRanges = [
    'All Prices',
    'Under Rs. 500',
    'Rs. 500 - Rs. 1,000',
    'Rs. 1,000 - Rs. 2,000',
    'Above Rs. 2,000'
  ];

  const shopName = branding.shopName || 'GlowJewels';
  const logo = branding.media?.logo || templateImages.logo;
  const heroImage = branding.media?.heroPrimary || templateImages.hero;
  const pageClassName = motionReady ? 'glow-site grid-lines glow-motion-ready' : 'glow-site grid-lines';

  const updateCategory = (id) => {
    const next = new URLSearchParams(searchParams);
    if (id === 'all') {
      next.delete('category');
    } else {
      next.set('category', id);
    }
    setSearchParams(next);
  };

  const addToCart = async (product) => {
    setError('');
    setCartMessage('');
    if (!product.inStock) {
      setError(`${product.name} is currently out of stock.`);
      return;
    }
    try {
      if (customerSession?.token) {
        try {
          const cart = await retailService.addToCart({ productId: product.id, quantity: 1 });
          setCartCount((cart.items || []).reduce((total, item) => total + Number(item.quantity || 0), 0));
        } catch (err) {
          if (!isCustomerAuthError(err)) {
            throw err;
          }
          clearCustomerSession();
          setCustomerSession(null);
          const items = addGuestCartItem(product.id, 1);
          setCartCount(items.reduce((total, item) => total + Number(item.quantity || 0), 0));
          setCartMessage(`${product.name} added to guest cart. Customer session expired, so please log in again at checkout.`);
          return;
        }
      } else {
        const items = addGuestCartItem(product.id, 1);
        setCartCount(items.reduce((total, item) => total + Number(item.quantity || 0), 0));
      }
      setCartMessage(`${product.name} added to cart.`);
    } catch (err) {
      setError(err.response?.data?.message || 'Unable to add product to cart.');
    }
  };

  return (
    <main className={pageClassName}>
      <StorefrontHeader logo={logo} shopName={shopName} navLinks={headerLinks} cartCount={cartCount} />

      <section className="glow-products-hero">
        <div className="glow-products-hero-copy">
          <nav className="glow-products-breadcrumb glow-reveal glow-reveal-delay-1">
            <Link to="/">Home</Link>
            <span>›</span>
            <span>Collections</span>
          </nav>

          <span className="glow-kicker glow-reveal glow-reveal-delay-2">{filteredProducts.length} Pieces in collection</span>
          <h1 className="editorial-text glow-products-title glow-reveal glow-reveal-delay-3">
            ALL
            <br />
            <span className="text-outline">{heroTitle}</span>
          </h1>
          <p className="glow-products-subtitle glow-reveal glow-reveal-delay-4">
            Browse the full storefront with live backend products, dynamic categories, and the template-style filter flow.
          </p>
        </div>

        <div className="glow-products-hero-image shimmer-border glow-reveal glow-reveal-delay-4">
          <img src={heroImage} alt={heroTitle} />
        </div>
      </section>

      <section className="glow-products-layout">
        <aside className="glow-products-sidebar">
          <div className="glow-products-filter-group">
            <h3>Category</h3>
            {categories.map((category) => (
              <button
                key={category.id}
                type="button"
                onClick={() => updateCategory(category.id)}
                className={activeCategory === category.id ? 'is-active' : ''}
              >
                {category.name}
              </button>
            ))}
          </div>

          <div className="glow-products-filter-group">
            <h3>Price Range</h3>
            {priceRanges.map((range) => (
              <button
                key={range}
                type="button"
                onClick={() => setActivePriceRange(range)}
                className={activePriceRange === range ? 'is-active' : ''}
              >
                {range}
              </button>
            ))}
          </div>
        </aside>

        <div className="glow-products-main">
          <div className="glow-products-toolbar glow-reveal glow-reveal-delay-2">
            <div className="glow-products-search">
              <input
                type="text"
                value={searchQuery}
                onChange={(event) => setSearchQuery(event.target.value)}
                placeholder="Search collection"
              />
            </div>

            <div className="glow-products-toolbar-meta">
              <select value={activeSort} onChange={(event) => setActiveSort(event.target.value)}>
                <option>Newest</option>
                <option>Name</option>
                <option>Price: Low to High</option>
                <option>Price: High to Low</option>
              </select>
              <span>{filteredProducts.length} results</span>
            </div>
          </div>

          {(activeCategory !== 'all' || activePriceRange !== 'All Prices' || searchQuery.trim()) ? (
            <div className="glow-products-active-filters">
              {activeCategory !== 'all' ? (
                <button type="button" onClick={() => updateCategory('all')}>
                  {categories.find((item) => item.id === activeCategory)?.name} ×
                </button>
              ) : null}
              {activePriceRange !== 'All Prices' ? (
                <button type="button" onClick={() => setActivePriceRange('All Prices')}>
                  {activePriceRange} ×
                </button>
              ) : null}
              {searchQuery.trim() ? (
                <button type="button" onClick={() => setSearchQuery('')}>
                  Search: {searchQuery} ×
                </button>
              ) : null}
            </div>
          ) : null}

          {error ? <p className="error-text">{error}</p> : null}
          {cartMessage ? <p className="success-text">{cartMessage}</p> : null}

          <div className="glow-products-grid">
            {filteredProducts.map((product, index) => (
              <article
                key={product.id}
                className="glow-product-card-template glow-reveal"
                style={{ transitionDelay: `${Math.min(index * 0.05, 0.45)}s` }}
              >
                <div className="glow-product-card-media">
                  <img
                    src={productImage(product, productFallback(index))}
                    alt={product.name}
                  />
                  {product.showInNewRelease ? <span className="glow-product-badge">New</span> : null}
                </div>
                <div className="glow-product-card-copy">
                  <p>{titleCaseCategory(product.category)}</p>
                  <h3>{product.name}</h3>
                  <div className="glow-product-card-meta">
                    <strong>{currency(product.sellingPrice)}</strong>
                    <span>{product.sku}</span>
                  </div>
                  <div className="glow-product-card-actions">
                    <button
                      type="button"
                      className="primary-btn compact-btn"
                      onClick={() => addToCart(product)}
                      disabled={!product.inStock}
                    >
                      {product.inStock ? 'Add to cart' : 'Out of stock'}
                    </button>
                    <span>{product.stockLabel || (product.inStock ? 'Available now' : 'Out of stock')}</span>
                  </div>
                </div>
              </article>
            ))}
          </div>

          {!filteredProducts.length ? (
            <div className="glow-products-empty">
              <strong>No products found</strong>
              <span>Try changing the category, search, or price range.</span>
            </div>
          ) : null}
        </div>
      </section>

      <footer className="glow-footer glow-footer-compact">
        <div>
          <div className="glow-brand glow-footer-brand">
            <img src={logo} alt={`${shopName} logo`} className="glow-brand-logo" />
            <div>
              <span className="glow-kicker">Customer storefront</span>
              <strong>{shopName}</strong>
            </div>
          </div>
          <p>{branding.contact?.address || 'Update store address from receipt settings.'}</p>
        </div>
        <div className="glow-footer-links glow-footer-links-compact">
          <Link to="/">Home</Link>
          <Link to="/cart">Cart</Link>
          <Link to="/login">Staff Login</Link>
        </div>
      </footer>
    </main>
  );
}
