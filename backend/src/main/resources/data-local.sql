insert into product_categories (id, code, display_name, active, created_at)
values
    ('0f100000-0000-0000-0000-000000000001', 'COSMETICS', 'Cosmetics', true, now()),
    ('0f100000-0000-0000-0000-000000000002', 'JEWELLERY', 'Jewellery', true, now());

insert into products (id, name, category, sku, cost_price, selling_price, quantity, low_stock_threshold, expiry_date, created_at)
values
    ('a1111111-1111-1111-1111-111111111111', 'Rose Matte Lipstick', 'COSMETICS', 'COS-LIP-001', 180.00, 299.00, 25, 8, '2027-12-31', now()),
    ('a2222222-2222-2222-2222-222222222222', 'Gold Tone Earrings', 'JEWELLERY', 'JEW-EAR-001', 450.00, 899.00, 14, 5, null, now()),
    ('a3333333-3333-3333-3333-333333333333', 'Hydra Glow Serum', 'COSMETICS', 'COS-SER-001', 320.00, 599.00, 4, 6, '2027-10-30', now()),
    ('a4444444-4444-4444-4444-444444444444', 'Velvet Kajal Pencil', 'COSMETICS', 'COS-KAJ-001', 70.00, 149.00, 40, 10, '2028-03-31', now()),
    ('a5555555-5555-5555-5555-555555555555', 'Radiance Compact Powder', 'COSMETICS', 'COS-COM-001', 190.00, 349.00, 22, 7, '2027-11-30', now()),
    ('a6666666-6666-6666-6666-666666666666', 'Aloe Vera Face Wash', 'COSMETICS', 'COS-FAC-001', 110.00, 225.00, 30, 8, '2027-09-30', now()),
    ('a7777777-7777-7777-7777-777777777777', 'Cherry Tint Nail Paint', 'COSMETICS', 'COS-NAI-001', 55.00, 120.00, 36, 10, '2028-01-15', now()),
    ('a8888888-8888-8888-8888-888888888888', 'Silk Finish Foundation', 'COSMETICS', 'COS-FOU-001', 260.00, 499.00, 18, 6, '2027-08-31', now()),
    ('a9999999-9999-9999-9999-999999999999', 'Crystal Bracelet', 'JEWELLERY', 'JEW-BRA-001', 320.00, 699.00, 20, 5, null, now()),
    ('ab111111-1111-1111-1111-111111111111', 'Pearl Pendant Set', 'JEWELLERY', 'JEW-PEN-001', 540.00, 1199.00, 12, 4, null, now()),
    ('ab222222-2222-2222-2222-222222222222', 'Bridal Bangles Set', 'JEWELLERY', 'JEW-BAN-001', 680.00, 1499.00, 10, 4, null, now()),
    ('ab333333-3333-3333-3333-333333333333', 'Silver Tone Anklet', 'JEWELLERY', 'JEW-ANK-001', 210.00, 499.00, 26, 6, null, now()),
    ('ab444444-4444-4444-4444-444444444444', 'Meenakari Ring', 'JEWELLERY', 'JEW-RIN-001', 180.00, 399.00, 24, 6, null, now());

