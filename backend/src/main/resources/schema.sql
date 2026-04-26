create table if not exists products (
    id uuid primary key,
    name varchar(255) not null,
    category varchar(50) not null,
    sku varchar(100) not null unique,
    cost_price numeric(12, 2) not null,
    selling_price numeric(12, 2) not null,
    quantity integer not null,
    low_stock_threshold integer not null,
    image_data_url text,
    show_in_editors_picks boolean not null default false,
    show_in_new_release boolean not null default false,
    show_in_customer_access boolean not null default false,
    show_in_shop_collection boolean not null default false,
    show_in_featured_pieces boolean not null default false,
    show_in_story boolean not null default false,
    show_in_curated_selections boolean not null default false,
    expiry_date date,
    created_at timestamp not null
);

create table if not exists product_categories (
    id uuid primary key,
    code varchar(100) not null unique,
    display_name varchar(255) not null unique,
    active boolean not null default true,
    created_at timestamp not null
);

create table if not exists image_assets (
    id uuid primary key,
    category varchar(255) not null,
    cloudfront_url varchar(2000) not null,
    s3_key varchar(1000) not null unique,
    content_type varchar(100) not null,
    file_size_bytes bigint not null,
    created_at timestamp not null
);

alter table products add column if not exists show_in_editors_picks boolean not null default false;
alter table products add column if not exists show_in_new_release boolean not null default false;
alter table products add column if not exists show_in_customer_access boolean not null default false;
alter table products add column if not exists show_in_shop_collection boolean not null default false;
alter table products add column if not exists show_in_featured_pieces boolean not null default false;
alter table products add column if not exists show_in_story boolean not null default false;
alter table products add column if not exists show_in_curated_selections boolean not null default false;

create table if not exists customers (
    id uuid primary key,
    name varchar(255) not null,
    mobile varchar(20) not null unique,
    created_at timestamp not null
);

