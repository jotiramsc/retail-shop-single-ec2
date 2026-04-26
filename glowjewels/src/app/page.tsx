import React from 'react';
import Header from '@/components/Header';
import Footer from '@/components/Footer';
import HeroSection from '@/app/components/HeroSection';
import CategoryBentoSection from '@/app/components/CategoryBentoSection';
import FeaturedProductsSection from '@/app/components/FeaturedProductsSection';
import TestimonialsSection from '@/app/components/TestimonialsSection';
import BrandStorySection from '@/app/components/BrandStorySection';

export default function HomePage() {
  return (
    <main className="min-h-screen bg-background grid-lines">
      <Header cartCount={2} wishlistCount={3} />
      <HeroSection />
      <CategoryBentoSection />
      <FeaturedProductsSection />
      <TestimonialsSection />
      <BrandStorySection />
      <Footer />
    </main>
  );
}