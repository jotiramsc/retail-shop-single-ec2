import React from 'react';
import Link from 'next/link';
import AppLogo from '@/components/ui/AppLogo';
import Icon from '@/components/ui/AppIcon';

const Footer: React.FC = () => {
  const productLinks = [
    { label: 'Rings', href: '/products' },
    { label: 'Necklaces', href: '/products' },
    { label: 'Earrings', href: '/products' },
    { label: 'Bracelets', href: '/products' },
  ];

  const companyLinks = [
    { label: 'Our Story', href: '/homepage' },
    { label: 'Sustainability', href: '/homepage' },
    { label: 'Press', href: '/homepage' },
  ];

  const supportLinks = [
    { label: 'Shipping Info', href: '/homepage' },
    { label: 'Returns', href: '/homepage' },
    { label: 'Track Order', href: '/homepage' },
    { label: 'Contact', href: '/homepage' },
  ];

  return (
    <footer className="border-t border-border pt-16 pb-8 px-6 lg:px-12 bg-secondary">
      <div className="max-w-[1400px] mx-auto">
        {/* Top Row */}
        <div className="flex flex-wrap gap-10 justify-between mb-12">
          {/* Brand */}
          <div className="flex flex-col gap-4 min-w-[180px]">
            <Link href="/homepage" className="flex items-center gap-2">
              <AppLogo size={32} />
              <span className="font-bold text-base tracking-tight text-foreground">GlowJewels</span>
            </Link>
            <p className="text-[13px] text-muted-foreground leading-relaxed max-w-[200px]">
              Luxury cosmetic jewelry for everyday elegance.
            </p>
          </div>

          {/* Links Groups */}
          <div className="flex flex-wrap gap-10">
            <div className="flex flex-col gap-3">
              <span className="text-[10px] font-bold uppercase tracking-widest text-primary mb-1">Shop</span>
              {productLinks.map((l) => (
                <Link key={l.label} href={l.href} className="text-[14px] font-500 text-muted-foreground hover:text-foreground transition-colors">
                  {l.label}
                </Link>
              ))}
            </div>
            <div className="flex flex-col gap-3">
              <span className="text-[10px] font-bold uppercase tracking-widest text-primary mb-1">Company</span>
              {companyLinks.map((l) => (
                <Link key={l.label} href={l.href} className="text-[14px] text-muted-foreground hover:text-foreground transition-colors">
                  {l.label}
                </Link>
              ))}
            </div>
            <div className="flex flex-col gap-3">
              <span className="text-[10px] font-bold uppercase tracking-widest text-primary mb-1">Support</span>
              {supportLinks.map((l) => (
                <Link key={l.label} href={l.href} className="text-[14px] text-muted-foreground hover:text-foreground transition-colors">
                  {l.label}
                </Link>
              ))}
            </div>
          </div>

          {/* Social */}
          <div className="flex flex-col gap-4">
            <span className="text-[10px] font-bold uppercase tracking-widest text-primary">Follow Us</span>
            <div className="flex items-center gap-3">
              {[
                { icon: 'GlobeAltIcon', label: 'Instagram' },
                { icon: 'ChatBubbleOvalLeftIcon', label: 'Pinterest' },
                { icon: 'VideoCameraIcon', label: 'TikTok' },
              ].map((s) => (
                <a
                  key={s.label}
                  href="#"
                  aria-label={s.label}
                  className="w-10 h-10 rounded-full border border-border flex items-center justify-center text-muted-foreground hover:text-primary hover:border-primary transition-all"
                >
                  <Icon name={s.icon as Parameters<typeof Icon>[0]['name']} size={16} />
                </a>
              ))}
            </div>
          </div>
        </div>

        {/* Bottom Row */}
        <div className="border-t border-border pt-6 flex flex-col sm:flex-row items-center justify-between gap-4">
          <p className="text-[13px] text-muted-foreground">
            © 2026 GlowJewels. All rights reserved.
          </p>
          <div className="flex items-center gap-6">
            <Link href="/homepage" className="text-[13px] text-muted-foreground hover:text-foreground transition-colors">
              Privacy Policy
            </Link>
            <Link href="/homepage" className="text-[13px] text-muted-foreground hover:text-foreground transition-colors">
              Terms of Service
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
};

export default Footer;