create table if not exists customer_otps (
    mobile varchar(20) primary key,
    otp_hash varchar(255) not null,
    channel varchar(50) not null,
    expiry timestamp not null,
    retry_count integer not null default 0,
    resend_allowed_at timestamp not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists cart (
    id uuid primary key,
    customer_id uuid not null unique references customers(id) on delete cascade,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table if not exists cart_items (
    id uuid primary key,
    cart_id uuid not null references cart(id) on delete cascade,
    product_id uuid not null references products(id),
    quantity integer not null,
    created_at timestamp not null,
    updated_at timestamp not null,
    unique (cart_id, product_id)
);

create table if not exists addresses (
    id uuid primary key,
    customer_id uuid not null references customers(id) on delete cascade,
    label varchar(100),
    recipient_name varchar(255) not null,
    mobile varchar(20) not null,
    line1 varchar(500) not null,
    line2 varchar(500),
    landmark varchar(255),
    city varchar(100) not null,
    state varchar(100) not null,
    pincode varchar(20) not null,
    latitude numeric(10, 7),
    longitude numeric(10, 7),
    created_at timestamp not null
);

create table if not exists orders (
    id uuid primary key,
    order_number varchar(100) not null unique,
    customer_id uuid not null references customers(id),
    address_id uuid references addresses(id),
    subtotal numeric(12, 2) not null,
    discount numeric(12, 2) not null,
    tax numeric(12, 2) not null default 0,
    delivery numeric(12, 2) not null default 0,
    final_amount numeric(12, 2) not null,
    coupon_code varchar(100),
    payment_gateway varchar(50),
    payment_order_id varchar(255),
    payment_id varchar(255),
    payment_status varchar(50) not null,
    order_source varchar(50) not null default 'WEBSITE',
    invoice_id uuid,
    status varchar(50) not null,
    created_at timestamp not null
);

alter table orders add column if not exists order_source varchar(50) not null default 'WEBSITE';
alter table orders add column if not exists invoice_id uuid;
alter table orders add column if not exists tax numeric(12, 2) not null default 0;
alter table orders add column if not exists delivery numeric(12, 2) not null default 0;
update orders set order_source = 'WEBSITE' where order_source is null;
create unique index if not exists idx_orders_invoice_id_unique on orders(invoice_id) where invoice_id is not null;

create table if not exists order_items (
    id uuid primary key,
    order_id uuid not null references orders(id) on delete cascade,
    product_id uuid references products(id),
    product_name varchar(255) not null,
    sku varchar(100) not null,
    category varchar(100) not null,
    price numeric(12, 2) not null,
    quantity integer not null,
    line_total numeric(12, 2) not null
);

create table if not exists invoices (
    id uuid primary key,
    invoice_number varchar(100) not null unique,
    customer_id uuid not null references customers(id),
    total_amount numeric(12, 2) not null,
    discount numeric(12, 2) not null,
    final_amount numeric(12, 2) not null,
    payment_mode varchar(20) not null,
    coupon_code varchar(100),
    created_at timestamp not null
);

alter table invoices add column if not exists coupon_code varchar(100);

create table if not exists invoice_items (
    id uuid primary key,
    invoice_id uuid not null references invoices(id),
    product_id uuid not null references products(id),
    quantity integer not null,
    price numeric(12, 2) not null,
    discount numeric(12, 2) not null
);

create table if not exists offers (
    id uuid primary key,
    name varchar(255) not null,
    type varchar(50) not null,
    offer_value numeric(12, 2) not null,
    category varchar(50),
    product_id uuid references products(id),
    start_date date not null,
    end_date date not null,
    active boolean not null,
    coupon_code varchar(100),
    discount_type varchar(50),
    discount_value numeric(12, 2),
    max_discount_amount numeric(12, 2),
    min_order_value numeric(12, 2),
    applicable_on varchar(50),
    valid_from date,
    valid_to date
);

alter table offers add column if not exists coupon_code varchar(100);
alter table offers add column if not exists discount_type varchar(50);
alter table offers add column if not exists discount_value numeric(12, 2);
alter table offers add column if not exists max_discount_amount numeric(12, 2);
alter table offers add column if not exists min_order_value numeric(12, 2);
alter table offers add column if not exists applicable_on varchar(50);
alter table offers add column if not exists valid_from date;
alter table offers add column if not exists valid_to date;

create table if not exists campaigns (
    id uuid primary key,
    name varchar(255) not null,
    type varchar(50) not null,
    content text not null,
    offer_product varchar(255),
    media_url text,
    hashtags varchar(1000),
    link_url text,
    channels varchar(255),
    draft boolean not null default false,
    created_by varchar(255),
    created_at timestamp not null
);

alter table campaigns add column if not exists offer_product varchar(255);
alter table campaigns add column if not exists media_url text;
alter table campaigns add column if not exists hashtags varchar(1000);
alter table campaigns add column if not exists link_url text;
alter table campaigns add column if not exists channels varchar(255);
alter table campaigns add column if not exists draft boolean not null default false;
alter table campaigns add column if not exists created_by varchar(255);

create table if not exists campaign_logs (
    id uuid primary key,
    campaign_id uuid not null references campaigns(id),
    customer_id uuid references customers(id),
    channel varchar(50),
    content text,
    media_url text,
    status varchar(50) not null,
    platform_response_id varchar(255),
    error_message text,
    published_by varchar(255),
    published_at timestamp,
    created_at timestamp not null
);

alter table campaign_logs add column if not exists channel varchar(50);
alter table campaign_logs add column if not exists content text;
alter table campaign_logs add column if not exists media_url text;
alter table campaign_logs add column if not exists platform_response_id varchar(255);
alter table campaign_logs add column if not exists error_message text;
alter table campaign_logs add column if not exists published_by varchar(255);
alter table campaign_logs add column if not exists published_at timestamp;

create table if not exists receipt_settings (
    id uuid primary key,
    shop_name varchar(255) not null,
    header_line varchar(255),
    logo_url text,
    login_kicker varchar(255),
    homepage_title varchar(1000),
    homepage_subtitle varchar(2000),
    hero_primary_image_url text,
    hero_secondary_image_url text,
    trust_badge_one varchar(255),
    trust_badge_two varchar(255),
    trust_badge_three varchar(255),
    trust_badge_four varchar(255),
    address varchar(1000) not null,
    phone_number varchar(50),
    gst_number varchar(100),
    footer_note varchar(500),
    show_address boolean not null,
    show_phone_number boolean not null,
    show_gst_number boolean not null
);

alter table receipt_settings add column if not exists logo_url text;
alter table receipt_settings add column if not exists login_kicker varchar(255);
alter table receipt_settings add column if not exists homepage_title varchar(1000);
alter table receipt_settings add column if not exists homepage_subtitle varchar(2000);
alter table receipt_settings add column if not exists hero_primary_image_url text;
alter table receipt_settings add column if not exists hero_secondary_image_url text;
alter table receipt_settings add column if not exists trust_badge_one varchar(255);
alter table receipt_settings add column if not exists trust_badge_two varchar(255);
alter table receipt_settings add column if not exists trust_badge_three varchar(255);
alter table receipt_settings add column if not exists trust_badge_four varchar(255);

alter table receipt_settings alter column logo_url type text;
alter table receipt_settings alter column hero_primary_image_url type text;
alter table receipt_settings alter column hero_secondary_image_url type text;

create table if not exists staff_users (
    id uuid primary key,
    username varchar(100) not null unique,
    password_hash varchar(255) not null,
    display_name varchar(255) not null,
    role varchar(50) not null,
    enabled boolean not null default true,
    created_at timestamp not null
);

create table if not exists staff_user_permissions (
    user_id uuid not null references staff_users(id) on delete cascade,
    permission varchar(100) not null,
    primary key (user_id, permission)
);
