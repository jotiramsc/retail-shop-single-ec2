'use client';

import React, { useState, useEffect, useRef } from 'react';
import Link from 'next/link';
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
  isNew?: boolean;
}

const products: Product[] = [
{
  id: 1,
  name: 'Celestial Rose Ring',
  category: 'Rings',
  price: 48,
  originalPrice: 65,
  rating: 5,
  reviewCount: 284,
  image: "/assets/images/maharashtrian_nath_nose_ring.png",
  alt: 'Traditional Maharashtrian Nath nose ring, large ornate gold nose ring with pearls and rubies, worn by beautiful Indian woman, warm golden lighting',
  badge: 'Bestseller'
},
{
  id: 2,
  name: 'Aurora Drop Earrings',
  category: 'Earrings',
  price: 36,
  rating: 5,
  reviewCount: 197,
  image: "/assets/images/maharashtrian_chandrakor_earrings.png",
  alt: 'Traditional Maharashtrian Chandrakor crescent moon shaped gold earrings with intricate filigree work and pearl drops, warm golden lighting',
  isNew: true
},
{
  id: 3,
  name: 'Halo Diamond Pendant',
  category: 'Necklaces',
  price: 54,
  originalPrice: 72,
  rating: 4,
  reviewCount: 143,
  image: "/assets/images/maharashtrian_necklace_kolhapuri.png",
  alt: 'Maharashtrian woman wearing traditional Kolhapuri Saaj gold necklace with intricate pendant, green Paithani saree, warm golden studio lighting',
  badge: 'Sale'
},
{
  id: 4,
  name: 'Infinity Tennis Bracelet',
  category: 'Bracelets',
  price: 62,
  rating: 5,
  reviewCount: 89,
  image: "/assets/images/maharashtrian_bangdi_bangles.png",
  alt: 'Maharashtrian woman wearing traditional gold Bangdi and Patlya bangles, close-up of wrists adorned with intricate gold bangles, green Paithani silk saree',
  isNew: true
},
{
  id: 5,
  name: 'Starburst Stud Earrings',
  category: 'Earrings',
  price: 28,
  rating: 4,
  reviewCount: 312,
  image: "/assets/images/maharashtrian_chandrakor_earrings.png",
  alt: 'Traditional Maharashtrian Chandrakor gold earrings with filigree work, close-up jewelry photography'
},
{
  id: 6,
  name: 'Bloom Cluster Ring',
  category: 'Rings',
  price: 42,
  originalPrice: 58,
  rating: 5,
  reviewCount: 176,
  image: "/assets/images/maharashtrian_jewelry_flatlay.png",
  alt: 'Traditional Maharashtrian bridal jewelry flatlay with gold Mangalsutra, Nath, Kolhapuri Saaj necklace and Bangdi bangles on red silk fabric with marigold flowers',
  badge: 'Sale'
},
{
  id: 7,
  name: 'Moonstone Choker',
  category: 'Necklaces',
  price: 58,
  rating: 4,
  reviewCount: 67,
  image: "/assets/images/maharashtrian_necklace_kolhapuri.png",
  alt: 'Maharashtrian woman wearing traditional Kolhapuri Saaj gold necklace, close-up portrait, green Paithani saree, warm golden studio lighting',
  isNew: true
},
{
  id: 8,
  name: 'Bridal Gift Set',
  category: 'Gift Sets',
  price: 128,
  rating: 5,
  reviewCount: 54,
  image: "/assets/images/maharashtrian_lady_full_jewelry.png",
  alt: 'Elegant Maharashtrian woman in traditional green Paithani saree wearing complete bridal jewelry set including Nath, Mangalsutra, gold bangles and Chandrakor earrings',
  badge: 'Limited'
}];


const StarRating: React.FC<{rating: number;size?: number;}> = ({ rating, size = 14 }) =>
<div className="flex items-center gap-0.5">
    {[1, 2, 3, 4, 5].map((star) =>
  <Icon
    key={star}
    name="StarIcon"
    size={size}
    variant={star <= rating ? 'solid' : 'outline'}
    className={star <= rating ? 'star-filled' : 'star-empty'} />

  )}
  </div>;


