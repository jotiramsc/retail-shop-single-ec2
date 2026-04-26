'use client';

import React, { useState, useEffect } from 'react';
import Link from 'next/link';
import AppLogo from '@/components/ui/AppLogo';
import Icon from '@/components/ui/AppIcon';

interface HeaderProps {
  cartCount?: number;
  wishlistCount?: number;
}

const Header: React.FC<HeaderProps> = ({ cartCount = 0, wishlistCount = 0 }) => {
  const [scrolled, setScrolled] = useState(false);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    const handleScroll = () => setScrolled(window.scrollY > 60);
    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  useEffect(() => {
    if (mobileOpen) {
      const handleScroll = () => setMobileOpen(false);
      window.addEventListener('scroll', handleScroll, { passive: true });
      return () => window.removeEventListener('scroll', handleScroll);
    }
  }, [mobileOpen]);

  const navLinks = [
    { label: 'Collections', href: '/products' },
    { label: 'Rings', href: '/products' },
    { label: 'Necklaces', href: '/products' },
    { label: 'Earrings', href: '/products' },
  ];

  return (
    <>
      <header
        className={`fixed top-0 left-0 w-full z-50 transition-all duration-500 ${
          scrolled
            ? 'bg-white/95 backdrop-blur-xl border-b border-border shadow-sm'
            : 'bg-transparent'
        }`}
      >
        <div className="max-w-[1400px] mx-auto px-6 lg:px-12 h-20 flex items-center justify-between">
          {/* Logo */}
          <Link href="/homepage" className="flex items-center gap-2 group">
            <AppLogo size={36} />
            <span className="font-bold text-lg tracking-tight text-foreground group-hover:text-primary transition-colors">
              GlowJewels
            </span>
          </Link>

          {/* Desktop Nav */}
          <nav className="hidden lg:flex items-center gap-8">
            {navLinks.map((link) => (
              <Link
                key={link.label}
                href={link.href}
                className="text-[11px] font-bold uppercase tracking-[0.25em] text-muted-foreground hover:text-primary transition-colors"
              >
                {link.label}
              </Link>
            ))}
          </nav>

          {/* Actions */}
          <div className="flex items-center gap-3">
            {/* Wishlist */}
            <Link
              href="/homepage"
              className="relative flex items-center justify-center w-10 h-10 rounded-full hover:bg-muted transition-colors"
              aria-label="Wishlist"
            >
              <Icon name="HeartIcon" size={20} className="text-muted-foreground hover:text-accent transition-colors" />
              {wishlistCount > 0 && (
                <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-accent text-white text-[9px] font-black rounded-full flex items-center justify-center">
                  {wishlistCount}
                </span>
              )}
            </Link>

            {/* Cart */}
            <Link
              href="/homepage"
              className="relative flex items-center justify-center w-10 h-10 rounded-full hover:bg-muted transition-colors"
              aria-label="Shopping cart"
            >
              <Icon name="ShoppingBagIcon" size={20} className="text-muted-foreground hover:text-primary transition-colors" />
              {cartCount > 0 && (
                <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-primary text-white text-[9px] font-black rounded-full flex items-center justify-center">
                  {cartCount}
                </span>
              )}
            </Link>

            {/* Account */}
            <Link
              href="/sign-up-login"
              className="hidden sm:flex items-center gap-2 px-6 py-2.5 btn-primary rounded-full text-[11px]"
            >
              <Icon name="UserIcon" size={14} className="text-white" />
              <span>Account</span>
            </Link>

            {/* Mobile Menu Toggle */}
            <button
              className="flex lg:hidden items-center justify-center w-10 h-10 rounded-full hover:bg-muted transition-colors"
              onClick={() => setMobileOpen(!mobileOpen)}
              aria-label={mobileOpen ? 'Close menu' : 'Open menu'}
            >
              <Icon name={mobileOpen ? 'XMarkIcon' : 'Bars3Icon'} size={22} className="text-foreground" />
            </button>
          </div>
        </div>
      </header>

      {/* Mobile Menu Overlay */}
      {mobileOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div
            className="absolute inset-0 bg-foreground/20 backdrop-blur-md"
            onClick={() => setMobileOpen(false)}
          />
          <div className="absolute top-20 left-0 right-0 bg-white border-b border-border p-8 flex flex-col gap-6">
            {navLinks.map((link) => (
              <Link
                key={link.label}
                href={link.href}
                className="text-[13px] font-bold uppercase tracking-[0.25em] text-muted-foreground hover:text-primary transition-colors py-2"
                onClick={() => setMobileOpen(false)}
              >
                {link.label}
              </Link>
            ))}
            <Link
              href="/sign-up-login"
              className="flex items-center justify-center gap-2 px-6 py-3 btn-primary rounded-full text-[11px] mt-2"
              onClick={() => setMobileOpen(false)}
            >
              <Icon name="UserIcon" size={14} className="text-white" />
              <span>Sign In / Create Account</span>
            </Link>
          </div>
        </div>
      )}
    </>
  );
};

export default Header;