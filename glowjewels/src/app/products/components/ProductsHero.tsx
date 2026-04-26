import React from 'react';
import Link from 'next/link';
import Icon from '@/components/ui/AppIcon';

const ProductsHero: React.FC = () => {
  return (
    <section className="pt-32 pb-12 px-6 lg:px-12 border-b border-border">
      <div className="max-w-[1400px] mx-auto">
        {/* Breadcrumb */}
        <nav className="flex items-center gap-2 text-[11px] font-bold uppercase tracking-widest text-muted-foreground mb-8">
          <Link href="/homepage" className="hover:text-primary transition-colors">Home</Link>
          <Icon name="ChevronRightIcon" size={12} className="text-muted-foreground" />
          <span className="text-foreground">Collections</span>
        </nav>

        <div className="flex flex-col md:flex-row md:items-end justify-between gap-6">
          <div>
            <span className="text-primary font-bold tracking-[0.4em] uppercase text-[10px] block mb-4">
              553 Pieces Available
            </span>
            <h1 className="editorial-text font-extrabold text-foreground" style={{ fontSize: 'clamp(2.5rem, 6vw, 6rem)' }}>
              ALL
              <br />
              <span className="text-outline">COLLECTIONS</span>
            </h1>
          </div>
          <p className="text-muted-foreground text-base max-w-sm leading-relaxed">
            Rings, necklaces, earrings, bracelets — handpicked for women who lead with elegance.
          </p>
        </div>
      </div>
    </section>
  );
};

export default ProductsHero;