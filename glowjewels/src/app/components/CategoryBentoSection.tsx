'use client';

import React, { useEffect, useRef } from 'react';
import Link from 'next/link';
import AppImage from '@/components/ui/AppImage';
import Icon from '@/components/ui/AppIcon';

interface Category {
  id: string;
  name: string;
  count: string;
  image: string;
  alt: string;
  colSpan: string;
  rowSpan: string;
  aspectClass: string;
}

// BENTO AUDIT:
// Array has 5 cards: [Rings, Necklaces, Earrings, Bracelets, Sets]
// Grid: 3 columns desktop
// Row 1: [col-1: Rings cs-1 rs-2] [col-2: Necklaces cs-1 rs-1] [col-3: Earrings cs-1 rs-1]
// Row 2: [col-1: FILLED by Rings rs-2] [col-2: Bracelets cs-1 rs-1] [col-3: Sets cs-1 rs-1]
// Placed 5/5 cards ✓

const categories: Category[] = [
{
  id: 'rings',
  name: 'Rings',
  count: '148 styles',
  image: "/assets/images/maharashtrian_jewelry_flatlay.png",
  alt: 'Traditional Maharashtrian bridal jewelry flatlay with gold Mangalsutra, Nath, Kolhapuri Saaj necklace and Bangdi bangles on red silk fabric with marigold flowers',
  colSpan: 'lg:col-span-1',
  rowSpan: 'lg:row-span-2',
  aspectClass: 'aspect-[3/4] lg:aspect-auto lg:h-full'
},
{
  id: 'necklaces',
  name: 'Necklaces',
  count: '92 styles',
  image: "/assets/images/maharashtrian_necklace_kolhapuri.png",
  alt: 'Maharashtrian woman wearing traditional Kolhapuri Saaj gold necklace with intricate pendant, green Paithani saree, warm golden studio lighting',
  colSpan: 'lg:col-span-1',
  rowSpan: 'lg:row-span-1',
  aspectClass: 'aspect-[4/3]'
},
{
  id: 'earrings',
  name: 'Earrings',
  count: '203 styles',
  image: "/assets/images/maharashtrian_chandrakor_earrings.png",
  alt: 'Traditional Maharashtrian Chandrakor crescent moon shaped gold earrings with intricate filigree work and pearl drops, warm golden lighting',
  colSpan: 'lg:col-span-1',
  rowSpan: 'lg:row-span-1',
  aspectClass: 'aspect-[4/3]'
},
{
  id: 'bracelets',
  name: 'Bracelets',
  count: '76 styles',
  image: "/assets/images/maharashtrian_bangdi_bangles.png",
  alt: 'Maharashtrian woman wearing traditional gold Bangdi and Patlya bangles, close-up of wrists adorned with intricate gold bangles, green Paithani silk saree',
  colSpan: 'lg:col-span-1',
  rowSpan: 'lg:row-span-1',
  aspectClass: 'aspect-[4/3]'
},
{
  id: 'sets',
  name: 'Gift Sets',
  count: '34 curated sets',
  image: "/assets/images/maharashtrian_lady_full_jewelry.png",
  alt: 'Elegant Maharashtrian woman in traditional green Paithani saree with gold zari border wearing complete bridal jewelry set including Nath, Mangalsutra, gold bangles and Chandrakor earrings',
  colSpan: 'lg:col-span-1',
  rowSpan: 'lg:row-span-1',
  aspectClass: 'aspect-[4/3]'
}];


