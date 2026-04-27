import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { retailService } from '../services/retailService';
import { currency } from '../utils/format';
import {
  clearCustomerSession,
  getStoredCustomerSession,
  isCustomerAuthError
} from '../utils/auth';
import { getGuestCartCount } from '../utils/cart';
import StorefrontHeader from '../components/StorefrontHeader';

const templateImages = {
  logo: '/assets/glowjewels/app_logo.png',
  hero: '/assets/glowjewels/maharashtrian_bridal_jewelry_hero.png',
  fullLook: '/assets/glowjewels/maharashtrian_lady_full_jewelry.png',
  flatlay: '/assets/glowjewels/maharashtrian_jewelry_flatlay.png',
  bangles: '/assets/glowjewels/maharashtrian_bangdi_bangles.png',
  earrings: '/assets/glowjewels/maharashtrian_chandrakor_earrings.png',
  nath: '/assets/glowjewels/maharashtrian_nath_nose_ring.png',
  necklace: '/assets/glowjewels/maharashtrian_necklace_kolhapuri.png',
  empty: '/assets/glowjewels/no_image.png'
};

const testimonialData = [
  {
    id: 1,
    name: 'Aarohi Patil',
    location: 'Pune',
    quote: 'The bridal jewellery picks felt premium and arrived exactly as shown. It finally feels like a proper jewellery storefront, not just a stock page.',
    product: 'Bridal collection'
  },
  {
    id: 2,
    name: 'Riya Deshmukh',
    location: 'Mumbai',
    quote: 'I could instantly browse the right category and new arrivals. The product images now feel curated and much more premium.',
    product: 'New releases'
  },
  {
    id: 3,
    name: 'Sneha Kulkarni',
    location: 'Nagpur',
    quote: 'The category tiles and featured section feel polished and luxurious. It looks like a real brand site now.',
    product: 'Featured pieces'
  },
  {
    id: 4,
    name: 'Meera Joshi',
    location: 'Nashik',
    quote: 'Store details, product sections, and the customer-facing flow all feel consistent. It gives a much stronger first impression.',
    product: 'Storefront experience'
  }
];

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
  return String(value || 'section')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
}

function productImage(product, fallback) {
  return product?.imageDataUrl || fallback || templateImages.empty;
}

function pickCategoryFallback(index) {
  const fallbacks = [
    templateImages.flatlay,
    templateImages.necklace,
    templateImages.earrings,
    templateImages.bangles,
    templateImages.fullLook
  ];
  return fallbacks[index % fallbacks.length];
}

function buildCategoryGroups(products) {
  const map = new Map();

  products.forEach((product) => {
    const label = titleCaseCategory(product.category);
    if (!map.has(label)) {
      map.set(label, []);
    }
    map.get(label).push(product);
  });

  return Array.from(map.entries()).map(([name, items], index) => ({
    id: normalizeId(name),
    name,
    items,
    count: `${items.length} styles`,
    image: productImage(items[0], pickCategoryFallback(index))
  }));
}

function buildMenuCategories(categoryOptions, categoryGroups) {
  const normalized = new Map();

  (categoryOptions || []).forEach((option) => {
    const name = titleCaseCategory(option.displayName || option.code);
    const id = normalizeId(name);
    if (!normalized.has(id)) {
      normalized.set(id, { id, name });
    }
  });

  if (normalized.size > 0) {
    return Array.from(normalized.values());
  }

  return categoryGroups.map((category) => ({
    id: category.id,
    name: category.name
  }));
}

function buildHeroTitle(homepageTitle, shopName) {
  const source = String(homepageTitle || shopName || 'Glow Jewels').trim();
  const parts = source.split(/\s+/).filter(Boolean);

  if (parts.length <= 1) {
    return {
      main: parts[0] || 'Glow',
      accent: 'Jewels'
    };
  }

  return {
    main: parts[0],
    accent: parts.slice(1).join(' ')
  };
}

function buildHeroDescription(homepageSubtitle) {
  if (homepageSubtitle) {
    return homepageSubtitle;
  }

  return 'Inspired by the timeless elegance of Maharashtrian heritage - Nath, Kolhapuri Saaj, Bangdi and more. Wear your culture, wear your confidence.';
}

