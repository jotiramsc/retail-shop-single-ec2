'use client';

import React, { useState, useEffect, useRef } from 'react';

import AppImage from '@/components/ui/AppImage';
import Icon from '@/components/ui/AppIcon';

interface Product {
  id: number;
  name: string;
  category: string;
  price: number;
  originalPrice?: number;
  rating: number;
  reviewCount: number;
  image: string;
  alt: string;
  badge?: string;
  material: string;
  isNew?: boolean;
}

const allProducts: Product[] = [
{ id: 1, name: 'Celestial Rose Ring', category: 'Rings', price: 48, originalPrice: 65, rating: 5, reviewCount: 284, image: "https://img.rocket.new/generatedImages/rocket_gen_img_1b3bb48ab-1772776773974.png", alt: 'Elegant rose gold ring with crystal stone on dark velvet, warm studio lighting', badge: 'Bestseller', material: 'Rose Gold' },
{ id: 2, name: 'Aurora Drop Earrings', category: 'Earrings', price: 36, rating: 5, reviewCount: 197, image: "https://images.unsplash.com/photo-1599428421270-6002819ce9f7", alt: 'Rose gold drop earrings against dark background, moody atmospheric lighting', isNew: true, material: 'Rose Gold' },
{ id: 3, name: 'Halo Diamond Pendant', category: 'Necklaces', price: 54, originalPrice: 72, rating: 4, reviewCount: 143, image: "https://images.unsplash.com/photo-1700083326405-85d0c1acd667", alt: 'Delicate pendant necklace with crystal halo on dark surface, dim studio lighting', badge: 'Sale', material: 'Gold Plated' },
{ id: 4, name: 'Infinity Tennis Bracelet', category: 'Bracelets', price: 62, rating: 5, reviewCount: 89, image: "https://images.unsplash.com/photo-1724937721130-c9a9a4ee477e", alt: 'Gold tennis bracelet with crystal links on dark velvet, atmospheric low lighting', isNew: true, material: 'Gold Plated' },
{ id: 5, name: 'Starburst Stud Earrings', category: 'Earrings', price: 28, rating: 4, reviewCount: 312, image: "https://images.unsplash.com/photo-1693213085231-fc580d8916de", alt: 'Small gold stud earrings on dark surface, moody studio lighting, close-up jewelry', material: 'Sterling Silver' },
{ id: 6, name: 'Bloom Cluster Ring', category: 'Rings', price: 42, originalPrice: 58, rating: 5, reviewCount: 176, image: "https://images.unsplash.com/photo-1715629343147-5e7721043c84", alt: 'Floral cluster ring with pink stones on dark velvet, warm luxury lighting', badge: 'Sale', material: 'Rose Gold' },
{ id: 7, name: 'Moonstone Choker', category: 'Necklaces', price: 58, rating: 4, reviewCount: 67, image: "https://images.unsplash.com/photo-1731531534495-7636bc08d2ec", alt: 'Delicate moonstone choker necklace on dark background, atmospheric lighting', isNew: true, material: 'Sterling Silver' },
{ id: 8, name: 'Bridal Gift Set', category: 'Gift Sets', price: 128, rating: 5, reviewCount: 54, image: "https://img.rocket.new/generatedImages/rocket_gen_img_1ebcd4b82-1772172905434.png", alt: 'Luxury bridal jewelry set in black gift box, dark moody background, low lighting', badge: 'Limited', material: 'Mixed' },
{ id: 9, name: 'Petal Hoop Earrings', category: 'Earrings', price: 32, rating: 4, reviewCount: 228, image: "https://images.unsplash.com/photo-1708222170231-88ed00b3c5e6", alt: 'Delicate floral hoop earrings against dark background, soft studio lighting', material: 'Gold Plated' },
{ id: 10, name: 'Twisted Band Ring', category: 'Rings', price: 38, rating: 5, reviewCount: 143, image: "https://img.rocket.new/generatedImages/rocket_gen_img_1b3bb48ab-1772776773974.png", alt: 'Twisted gold band ring on dark surface, warm atmospheric lighting, luxury product', material: 'Gold Plated' },
{ id: 11, name: 'Layered Chain Necklace', category: 'Necklaces', price: 44, rating: 4, reviewCount: 98, image: "https://images.unsplash.com/photo-1671513579426-1bec083940c6", alt: 'Layered gold chain necklace on dark velvet, moody studio photography, elegant', material: 'Gold Plated' },
{ id: 12, name: 'Charm Bangle Set', category: 'Bracelets', price: 52, rating: 5, reviewCount: 71, image: "https://images.unsplash.com/photo-1619525673983-81151d6cc193", alt: 'Gold charm bangle bracelets set on dark surface, atmospheric low lighting', material: 'Rose Gold' }];


