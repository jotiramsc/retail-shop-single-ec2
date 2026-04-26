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
    expiry_date date,
    created_at timestamp not null
);

alter table products add column if not exists show_in_editors_picks boolean not null default false;
alter table products add column if not exists show_in_new_release boolean not null default false;
alter table products add column if not exists show_in_customer_access boolean not null default false;

create table if not exists customers (
    id uuid primary key,
    name varchar(255) not null,
    mobile varchar(20) not null unique,
    created_at timestamp not null
);

create table if not exists invoices (
    id uuid primary key,
    invoice_number varchar(100) not null unique,
    customer_id uuid not null references customers(id),
    total_amount numeric(12, 2) not null,
    discount numeric(12, 2) not null,
    final_amount numeric(12, 2) not null,
    payment_mode varchar(20) not null,
    created_at timestamp not null
);

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
    active boolean not null
);

create table if not exists campaigns (
    id uuid primary key,
    name varchar(255) not null,
    type varchar(50) not null,
    content text not null,
    created_at timestamp not null
);

create table if not exists campaign_logs (
    id uuid primary key,
    campaign_id uuid not null references campaigns(id),
    customer_id uuid references customers(id),
    status varchar(50) not null,
    created_at timestamp not null
);

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
