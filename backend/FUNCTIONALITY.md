# Retail Shop Backend - Functionality Overview

**Version:** 1.0.0  
**Last Updated:** May 2026  
**Platform:** Spring Boot 3.3.5 | Java 17 | PostgreSQL

---

## Executive Summary

The Retail Shop Backend is a production-ready, comprehensive e-commerce and marketing automation platform designed for jewelry and cosmetics retail businesses. It provides omnichannel commerce, AI-powered customer interactions, marketing automation, and real-time order management capabilities.

---

## 🎯 Core Features

### 1. **E-Commerce & Sales Management**
- **Product Catalog Management**
  - Multi-category product organization (Cosmetics, Jewelry)
  - Dynamic pricing with website markup support
  - Inventory tracking with low-stock alerts
  - Product availability by collection (featured, new releases, editors' picks, etc.)

- **Shopping Experience**
  - Shopping cart with persistent state (Redis-backed)
  - Wishlist functionality
  - Order creation and tracking
  - Multiple order sources (Website, Omnichannel, Offline)

- **Checkout & Payments**
  - Secure payment gateway integration (Razorpay)
  - Real-time order pricing with dynamic calculations
  - Coupon and discount application
  - Tax and delivery charge computation
  - Support for multiple payment modes (UPI, Cards, Wallets)

### 2. **Customer Management**
- **Authentication**
  - OTP-based login (WhatsApp delivery)
  - Google Sign-In integration
  - Dual authentication channels (SMS via MSG91 fallback)
  - Mobile and email verification

- **Customer Profiles**
  - Complete profile management (name, email, phone, address)
  - Profile completion tracking
  - Address management with geolocation
  - Purchase history and preferences

### 3. **Omnichannel Commerce**
- **Lead Capture & Engagement**
  - Social media lead conversion (Meta/Facebook integration)
  - Omnichannel conversation threading
  - AI-powered product recommendations
  - Webhook-based real-time synchronization

- **Supported Channels**
  - WhatsApp (Meta/Gupshup)
  - Facebook Messenger
  - Instagram Direct Messages
  - Website

### 4. **WhatsApp Conversational AI Bot**
- **Bot Capabilities**
  - Intent classification using OpenAI
  - Contextual customer memory (Qdrant vector DB)
  - Product search and recommendations
  - Order tracking and status updates
  - Multi-language support

- **Features**
  - Quick reply templates
  - Interactive button menus
  - List message interfaces
  - Conversation summarization
  - Agent handoff capability
  - Fallback to text when templates fail

### 5. **Marketing Automation**
- **Campaign Management**
  - Multi-platform campaign creation (Instagram, Facebook, WhatsApp, Email)
  - AI-generated content suggestions
  - Campaign approval workflow
  - Scheduled publishing
  - Real-time performance analytics

- **Content Generation**
  - AI-powered caption writing
  - Dynamic image suggestions
  - Hashtag and CTA generation
  - Multi-language content creation
  - Tone and style customization

- **Audience Targeting**
  - Customer segmentation
  - Behavioral targeting
  - Campaign analytics and ROI tracking
  - A/B testing support

### 6. **Billing & Invoicing**
- **Invoice Management**
  - Digital invoice creation
  - Coupon tracking per invoice
  - Salesperson attribution
  - Multi-format export

- **Receipt Customization**
  - Branded receipt settings
  - Logo and header customization
  - GST number and business info
  - Trust badges and footer notes

### 7. **Staff Management & Permissions**
- **User Roles**
  - Admin: Full system access
  - Staff: Operational access
  - Salesperson: Sales tracking and commissions

- **Permission-Based Access Control**
  - Granular permissions (BILLING, PRODUCTS, CUSTOMERS, CAMPAIGNS, etc.)
  - Role-based and permission-based authorization
  - Staff user lifecycle management

### 8. **Analytics & Reporting**
- **Site Analytics**
  - Visitor tracking and geolocation
  - Traffic source analysis
  - Landing page performance
  - Conversion tracking

- **Sales Reports**
  - Salesperson performance metrics
  - Daily/weekly/monthly sales trends
  - Product performance analytics
  - Channel-wise sales breakdown

- **Offer Performance**
  - Campaign ROI tracking
  - Engagement metrics
  - Conversion rates by channel

### 9. **Integration Capabilities**
- **Payment Gateways**
  - Razorpay (primary)
  - PhonePe (fallback available)
  
- **Communication Channels**
  - WhatsApp (Meta & Gupshup)
  - Email
  - SMS (MSG91)

- **External Services**
  - AWS S3 (image storage)
  - Google Maps API
  - Google OAuth
  - OpenAI (GPT-4, DALL-E)
  - Qdrant (vector embedding)

---

## 📊 Data Model

### Core Entities
- **Products** - Inventory with pricing and categorization
- **Customers** - User profiles with authentication
- **Orders** - Sales transactions with items and fulfillment tracking
- **Invoices** - Billing documents
- **Campaigns** - Marketing campaigns with multi-platform content
- **Offers** - Discounts and promotions
- **Omnichannel Leads** - Social media prospects
- **Conversations** - Multi-message threads

---

## 🔐 Security Features

- **JWT Authentication** - Separate tokens for staff and customers
- **Password Hashing** - Bcrypt with salt
- **OTP Security** - SHA-256 hashing with pepper
- **Webhook Signature Verification** - HMAC-SHA256 validation
- **Role-Based Access Control (RBAC)** - Permission-based authorization
- **CORS Configuration** - Restricted cross-origin access
- **Stateless Design** - Session-free architecture for scalability

---

## 🚀 Performance Features

- **Redis Caching** - Cart and OTP state persistence
- **Database Indexing** - Optimized queries for high traffic
- **Pagination Support** - Configurable page sizes (max 250)
- **Lazy Loading** - Resource-efficient data retrieval
- **Connection Pooling** - Efficient database connections
- **Async Processing** - Marketing scheduler with fixed delay

---

## 📱 API Endpoints Overview

### Authentication
- `POST /api/auth/login` - Staff login
- `POST /api/auth/send-otp` - Customer OTP request
- `POST /api/auth/verify-otp` - OTP verification
- `POST /api/auth/google` - Google Sign-In

### Products
- `GET /api/products` - List all products (staff)
- `POST /api/products` - Create product
- `GET /api/products/catalog` - Public product catalog
- `GET /api/products/catalog/home` - Homepage featured products
- `GET /api/products/catalog/trending` - Trending products

### Orders & Checkout
- `POST /api/checkout/quote` - Get pricing quote
- `POST /api/order/place` - Place new order
- `GET /api/order/{id}` - Get order details
- `GET /api/orders` - Get customer orders

### Campaigns
- `POST /api/marketing/campaigns` - Create campaign
- `GET /api/marketing/campaigns` - List campaigns
- `POST /api/marketing/content/{id}/publish` - Publish campaign

### WhatsApp Bot
- `GET /api/whatsapp/webhook` - Webhook verification
- `POST /api/whatsapp/webhook` - Receive messages

### Omnichannel
- `GET /api/omnichannel/products/search` - AI product search
- `POST /api/omnichannel/leads` - Capture social leads
- `POST /api/omnichannel/webhooks/meta` - Meta webhook handler

---

## 🔧 Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| **Framework** | Spring Boot | 3.3.5 |
| **Language** | Java | 17 |
| **Database** | PostgreSQL | 15+ (H2 for local) |
| **Cache** | Redis | 6+ |
| **AI/ML** | OpenAI API, Qdrant | Latest |
| **Payment** | Razorpay | v1 |
| **Cloud Storage** | AWS S3 | Latest |
| **Message Queue** | Built-in Scheduler | N/A |
| **Container** | Docker | Latest |

---

## 📈 Scalability & Deployment

- **Containerized** - Docker & Docker Compose
- **Cloud-Ready** - AWS ECS, EC2 compatible
- **Horizontal Scaling** - Stateless design
- **Multi-Environment** - Local, staging, production profiles
- **Health Checks** - Spring Boot Actuator endpoints
- **Monitoring Ready** - Structured logging with SLF4J

---

## 💰 Business Value

✅ **Increased Sales** - Omnichannel reach + AI recommendations  
✅ **Customer Engagement** - Conversational AI on WhatsApp  
✅ **Marketing Efficiency** - Automated campaign creation and publishing  
✅ **Operational Efficiency** - Integrated order and billing management  
✅ **Data-Driven** - Real-time analytics and performance tracking  
✅ **Reduced Support Load** - Bot handles routine inquiries  
✅ **Brand Consistency** - Unified experience across channels  

---

## 📞 Support & Maintenance

- **Error Handling** - Global exception handler with structured error responses
- **Validation** - Input validation at API layer
- **Audit Trail** - Comprehensive logging
- **Database Migrations** - Schema versioning included
- **Webhook Retry Logic** - Automatic retry with configurable delays

---

## 🎓 Getting Started

New users should reference:
- **Deployment Guide** → `DEPLOYMENT.md`
- **Configuration Guide** → `CONFIGURATION.md`
- **API Documentation** → Swagger UI at `/swagger-ui.html`
- **Developer Guide** → `AGENTS.md`

---

**For more information, contact the development team.**

