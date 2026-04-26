'use client';

import React, { useEffect, useRef } from 'react';
import Link from 'next/link';
import AppImage from '@/components/ui/AppImage';
import Icon from '@/components/ui/AppIcon';

const differentiators = [
{
  icon: 'SparklesIcon' as const,
  title: 'Hypoallergenic Materials',
  description: 'Every piece uses surgical-grade alloys and 18k gold plating — safe for sensitive skin, built to last.'
},
{
  icon: 'ShieldCheckIcon' as const,
  title: '365-Day Guarantee',
  description: 'If your piece tarnishes, fades, or breaks within a year, we replace it free. No questions asked.'
},
{
  icon: 'TruckIcon' as const,
  title: 'Ships in 24 Hours',
  description: 'Orders placed before 2pm EST ship same day. Tracked delivery to all 50 states and 40+ countries.'
}];


const BrandStorySection: React.FC = () => {
  const leftRef = useRef<HTMLDivElement>(null);
  const rightRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const elements = [leftRef.current, rightRef.current].filter(Boolean) as HTMLElement[];
    elements.forEach((el, i) => {
      el.style.opacity = '0';
      el.style.transform = i === 0 ? 'translateX(-40px)' : 'translateX(40px)';
    });

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            elements.forEach((el, i) => {
              setTimeout(() => {
                el.style.transition = 'opacity 1s cubic-bezier(0.16, 1, 0.3, 1), transform 1s cubic-bezier(0.16, 1, 0.3, 1)';
                el.style.opacity = '1';
                el.style.transform = 'translateX(0)';
              }, i * 150);
            });
            observer.disconnect();
          }
        });
      },
      { threshold: 0.2 }
    );

    if (leftRef.current) observer.observe(leftRef.current);
    return () => observer.disconnect();
  }, []);

  return (
    <section className="pt-12 pb-20 px-6 lg:px-12">
      <div className="max-w-[1400px] mx-auto grid grid-cols-1 lg:grid-cols-2 gap-16 lg:gap-20 items-center">
        {/* Left: Image */}
        <div ref={leftRef} className="relative">
          <div className="relative aspect-[4/5] rounded-2xl overflow-hidden bg-muted">
            <AppImage
              src="https://img.rocket.new/generatedImages/rocket_gen_img_16b2ef368-1772200148160.png"
              alt="Jewelry artisan crafting a delicate rose gold piece at workbench, warm workshop lighting, natural wood textures, bright airy studio environment"
              fill
              className="object-cover"
              sizes="(max-width: 1024px) 100vw, 50vw" />

            {/* Light text on this image — scrim from bottom */}
            <div className="absolute inset-0 bg-gradient-to-t from-foreground/70 via-foreground/15 to-transparent" />

            {/* Floating stat */}
            <div className="absolute bottom-8 left-8 right-8">
              <div className="bg-white/90 backdrop-blur-xl border border-border rounded-2xl p-5">
                <div className="grid grid-cols-3 divide-x divide-border">
                  {[
                  { value: '2019', label: 'Founded' },
                  { value: '553+', label: 'Designs' },
                  { value: '24.8K', label: 'Customers' }].
                  map((stat) =>
                  <div key={stat.label} className="text-center px-4">
                      <p className="text-lg font-extrabold text-primary">{stat.value}</p>
                      <p className="text-[9px] font-bold uppercase tracking-widest text-muted-foreground mt-0.5">
                        {stat.label}
                      </p>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>

          {/* Decorative accent */}
          <div
            className="absolute -top-6 -right-6 w-32 h-32 rounded-full opacity-20 pointer-events-none"
            style={{ background: 'radial-gradient(circle, #F2A0B0 0%, transparent 70%)', filter: 'blur(20px)' }} />

        </div>

        {/* Right: Story + Differentiators */}
        <div ref={rightRef} className="flex flex-col justify-between gap-10">
          <div>
            <span className="text-primary font-bold tracking-[0.4em] uppercase text-[10px] block mb-6">
              Our Story
            </span>
            <h2 className="editorial-text font-extrabold text-foreground mb-6" style={{ fontSize: 'clamp(2rem, 4vw, 4rem)' }}>
              CRAFTED WITH
              <br />
              <span className="text-outline">INTENTION</span>
            </h2>
            <p className="text-muted-foreground text-base leading-relaxed max-w-lg">
              GlowJewels was born in 2019 from a simple belief: every woman deserves to feel adorned, 
              without the luxury markup. We design each piece with a jeweler&apos;s eye and a realist&apos;s budget — 
              so your everyday moments feel like occasions.
            </p>
          </div>

          {/* Differentiators */}
          <div className="flex flex-col gap-6">
            {differentiators.map((d) =>
            <div key={d.title} className="flex items-start gap-4 group">
                <div className="w-12 h-12 rounded-xl bg-primary/10 flex items-center justify-center shrink-0 group-hover:bg-primary/20 transition-colors">
                  <Icon name={d.icon} size={20} className="text-primary" />
                </div>
                <div>
                  <h3 className="text-[15px] font-bold text-foreground mb-1">{d.title}</h3>
                  <p className="text-[13px] text-muted-foreground leading-relaxed">{d.description}</p>
                </div>
              </div>
            )}
          </div>

          {/* CTA */}
          <div className="flex items-center gap-4">
            <Link
              href="/products"
              className="px-10 py-4 btn-primary rounded-full text-[11px] inline-flex items-center gap-2">

              Shop Now
              <Icon name="ArrowRightIcon" size={14} className="text-white" />
            </Link>
            <Link
              href="/homepage"
              className="flex items-center gap-2 text-[11px] font-bold uppercase tracking-widest text-muted-foreground hover:text-primary transition-colors">

              Our Story
              <Icon name="ArrowRightIcon" size={14} />
            </Link>
          </div>
        </div>
      </div>
    </section>);

};

export default BrandStorySection;