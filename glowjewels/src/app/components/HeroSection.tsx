'use client';

import React, { useEffect, useRef } from 'react';
import Link from 'next/link';
import AppImage from '@/components/ui/AppImage';
import Icon from '@/components/ui/AppIcon';

const HeroSection: React.FC = () => {
  const headlineRef = useRef<HTMLHeadingElement>(null);
  const metaRef = useRef<HTMLDivElement>(null);
  const imageRef = useRef<HTMLDivElement>(null);
  const verticalTextRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const elements = [
    headlineRef.current,
    metaRef.current,
    imageRef.current,
    verticalTextRef.current].
    filter(Boolean) as HTMLElement[];

    elements.forEach((el, i) => {
      el.style.opacity = '0';
      el.style.transform = 'translateY(60px)';
      el.style.transition = `opacity 1.2s cubic-bezier(0.16, 1, 0.3, 1) ${i * 0.18}s, transform 1.2s cubic-bezier(0.16, 1, 0.3, 1) ${i * 0.18}s`;
    });

    const timer = setTimeout(() => {
      elements.forEach((el) => {
        el.style.opacity = '1';
        el.style.transform = 'translateY(0)';
      });
    }, 100);

    return () => clearTimeout(timer);
  }, []);

  useEffect(() => {
    const headline = headlineRef.current;
    if (!headline) return;

    const handleMouseMove = (e: MouseEvent) => {
      const xPos = (e.clientX - window.innerWidth / 2) / 80;
      const yPos = (e.clientY - window.innerHeight / 2) / 80;
      headline.style.transform = `translate(${xPos}px, ${yPos}px)`;
    };

    window.addEventListener('mousemove', handleMouseMove);
    return () => window.removeEventListener('mousemove', handleMouseMove);
  }, []);

  return (
    <section className="relative min-h-screen flex flex-col justify-center px-6 lg:px-12 overflow-hidden pt-20">
      {/* Rich Maharashtrian-inspired decorative background */}
      <div className="absolute inset-0 pointer-events-none overflow-hidden">
        {/* Warm saffron-gold gradient base */}
        <div
          className="absolute inset-0"
          style={{
            background: 'linear-gradient(135deg, #FDF8F4 0%, #FFF3E8 30%, #FDF0E0 60%, #FDF8F4 100%)'
          }}
        />

        {/* Top-right mandala decorative circle */}
        <div
          className="absolute -top-32 -right-32 w-[600px] h-[600px] rounded-full opacity-[0.07]"
          style={{
            background: 'conic-gradient(from 0deg, #B8733A, #D4A017, #C8895A, #B8733A, #D4607A, #B8733A)',
            filter: 'blur(2px)'
          }}
        />
        {/* Mandala ring detail top-right */}
        <div
          className="absolute -top-20 -right-20 w-[500px] h-[500px] rounded-full opacity-[0.05]"
          style={{
            border: '40px solid transparent',
            borderImage: 'conic-gradient(#B8733A, #D4A017, #B8733A) 1',
            background: 'transparent'
          }}
        />

        {/* Bottom-left decorative paisley glow */}
        <div
          className="absolute -bottom-40 -left-40 w-[700px] h-[700px] rounded-full opacity-[0.08]"
          style={{
            background: 'conic-gradient(from 45deg, #D4607A, #B8733A, #D4A017, #C8895A, #D4607A)',
            filter: 'blur(3px)'
          }}
        />

        {/* Center atmospheric warm glow */}
        <div
          className="absolute top-1/3 left-1/4 w-[500px] h-[500px] rounded-full opacity-20"
          style={{ background: 'radial-gradient(circle, rgba(212,160,23,0.25) 0%, transparent 70%)', filter: 'blur(60px)' }}
        />
        <div
          className="absolute bottom-1/4 right-1/3 w-[350px] h-[350px] rounded-full opacity-15"
          style={{ background: 'radial-gradient(circle, rgba(212,96,122,0.2) 0%, transparent 70%)', filter: 'blur(50px)' }}
        />

        {/* Decorative SVG mandala pattern top-right */}
        <svg
          className="absolute top-0 right-0 w-[420px] h-[420px] opacity-[0.06]"
          viewBox="0 0 400 400"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
        >
          <circle cx="200" cy="200" r="180" stroke="#B8733A" strokeWidth="1.5" strokeDasharray="8 4" />
          <circle cx="200" cy="200" r="140" stroke="#D4A017" strokeWidth="1" strokeDasharray="6 6" />
          <circle cx="200" cy="200" r="100" stroke="#B8733A" strokeWidth="1.5" strokeDasharray="4 4" />
          <circle cx="200" cy="200" r="60" stroke="#D4607A" strokeWidth="1" strokeDasharray="3 5" />
          {[0,30,60,90,120,150,180,210,240,270,300,330].map((angle, i) => (
            <line
              key={i}
              x1="200" y1="20"
              x2="200" y2="380"
              stroke="#B8733A"
              strokeWidth="0.5"
              strokeDasharray="2 8"
              transform={`rotate(${angle} 200 200)`}
            />
          ))}
          {[0,45,90,135,180,225,270,315].map((angle, i) => (
            <ellipse
              key={i}
              cx="200" cy="110"
              rx="12" ry="28"
              fill="#D4A017"
              opacity="0.4"
              transform={`rotate(${angle} 200 200)`}
            />
          ))}
        </svg>

        {/* Decorative SVG mandala pattern bottom-left */}
        <svg
          className="absolute bottom-0 left-0 w-[320px] h-[320px] opacity-[0.05]"
          viewBox="0 0 400 400"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
        >
          <circle cx="200" cy="200" r="180" stroke="#D4607A" strokeWidth="1.5" strokeDasharray="8 4" />
          <circle cx="200" cy="200" r="130" stroke="#B8733A" strokeWidth="1" strokeDasharray="6 6" />
          <circle cx="200" cy="200" r="80" stroke="#D4A017" strokeWidth="1.5" strokeDasharray="4 4" />
          {[0,40,80,120,160,200,240,280,320].map((angle, i) => (
            <ellipse
              key={i}
              cx="200" cy="90"
              rx="10" ry="22"
              fill="#D4607A"
              opacity="0.35"
              transform={`rotate(${angle} 200 200)`}
            />
          ))}
        </svg>

        {/* Horizontal decorative border lines */}
        <div
          className="absolute top-[18%] left-0 right-0 h-px opacity-[0.12]"
          style={{ background: 'linear-gradient(to right, transparent, #B8733A 20%, #D4A017 50%, #B8733A 80%, transparent)' }}
        />
        <div
          className="absolute bottom-[18%] left-0 right-0 h-px opacity-[0.10]"
          style={{ background: 'linear-gradient(to right, transparent, #D4607A 20%, #B8733A 50%, #D4607A 80%, transparent)' }}
        />
      </div>

      {/* Sparkle decorations */}
      <div className="absolute top-[30%] left-[8%] pointer-events-none hidden lg:block">
        <Icon name="SparklesIcon" size={16} className="text-primary opacity-40 animate-sparkle" />
      </div>
      <div className="absolute top-[55%] left-[12%] pointer-events-none hidden lg:block" style={{ animationDelay: '0.8s' }}>
        <Icon name="SparklesIcon" size={10} className="text-accent opacity-30 animate-sparkle" />
      </div>
      <div className="absolute top-[20%] right-[38%] pointer-events-none hidden lg:block" style={{ animationDelay: '1.4s' }}>
        <Icon name="SparklesIcon" size={12} className="text-primary opacity-35 animate-sparkle" />
      </div>
      <div className="absolute top-[70%] right-[10%] pointer-events-none hidden lg:block" style={{ animationDelay: '2s' }}>
        <Icon name="SparklesIcon" size={14} className="text-accent opacity-25 animate-sparkle" />
      </div>

      <div className="relative z-10 grid grid-cols-1 lg:grid-cols-12 gap-10 max-w-[1400px] mx-auto w-full">
        {/* Left: Editorial Typography */}
        <div className="lg:col-span-8 flex flex-col justify-center">
          {/* Eyebrow */}
          <div className="mb-8 lg:mb-12">
            {/* Cultural tagline */}
            <div className="flex items-center gap-3 mb-4">
              <div className="h-px w-8 bg-primary opacity-60" />
              <span className="text-primary font-bold tracking-[0.4em] uppercase text-[10px]">
                पारंपरिक · Traditional · Maharashtrian
              </span>
              <div className="h-px w-8 bg-primary opacity-60" />
            </div>
            <span className="text-primary font-bold tracking-[0.5em] uppercase text-[10px] block mb-6">
              New Collection · Spring 2026
            </span>
            <h1
              ref={headlineRef}
              className="editorial-text font-extrabold"
              style={{ fontSize: 'clamp(3.5rem, 10vw, 11rem)', transition: 'transform 0.8s cubic-bezier(0.16, 1, 0.3, 1)' }}>

              <span className="block text-foreground">GLOW</span>
              <span className="block animate-shimmer-text">JEWELS</span>
            </h1>
          </div>

          <div ref={metaRef} className="flex flex-col md:flex-row gap-12 items-start">
            <p className="max-w-md text-muted-foreground text-lg font-medium leading-relaxed">
              Inspired by the timeless elegance of <span className="text-foreground font-bold">Maharashtrian heritage</span> — 
              Nath, Kolhapuri Saaj, Bangdi & more. Wear your culture, wear your confidence.
            </p>
            <div className="flex flex-col gap-4 shrink-0">
              <Link
                href="/products"
                className="px-12 py-5 btn-primary rounded-2xl text-[11px] inline-flex items-center gap-2">

                Shop Collection
                <Icon name="ArrowRightIcon" size={14} className="text-white" />
              </Link>
              <Link
                href="/sign-up-login"
                className="px-12 py-4 btn-outline rounded-full text-[11px] inline-flex items-center justify-center gap-2">

                Create Account
              </Link>
              <span className="text-[9px] font-bold uppercase tracking-[0.4em] text-muted-foreground text-center">
                Free returns · Secure checkout
              </span>
            </div>
          </div>

          {/* Maharashtrian jewelry thumbnail strip */}
          <div className="mt-10 flex items-center gap-4">
            <span className="text-[9px] font-bold uppercase tracking-[0.35em] text-muted-foreground whitespace-nowrap">
              Traditional Styles
            </span>
            <div className="flex gap-3">
              {[
                { src: '/assets/images/maharashtrian_nath_nose_ring.png', label: 'Nath' },
                { src: '/assets/images/maharashtrian_chandrakor_earrings.png', label: 'Chandrakor' },
                { src: '/assets/images/maharashtrian_bangdi_bangles.png', label: 'Bangdi' },
                { src: '/assets/images/maharashtrian_necklace_kolhapuri.png', label: 'Saaj' },
              ].map((item) => (
                <Link href="/products" key={item.label} className="group flex flex-col items-center gap-1">
                  <div className="w-12 h-12 rounded-xl overflow-hidden border-2 border-primary/20 group-hover:border-primary/60 transition-all duration-300 shadow-sm">
                    <AppImage
                      src={item.src}
                      alt={`Traditional Maharashtrian ${item.label} jewelry`}
                      width={48}
                      height={48}
                      className="object-cover w-full h-full group-hover:scale-110 transition-transform duration-500"
                    />
                  </div>
                  <span className="text-[8px] font-bold uppercase tracking-wider text-muted-foreground group-hover:text-primary transition-colors">{item.label}</span>
                </Link>
              ))}
            </div>
          </div>
        </div>

        {/* Right: Product Portrait */}
        <div className="lg:col-span-4 hidden lg:flex flex-col justify-center items-end relative">
          {/* Vertical text */}
          <div
            ref={verticalTextRef}
            className="absolute right-0 top-1/2 -translate-y-1/2 text-[9px] font-bold uppercase tracking-[1.2em] text-muted-foreground/40 pointer-events-none"
            style={{ writingMode: 'vertical-rl', textOrientation: 'mixed' }}>

            Nath · Mangalsutra · Bangdi · Chandrakor
          </div>

          <div ref={imageRef} className="relative w-full max-w-[320px] group">
            {/* Decorative mandala ring behind image */}
            <div
              className="absolute -inset-4 rounded-3xl opacity-20 pointer-events-none"
              style={{
                background: 'conic-gradient(from 0deg, #B8733A, #D4A017, #D4607A, #B8733A)',
                filter: 'blur(8px)'
              }}
            />
            {/* Shimmer border frame */}
            <div className="absolute inset-0 rounded-2xl shimmer-border z-10 pointer-events-none" />

            <div className="aspect-[3/4] w-full rounded-2xl overflow-hidden bg-muted">
              <AppImage
                src="/assets/images/maharashtrian_bridal_jewelry_hero.png"
                alt="Beautiful Maharashtrian woman in green Paithani saree wearing traditional bridal jewelry including Nath, Mangalsutra, Kolhapuri Saaj necklace and gold bangles"
                fill
                className="object-cover group-hover:scale-105 transition-all duration-1000"
                priority />

            </div>

            {/* Floating stat card */}
            <div className="absolute -bottom-6 -left-8 bg-white/95 backdrop-blur-xl border border-border p-4 rounded-2xl shadow-2xl z-20 animate-float">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-primary/20 flex items-center justify-center">
                  <Icon name="HeartIcon" size={18} className="text-accent" />
                </div>
                <div>
                  <p className="text-[10px] font-bold uppercase tracking-widest text-muted-foreground">Loved by</p>
                  <p className="text-lg font-extrabold text-foreground tracking-tight">24,800+</p>
                </div>
              </div>
            </div>

            {/* Rating badge */}
            <div className="absolute -top-4 -right-4 bg-primary text-white px-3 py-2 rounded-xl z-20 shadow-lg">
              <div className="flex items-center gap-1">
                <Icon name="StarIcon" size={12} className="text-white" variant="solid" />
                <span className="text-[11px] font-black">4.9</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Scroll indicator */}
      <div className="absolute bottom-8 left-1/2 -translate-x-1/2 flex flex-col items-center gap-2 opacity-40">
        <span className="text-[9px] font-bold uppercase tracking-[0.4em] text-muted-foreground">Scroll</span>
        <div className="w-px h-12 bg-gradient-to-b from-primary to-transparent" />
      </div>
    </section>);

};

export default HeroSection;