import { useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import StorefrontHeader from '../components/StorefrontHeader';
import { retailService } from '../services/retailService';
import { clearCustomerSession, getStoredCustomerSession, isCustomerAuthError } from '../utils/auth';
import { defaultBranding } from '../utils/branding';
import { addGuestCartItem, getGuestCartCount } from '../utils/cart';
import { storeCheckoutCouponCode } from '../utils/checkout';
import { currency } from '../utils/format';
import { applySeo, preloadImage } from '../utils/seo';
import { getApiErrorMessage } from '../utils/validation';

const fallbackImages = [
  '/assets/glowjewels/maharashtrian_jewelry_flatlay.png',
  '/assets/glowjewels/maharashtrian_lady_full_jewelry.png',
  '/assets/glowjewels/maharashtrian_bangdi_bangles.png'
];

function titleCaseCategory(category) {
  return String(category || 'Jewellery')
    .toLowerCase()
    .split(/[_\s]+/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

function productImage(product) {
  return product?.imageDataUrl || fallbackImages[0];
}

function isUuid(value) {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(String(value || ''));
}

function campaignSessionId() {
  const key = 'krishnai_campaign_session_id';
  const existing = window.localStorage.getItem(key);
  if (existing) return existing;
  const generated = window.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  window.localStorage.setItem(key, generated);
  return generated;
}

export default function PublicProductDetailPage({ branding }) {
  const { productId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const [product, setProduct] = useState(null);
  const [related, setRelated] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [cartCount, setCartCount] = useState(0);
  const [wishlistCount, setWishlistCount] = useState(0);
  const [customerSession, setCustomerSession] = useState(() => getStoredCustomerSession());

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    Promise.all([
      retailService.getPublicProduct(productId),
      retailService.getPublicCatalog()
    ])
      .then(([detail, catalog]) => {
        if (cancelled) return;
        if (!detail || typeof detail !== 'object' || !detail.id) {
          throw new Error('Invalid product response');
        }
        setProduct(detail);
        const safeCatalog = Array.isArray(catalog) ? catalog : [];
        setRelated(safeCatalog.filter((item) => item.id !== detail.id && item.category === detail.category).slice(0, 4));
        setError('');
      })
      .catch(() => {
        if (!cancelled) setError('Product is not available right now.');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [productId]);

  useEffect(() => {
    const campaignId = searchParams.get('campaignId');
    const source = (searchParams.get('source') || '').toLowerCase();
    if (!isUuid(campaignId) || !['facebook', 'instagram', 'whatsapp', 'direct'].includes(source) || !isUuid(productId)) {
      return;
    }
    retailService.recordCampaignLeadVisit({
      campaignId,
      source,
      productId,
      sessionId: campaignSessionId(),
      timestamp: new Date().toISOString()
    }).catch(() => {});
  }, [productId, searchParams]);

  useEffect(() => {
    if (customerSession?.token) {
      Promise.all([retailService.getCart(), retailService.getWishlist()])
        .then(([cart, wishlist]) => {
          setCartCount((cart.items || []).reduce((total, item) => total + Number(item.quantity || 0), 0));
          setWishlistCount((wishlist || []).length);
        })
        .catch((err) => {
          if (isCustomerAuthError(err)) {
            clearCustomerSession();
            setCustomerSession(null);
            setCartCount(getGuestCartCount());
            setWishlistCount(0);
          }
        });
      return;
    }
    setCartCount(getGuestCartCount());
    setWishlistCount(0);
  }, [customerSession?.token]);

  useEffect(() => {
    if (!product) return;
    const image = productImage(product);
    applySeo({
      title: `${product.name} | ${branding.shopName || defaultBranding.shopName}`,
      description: `${product.name} in ${titleCaseCategory(product.category)} with live stock, price, and checkout options.`,
      path: `/products/${product.id}`,
      image,
      keywords: [product.name, product.category, product.sku, 'jewellery', 'online shopping'].filter(Boolean).join(', ')
    });
    preloadImage(image, 'high');
  }, [branding.shopName, product]);

  const headerLinks = useMemo(() => [
    { to: '/', label: 'Home' },
    { to: '/products', label: 'Collections' },
    ...(product ? [{ to: `/products?category=${titleCaseCategory(product.category).toLowerCase().replace(/[^a-z0-9]+/g, '-')}`, label: titleCaseCategory(product.category) }] : [])
  ], [product]);

  if (!productId) {
    return <Navigate to="/products" replace />;
  }

  const shopName = branding.shopName || defaultBranding.shopName;
  const logo = branding.media?.logo || '/assets/glowjewels/app_logo.png';

  const addToCart = async (redirectTarget = '') => {
    if (!product?.inStock) {
      setError(`${product?.name || 'This product'} is currently out of stock.`);
      return;
    }
    setError('');
    setMessage('');
    const coupon = searchParams.get('coupon');
    if (coupon) {
      storeCheckoutCouponCode(coupon);
    }
    try {
      if (customerSession?.token) {
        const cart = await retailService.addToCart({ productId: product.id, quantity: 1 });
        setCartCount((cart.items || []).reduce((total, item) => total + Number(item.quantity || 0), 0));
      } else {
        const items = addGuestCartItem(product.id, 1);
        setCartCount(items.reduce((total, item) => total + Number(item.quantity || 0), 0));
      }
      if (redirectTarget === 'checkout') {
        navigate(customerSession?.token ? '/checkout' : '/customer-login?redirect=/checkout');
        return;
      }
      if (redirectTarget === 'cart') {
        navigate('/cart');
        return;
      }
      setMessage(`${product.name} added to cart.`);
    } catch (err) {
      if (isCustomerAuthError(err)) {
        clearCustomerSession();
        setCustomerSession(null);
        const items = addGuestCartItem(product.id, 1);
        setCartCount(items.reduce((total, item) => total + Number(item.quantity || 0), 0));
        setMessage(`${product.name} added to guest cart.`);
        return;
      }
      setError(getApiErrorMessage(err, 'Unable to add product to cart.'));
    }
  };

  return (
    <main className="glow-site grid-lines glow-motion-ready">
      <StorefrontHeader logo={logo} shopName={shopName} navLinks={headerLinks} cartCount={cartCount} wishlistCount={wishlistCount} />

      {loading ? (
        <section className="glow-products-empty"><strong>Loading product</strong><span>Please wait.</span></section>
      ) : error && !product ? (
        <section className="glow-products-empty"><strong>{error}</strong><Link to="/products">Back to collections</Link></section>
      ) : (
        <>
          <section className="glow-detail-hero">
            <div className="glow-detail-media shimmer-border">
              <img src={productImage(product)} alt={product.name} fetchPriority="high" loading="eager" decoding="async" />
            </div>
            <div className="glow-detail-copy">
              <nav className="glow-products-breadcrumb">
                <Link to="/">Home</Link><span>›</span><Link to="/products">Collections</Link><span>›</span><span>{product.name}</span>
              </nav>
              <span className="glow-kicker">{titleCaseCategory(product.category)} · {product.sku}</span>
              <h1 className="editorial-text glow-products-title">{product.name}</h1>
              <div className="glow-detail-price-row">
                <strong>{currency(product.sellingPrice)}</strong>
                <span>{product.stockLabel || (product.inStock ? 'Available now' : 'Out of stock')}</span>
              </div>
              <p className="glow-products-subtitle">
                A ready-to-order store pick with current stock visibility, secure checkout, and simple order tracking after purchase.
              </p>
              {error ? <p className="error-text storefront-feedback error" role="alert">{error}</p> : null}
              {message ? <p className="success-text storefront-feedback" role="status">{message}</p> : null}
              <div className="glow-detail-actions">
                <button type="button" className="primary-btn" disabled={!product.inStock} onClick={() => addToCart('cart')}>Add to cart</button>
                <button type="button" className="ghost-btn" disabled={!product.inStock} onClick={() => addToCart('checkout')}>Buy now</button>
              </div>
            </div>
          </section>

          {related.length ? (
            <section className="glow-featured">
              <div className="glow-section-head"><div><span className="glow-kicker">Same category</span><h2 className="editorial-text">You may also like</h2></div></div>
              <div className="glow-products-grid">
                {related.map((item, index) => (
                  <Link key={item.id} to={`/products/${item.id}`} className="glow-product-card-template glow-product-card-link">
                    <div className="glow-product-card-media"><img src={item.imageDataUrl || fallbackImages[index % fallbackImages.length]} alt={item.name} loading="lazy" decoding="async" /></div>
                    <div className="glow-product-card-copy"><p>{titleCaseCategory(item.category)}</p><h3>{item.name}</h3><div className="glow-product-card-meta"><strong>{currency(item.sellingPrice)}</strong><span>{item.stockLabel}</span></div></div>
                  </Link>
                ))}
              </div>
            </section>
          ) : null}
        </>
      )}
    </main>
  );
}
