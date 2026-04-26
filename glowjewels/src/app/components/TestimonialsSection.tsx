'use client';

import React, { useEffect, useRef } from 'react';
import AppImage from '@/components/ui/AppImage';
import Icon from '@/components/ui/AppIcon';

interface Testimonial {
  id: number;
  name: string;
  location: string;
  avatar: string;
  avatarAlt: string;
  quote: string;
  rating: number;
  product: string;
  verified: boolean;
}

const testimonials: Testimonial[] = [
{
  id: 1,
  name: 'Mia Thornton',
  location: 'New York, NY',
  avatar: "https://img.rocket.new/generatedImages/rocket_gen_img_1bfddacc6-1772205842001.png",
  avatarAlt: 'Young woman with dark hair smiling, warm natural lighting, portrait photo',
  quote: "I wore the Celestial Rose Ring to my sister's wedding and got 12 compliments before the ceremony even started. The quality is genuinely indistinguishable from pieces 5x the price.",
  rating: 5,
  product: 'Celestial Rose Ring',
  verified: true
},
{
  id: 2,
  name: 'Jasmine Park',
  location: 'Los Angeles, CA',
  avatar: "https://img.rocket.new/generatedImages/rocket_gen_img_1a3bbaf5d-1770154803983.png",
  avatarAlt: 'Woman with long dark hair and bright smile, natural daylight portrait',
  quote: "The Aurora earrings are my daily staples now. They haven't tarnished in 4 months of daily wear — I shower with them, sleep in them. Obsessed doesn't cover it.",
  rating: 5,
  product: 'Aurora Drop Earrings',
  verified: true
},
{
  id: 3,
  name: 'Sofia Reyes',
  location: 'Miami, FL',
  avatar: "https://img.rocket.new/generatedImages/rocket_gen_img_1e91f72fe-1772071256926.png",
  avatarAlt: 'Smiling woman with curly hair, bright natural light portrait photography',
  quote: "Bought the bridal gift set for my best friend's bachelorette. The packaging alone made her cry. She's been wearing the bracelet every single day since.",
  rating: 5,
  product: 'Bridal Gift Set',
  verified: true
},
{
  id: 4,
  name: 'Priya Kapoor',
  location: 'Chicago, IL',
  avatar: "https://img.rocket.new/generatedImages/rocket_gen_img_18bf87a42-1772070280259.png",
  avatarAlt: 'Woman with warm smile and dark eyes, soft natural lighting portrait',
  quote: "Finally a jewelry brand that ships fast AND packages beautifully. My Halo pendant arrived in 2 days, gift-wrapped in a black box. Felt like a luxury unboxing experience.",
  rating: 4,
  product: 'Halo Diamond Pendant',
  verified: true
}];


const StarRating: React.FC<{rating: number;}> = ({ rating }) =>
<div className="flex items-center gap-0.5">
    {[1, 2, 3, 4, 5].map((star) =>
  <Icon
    key={star}
    name="StarIcon"
    size={12}
    variant={star <= rating ? 'solid' : 'outline'}
    className={star <= rating ? 'star-filled' : 'star-empty'} />

  )}
  </div>;


const TestimonialsSection: React.FC = () => {
  const sectionRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const cards = sectionRef.current?.querySelectorAll<HTMLElement>('.testimonial-card');
    if (!cards) return;

    cards.forEach((card) => {
      card.style.opacity = '0';
      card.style.transform = 'translateY(50px)';
    });

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            const allCards = sectionRef.current?.querySelectorAll<HTMLElement>('.testimonial-card');
            allCards?.forEach((card, i) => {
              setTimeout(() => {
                card.style.transition = 'opacity 0.9s cubic-bezier(0.16, 1, 0.3, 1), transform 0.9s cubic-bezier(0.16, 1, 0.3, 1)';
                card.style.opacity = '1';
                card.style.transform = 'translateY(0)';
              }, i * 100);
            });
            observer.disconnect();
          }
        });
      },
      { threshold: 0.1 }
    );

    if (sectionRef.current) observer.observe(sectionRef.current);
    return () => observer.disconnect();
  }, []);

  return (
    <section className="pt-12 pb-20 px-6 lg:px-12 bg-secondary" ref={sectionRef}>
      <div className="max-w-[1400px] mx-auto">
        {/* Header */}
        <div className="text-center mb-16">
          <span className="text-primary font-bold tracking-[0.4em] uppercase text-[10px] block mb-4">
            Real Customers · Verified Reviews
          </span>
          <h2 className="editorial-text font-extrabold text-foreground" style={{ fontSize: 'clamp(2rem, 5vw, 4.5rem)' }}>
            WORN &amp; LOVED
          </h2>

          {/* Aggregate rating */}
          <div className="flex items-center justify-center gap-4 mt-6">
            <div className="flex items-center gap-1">
              {[1, 2, 3, 4, 5].map((s) =>
              <Icon key={s} name="StarIcon" size={18} variant="solid" className="star-filled" />
              )}
            </div>
            <span className="text-2xl font-extrabold text-foreground">4.9</span>
            <span className="text-muted-foreground text-sm">from 6,240 reviews</span>
          </div>
        </div>

        {/* Testimonials Grid */}
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6">
          {testimonials.map((t, idx) =>
          <div
            key={t.id}
            className={`testimonial-card bg-card border border-border rounded-2xl p-6 flex flex-col gap-4 hover:border-primary/30 transition-colors ${
            idx === 0 ? 'xl:col-span-2' : ''}`
            }>

              {/* Stars */}
              <StarRating rating={t.rating} />

              {/* Quote */}
              <blockquote className="text-[14px] text-foreground/80 leading-relaxed flex-1">
                &ldquo;{t.quote}&rdquo;
              </blockquote>

              {/* Product tag */}
              <div className="flex items-center gap-2">
                <span className="px-2 py-1 bg-primary/10 text-primary text-[9px] font-bold uppercase tracking-widest rounded-full">
                  {t.product}
                </span>
                {t.verified &&
              <span className="flex items-center gap-1 text-[9px] font-bold uppercase tracking-widest text-muted-foreground">
                    <Icon name="CheckBadgeIcon" size={12} className="text-primary" variant="solid" />
                    Verified
                  </span>
              }
              </div>

              {/* Author */}
              <div className="flex items-center gap-3 pt-2 border-t border-border">
                <div className="w-10 h-10 rounded-full overflow-hidden shrink-0">
                  <AppImage
                  src={t.avatar}
                  alt={t.avatarAlt}
                  width={40}
                  height={40}
                  className="object-cover w-full h-full" />

                </div>
                <div>
                  <p className="text-[13px] font-bold text-foreground">{t.name}</p>
                  <p className="text-[11px] text-muted-foreground">{t.location}</p>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>
    </section>);

};

export default TestimonialsSection;