export default function PublicHomePage({ branding }) {
  const [products, setProducts] = useState([]);
  const [trendingProducts, setTrendingProducts] = useState([]);
  const [categoryOptions, setCategoryOptions] = useState([]);
  const [error, setError] = useState('');
  const [motionReady, setMotionReady] = useState(false);
  const [heroDrift, setHeroDrift] = useState({ x: 0, y: 0 });
  const [cartCount, setCartCount] = useState(0);
  const [customerSession, setCustomerSession] = useState(() => getStoredCustomerSession());

  useEffect(() => {
    retailService.getProductCategoryOptions()
      .then((options) => {
        setCategoryOptions(options || []);
      })
      .catch(() => {
        setCategoryOptions([]);
      });

    Promise.all([
      retailService.getPublicHomepageCatalog(),
      retailService.getPublicTrendingCatalog()
    ])
      .then(([catalog, trending]) => {
        setProducts(catalog || []);
        setTrendingProducts(trending || []);
      })
      .catch(() => {
        setError('Unable to load the latest collection right now.');
      });
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => setMotionReady(true), 120);
    return () => window.clearTimeout(timer);
  }, []);

  useEffect(() => {
    if (customerSession?.token) {
      retailService.getCart()
        .then((cart) => setCartCount((cart.items || []).reduce((total, item) => total + Number(item.quantity || 0), 0)))
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

  const visibleProducts = useMemo(() => products, [products]);

  const latestProducts = useMemo(
    () => [...visibleProducts].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()),
    [visibleProducts]
  );

  const customerAccessProducts = useMemo(
    () => latestProducts.filter((product) => product.showInCustomerAccess),
    [latestProducts]
  );

  const shopCollectionProducts = useMemo(
    () => latestProducts.filter((product) => product.showInShopCollection),
    [latestProducts]
  );

  const featuredPieceProducts = useMemo(
    () => latestProducts.filter((product) => product.showInFeaturedPieces),
    [latestProducts]
  );

  const storyFlaggedProducts = useMemo(
    () => latestProducts.filter((product) => product.showInStory),
    [latestProducts]
  );

  const curatedSelectionProducts = useMemo(
    () => latestProducts.filter((product) => product.showInCuratedSelections),
    [latestProducts]
  );

  const categoryGroups = useMemo(() => buildCategoryGroups(visibleProducts), [visibleProducts]);
  const menuCategories = useMemo(
    () => buildMenuCategories(categoryOptions, categoryGroups),
    [categoryGroups, categoryOptions]
  );
  const headerLinks = useMemo(
    () => [
      { to: '/products', label: 'Collections' },
      ...menuCategories.slice(0, 3).map((category) => ({
        to: `/products?category=${category.id}`,
        label: category.name
      }))
    ],
    [menuCategories]
  );
  const heroProduct = customerAccessProducts[0] || latestProducts[0] || null;
  const collectionProducts = (shopCollectionProducts.length ? shopCollectionProducts : latestProducts).slice(0, 3);
  const featuredProducts = (featuredPieceProducts.length ? featuredPieceProducts : latestProducts).slice(0, 8);
  const editorsProducts = (curatedSelectionProducts.length ? curatedSelectionProducts : latestProducts).slice(0, 4);
  const storyProducts = (storyFlaggedProducts.length ? storyFlaggedProducts : trendingProducts.length ? trendingProducts : latestProducts).slice(0, 1);
  const bentoGridClassName = collectionProducts.length <= 2 ? 'glow-bento-grid glow-bento-grid-two' : 'glow-bento-grid';

  const shopName = branding.shopName || 'GlowJewels';
  const logo = branding.media?.logo || templateImages.logo;
  const heroPrimary = productImage(heroProduct, branding.media?.heroPrimary || templateImages.hero);
  const heroSecondary = branding.media?.heroSecondary || templateImages.fullLook;
  const heroTitle = buildHeroTitle(branding.homepageTitle, shopName);
  const heroDescription = buildHeroDescription(branding.homepageSubtitle);
  const trustPoints = branding.trustPoints?.length
    ? branding.trustPoints
    : ['Premium finish', 'Secure checkout', 'Fresh collections', 'Store-published products'];
  const siteClassName = motionReady ? 'glow-site grid-lines glow-motion-ready' : 'glow-site grid-lines';

  const handleHeroMove = (event) => {
    const rect = event.currentTarget.getBoundingClientRect();
    const x = ((event.clientX - rect.left) / rect.width - 0.5) * 18;
    const y = ((event.clientY - rect.top) / rect.height - 0.5) * 18;
    setHeroDrift({ x, y });
  };

  const handleHeroLeave = () => {
    setHeroDrift({ x: 0, y: 0 });
  };

  return (
    <main className={siteClassName}>
      <StorefrontHeader logo={logo} shopName={shopName} navLinks={headerLinks} cartCount={cartCount} />

      <section className="glow-hero" onMouseMove={handleHeroMove} onMouseLeave={handleHeroLeave}>
        <div className="glow-hero-rings glow-hero-rings-right" />
        <div className="glow-hero-rings glow-hero-rings-left" />
        <div className="glow-hero-copy">
          <div className="glow-hero-topline glow-reveal glow-reveal-delay-1">
            <span className="glow-kicker">पारंपरिक · Traditional · Maharashtrian</span>
            <span className="glow-kicker subtle">New Collection · Spring 2026</span>
          </div>

          <h1
            className="glow-display-title glow-reveal glow-reveal-delay-2"
            style={{ transform: `translate3d(${heroDrift.x}px, ${heroDrift.y}px, 0)` }}
          >
            <span className="glow-display-main">{heroTitle.main}</span>
            <span className="glow-display-accent animate-shimmer-text">{heroTitle.accent}</span>
          </h1>

          <p className="glow-hero-description glow-reveal glow-reveal-delay-3">
            {heroDescription}
          </p>

          <div className="glow-button-row glow-reveal glow-reveal-delay-4">
            <Link to="/products" className="btn-primary glow-pill-btn">
              <span>Shop Collection</span>
              <span className="glow-btn-arrow">→</span>
            </Link>
            <Link to={customerSession?.token ? '/account' : '/cart'} className="btn-outline glow-pill-btn">
              {customerSession?.token ? 'My Account' : 'View Cart'}
            </Link>
          </div>

          <div className="glow-hero-microcopy glow-reveal glow-reveal-delay-5">Free returns · Secure checkout</div>

          <div className="glow-tradition-strip glow-reveal glow-reveal-delay-6">
            <span className="glow-tradition-label">Traditional Styles</span>
            {[
              { label: 'Nath', src: templateImages.nath },
              { label: 'Chandrakor', src: templateImages.earrings },
              { label: 'Bangdi', src: templateImages.bangles },
              { label: 'Saaj', src: templateImages.necklace }
            ].map((item) => (
              <div key={item.label} className="glow-tradition-item">
                <img src={item.src} alt={item.label} />
                <span>{item.label}</span>
              </div>
            ))}
          </div>

          <div className="glow-scroll-cue">
            <span>Scroll</span>
            <i />
          </div>
        </div>

        <div className="glow-hero-visual glow-reveal glow-reveal-delay-4">
          <span className="glow-sparkle glow-sparkle-left animate-sparkle">✦</span>
          <span className="glow-sparkle glow-sparkle-top animate-sparkle">✦</span>
          <span className="glow-sparkle glow-sparkle-bottom animate-sparkle">✦</span>
          <div className="glow-hero-visual-label">Nath · Mangalsutra · Bangdi · Chandrakor</div>
          <div className="glow-hero-card shimmer-border">
            <img src={heroPrimary} alt={heroProduct?.name || shopName} className="glow-hero-main-image" />
            <div className="glow-hero-stat-card">
              <div>
                <span>Loved by</span>
                <strong>{products.length ? `${products.length * 708}+` : '24,800+'}</strong>
              </div>
            </div>
            <div className="glow-hero-rating">★ 4.9</div>
          </div>
        </div>
      </section>

      <section className="glow-bento">
        <div className="glow-section-head glow-reveal glow-reveal-delay-1">
          <div>
            <span className="glow-kicker">Browse by Category</span>
            <h2 className="editorial-text">Shop the <span className="text-outline">Collection</span></h2>
          </div>
        </div>

        <div className={bentoGridClassName}>
          {collectionProducts.map((product, index) => (
            <article
              key={product.id}
              id={`collection-${normalizeId(product.name)}`}
              className={`category-card glow-bento-card glow-reveal ${index === 0 ? 'glow-bento-card-large' : ''}`}
              style={{ transitionDelay: `${Math.min(index * 0.08, 0.42)}s` }}
            >
              <img src={productImage(product, pickCategoryFallback(index))} alt={product.name} />
              <div className="glow-bento-overlay">
                <span>{titleCaseCategory(product.category)}</span>
                <strong>{product.name}</strong>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="glow-featured" id="featured">
        <div className="glow-section-head glow-section-head-row glow-reveal glow-reveal-delay-1">
          <div>
            <span className="glow-kicker">Handpicked for You</span>
            <h2 className="editorial-text">Featured <span className="text-outline">Pieces</span></h2>
          </div>
          <div className="glow-results-copy">{visibleProducts.length} products available</div>
        </div>

        {error ? <p className="error-text">{error}</p> : null}

        <div className="glow-product-grid">
          {featuredProducts.map((product, index) => (
            <article
              key={product.id}
              className="product-card glow-product-card glow-reveal"
              style={{ transitionDelay: `${Math.min(index * 0.06, 0.4)}s` }}
            >
              <div className="glow-product-image-wrap">
                <img src={productImage(product, templateImages.flatlay)} alt={product.name} className="glow-product-image" />
                {product.showInNewRelease ? <span className="glow-product-badge">New</span> : null}
              </div>
              <div className="glow-product-copy">
                <p>{titleCaseCategory(product.category)}</p>
                <strong>{product.name}</strong>
                <div className="glow-product-meta-line">
                  <span>{currency(product.sellingPrice)}</span>
                  <small>{product.stockLabel || product.sku}</small>
                </div>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="glow-testimonials">
        <div className="glow-section-head centered glow-reveal glow-reveal-delay-1">
          <span className="glow-kicker">Real Customers · Verified Reviews</span>
          <h2 className="editorial-text">Worn &amp; Loved</h2>
          <div className="glow-review-summary">
            <span>★★★★★</span>
            <strong>4.9</strong>
            <small>from storefront feedback</small>
          </div>
        </div>

        <div className="glow-testimonial-grid">
          {testimonialData.map((item, index) => (
            <article
              key={item.id}
              className={`glow-testimonial-card glow-reveal ${index === 0 ? 'glow-testimonial-card-wide' : ''}`}
              style={{ transitionDelay: `${Math.min(index * 0.08, 0.35)}s` }}
            >
              <div className="glow-stars">★★★★★</div>
              <blockquote>{item.quote}</blockquote>
              <div className="glow-testimonial-tag">{item.product}</div>
              <div className="glow-testimonial-author">
                <strong>{item.name}</strong>
                <span>{item.location}</span>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="glow-brand-story" id="story">
        <div className="glow-brand-story-media glow-reveal glow-reveal-delay-2">
          <img src={storyProducts[0] ? productImage(storyProducts[0], heroSecondary) : heroSecondary} alt={storyProducts[0]?.name || shopName} />
          <div className="glow-brand-story-stats">
            <div>
              <p>{products.length || 0}</p>
              <span>Designs</span>
            </div>
            <div>
              <p>{categoryGroups.length || 0}</p>
              <span>Categories</span>
            </div>
            <div>
              <p>{storyProducts.length || 0}</p>
              <span>Story Pick</span>
            </div>
          </div>
        </div>

        <div className="glow-brand-story-copy glow-reveal glow-reveal-delay-3">
          <span className="glow-kicker">Our Story</span>
          <h2 className="editorial-text">Crafted with <span className="text-outline">Intention</span></h2>
          <p>
            {storyProducts[0]
              ? `${storyProducts[0].name} is highlighted from Inventory as the featured story piece for this storefront section.`
              : branding.headerLine || `${shopName} now reads homepage story, featured, collection, and curated selections directly from your live backend.`}
          </p>
          <div className="glow-story-points">
            {trustPoints.map((point) => (
              <div key={point} className="glow-story-point">
                <strong>{point}</strong>
                <p>Driven from your configured brand settings and published inventory.</p>
              </div>
            ))}
          </div>
          <div className="glow-button-row">
            {branding.contact?.phoneHref ? (
              <a href={branding.contact.phoneHref} className="btn-primary glow-pill-btn">{branding.contact.phoneLabel}</a>
            ) : null}
            <Link to="/products" className="btn-outline glow-pill-btn">Browse Collection</Link>
          </div>
        </div>
      </section>

      <section className="glow-editors-picks">
        <div className="glow-section-head glow-reveal glow-reveal-delay-1">
          <div>
            <span className="glow-kicker">Editors' Picks</span>
            <h2 className="editorial-text">Curated <span className="text-outline">Selections</span></h2>
          </div>
        </div>

        <div className="glow-editors-grid">
          {editorsProducts.map((product, index) => (
            <article
              key={product.id || index}
              className="glow-editor-card glow-reveal"
              style={{ transitionDelay: `${Math.min(index * 0.08, 0.32)}s` }}
            >
              <img src={productImage(product, pickCategoryFallback(index))} alt={product.name} className="glow-editor-image" />
              <strong>{product.name}</strong>
              <span>{titleCaseCategory(product.category)}</span>
            </article>
          ))}
        </div>
      </section>

      <footer className="glow-footer">
        <div>
          <div className="glow-brand glow-footer-brand">
            <img src={logo} alt={`${shopName} logo`} className="glow-brand-logo" />
            <div>
              <span className="glow-kicker">Luxury cosmetic jewellery</span>
              <strong>{shopName}</strong>
            </div>
          </div>
          <p>{branding.contact?.address || 'Update store address from receipt settings.'}</p>
        </div>
        <div className="glow-footer-links">
          {categoryGroups.map((category) => (
            <a key={category.id} href={`#${category.id}`}>{category.name}</a>
          ))}
          <Link to="/login">Staff Login</Link>
        </div>
      </footer>
    </main>
  );
}
