import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { retailService } from '../services/retailService';
import { currency } from '../utils/format';
import {
  clearCustomerSession,
  getStoredCustomerSession,
  isCustomerAuthError
} from '../utils/auth';
import { defaultBranding } from '../utils/branding';
import { addGuestCartItem, getGuestCartCount } from '../utils/cart';
import { storeCheckoutCouponCode } from '../utils/checkout';
import { getApiErrorMessage } from '../utils/validation';
import { trackMetaEvent } from '../utils/metaPixel';
import { applySeo, preloadImage } from '../utils/seo';
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
  return product?.productImages?.[0] || product?.imageDataUrl || fallback || templateImages.empty;
}

function productImages(product, fallback) {
  const images = product?.productImages?.length ? product.productImages : (product?.imageDataUrl ? [product.imageDataUrl] : []);
  return images.length ? images : [fallback || templateImages.empty];
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

function finalProductPrice(product) {
  return Number(product?.offerPrice ?? product?.websitePrice ?? product?.sellingPrice ?? 0);
}

function ProductDealPrice({ product, compact = false }) {
  const originalPrice = Number(product?.originalPrice ?? product?.websitePrice ?? product?.sellingPrice ?? 0);
  const finalPrice = finalProductPrice(product);
  const hasDiscount = Number(product?.youSave || 0) > 0 && originalPrice > finalPrice;
  const discountPercent = Number(product?.discountPercent || 0);

  return (
    <div className={`product-deal-price ${compact ? 'compact' : ''}`}>
      {hasDiscount ? (
        <div className="deal-price-line">
          <span className="deal-original">{currency(originalPrice)}</span>
          {discountPercent > 0 ? <span className="deal-badge">{discountPercent}% OFF</span> : null}
        </div>
      ) : null}
      <strong>{currency(finalPrice)}</strong>
    </div>
  );
}

function HeartIcon({ filled = false }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className="glow-icon-svg">
      <path
        d="M12 20.3 4.9 13.7a4.7 4.7 0 0 1 6.6-6.7l.5.5.5-.5a4.7 4.7 0 1 1 6.6 6.7L12 20.3Z"
        fill={filled ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="1.8"
      />
    </svg>
  );
}

function buildPublicCategories(categoryOptions, products) {
  const byId = new Map();

  (Array.isArray(categoryOptions) ? categoryOptions : []).forEach((option) => {
    const name = titleCaseCategory(option.displayName || option.code);
    const id = normalizeId(name);
    if (!byId.has(id)) {
      byId.set(id, { id, name, iconImageUrl: option.iconImageUrl || '' });
      return;
    }
    const current = byId.get(id);
    if (!current.iconImageUrl && option.iconImageUrl) {
      byId.set(id, { ...current, iconImageUrl: option.iconImageUrl });
    }
  });

  (Array.isArray(products) ? products : []).forEach((product) => {
    const name = titleCaseCategory(product.category);
    const id = normalizeId(name);
    if (!byId.has(id)) {
      byId.set(id, { id, name, iconImageUrl: '' });
    }
  });

  return [{ id: 'all', name: 'All' }, ...Array.from(byId.values())];
}

function categoryIdsForProduct(product, categoryOptions) {
  const ids = new Set([normalizeId(titleCaseCategory(product.category)), normalizeId(product.category)]);
  const productCategory = normalizeId(product.category);
  (Array.isArray(categoryOptions) ? categoryOptions : []).forEach((option) => {
    const code = normalizeId(option.code);
    const display = normalizeId(option.displayName || option.code);
    if (productCategory === code || productCategory === display) {
      ids.add(code);
      ids.add(display);
    }
    if (display && productCategory.includes(display)) {
      ids.add(code);
      ids.add(display);
    }
  });
  return ids;
}

function priceRangeLabel(price) {
  if (price < 500) return 'Under Rs. 500';
  if (price <= 1000) return 'Rs. 500 - Rs. 1,000';
  if (price <= 2000) return 'Rs. 1,000 - Rs. 2,000';
  return 'Above Rs. 2,000';
}

function detailPath(product) {
  return `/products/${product.id}`;
}

function ProductCardImageCarousel({ product, fallback }) {
  const [activeIndex, setActiveIndex] = useState(0);
  const touchStartXRef = useRef(null);
  const images = productImages(product, fallback);
  const activeImage = images[Math.min(activeIndex, images.length - 1)] || fallback || templateImages.empty;
  const hasMultipleImages = images.length > 1;

  useEffect(() => {
    setActiveIndex(0);
  }, [product.id]);

  const moveImage = (event, direction) => {
    event.preventDefault();
    event.stopPropagation();
    setActiveIndex((current) => {
      const next = current + direction;
      if (next < 0) {
        return images.length - 1;
      }
      if (next >= images.length) {
        return 0;
      }
      return next;
    });
  };

  const handleTouchStart = (event) => {
    if (!hasMultipleImages) {
      return;
    }
    touchStartXRef.current = event.touches?.[0]?.clientX ?? null;
  };

  const handleTouchEnd = (event) => {
    if (!hasMultipleImages || touchStartXRef.current == null) {
      return;
    }
    const endX = event.changedTouches?.[0]?.clientX ?? touchStartXRef.current;
    const delta = endX - touchStartXRef.current;
    touchStartXRef.current = null;
    if (Math.abs(delta) < 32) {
      return;
    }
    setActiveIndex((current) => {
      if (delta < 0) {
        return current + 1 >= images.length ? 0 : current + 1;
      }
      return current - 1 < 0 ? images.length - 1 : current - 1;
    });
  };

  return (
    <div className="glow-product-card-media" onTouchStart={handleTouchStart} onTouchEnd={handleTouchEnd}>
      <img key={activeImage} src={activeImage} alt={product.name} loading="lazy" decoding="async" />
      {hasMultipleImages ? (
        <>
          <button
            type="button"
            className="product-card-image-arrow is-left"
            onClick={(event) => moveImage(event, -1)}
            aria-label="Previous product image"
          >
            ‹
          </button>
          <button
            type="button"
            className="product-card-image-arrow is-right"
            onClick={(event) => moveImage(event, 1)}
            aria-label="Next product image"
          >
            ›
          </button>
        </>
      ) : null}
      {hasMultipleImages ? (
        <div className="product-card-image-dots" aria-label={`${product.name} image selector`}>
          {images.slice(0, 5).map((image, index) => (
            <button
              key={`${image}-${index}`}
              type="button"
              className={index === activeIndex ? 'is-active' : ''}
              onClick={(event) => {
                event.preventDefault();
                event.stopPropagation();
                setActiveIndex(index);
              }}
              aria-label={`Show image ${index + 1}`}
            />
          ))}
        </div>
      ) : null}
    </div>
  );
}

export default function PublicProductsPage({ branding }) {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const autoAddHandledRef = useRef('');
  const [products, setProducts] = useState([]);
  const [categoryOptions, setCategoryOptions] = useState([]);
  const [searchQuery, setSearchQuery] = useState(searchParams.get('q') || '');
  const [activeSort, setActiveSort] = useState('Newest');
  const [activePriceRange, setActivePriceRange] = useState('All Prices');
  const [error, setError] = useState('');
  const [cartMessage, setCartMessage] = useState('');
  const [cartCount, setCartCount] = useState(0);
  const [wishlistCount, setWishlistCount] = useState(0);
  const [wishlistProductIds, setWishlistProductIds] = useState(() => new Set());
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
      Promise.all([
        retailService.getCart(),
        retailService.getWishlist()
      ])
        .then(([cart, wishlist]) => {
          setCartCount((cart.items || []).reduce((total, item) => total + Number(item.quantity || 0), 0));
          setWishlistCount((wishlist || []).length);
          setWishlistProductIds(new Set((wishlist || []).map((item) => String(item.productId))));
        })
        .catch((err) => {
          if (isCustomerAuthError(err)) {
            clearCustomerSession();
            setCustomerSession(null);
            setCartCount(getGuestCartCount());
            setWishlistCount(0);
            setWishlistProductIds(new Set());
            return;
          }
          setCartCount(0);
          setWishlistCount(0);
          setWishlistProductIds(new Set());
        });
      return;
    }

    setCartCount(getGuestCartCount());
    setWishlistCount(0);
    setWishlistProductIds(new Set());
  }, [customerSession?.token]);

  useEffect(() => {
    if (!cartMessage) {
      return undefined;
    }
    const timer = window.setTimeout(() => setCartMessage(''), 4200);
    return () => window.clearTimeout(timer);
  }, [cartMessage]);

  useEffect(() => {
    if (!error) {
      return undefined;
    }
    const timer = window.setTimeout(() => setError(''), 4200);
    return () => window.clearTimeout(timer);
  }, [error]);

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
        label: category.name,
        iconImageUrl: category.iconImageUrl
      }))
    ],
    [categories]
  );

  const activeCategory = searchParams.get('category') || 'all';

  const filteredProducts = useMemo(() => {
    return products
      .filter((product) => {
        const categoryIds = categoryIdsForProduct(product, categoryOptions);
        const matchesCategory = activeCategory === 'all' || categoryIds.has(activeCategory);
        const matchesSearch = !searchQuery.trim()
          || `${product.name} ${product.sku} ${product.category}`.toLowerCase().includes(searchQuery.trim().toLowerCase());
        const matchesPrice = activePriceRange === 'All Prices'
          || priceRangeLabel(finalProductPrice(product)) === activePriceRange;

        return matchesCategory && matchesSearch && matchesPrice;
      })
      .sort((left, right) => {
        if (activeSort === 'Price: Low to High') {
          return finalProductPrice(left) - finalProductPrice(right);
        }

        if (activeSort === 'Price: High to Low') {
          return finalProductPrice(right) - finalProductPrice(left);
        }

        if (activeSort === 'Name') {
          return left.name.localeCompare(right.name);
        }

        return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
      });
  }, [products, categoryOptions, activeCategory, searchQuery, activePriceRange, activeSort]);

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

  const shopName = branding.shopName || defaultBranding.shopName;
  const logo = branding.media?.logo || templateImages.logo;
  const heroImage = branding.media?.heroPrimary || templateImages.hero;
  const pageClassName = 'glow-site grid-lines glow-motion-ready';
  const seoDescription = activeCategory === 'all'
    ? `Browse the full ${shopName} collection with live product availability, pearl jewellery, bangles, earrings, cosmetics, and festive store picks.`
    : `Browse ${heroTitle} at ${shopName} with live product availability, pricing, and ready-to-order collection pieces.`;

  useEffect(() => {
    applySeo({
      title: `${heroTitle} | ${shopName}`,
      description: seoDescription,
      path: window.location.pathname + window.location.search,
      image: heroImage,
      keywords: [shopName, heroTitle, 'collection', 'jewellery', 'cosmetics', 'online shopping'].join(', ')
    });
    preloadImage(heroImage, 'high');
  }, [heroImage, heroTitle, seoDescription, shopName]);

  useEffect(() => {
    if (!customerSession?.token) {
      return undefined;
    }
    const timeout = window.setTimeout(() => {
      const hasSearchContext = searchQuery.trim() || activeCategory !== 'all' || activePriceRange !== 'All Prices';
      if (!hasSearchContext) {
        return;
      }
      retailService.trackCustomerActivity({
        activityType: searchQuery.trim() ? 'SEARCH' : 'FILTER',
        searchKeyword: searchQuery.trim(),
        category: activeCategory !== 'all' ? categories.find((item) => item.id === activeCategory)?.name : '',
        filterUsed: activePriceRange !== 'All Prices' ? activePriceRange : '',
        priceRange: activePriceRange !== 'All Prices' ? activePriceRange : '',
        resultCount: filteredProducts.length,
        page: '/products'
      }).catch(() => {});
    }, 700);
    return () => window.clearTimeout(timeout);
  }, [activeCategory, activePriceRange, categories, customerSession?.token, searchQuery]);

  const updateCategory = (id) => {
    const next = new URLSearchParams(searchParams);
    if (id === 'all') {
      next.delete('category');
    } else {
      next.set('category', id);
    }
    setSearchParams(next);
  };

  const addToCart = useCallback(async (product, successMessage = `${product.name} added to cart.`) => {
    setError('');
    setCartMessage('');
    if (!product.inStock) {
      setError(`${product.name} is currently out of stock.`);
      return false;
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
          return true;
        }
      } else {
        const items = addGuestCartItem(product.id, 1);
        setCartCount(items.reduce((total, item) => total + Number(item.quantity || 0), 0));
      }
      trackMetaEvent(branding.metaPixelId, 'AddToCart', {
        content_ids: [product.sku || product.id],
        content_type: 'product',
        value: Number(product.offerPrice ?? product.sellingPrice ?? 0),
        currency: 'INR'
      });
      if (customerSession?.token) {
        retailService.trackCustomerActivity({
          activityType: 'ADD_TO_CART',
          productId: product.id,
          productName: product.name,
          category: product.category,
          page: '/products'
        }).catch(() => {});
      }
      setCartMessage(successMessage);
      return true;
    } catch (err) {
      setError(getApiErrorMessage(err, 'Unable to add product to cart. Please check stock and try again.'));
      return false;
    }
  }, [customerSession?.token]);

  const saveToWishlist = async (product) => {
    setError('');
    setCartMessage('');
    if (!customerSession?.token) {
      setError('Please login or sign up to save products in your wishlist.');
      navigate(`/customer-login?redirect=${encodeURIComponent('/products')}`);
      return;
    }
    try {
      const wishlist = await retailService.addToWishlist({ productId: product.id, quantity: 1 });
      setWishlistCount((wishlist || []).length);
      setWishlistProductIds(new Set((wishlist || []).map((item) => String(item.productId))));
      retailService.trackCustomerActivity({
        activityType: 'WISHLIST_ADD',
        productId: product.id,
        productName: product.name,
        category: product.category,
        page: '/products'
      }).catch(() => {});
      setCartMessage(`${product.name} added to wishlist.`);
    } catch (err) {
      if (isCustomerAuthError(err)) {
        clearCustomerSession();
        setCustomerSession(null);
        navigate(`/customer-login?redirect=${encodeURIComponent('/products')}`);
        return;
      }
      setError(getApiErrorMessage(err, 'Unable to save product to wishlist. Please try again.'));
    }
  };

  useEffect(() => {
    const productId = searchParams.get('autoAdd') || searchParams.get('productId');
    if (!productId || !products.length) {
      return undefined;
    }

    const redirectTarget = (searchParams.get('redirect') || '').trim().toLowerCase();
    const couponCode = searchParams.get('coupon') || '';
    const handledKey = [productId, redirectTarget, couponCode, customerSession?.token ? 'auth' : 'guest'].join(':');
    if (autoAddHandledRef.current === handledKey) {
      return undefined;
    }
    autoAddHandledRef.current = handledKey;

    if (couponCode) {
      storeCheckoutCouponCode(couponCode);
    }

    const product = products.find((item) => String(item.id) === String(productId));
    if (!product) {
      setError('The campaign product link is no longer available.');
      return undefined;
    }

    let cancelled = false;
    addToCart(product, `${product.name} added to cart from campaign link.`).then((added) => {
      if (cancelled || !added) {
        return;
      }

      if (redirectTarget === 'checkout') {
        navigate(customerSession?.token ? '/checkout' : '/customer-login?redirect=/checkout');
        return;
      }

      if (redirectTarget === 'cart') {
        navigate('/cart');
      }
    });

    return () => {
      cancelled = true;
    };
  }, [addToCart, customerSession?.token, navigate, products, searchParams]);

  return (
    <main className={pageClassName}>
      <StorefrontHeader
        logo={logo}
        shopName={shopName}
        navLinks={headerLinks}
        cartCount={cartCount}
        wishlistCount={wishlistCount}
      />

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
            Browse ready-to-order jewellery, cosmetics, festive picks, and daily-wear styles with current pricing and live stock visibility.
          </p>
        </div>

        <div className="glow-products-hero-image shimmer-border glow-reveal glow-reveal-delay-4">
          <img src={heroImage} alt={heroTitle} fetchPriority="high" loading="eager" decoding="async" />
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
                {category.iconImageUrl ? (
                  <span className="glow-category-icon" aria-hidden="true">
                    <img src={category.iconImageUrl} alt="" loading="lazy" decoding="async" />
                  </span>
                ) : null}
                <span>{category.name}</span>
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

          {error ? <p className="error-text storefront-feedback error" role="alert">{error}</p> : null}
          {cartMessage ? <p className="success-text storefront-feedback" role="status" aria-live="polite">{cartMessage}</p> : null}

          <div className="glow-products-grid">
            {filteredProducts.map((product, index) => (
              <article
                key={product.id}
                className="glow-product-card-template glow-reveal"
                style={{ transitionDelay: `${Math.min(index * 0.05, 0.45)}s` }}
              >
                <ProductCardImageCarousel
                  product={product}
                  fallback={productFallback(index)}
                />
                <div className="glow-product-card-copy">
                  <p>{titleCaseCategory(product.category)}</p>
                  <h3>
                    <Link
                      to={detailPath(product)}
                      onClick={() => {
                        if (customerSession?.token) {
                          retailService.trackCustomerActivity({
                            activityType: 'PRODUCT_CLICK',
                            productId: product.id,
                            productName: product.name,
                            clickedProduct: product.name,
                            category: product.category,
                            page: '/products'
                          }).catch(() => {});
                        }
                      }}
                    >
                      {product.name}
                    </Link>
                  </h3>
                  <div className="glow-product-card-meta">
                    <ProductDealPrice product={product} compact />
                    {product.inStock ? (
                      <span className="clean-stock-badge">{product.stockLabel || 'Available now'}</span>
                    ) : (
                      <span className="clean-stock-badge is-out">Out of stock</span>
                    )}
                  </div>
                  <div className="glow-product-card-actions">
                    <Link
                      className="ghost-btn compact-btn"
                      to={detailPath(product)}
                      onClick={() => {
                        if (customerSession?.token) {
                          retailService.trackCustomerActivity({
                            activityType: 'PRODUCT_CLICK',
                            productId: product.id,
                            productName: product.name,
                            clickedProduct: product.name,
                            category: product.category,
                            page: '/products'
                          }).catch(() => {});
                        }
                      }}
                    >
                      View details
                    </Link>
                    <button
                      type="button"
                      className="primary-btn compact-btn"
                      onClick={() => addToCart(product)}
                      disabled={!product.inStock}
                    >
                      {product.inStock ? 'Add to cart' : 'Out of stock'}
                    </button>
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
          <p>{branding.contact?.address || 'Update store address from brand configuration.'}</p>
        </div>
        <div className="glow-footer-links glow-footer-links-compact">
          <Link to="/">Home</Link>
          <Link to="/cart">Cart</Link>
          <Link to="/privacy-policy">Privacy Policy</Link>
          <Link to="/login">Staff Login</Link>
        </div>
      </footer>
    </main>
  );
}