const categories = ['All', 'Rings', 'Necklaces', 'Earrings', 'Bracelets', 'Gift Sets'];
const materials = ['All Materials', 'Rose Gold', 'Gold Plated', 'Sterling Silver', 'Mixed'];
const sortOptions = ['Featured', 'Price: Low to High', 'Price: High to Low', 'Highest Rated', 'Newest'];
const priceRanges = ['All Prices', 'Under $30', '$30–$60', '$60–$100', 'Over $100'];

const StarRating: React.FC<{rating: number;}> = ({ rating }) =>
<div className="flex items-center gap-0.5">
    {[1, 2, 3, 4, 5].map((star) =>
  <Icon key={star} name="StarIcon" size={11} variant={star <= rating ? 'solid' : 'outline'} className={star <= rating ? 'star-filled' : 'star-empty'} />
  )}
  </div>;


const ProductCard: React.FC<{product: Product;index: number;}> = ({ product, index }) => {
  const [wishlisted, setWishlisted] = useState(false);
  const [added, setAdded] = useState(false);
  const cardRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const card = cardRef.current;
    if (!card) return;
    card.style.opacity = '0';
    card.style.transform = 'translateY(30px)';
    setTimeout(() => {
      card.style.transition = 'opacity 0.7s cubic-bezier(0.16, 1, 0.3, 1), transform 0.7s cubic-bezier(0.16, 1, 0.3, 1)';
      card.style.opacity = '1';
      card.style.transform = 'translateY(0)';
    }, index * 60);
  }, [index]);

  const handleCart = () => {
    setAdded(true);
    setTimeout(() => setAdded(false), 2000);
  };

  return (
    <div ref={cardRef} className="product-card group bg-card rounded-2xl overflow-hidden border border-border">
      <div className="relative aspect-square overflow-hidden bg-muted">
        <AppImage
          src={product.image}
          alt={product.alt}
          fill
          className="object-cover group-hover:scale-110 transition-transform duration-700"
          sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 33vw" />

        <div className="absolute inset-0 bg-foreground/0 group-hover:bg-foreground/10 transition-colors duration-300" />

        {product.badge &&
        <div className={`absolute top-3 left-3 px-2.5 py-1 rounded-full text-[9px] font-black uppercase tracking-widest z-10 ${product.badge === 'Sale' ? 'bg-accent text-white' : product.badge === 'Bestseller' ? 'bg-primary text-white' : 'bg-white border border-border text-foreground'}`}>
            {product.badge}
          </div>
        }
        {product.isNew && !product.badge &&
        <div className="absolute top-3 left-3 px-2.5 py-1 rounded-full text-[9px] font-black uppercase tracking-widest bg-white border border-primary/40 text-primary z-10">New</div>
        }

        <button
          onClick={() => setWishlisted(!wishlisted)}
          className={`absolute top-3 right-3 w-8 h-8 rounded-full bg-white/90 backdrop-blur-sm flex items-center justify-center wishlist-btn z-10 ${wishlisted ? 'active' : ''}`}
          aria-label={wishlisted ? 'Remove from wishlist' : 'Add to wishlist'}>

          <Icon name="HeartIcon" size={16} variant={wishlisted ? 'solid' : 'outline'} className={wishlisted ? 'text-accent' : 'text-muted-foreground'} />
        </button>

        <div className="absolute bottom-3 left-3 right-3 opacity-0 group-hover:opacity-100 transition-all duration-300 translate-y-2 group-hover:translate-y-0 z-10">
          <button
            onClick={handleCart}
            className={`w-full py-2.5 rounded-xl text-[10px] font-black uppercase tracking-widest transition-all ${added ? 'bg-primary/30 text-primary border border-primary/40' : 'btn-primary'}`}>

            {added ? '✓ Added to Cart' : 'Add to Cart'}
          </button>
        </div>
      </div>

      <div className="p-4">
        <p className="text-[9px] font-bold uppercase tracking-widest text-muted-foreground mb-1">{product.category}</p>
        <h3 className="text-[14px] font-bold text-foreground mb-2 leading-tight">{product.name}</h3>
        <div className="flex items-center gap-2 mb-3">
          <StarRating rating={product.rating} />
          <span className="text-[11px] text-muted-foreground">({product.reviewCount})</span>
        </div>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-base font-extrabold text-foreground">${product.price}</span>
            {product.originalPrice &&
            <span className="text-[12px] text-muted-foreground line-through">${product.originalPrice}</span>
            }
          </div>
          <span className="text-[10px] text-muted-foreground">{product.material}</span>
        </div>
      </div>
    </div>);

};