const ProductCard: React.FC<{product: Product;index: number;}> = ({ product, index }) => {
  const [wishlisted, setWishlisted] = useState(false);
  const [addedToCart, setAddedToCart] = useState(false);
  const cardRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const card = cardRef.current;
    if (!card) return;
    card.style.opacity = '0';
    card.style.transform = 'translateY(40px)';

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            setTimeout(() => {
              card.style.transition = 'opacity 0.8s cubic-bezier(0.16, 1, 0.3, 1), transform 0.8s cubic-bezier(0.16, 1, 0.3, 1)';
              card.style.opacity = '1';
              card.style.transform = 'translateY(0)';
            }, index * 80);
            observer.disconnect();
          }
        });
      },
      { threshold: 0.1 }
    );
    observer.observe(card);
    return () => observer.disconnect();
  }, [index]);

  const handleAddToCart = () => {
    setAddedToCart(true);
    setTimeout(() => setAddedToCart(false), 2000);
  };

  return (
    <div ref={cardRef} className="product-card group bg-card rounded-2xl overflow-hidden border border-border">
      {/* Image */}
      <div className="relative aspect-square overflow-hidden bg-muted">
        <AppImage
          src={product.image}
          alt={product.alt}
          fill
          className="object-cover group-hover:scale-110 transition-transform duration-700"
          sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 25vw" />


        {/* Overlay on hover */}
        <div className="absolute inset-0 bg-foreground/0 group-hover:bg-foreground/10 transition-colors duration-300" />

        {/* Badge */}
        {product.badge &&
        <div className={`absolute top-3 left-3 px-2.5 py-1 rounded-full text-[9px] font-black uppercase tracking-widest z-10
            ${product.badge === 'Sale' ? 'bg-accent text-white' :
        product.badge === 'Bestseller' ? 'bg-primary text-white' : 'bg-white border border-border text-foreground'}`}>
            {product.badge}
          </div>
        }
        {product.isNew && !product.badge &&
        <div className="absolute top-3 left-3 px-2.5 py-1 rounded-full text-[9px] font-black uppercase tracking-widest bg-white border border-primary/40 text-primary z-10">
            New
          </div>
        }

        {/* Wishlist */}
        <button
          onClick={() => setWishlisted(!wishlisted)}
          className={`absolute top-3 right-3 w-8 h-8 rounded-full bg-white/90 backdrop-blur-sm flex items-center justify-center wishlist-btn z-10 ${wishlisted ? 'active' : ''}`}
          aria-label={wishlisted ? 'Remove from wishlist' : 'Add to wishlist'}>

          <Icon
            name="HeartIcon"
            size={16}
            variant={wishlisted ? 'solid' : 'outline'}
            className={wishlisted ? 'text-accent' : 'text-muted-foreground'} />

        </button>

        {/* Quick add */}
        <div className="absolute bottom-3 left-3 right-3 opacity-0 group-hover:opacity-100 transition-all duration-300 translate-y-2 group-hover:translate-y-0 z-10">
          <button
            onClick={handleAddToCart}
            className={`w-full py-2.5 rounded-xl text-[10px] font-black uppercase tracking-widest transition-all ${
            addedToCart ?
            'bg-primary/30 text-primary border border-primary/40' : 'btn-primary'}`
            }>

            {addedToCart ? '✓ Added to Cart' : 'Quick Add'}
          </button>
        </div>
      </div>

      {/* Info */}
      <div className="p-4">
        <p className="text-[9px] font-bold uppercase tracking-widest text-muted-foreground mb-1">
          {product.category}
        </p>
        <h3 className="text-[15px] font-bold text-foreground mb-2 leading-tight">
          {product.name}
        </h3>
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
          <Link
            href="/sign-up-login"
            className="text-[10px] font-bold uppercase tracking-widest text-primary hover:text-accent transition-colors">

            View
          </Link>
        </div>
      </div>
    </div>);

};

const FeaturedProductsSection: React.FC = () => {
  return (
    <section className="pt-12 pb-20 px-6 lg:px-12">
      <div className="max-w-[1400px] mx-auto">
        {/* Header */}
        <div className="flex flex-col md:flex-row md:items-end justify-between gap-6 mb-12">
          <div>
            <span className="text-primary font-bold tracking-[0.4em] uppercase text-[10px] block mb-4">
              Handpicked for You
            </span>
            <h2 className="editorial-text font-extrabold text-foreground" style={{ fontSize: 'clamp(2rem, 5vw, 4.5rem)' }}>
              FEATURED
              <br />
              <span className="text-outline">PIECES</span>
            </h2>
          </div>
          <Link
            href="/products"
            className="flex items-center gap-2 text-[11px] font-bold uppercase tracking-widest text-muted-foreground hover:text-primary transition-colors">

            View All Products
            <Icon name="ArrowRightIcon" size={14} />
          </Link>
        </div>

        {/* Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {products.map((product, index) =>
          <ProductCard key={product.id} product={product} index={index} />
          )}
        </div>

        {/* CTA */}
        <div className="text-center mt-12">
          <Link
            href="/products"
            className="inline-flex items-center gap-3 px-12 py-4 btn-outline rounded-full text-[11px]">

            Browse All 553 Pieces
            <Icon name="ArrowRightIcon" size={14} className="text-primary" />
          </Link>
        </div>
      </div>
    </section>);

};

export default FeaturedProductsSection;