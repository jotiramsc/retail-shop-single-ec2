import React from 'react';
import Header from '@/components/Header';
import Footer from '@/components/Footer';
import ProductsHero from '@/app/products/components/ProductsHero';
import ProductsGrid from '@/app/products/components/ProductsGrid';

export default function ProductsPage() {
  return (
    <main className="min-h-screen bg-background">
      <Header cartCount={2} wishlistCount={3} />
      <ProductsHero />
      <ProductsGrid />
      <Footer />
    </main>
  );
}