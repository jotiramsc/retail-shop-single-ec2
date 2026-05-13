package com.retailshop.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PrivacyPolicyController {

    @GetMapping(value = "/privacy-policy", produces = MediaType.TEXT_HTML_VALUE)
    public String privacyPolicy() {
        return """
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Privacy Policy | Krishnai Pearl Shopee</title>
                    <meta name="description" content="Privacy policy for Krishnai Pearl Shopee customer accounts, orders, payments, WhatsApp OTP, and marketing communication.">
                    <meta name="robots" content="index,follow">
                    <link rel="canonical" href="https://kpskrishnai.com/privacy-policy">
                    <style>
                      body { margin: 0; font-family: Arial, sans-serif; color: #2b1b18; background: #fbf7ef; line-height: 1.65; }
                      main { max-width: 920px; margin: 0 auto; padding: 48px 20px 72px; }
                      header { padding: 36px; border-radius: 12px; background: #143c32; color: #fff7ea; }
                      h1 { margin: 0 0 12px; font-size: clamp(2.3rem, 6vw, 4.5rem); line-height: 1; }
                      h2 { margin: 0 0 10px; font-size: 1.6rem; }
                      section { margin-top: 18px; padding: 26px; border: 1px solid #eadbc8; border-radius: 12px; background: #fff; }
                      p { margin: 0 0 12px; }
                      a { color: #143c32; font-weight: 700; }
                      .updated { font-weight: 700; color: #f3dcb4; }
                    </style>
                  </head>
                  <body>
                    <main>
                      <header>
                        <p>Customer policy</p>
                        <h1>Privacy Policy</h1>
                        <p>This policy explains how Krishnai Pearl Shopee collects, uses, stores, and protects customer information when customers browse products, create an account, verify mobile OTP, place orders, or contact us on WhatsApp.</p>
                        <p class="updated">Last updated: 10 May 2026</p>
                      </header>
                      <section>
                        <h2>Information We Collect</h2>
                        <p>We may collect customer name, mobile number, email address, delivery address, order details, wishlist and cart activity, login method, OTP verification status, payment reference, and customer support messages.</p>
                        <p>When customers use Google sign-in, we receive basic account information shared by Google, such as name, email address, and a Google account identifier. Mobile OTP verification is still required for account security.</p>
                      </section>
                      <section>
                        <h2>How We Use Information</h2>
                        <p>We use customer information to create and secure accounts, send WhatsApp OTPs, process orders, manage delivery, support returns or order questions, improve product recommendations, prevent fraud, and send relevant offers or campaign updates when permitted.</p>
                        <p>We also use website analytics to understand product interest, campaign performance, cart activity, and customer journey improvements.</p>
                      </section>
                      <section>
                        <h2>Payments</h2>
                        <p>Online payments may be processed through payment partners such as Razorpay. We do not store full card numbers, CVV, UPI PIN, or banking passwords on our website. Payment status and transaction references may be stored for order confirmation, refunds, reconciliation, and customer support.</p>
                      </section>
                      <section>
                        <h2>WhatsApp, Facebook, Instagram, and Google</h2>
                        <p>We may use Meta WhatsApp, Facebook, and Instagram services for OTP delivery, customer conversations, campaign publishing, and marketing communication. Google services may be used for sign-in, maps, and analytics. These providers process information according to their own platform policies.</p>
                      </section>
                      <section>
                        <h2>Sharing and Security</h2>
                        <p>We do not sell customer personal information. Information may be shared only with service providers needed to run the store, including hosting, payment, delivery, messaging, analytics, and fraud-prevention partners. We use secure access controls, token-based authentication, and restricted admin access to protect data.</p>
                      </section>
                      <section>
                        <h2>Customer Choices</h2>
                        <p>Customers can update profile details, saved addresses, and account information from the customer account page. Customers can contact us to request correction or deletion of their information, subject to order, tax, fraud-prevention, and legal record requirements.</p>
                      </section>
                      <section>
                        <h2>Contact Us</h2>
                        <p>For privacy questions, account help, or data requests, contact Krishnai Pearl Shopee.</p>
                        <p>Website: <a href="https://kpskrishnai.com">https://kpskrishnai.com</a></p>
                        <p>Phone: <a href="tel:+919175834000">+91 9175834000</a></p>
                        <p>Address: Pune, Maharashtra, India</p>
                      </section>
                    </main>
                  </body>
                </html>
                """;
    }
}
