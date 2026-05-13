import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { defaultBranding } from '../utils/branding';
import { applySeo } from '../utils/seo';

export default function PrivacyPolicyPage({ branding }) {
  const shopName = branding?.shopName || defaultBranding.shopName || 'Krishnai Pearl Shopee';
  const logo = branding?.media?.logo || defaultBranding.media.logo;
  const phone = branding?.contact?.phoneLabel || '+91 9175834000';
  const address = branding?.contact?.address || 'Pune, Maharashtra, India';
  const websiteUrl = 'https://kpskrishnai.com';

  useEffect(() => {
    applySeo({
      title: `Privacy Policy | ${shopName}`,
      description: `${shopName} privacy policy for customer accounts, orders, payments, WhatsApp OTP, and marketing communication.`,
      image: logo,
      path: '/privacy-policy'
    });
  }, [logo, shopName]);

  return (
    <main className="glow-page privacy-policy-page">
      <header className="privacy-policy-hero">
        <Link to="/" className="glow-brand privacy-policy-brand">
          <img src={logo} alt={`${shopName} logo`} className="glow-brand-logo" />
          <div>
            <span className="glow-kicker">Customer policy</span>
            <strong>{shopName}</strong>
          </div>
        </Link>
        <div className="privacy-policy-hero-copy">
          <span className="glow-kicker">Privacy Policy</span>
          <h1 className="editorial-text">How we protect customer information</h1>
          <p>
            This policy explains how {shopName} collects, uses, stores, and protects customer information when
            customers browse products, create an account, verify mobile OTP, place orders, or contact us on WhatsApp.
          </p>
          <p className="privacy-policy-updated">Last updated: 10 May 2026</p>
        </div>
      </header>

      <section className="privacy-policy-card">
        <h2>Information We Collect</h2>
        <p>
          We may collect customer name, mobile number, email address, delivery address, order details, wishlist and
          cart activity, login method, OTP verification status, payment reference, and customer support messages.
        </p>
        <p>
          When customers use Google sign-in, we receive basic account information shared by Google, such as name,
          email address, and a Google account identifier. Mobile OTP verification is still required for account security.
        </p>
      </section>

      <section className="privacy-policy-card">
        <h2>How We Use Information</h2>
        <p>
          We use customer information to create and secure accounts, send WhatsApp OTPs, process orders, manage
          delivery, support returns or order questions, improve product recommendations, prevent fraud, and send
          relevant offers or campaign updates when permitted.
        </p>
        <p>
          We also use website analytics to understand product interest, campaign performance, cart activity, and
          customer journey improvements.
        </p>
      </section>

      <section className="privacy-policy-card">
        <h2>Payments</h2>
        <p>
          Online payments may be processed through payment partners such as Razorpay. We do not store full card
          numbers, CVV, UPI PIN, or banking passwords on our website. Payment status and transaction references may
          be stored for order confirmation, refunds, reconciliation, and customer support.
        </p>
      </section>

      <section className="privacy-policy-card">
        <h2>WhatsApp, Facebook, Instagram, and Google</h2>
        <p>
          We may use Meta WhatsApp, Facebook, and Instagram services for OTP delivery, customer conversations,
          campaign publishing, and marketing communication. Google services may be used for sign-in, maps, and
          analytics. These providers process information according to their own platform policies.
        </p>
      </section>

      <section className="privacy-policy-card">
        <h2>Sharing and Security</h2>
        <p>
          We do not sell customer personal information. Information may be shared only with service providers needed
          to run the store, including hosting, payment, delivery, messaging, analytics, and fraud-prevention partners.
          We use secure access controls, token-based authentication, and restricted admin access to protect data.
        </p>
      </section>

      <section className="privacy-policy-card">
        <h2>Customer Choices</h2>
        <p>
          Customers can update profile details, saved addresses, and account information from the customer account
          page. Customers can contact us to request correction or deletion of their information, subject to order,
          tax, fraud-prevention, and legal record requirements.
        </p>
      </section>

      <section className="privacy-policy-card privacy-policy-contact">
        <h2>Contact Us</h2>
        <p>
          For privacy questions, account help, or data requests, contact {shopName}.
        </p>
        <div>
          <span>Website</span>
          <a href={websiteUrl}>{websiteUrl}</a>
        </div>
        <div>
          <span>Phone</span>
          <a href={`tel:${phone.replace(/\s+/g, '')}`}>{phone}</a>
        </div>
        <div>
          <span>Address</span>
          <p>{address}</p>
        </div>
      </section>
    </main>
  );
}
