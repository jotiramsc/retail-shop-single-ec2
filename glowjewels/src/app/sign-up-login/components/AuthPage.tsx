'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import AppImage from '@/components/ui/AppImage';
import AppLogo from '@/components/ui/AppLogo';
import Icon from '@/components/ui/AppIcon';

type Tab = 'signin' | 'signup';

interface FormData {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  confirmPassword: string;
}

const AuthPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState<Tab>('signin');
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState<FormData>({
    firstName: '',
    lastName: '',
    email: '',
    password: '',
    confirmPassword: ''
  });
  const [errors, setErrors] = useState<Partial<FormData>>({});

  const validate = (): boolean => {
    const newErrors: Partial<FormData> = {};
    if (!formData.email) newErrors.email = 'Email is required';else
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) newErrors.email = 'Enter a valid email';
    if (!formData.password) newErrors.password = 'Password is required';else
    if (formData.password.length < 8) newErrors.password = 'Password must be at least 8 characters';
    if (activeTab === 'signup') {
      if (!formData.firstName) newErrors.firstName = 'First name is required';
      if (!formData.lastName) newErrors.lastName = 'Last name is required';
      if (!formData.confirmPassword) newErrors.confirmPassword = 'Please confirm your password';else
      if (formData.password !== formData.confirmPassword) newErrors.confirmPassword = 'Passwords do not match';
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;
    setLoading(true);
    // Mock submit — backend integration point
    setTimeout(() => setLoading(false), 1500);
  };

  const updateField = (field: keyof FormData, value: string) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    if (errors[field]) setErrors((prev) => ({ ...prev, [field]: undefined }));
  };

  return (
    <div className="min-h-screen flex">
      {/* Left: Jewelry Image Panel */}
      <div className="hidden lg:block lg:w-[55%] relative overflow-hidden">
        <AppImage
          src="https://img.rocket.new/generatedImages/rocket_gen_img_18042d79e-1772344579667.png"
          alt="Luxury rose gold jewelry collection arranged on dark velvet, dim atmospheric studio lighting, deep shadows, moody elegant product photography"
          fill
          className="object-cover"
          priority />

        {/* Scrim for white text legibility */}
        <div className="absolute inset-0 bg-gradient-to-r from-[#0A0806]/75 via-[#0A0806]/40 to-transparent" />

        {/* Brand overlay content */}
        <div className="absolute inset-0 flex flex-col justify-between p-12">
          {/* Logo */}
          <Link href="/homepage" className="flex items-center gap-2">
            <AppLogo size={36} />
            <span className="font-bold text-lg tracking-tight text-white">GlowJewels</span>
          </Link>

          {/* Editorial headline */}
          <div className="max-w-md">
            <h2 className="editorial-text font-extrabold text-white mb-6" style={{ fontSize: 'clamp(2.5rem, 5vw, 5rem)' }}>
              YOUR STORY
              <br />
              <span style={{ WebkitTextStroke: '1.5px rgba(200,149,108,0.6)', color: 'transparent' }}>
                BEGINS HERE
              </span>
            </h2>
            <p className="text-white/70 text-base leading-relaxed">
              Join 24,800+ women who wear their confidence every day. 
              Create your account to unlock wishlists, order tracking, and exclusive member offers.
            </p>

            {/* Trust indicators */}
            <div className="flex flex-col gap-3 mt-8">
              {[
              { icon: 'ShieldCheckIcon' as const, text: 'Secure checkout, always encrypted' },
              { icon: 'TruckIcon' as const, text: 'Free returns within 30 days' },
              { icon: 'HeartIcon' as const, text: 'Save favorites to your wishlist' }].
              map((item) =>
              <div key={item.text} className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-full bg-primary/20 flex items-center justify-center shrink-0">
                    <Icon name={item.icon} size={14} className="text-primary" />
                  </div>
                  <span className="text-white/70 text-[13px]">{item.text}</span>
                </div>
              )}
            </div>
          </div>

          {/* Bottom attribution */}
          <p className="text-white/30 text-[11px] font-bold uppercase tracking-widest">
            © 2026 GlowJewels
          </p>
        </div>
      </div>

      {/* Right: Auth Form */}
      <div className="w-full lg:w-[45%] flex flex-col bg-background overflow-y-auto">
        {/* Mobile header */}
        <div className="flex lg:hidden items-center justify-between p-6 border-b border-border">
          <Link href="/homepage" className="flex items-center gap-2">
            <AppLogo size={32} />
            <span className="font-bold text-base tracking-tight text-foreground">GlowJewels</span>
          </Link>
          <Link href="/homepage" className="text-muted-foreground hover:text-foreground transition-colors">
            <Icon name="XMarkIcon" size={22} />
          </Link>
        </div>

        <div className="flex-1 flex flex-col justify-center px-8 sm:px-12 lg:px-16 py-12">
          {/* Tabs */}
          <div className="flex gap-1 p-1 bg-muted border border-border rounded-2xl mb-10 w-full max-w-sm mx-auto">
            {(['signin', 'signup'] as Tab[]).map((tab) =>
            <button
              key={tab}
              onClick={() => {setActiveTab(tab);setErrors({});}}
              className={`flex-1 py-3 rounded-xl text-[11px] font-black uppercase tracking-widest transition-all ${
              activeTab === tab ?
              'bg-primary text-white shadow-lg' :
              'text-muted-foreground hover:text-foreground'}`
              }>

                {tab === 'signin' ? 'Sign In' : 'Create Account'}
              </button>
            )}
          </div>

          {/* Heading */}
          <div className="mb-8 max-w-sm mx-auto w-full">
            <h1 className="text-2xl font-extrabold text-foreground mb-2">
              {activeTab === 'signin' ? 'Welcome back' : 'Join GlowJewels'}
            </h1>
            <p className="text-muted-foreground text-[13px]">
              {activeTab === 'signin' ? 'Sign in to access your orders, wishlist, and saved addresses.' : 'Create your account to start shopping and save your favorites.'}
            </p>
          </div>

          {/* Social Buttons */}
          <div className="flex flex-col gap-3 max-w-sm mx-auto w-full mb-8">
            <button className="flex items-center justify-center gap-3 w-full py-3.5 bg-card border border-border rounded-xl text-[13px] font-bold text-foreground hover:border-primary/40 hover:bg-muted transition-all">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none">
                <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4" />
                <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853" />
                <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05" />
                <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335" />
              </svg>
              Continue with Google
            </button>
            <button className="flex items-center justify-center gap-3 w-full py-3.5 bg-card border border-border rounded-xl text-[13px] font-bold text-foreground hover:border-primary/40 hover:bg-muted transition-all">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12.017 0C5.396 0 .029 5.367.029 11.987c0 5.079 3.158 9.417 7.618 11.162-.105-.949-.2-2.405.042-3.441.218-.937 1.407-5.965 1.407-5.965s-.359-.719-.359-1.782c0-1.668.967-2.914 2.171-2.914 1.023 0 1.518.769 1.518 1.69 0 1.029-.655 2.568-.994 3.995-.283 1.194.599 2.169 1.777 2.169 2.133 0 3.772-2.249 3.772-5.495 0-2.873-2.064-4.882-5.012-4.882-3.414 0-5.418 2.561-5.418 5.207 0 1.031.397 2.138.893 2.738a.36.36 0 0 1 .083.345l-.333 1.36c-.053.22-.174.267-.402.161-1.499-.698-2.436-2.889-2.436-4.649 0-3.785 2.75-7.262 7.929-7.262 4.163 0 7.398 2.967 7.398 6.931 0 4.136-2.607 7.464-6.227 7.464-1.216 0-2.359-.632-2.75-1.378l-.748 2.853c-.271 1.043-1.002 2.35-1.492 3.146C9.57 23.812 10.763 24.009 12.017 24.009c6.624 0 11.99-5.367 11.99-11.988C24.007 5.367 18.641 0 12.017 0z" />
              </svg>
              Continue with Apple
            </button>
          </div>

          {/* Divider */}
          <div className="flex items-center gap-4 max-w-sm mx-auto w-full mb-8">
            <div className="flex-1 h-px bg-border" />
            <span className="text-[11px] font-bold uppercase tracking-widest text-muted-foreground">or</span>
            <div className="flex-1 h-px bg-border" />
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="flex flex-col gap-5 max-w-sm mx-auto w-full" noValidate>
            {activeTab === 'signup' &&
            <div className="grid grid-cols-2 gap-4">
                {/* First Name */}
                <div className="flex flex-col gap-1.5">
                  <label className="text-[11px] font-bold uppercase tracking-widest text-muted-foreground">
                    First Name
                  </label>
                  <input
                  type="text"
                  value={formData.firstName}
                  onChange={(e) => updateField('firstName', e.target.value)}
                  placeholder="Mia"
                  className={`px-4 py-3 bg-card border rounded-xl text-[13px] text-foreground placeholder:text-muted-foreground/50 focus:outline-none transition-colors ${
                  errors.firstName ? 'border-red-500/60 focus:border-red-500' : 'border-border focus:border-primary/50'}`
                  } />

                  {errors.firstName && <span className="text-[11px] text-red-400">{errors.firstName}</span>}
                </div>
                {/* Last Name */}
                <div className="flex flex-col gap-1.5">
                  <label className="text-[11px] font-bold uppercase tracking-widest text-muted-foreground">
                    Last Name
                  </label>
                  <input
                  type="text"
                  value={formData.lastName}
                  onChange={(e) => updateField('lastName', e.target.value)}
                  placeholder="Thornton"
                  className={`px-4 py-3 bg-card border rounded-xl text-[13px] text-foreground placeholder:text-muted-foreground/50 focus:outline-none transition-colors ${
                  errors.lastName ? 'border-red-500/60 focus:border-red-500' : 'border-border focus:border-primary/50'}`
                  } />

                  {errors.lastName && <span className="text-[11px] text-red-400">{errors.lastName}</span>}
                </div>
              </div>
            }

            {/* Email */}
            <div className="flex flex-col gap-1.5">
              <label className="text-[11px] font-bold uppercase tracking-widest text-muted-foreground">
                Email Address
              </label>
              <div className="relative">
                <Icon name="EnvelopeIcon" size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
                <input
                  type="email"
                  value={formData.email}
                  onChange={(e) => updateField('email', e.target.value)}
                  placeholder="you@example.com"
                  className={`w-full pl-10 pr-4 py-3 bg-card border rounded-xl text-[13px] text-foreground placeholder:text-muted-foreground/50 focus:outline-none transition-colors ${
                  errors.email ? 'border-red-500/60 focus:border-red-500' : 'border-border focus:border-primary/50'}`
                  } />

              </div>
              {errors.email && <span className="text-[11px] text-red-400">{errors.email}</span>}
            </div>

            {/* Password */}
            <div className="flex flex-col gap-1.5">
              <div className="flex items-center justify-between">
                <label className="text-[11px] font-bold uppercase tracking-widest text-muted-foreground">
                  Password
                </label>
                {activeTab === 'signin' &&
                <button type="button" className="text-[11px] font-bold text-primary hover:text-accent transition-colors">
                    Forgot password?
                  </button>
                }
              </div>
              <div className="relative">
                <Icon name="LockClosedIcon" size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={formData.password}
                  onChange={(e) => updateField('password', e.target.value)}
                  placeholder="Min. 8 characters"
                  className={`w-full pl-10 pr-12 py-3 bg-card border rounded-xl text-[13px] text-foreground placeholder:text-muted-foreground/50 focus:outline-none transition-colors ${
                  errors.password ? 'border-red-500/60 focus:border-red-500' : 'border-border focus:border-primary/50'}`
                  } />

                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}>

                  <Icon name={showPassword ? 'EyeSlashIcon' : 'EyeIcon'} size={16} />
                </button>
              </div>
              {errors.password && <span className="text-[11px] text-red-400">{errors.password}</span>}
            </div>

            {/* Confirm Password (signup only) */}
            {activeTab === 'signup' &&
            <div className="flex flex-col gap-1.5">
                <label className="text-[11px] font-bold uppercase tracking-widest text-muted-foreground">
                  Confirm Password
                </label>
                <div className="relative">
                  <Icon name="LockClosedIcon" size={16} className="absolute left-3.5 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
                  <input
                  type={showConfirmPassword ? 'text' : 'password'}
                  value={formData.confirmPassword}
                  onChange={(e) => updateField('confirmPassword', e.target.value)}
                  placeholder="Repeat your password"
                  className={`w-full pl-10 pr-12 py-3 bg-card border rounded-xl text-[13px] text-foreground placeholder:text-muted-foreground/50 focus:outline-none transition-colors ${
                  errors.confirmPassword ? 'border-red-500/60 focus:border-red-500' : 'border-border focus:border-primary/50'}`
                  } />

                  <button
                  type="button"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  className="absolute right-3.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                  aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}>

                    <Icon name={showConfirmPassword ? 'EyeSlashIcon' : 'EyeIcon'} size={16} />
                  </button>
                </div>
                {errors.confirmPassword && <span className="text-[11px] text-red-400">{errors.confirmPassword}</span>}
              </div>
            }

            {/* Terms (signup only) */}
            {activeTab === 'signup' &&
            <p className="text-[12px] text-muted-foreground leading-relaxed">
                By creating an account, you agree to our{' '}
                <Link href="/homepage" className="text-primary hover:text-accent transition-colors font-bold">Terms of Service</Link>
                {' '}and{' '}
                <Link href="/homepage" className="text-primary hover:text-accent transition-colors font-bold">Privacy Policy</Link>.
              </p>
            }

            {/* Submit */}
            <button
              type="submit"
              disabled={loading}
              className="w-full py-4 btn-primary rounded-xl text-[12px] flex items-center justify-center gap-2 disabled:opacity-60 disabled:cursor-not-allowed mt-2">

              {loading ?
              <>
                  <svg className="animate-spin w-4 h-4 text-white" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  {activeTab === 'signin' ? 'Signing In...' : 'Creating Account...'}
                </> :

              <>
                  {activeTab === 'signin' ? 'Sign In to GlowJewels' : 'Create My Account'}
                  <Icon name="ArrowRightIcon" size={14} className="text-white" />
                </>
              }
            </button>
          </form>

          {/* Switch tab link */}
          <p className="text-center text-[13px] text-muted-foreground mt-8 max-w-sm mx-auto">
            {activeTab === 'signin' ? "Don't have an account? " : 'Already have an account? '}
            <button
              onClick={() => {setActiveTab(activeTab === 'signin' ? 'signup' : 'signin');setErrors({});}}
              className="text-primary font-bold hover:text-accent transition-colors">

              {activeTab === 'signin' ? 'Create one free' : 'Sign in'}
            </button>
          </p>

          {/* Back to shop */}
          <div className="text-center mt-6">
            <Link
              href="/homepage"
              className="inline-flex items-center gap-2 text-[11px] font-bold uppercase tracking-widest text-muted-foreground hover:text-foreground transition-colors">

              <Icon name="ArrowLeftIcon" size={12} />
              Back to Shop
            </Link>
          </div>
        </div>
      </div>
    </div>);

};

export default AuthPage;