const ProductsGrid: React.FC = () => {
  const [activeCategory, setActiveCategory] = useState('All');
  const [activeMaterial, setActiveMaterial] = useState('All Materials');
  const [activePriceRange, setActivePriceRange] = useState('All Prices');
  const [activeSort, setActiveSort] = useState('Featured');
  const [searchQuery, setSearchQuery] = useState('');
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [minRating, setMinRating] = useState(0);

  const filtered = allProducts.filter((p) => {
    const matchesCategory = activeCategory === 'All' || p.category === activeCategory;
    const matchesMaterial = activeMaterial === 'All Materials' || p.material === activeMaterial;
    const matchesSearch = p.name.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesRating = p.rating >= minRating;
    const matchesPrice =
    activePriceRange === 'All Prices' ||
    activePriceRange === 'Under $30' && p.price < 30 ||
    activePriceRange === '$30–$60' && p.price >= 30 && p.price <= 60 ||
    activePriceRange === '$60–$100' && p.price > 60 && p.price <= 100 ||
    activePriceRange === 'Over $100' && p.price > 100;
    return matchesCategory && matchesMaterial && matchesSearch && matchesRating && matchesPrice;
  });

  const sorted = [...filtered].sort((a, b) => {
    if (activeSort === 'Price: Low to High') return a.price - b.price;
    if (activeSort === 'Price: High to Low') return b.price - a.price;
    if (activeSort === 'Highest Rated') return b.rating - a.rating;
    return 0;
  });

  const activeFilters = [
  activeCategory !== 'All' ? activeCategory : null,
  activeMaterial !== 'All Materials' ? activeMaterial : null,
  activePriceRange !== 'All Prices' ? activePriceRange : null,
  minRating > 0 ? `${minRating}+ Stars` : null].
  filter(Boolean) as string[];

  const clearFilter = (filter: string) => {
    if (categories.includes(filter)) setActiveCategory('All');else
    if (materials.includes(filter)) setActiveMaterial('All Materials');else
    if (priceRanges.includes(filter)) setActivePriceRange('All Prices');else
    setMinRating(0);
  };

  return (
    <section className="pt-8 pb-20 px-6 lg:px-12">
      <div className="max-w-[1400px] mx-auto">
        {/* Top Controls */}
        <div className="flex flex-col sm:flex-row gap-4 items-start sm:items-center justify-between mb-8">
          {/* Search */}
          <div className="relative w-full sm:max-w-xs">
            <Icon name="MagnifyingGlassIcon" size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <input
              type="text"
              placeholder="Search jewelry..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-4 py-2.5 bg-card border border-border rounded-xl text-[13px] text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-primary/50 transition-colors" />

          </div>

          <div className="flex items-center gap-3 w-full sm:w-auto">
            {/* Mobile filter toggle */}
            <button
              onClick={() => setSidebarOpen(!sidebarOpen)}
              className="flex lg:hidden items-center gap-2 px-4 py-2.5 bg-card border border-border rounded-xl text-[12px] font-bold uppercase tracking-widest text-muted-foreground hover:text-primary hover:border-primary/40 transition-all">

              <Icon name="AdjustmentsHorizontalIcon" size={14} />
              Filters
              {activeFilters.length > 0 &&
              <span className="w-5 h-5 bg-primary text-white text-[9px] font-black rounded-full flex items-center justify-center">
                  {activeFilters.length}
                </span>
              }
            </button>

            {/* Sort */}
            <div className="relative flex-1 sm:flex-none">
              <select
                value={activeSort}
                onChange={(e) => setActiveSort(e.target.value)}
                className="w-full appearance-none bg-card border border-border rounded-xl px-4 py-2.5 pr-8 text-[12px] font-bold text-foreground focus:outline-none focus:border-primary/50 cursor-pointer">

                {sortOptions.map((o) =>
                <option key={o} value={o} className="bg-white">{o}</option>
                )}
              </select>
              <Icon name="ChevronDownIcon" size={14} className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
            </div>

            <span className="text-[12px] text-muted-foreground whitespace-nowrap">
              {sorted.length} results
            </span>
          </div>
        </div>

        {/* Active Filter Chips */}
        {activeFilters.length > 0 &&
        <div className="flex flex-wrap gap-2 mb-6">
            {activeFilters.map((f) =>
          <button
            key={f}
            onClick={() => clearFilter(f)}
            className="flex items-center gap-1.5 px-3 py-1.5 bg-primary/10 border border-primary/30 rounded-full text-[11px] font-bold text-primary hover:bg-primary/20 transition-colors">

                {f}
                <Icon name="XMarkIcon" size={12} className="text-primary" />
              </button>
          )}
            <button
            onClick={() => {setActiveCategory('All');setActiveMaterial('All Materials');setActivePriceRange('All Prices');setMinRating(0);}}
            className="px-3 py-1.5 text-[11px] font-bold text-muted-foreground hover:text-foreground transition-colors">

              Clear all
            </button>
          </div>
        }

        <div className="flex gap-8">
          {/* Sidebar Filters — Desktop always visible, Mobile toggle */}
          <aside className={`${sidebarOpen ? 'block' : 'hidden'} lg:block w-full lg:w-56 shrink-0 flex-col gap-8`}>
            <div className="flex flex-col gap-8 bg-card lg:bg-transparent border lg:border-none border-border rounded-2xl p-6 lg:p-0">
              {/* Category */}
              <div>
                <h3 className="text-[10px] font-black uppercase tracking-widest text-primary mb-4">Category</h3>
                <div className="flex flex-col gap-2">
                  {categories.map((c) =>
                  <button
                    key={c}
                    onClick={() => setActiveCategory(c)}
                    className={`text-left text-[13px] font-medium px-3 py-2 rounded-lg transition-all ${
                    activeCategory === c ?
                    'bg-primary/15 text-primary font-bold' : 'text-muted-foreground hover:text-foreground hover:bg-muted'}`
                    }>

                      {c}
                    </button>
                  )}
                </div>
              </div>

              {/* Price Range */}
              <div>
                <h3 className="text-[10px] font-black uppercase tracking-widest text-primary mb-4">Price Range</h3>
                <div className="flex flex-col gap-2">
                  {priceRanges.map((r) =>
                  <button
                    key={r}
                    onClick={() => setActivePriceRange(r)}
                    className={`text-left text-[13px] font-medium px-3 py-2 rounded-lg transition-all ${
                    activePriceRange === r ?
                    'bg-primary/15 text-primary font-bold' : 'text-muted-foreground hover:text-foreground hover:bg-muted'}`
                    }>

                      {r}
                    </button>
                  )}
                </div>
              </div>

              {/* Material */}
              <div>
                <h3 className="text-[10px] font-black uppercase tracking-widest text-primary mb-4">Material</h3>
                <div className="flex flex-col gap-2">
                  {materials.map((m) =>
                  <button
                    key={m}
                    onClick={() => setActiveMaterial(m)}
                    className={`text-left text-[13px] font-medium px-3 py-2 rounded-lg transition-all ${
                    activeMaterial === m ?
                    'bg-primary/15 text-primary font-bold' : 'text-muted-foreground hover:text-foreground hover:bg-muted'}`
                    }>

                      {m}
                    </button>
                  )}
                </div>
              </div>

              {/* Rating */}
              <div>
                <h3 className="text-[10px] font-black uppercase tracking-widest text-primary mb-4">Min Rating</h3>
                <div className="flex flex-col gap-2">
                  {[0, 3, 4, 5].map((r) =>
                  <button
                    key={r}
                    onClick={() => setMinRating(r)}
                    className={`text-left flex items-center gap-2 px-3 py-2 rounded-lg transition-all ${
                    minRating === r ?
                    'bg-primary/15 text-primary font-bold' : 'text-muted-foreground hover:text-foreground hover:bg-muted'}`
                    }>

                      {r === 0 ?
                    <span className="text-[13px] font-medium">Any Rating</span> :

                    <div className="flex items-center gap-1.5">
                          {[1, 2, 3, 4, 5].map((s) =>
                      <Icon key={s} name="StarIcon" size={11} variant={s <= r ? 'solid' : 'outline'} className={s <= r ? 'star-filled' : 'star-empty'} />
                      )}
                          <span className="text-[12px]">& up</span>
                        </div>
                    }
                    </button>
                  )}
                </div>
              </div>

              {/* Close on mobile */}
              <button
                onClick={() => setSidebarOpen(false)}
                className="flex lg:hidden items-center justify-center gap-2 w-full py-3 btn-primary rounded-xl text-[11px] mt-2">

                Apply Filters ({sorted.length} results)
              </button>
            </div>
          </aside>

          {/* Product Grid */}
          <div className="flex-1 min-w-0">
            {sorted.length === 0 ?
            <div className="flex flex-col items-center justify-center py-24 gap-4">
                <Icon name="MagnifyingGlassIcon" size={40} className="text-muted-foreground" />
                <p className="text-lg font-bold text-foreground">No pieces found</p>
                <p className="text-muted-foreground text-sm">Try adjusting your filters</p>
                <button
                onClick={() => {setActiveCategory('All');setActiveMaterial('All Materials');setActivePriceRange('All Prices');setMinRating(0);setSearchQuery('');}}
                className="mt-2 px-8 py-3 btn-primary rounded-full text-[11px]">

                  Clear All Filters
                </button>
              </div> :

            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-5">
                {sorted.map((product, i) =>
              <ProductCard key={product.id} product={product} index={i} />
              )}
              </div>
            }

            {/* Pagination */}
            {sorted.length > 0 &&
            <div className="flex items-center justify-center gap-2 mt-12">
                <button className="w-10 h-10 rounded-xl border border-border flex items-center justify-center text-muted-foreground hover:text-primary hover:border-primary/40 transition-all">
                  <Icon name="ChevronLeftIcon" size={16} />
                </button>
                {[1, 2, 3].map((page) =>
              <button
                key={page}
                className={`w-10 h-10 rounded-xl text-[13px] font-bold transition-all ${
                page === 1 ?
                'bg-primary text-white' : 'border border-border text-muted-foreground hover:text-primary hover:border-primary/40'}`
                }>

                    {page}
                  </button>
              )}
                <button className="w-10 h-10 rounded-xl border border-border flex items-center justify-center text-muted-foreground hover:text-primary hover:border-primary/40 transition-all">
                  <Icon name="ChevronRightIcon" size={16} />
                </button>
              </div>
            }
          </div>
        </div>
      </div>
    </section>);

};

export default ProductsGrid;