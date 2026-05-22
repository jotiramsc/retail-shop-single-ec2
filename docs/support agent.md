I am building a WhatsApp customer support system integrated with an admin web portal.

Please build a production-ready solution with a simple architecture.

## Business rules

* There is only ONE support agent for now.
* Whenever a customer selects "Connect to Agent" on WhatsApp:

  * Automatically route chat to the logged-in admin/support user.
  * Instantly show a popup/live chat window in the admin portal.
  * Play notification sound or show alert badge for new customer messages.
* No multi-agent routing, queues, or round-robin logic needed.

## Core requirements

### 1. WhatsApp customer flow

When customer clicks "Connect to Agent":

* Trigger handoff event
* Save customer details:

  * Name
  * Phone number
  * Timestamp
* Save chat history
* Mark chat status as:

  * OPEN
  * IN_PROGRESS
  * RESOLVED
* Immediately push chat to admin portal

### 2. Admin support portal

Create admin website support window where logged-in admin can:

* See popup when new WhatsApp message arrives
* Open live chat window
* Reply to customer in real time
* Mark issue as resolved
* View previous messages
* Search customers by phone/name
* See unread message badge

### 3. Product sharing inside chat

In admin chat window, add a "Send Product" button.

When clicked:

* Open product picker popup
* Load products from inventory page/database

Product card should include:

* Product image
* Product name
* Product description
* Price
* Stock availability
* Product link

When admin selects a product:

* Send product card/message to customer on WhatsApp
* Product should appear like an ecommerce card

### 4. Inventory module

Create admin inventory page where admin can:

* Add product
* Edit product
* Delete product
* Upload image
* Manage price
* Manage stock
* Generate product URL

These products should automatically appear in chat product picker.

## Tech stack

Backend:

* Java
* Spring Boot
* WebSocket for live chat
* REST APIs
* JPA/Hibernate
* MySQL

Frontend:

* React OR Angular
* Admin dashboard
* Popup chat UI
* Product modal

Messaging:

* WhatsApp Business API webhook integration

## Deliverables

Generate:

* System architecture
* Database schema
* Backend entities
* DTOs
* Repositories
* Services
* Controllers
* WebSocket implementation
* WhatsApp webhook handlers
* Inventory APIs
* Product sharing flow
* Frontend UI components
* JWT authentication
* Unit tests

Use clean architecture, modular code, and enterprise coding standards.