const CategoryBentoSection: React.FC = () => {
  const sectionRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const cards = sectionRef.current?.querySelectorAll<HTMLElement>('.bento-card');
    if (!cards) return;

    cards.forEach((card) => {
      card.style.opacity = '0';
      card.style.transform = 'translateY(50px)';
    });

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            const allCards = sectionRef.current?.querySelectorAll<HTMLElement>('.bento-card');
            allCards?.forEach((card, i) => {
              setTimeout(() => {
                card.style.transition = `opacity 0.9s cubic-bezier(0.16, 1, 0.3, 1), transform 0.9s cubic-bezier(0.16, 1, 0.3, 1)`;
                card.style.opacity = '1';
                card.style.transform = 'translateY(0)';
              }, i * 120);
            });
            observer.disconnect();
          }
        });
      },
      { threshold: 0.15 }
    );

    if (sectionRef.current) observer.observe(sectionRef.current);
    return () => observer.disconnect();
  }, []);

  return (
    <section className="pt-12 pb-20 px-6 lg:px-12" ref={sectionRef}>
      <div className="max-w-[1400px] mx-auto">
        {/* Header */}
        <div className="flex flex-col md:flex-row md:items-end justify-between gap-6 mb-12">
          <div>
            <span className="text-primary font-bold tracking-[0.4em] uppercase text-[10px] block mb-4">
              Browse by Category
            </span>
            <h2 className="editorial-text font-extrabold text-foreground" style={{ fontSize: 'clamp(2rem, 5vw, 4.5rem)' }}>
              SHOP THE
              <br />
              <span className="text-outline">COLLECTION</span>
            </h2>
          </div>
          <Link
            href="/products"
            className="flex items-center gap-2 text-[11px] font-bold uppercase tracking-widest text-muted-foreground hover:text-primary transition-colors">

            View All Categories
            <Icon name="ArrowRightIcon" size={14} />
          </Link>
        </div>

        {/* Bento Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 lg:grid-rows-2">
          {/* Card: Rings — col-span-1 row-span-2 */}
          <div className={`bento-card category-card rounded-2xl cursor-pointer ${categories[0].colSpan} ${categories[0].rowSpan} ${categories[0].aspectClass} min-h-[300px] lg:min-h-0`}>
            <Link href="/products" className="block w-full h-full relative">
              <AppImage
                src={categories[0].image}
                alt={categories[0].alt}
                fill
                className="object-cover"
                sizes="(max-width: 768px) 100vw, 33vw" />

              <div className="absolute inset-0 z-10 flex flex-col justify-end p-6">
                <span className="text-[9px] font-bold uppercase tracking-[0.4em] text-white/60 mb-1">
                  {categories[0].count}
                </span>
                <h3 className="text-3xl font-extrabold text-white tracking-tight">{categories[0].name}</h3>
                <div className="mt-3 flex items-center gap-2 text-[10px] font-bold uppercase tracking-widest text-primary">
                  Shop Now <Icon name="ArrowRightIcon" size={12} className="text-primary" />
                </div>
              </div>
            </Link>
          </div>

          {/* Card: Necklaces — col-span-1 row-span-1 */}
          <div className={`bento-card category-card rounded-2xl cursor-pointer ${categories[1].colSpan} ${categories[1].rowSpan} ${categories[1].aspectClass}`}>
            <Link href="/products" className="block w-full h-full relative">
              <AppImage
                src={categories[1].image}
                alt={categories[1].alt}
                fill
                className="object-cover"
                sizes="(max-width: 768px) 100vw, 33vw" />

              <div className="absolute inset-0 z-10 flex flex-col justify-end p-6">
                <span className="text-[9px] font-bold uppercase tracking-[0.4em] text-white/60 mb-1">
                  {categories[1].count}
                </span>
                <h3 className="text-2xl font-extrabold text-white tracking-tight">{categories[1].name}</h3>
                <div className="mt-2 flex items-center gap-2 text-[10px] font-bold uppercase tracking-widest text-primary">
                  Shop Now <Icon name="ArrowRightIcon" size={12} className="text-primary" />
                </div>
              </div>
            </Link>
          </div>

          {/* Card: Earrings — col-span-1 row-span-1 */}
          <div className={`bento-card category-card rounded-2xl cursor-pointer ${categories[2].colSpan} ${categories[2].rowSpan} ${categories[2].aspectClass}`}>
            <Link href="/products" className="block w-full h-full relative">
              <AppImage
                src={categories[2].image}
                alt={categories[2].alt}
                fill
                className="object-cover"
                sizes="(max-width: 768px) 100vw, 33vw" />

              <div className="absolute inset-0 z-10 flex flex-col justify-end p-6">
                <span className="text-[9px] font-bold uppercase tracking-[0.4em] text-white/60 mb-1">
                  {categories[2].count}
                </span>
                <h3 className="text-2xl font-extrabold text-white tracking-tight">{categories[2].name}</h3>
                <div className="mt-2 flex items-center gap-2 text-[10px] font-bold uppercase tracking-widest text-primary">
                  Shop Now <Icon name="ArrowRightIcon" size={12} className="text-primary" />
                </div>
              </div>
            </Link>
          </div>

          {/* Card: Bracelets — col-span-1 row-span-1 */}
          <div className={`bento-card category-card rounded-2xl cursor-pointer ${categories[3].colSpan} ${categories[3].rowSpan} ${categories[3].aspectClass}`}>
            <Link href="/products" className="block w-full h-full relative">
              <AppImage
                src={categories[3].image}
                alt={categories[3].alt}
                fill
                className="object-cover"
                sizes="(max-width: 768px) 100vw, 33vw" />

              <div className="absolute inset-0 z-10 flex flex-col justify-end p-6">
                <span className="text-[9px] font-bold uppercase tracking-[0.4em] text-white/60 mb-1">
                  {categories[3].count}
                </span>
                <h3 className="text-2xl font-extrabold text-white tracking-tight">{categories[3].name}</h3>
                <div className="mt-2 flex items-center gap-2 text-[10px] font-bold uppercase tracking-widest text-primary">
                  Shop Now <Icon name="ArrowRightIcon" size={12} className="text-primary" />
                </div>
              </div>
            </Link>
          </div>

          {/* Card: Sets — col-span-1 row-span-1 */}
          <div className={`bento-card category-card rounded-2xl cursor-pointer ${categories[4].colSpan} ${categories[4].rowSpan} ${categories[4].aspectClass}`}>
            <Link href="/products" className="block w-full h-full relative">
              <AppImage
                src={categories[4].image}
                alt={categories[4].alt}
                fill
                className="object-cover"
                sizes="(max-width: 768px) 100vw, 33vw" />

              <div className="absolute inset-0 z-10 flex flex-col justify-end p-6">
                <span className="text-[9px] font-bold uppercase tracking-[0.4em] text-white/60 mb-1">
                  {categories[4].count}
                </span>
                <h3 className="text-2xl font-extrabold text-white tracking-tight">{categories[4].name}</h3>
                <div className="mt-2 flex items-center gap-2 text-[10px] font-bold uppercase tracking-widest text-primary">
                  Shop Now <Icon name="ArrowRightIcon" size={12} className="text-primary" />
                </div>
              </div>
            </Link>
          </div>
        </div>
      </div>
    </section>);

};

export default CategoryBentoSection;