insert into products (id, name, category, sku, cost_price, selling_price, quantity, low_stock_threshold, image_data_url, expiry_date, created_at)
values
    ('ac111111-1111-1111-1111-111111111111', 'Luminous Blush Palette', 'COSMETICS', 'COS-BLU-001', 210.00, 389.00, 16, 6, 'https://picsum.photos/seed/luminous-blush-palette/600/600', '2028-04-30', now()),
    ('ac222222-2222-2222-2222-222222222222', 'Vitamin C Day Cream', 'COSMETICS', 'COS-DAY-001', 240.00, 449.00, 20, 7, 'https://picsum.photos/seed/vitamin-c-day-cream/600/600', '2028-02-28', now()),
    ('ac333333-3333-3333-3333-333333333333', 'Satin Nude Lip Gloss', 'COSMETICS', 'COS-GLS-001', 95.00, 189.00, 28, 8, 'https://picsum.photos/seed/satin-nude-lip-gloss/600/600', '2028-06-30', now()),
    ('ac444444-4444-4444-4444-444444444444', 'Royal Kohl Eyeliner', 'COSMETICS', 'COS-EYE-001', 80.00, 169.00, 34, 10, 'https://picsum.photos/seed/royal-kohl-eyeliner/600/600', '2028-03-31', now()),
    ('ac555555-5555-5555-5555-555555555555', 'Dew Mist Primer', 'COSMETICS', 'COS-PRI-001', 180.00, 329.00, 18, 6, 'https://picsum.photos/seed/dew-mist-primer/600/600', '2028-05-31', now()),
    ('ac666666-6666-6666-6666-666666666666', 'Charcoal Peel Mask', 'COSMETICS', 'COS-MSK-001', 120.00, 249.00, 22, 8, 'https://picsum.photos/seed/charcoal-peel-mask/600/600', '2027-12-31', now()),
    ('ac777777-7777-7777-7777-777777777777', 'Rosewater Toner', 'COSMETICS', 'COS-TON-001', 90.00, 199.00, 26, 9, 'https://picsum.photos/seed/rosewater-toner/600/600', '2028-01-31', now()),
    ('ac888888-8888-8888-8888-888888888888', 'Velvet Touch Concealer', 'COSMETICS', 'COS-CON-001', 160.00, 299.00, 19, 6, 'https://picsum.photos/seed/velvet-touch-concealer/600/600', '2028-07-31', now()),
    ('ac999999-9999-9999-9999-999999999999', 'Cocoa Brow Kit', 'COSMETICS', 'COS-BRO-001', 110.00, 229.00, 15, 5, 'https://picsum.photos/seed/cocoa-brow-kit/600/600', '2028-08-31', now()),
    ('ad111111-1111-1111-1111-111111111111', 'Moonlight Highlighter', 'COSMETICS', 'COS-HIG-001', 175.00, 319.00, 17, 6, 'https://picsum.photos/seed/moonlight-highlighter/600/600', '2028-09-30', now()),
    ('ad222222-2222-2222-2222-222222222222', 'Kundan Choker Set', 'JEWELLERY', 'JEW-CHO-001', 890.00, 1799.00, 8, 3, 'https://picsum.photos/seed/kundan-choker-set/600/600', null, now()),
    ('ad333333-3333-3333-3333-333333333333', 'Minimal Hoop Earrings', 'JEWELLERY', 'JEW-HOO-001', 260.00, 599.00, 18, 5, 'https://picsum.photos/seed/minimal-hoop-earrings/600/600', null, now()),
    ('ad444444-4444-4444-4444-444444444444', 'Emerald Stone Ring', 'JEWELLERY', 'JEW-EMR-001', 340.00, 749.00, 14, 5, 'https://picsum.photos/seed/emerald-stone-ring/600/600', null, now()),
    ('ad555555-5555-5555-5555-555555555555', 'Temple Design Necklace', 'JEWELLERY', 'JEW-TEM-001', 980.00, 2099.00, 6, 2, 'https://picsum.photos/seed/temple-design-necklace/600/600', null, now()),
    ('ad666666-6666-6666-6666-666666666666', 'Rose Gold Pendant Chain', 'JEWELLERY', 'JEW-ROS-001', 410.00, 899.00, 11, 4, 'https://picsum.photos/seed/rose-gold-pendant-chain/600/600', null, now()),
    ('ad777777-7777-7777-7777-777777777777', 'Pearl Drop Earrings', 'JEWELLERY', 'JEW-PRL-001', 300.00, 699.00, 13, 4, 'https://picsum.photos/seed/pearl-drop-earrings/600/600', null, now()),
    ('ad888888-8888-8888-8888-888888888888', 'Statement Cuff Bracelet', 'JEWELLERY', 'JEW-CUF-001', 520.00, 1099.00, 9, 3, 'https://picsum.photos/seed/statement-cuff-bracelet/600/600', null, now()),
    ('ad999999-9999-9999-9999-999999999999', 'Crystal Nose Pin', 'JEWELLERY', 'JEW-NOS-001', 90.00, 249.00, 25, 8, 'https://picsum.photos/seed/crystal-nose-pin/600/600', null, now()),
    ('ae111111-1111-1111-1111-111111111111', 'Antique Jhumka Pair', 'JEWELLERY', 'JEW-JHU-001', 380.00, 849.00, 12, 4, 'https://picsum.photos/seed/antique-jhumka-pair/600/600', null, now()),
    ('ae222222-2222-2222-2222-222222222222', 'Layered Charm Anklet', 'JEWELLERY', 'JEW-LAY-001', 170.00, 429.00, 16, 5, 'https://picsum.photos/seed/layered-charm-anklet/600/600', null, now());

insert into customers (id, name, mobile, created_at)
values
    ('b1111111-1111-1111-1111-111111111111', 'Anika Sharma', '9876543210', now()),
    ('b2222222-2222-2222-2222-222222222222', 'Riya Patel', '9988776655', now());

insert into offers (id, name, type, offer_value, category, product_id, start_date, end_date, active)
values
    ('c1111111-1111-1111-1111-111111111111', 'Glow Week 10%', 'CATEGORY', 10.00, 'COSMETICS', null, current_date - 5, current_date + 10, true),
    ('c2222222-2222-2222-2222-222222222222', 'Earrings Flat 50', 'FLAT', 50.00, null, 'a2222222-2222-2222-2222-222222222222', current_date - 1, current_date + 15, true);

insert into receipt_settings (id, shop_name, header_line, address, phone_number, gst_number, footer_note, show_address, show_phone_number, show_gst_number)
values
    ('d1111111-1111-1111-1111-111111111111', 'Luxe Retail Studio', 'Ladies Cosmetics and Jewellery', '12 Fashion Street, City Center', '+91 98765 43210', 'GSTIN-22ABCDE1234F1Z5', 'Thank you for shopping with us', true, true, false);

insert into staff_users (id, username, password_hash, display_name, role, enabled, created_at)
values
    ('f1111111-1111-1111-1111-111111111111', 'admin', '$2y$10$FUpni7O48CMC2kW3TpKmbe0b5yR2EJAemY5/rAIYOHO3bcBaw4PlG', 'Store Admin', 'ADMIN', true, now()),
    ('f2222222-2222-2222-2222-222222222222', 'cashier', '$2y$10$3dBdrkI7bC6iUjvatzjkGeiZQE5Qh9u.JsqOnFQqOj2bmQo3flSSm', 'Counter Cashier', 'STAFF', true, now());

insert into staff_user_permissions (user_id, permission)
values
    ('f1111111-1111-1111-1111-111111111111', 'BILLING'),
    ('f1111111-1111-1111-1111-111111111111', 'PRODUCTS'),
    ('f1111111-1111-1111-1111-111111111111', 'CUSTOMERS'),
    ('f1111111-1111-1111-1111-111111111111', 'OFFERS'),
    ('f1111111-1111-1111-1111-111111111111', 'CAMPAIGNS'),
    ('f1111111-1111-1111-1111-111111111111', 'REPORTS'),
    ('f1111111-1111-1111-1111-111111111111', 'RECEIPT_SETTINGS'),
    ('f1111111-1111-1111-1111-111111111111', 'USER_MANAGEMENT'),
    ('f2222222-2222-2222-2222-222222222222', 'BILLING');
