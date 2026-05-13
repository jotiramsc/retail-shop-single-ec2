create table if not exists products (
    id uuid primary key,
    name varchar(255) not null,
    category varchar(50) not null,
    sku varchar(100) not null unique,
    cost_price numeric(12, 2) not null,
    selling_price numeric(12, 2) not null,
    website_price_percentage numeric(7, 2),
    website_price numeric(12, 2),
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
alter table products add column if not exists website_price_percentage numeric(7, 2);
alter table products add column if not exists website_price numeric(12, 2);
update products
set website_price = round((selling_price + (selling_price * website_price_percentage / 100))::numeric, 2)
where website_price_percentage is not null
  and website_price_percentage > 0;
update products
set website_price = selling_price
where website_price is null;

create table if not exists customers (
    id uuid primary key,
    name varchar(255),
    mobile varchar(20) unique,
    email varchar(255),
    password_hash varchar(255),
    auth_provider varchar(50) not null default 'OTP',
    google_subject varchar(255),
    mobile_verified boolean not null default false,
    email_verified boolean not null default false,
    updated_at timestamp,
    profile_completed_at timestamp,
    date_of_birth date,
    gender varchar(40),
    profile_image_url varchar(1000),
    alternate_mobile varchar(20),
    created_at timestamp not null
);

alter table customers alter column name drop not null;
alter table customers alter column mobile drop not null;
alter table customers add column if not exists email varchar(255);
alter table customers add column if not exists password_hash varchar(255);
alter table customers add column if not exists auth_provider varchar(50) not null default 'OTP';
alter table customers add column if not exists google_subject varchar(255);
alter table customers add column if not exists mobile_verified boolean not null default false;
alter table customers add column if not exists email_verified boolean not null default false;
alter table customers add column if not exists updated_at timestamp;
alter table customers add column if not exists profile_completed_at timestamp;
alter table customers add column if not exists date_of_birth date;
alter table customers add column if not exists gender varchar(40);
alter table customers add column if not exists profile_image_url varchar(1000);
alter table customers add column if not exists alternate_mobile varchar(20);
update customers set auth_provider = 'OTP' where auth_provider is null or trim(auth_provider) = '';
update customers set updated_at = created_at where updated_at is null;
create unique index if not exists idx_customers_email_unique on customers (lower(email)) where email is not null and trim(email) <> '';
create unique index if not exists idx_customers_google_subject_unique on customers (google_subject) where google_subject is not null and trim(google_subject) <> '';

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

create table if not exists site_visits (
    id uuid primary key,
    visitor_id varchar(100) not null,
    visit_date date not null,
    landing_path varchar(500) not null,
    referrer text,
    referrer_host varchar(255),
    utm_source varchar(255),
    utm_medium varchar(255),
    utm_campaign varchar(255),
    source_type varchar(50) not null,
    source_label varchar(255) not null,
    ip_address varchar(100),
    city varchar(255),
    region varchar(255),
    country_name varchar(255),
    country_code varchar(20),
    timezone varchar(255),
    exact_location_name varchar(500),
    postal_code varchar(40),
    location_source varchar(40),
    latitude double precision,
    longitude double precision,
    location_accuracy_meters double precision,
    organization varchar(255),
    accept_language varchar(255),
    user_agent varchar(1000),
    created_at timestamp not null
);

alter table site_visits add column if not exists ip_address varchar(100);
alter table site_visits add column if not exists city varchar(255);
alter table site_visits add column if not exists region varchar(255);
alter table site_visits add column if not exists country_name varchar(255);
alter table site_visits add column if not exists country_code varchar(20);
alter table site_visits add column if not exists timezone varchar(255);
alter table site_visits add column if not exists exact_location_name varchar(500);
alter table site_visits add column if not exists postal_code varchar(40);
alter table site_visits add column if not exists location_source varchar(40);
alter table site_visits add column if not exists latitude double precision;
alter table site_visits add column if not exists longitude double precision;
alter table site_visits add column if not exists location_accuracy_meters double precision;
alter table site_visits add column if not exists organization varchar(255);
alter table site_visits add column if not exists accept_language varchar(255);

create unique index if not exists idx_site_visits_unique_daily_visitor on site_visits(visitor_id, visit_date);
create index if not exists idx_site_visits_created_at on site_visits(created_at desc);
create index if not exists idx_site_visits_source_type on site_visits(source_type);

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

create table if not exists wishlist_items (
    id uuid primary key,
    customer_id uuid not null references customers(id) on delete cascade,
    product_id uuid not null references products(id) on delete cascade,
    created_at timestamp not null,
    unique (customer_id, product_id)
);

create index if not exists idx_wishlist_customer_created_at on wishlist_items(customer_id, created_at desc);

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

create sequence if not exists customer_order_number_seq start with 100 increment by 1;

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
    sales_person_user_id uuid,
    sales_person_name varchar(255) not null default 'Website',
    status varchar(50) not null,
    created_at timestamp not null
);

alter table orders add column if not exists order_source varchar(50) not null default 'WEBSITE';
alter table orders add column if not exists invoice_id uuid;
alter table orders add column if not exists tax numeric(12, 2) not null default 0;
alter table orders add column if not exists delivery numeric(12, 2) not null default 0;
alter table orders add column if not exists sales_person_user_id uuid;
alter table orders add column if not exists sales_person_name varchar(255) not null default 'Website';
update orders set order_source = 'WEBSITE' where order_source is null;
update orders
set sales_person_name = case
    when order_source = 'WEBSITE' then 'Website'
    else 'Unassigned'
end
where sales_person_name is null or trim(sales_person_name) = '';
create unique index if not exists idx_orders_invoice_id_unique on orders(invoice_id) where invoice_id is not null;
create index if not exists idx_orders_sales_person_created_at on orders (sales_person_user_id, created_at desc);

create table if not exists payment_transactions (
    id uuid primary key,
    provider varchar(50) not null,
    operation varchar(80) not null,
    status varchar(80) not null,
    customer_id uuid,
    order_id uuid,
    order_number varchar(100),
    gateway_order_id varchar(255),
    gateway_payment_id varchar(255),
    receipt varchar(100),
    currency varchar(20),
    amount numeric(12, 2),
    amount_subunits bigint,
    payment_state varchar(100),
    gateway_status varchar(100),
    webhook_event varchar(255),
    signature_status varchar(100),
    failure_code varchar(255),
    error_message varchar(4000),
    request_payload text,
    response_payload text,
    created_at timestamp not null,
    updated_at timestamp not null
);

alter table payment_transactions add column if not exists customer_id uuid;
alter table payment_transactions add column if not exists order_id uuid;
alter table payment_transactions add column if not exists order_number varchar(100);
alter table payment_transactions add column if not exists gateway_order_id varchar(255);
alter table payment_transactions add column if not exists gateway_payment_id varchar(255);
alter table payment_transactions add column if not exists receipt varchar(100);
alter table payment_transactions add column if not exists currency varchar(20);
alter table payment_transactions add column if not exists amount numeric(12, 2);
alter table payment_transactions add column if not exists amount_subunits bigint;
alter table payment_transactions add column if not exists payment_state varchar(100);
alter table payment_transactions add column if not exists gateway_status varchar(100);
alter table payment_transactions add column if not exists webhook_event varchar(255);
alter table payment_transactions add column if not exists signature_status varchar(100);
alter table payment_transactions add column if not exists failure_code varchar(255);
alter table payment_transactions add column if not exists error_message varchar(4000);
alter table payment_transactions add column if not exists request_payload text;
alter table payment_transactions add column if not exists response_payload text;
alter table payment_transactions add column if not exists updated_at timestamp not null default now();
create index if not exists idx_payment_transactions_created_at on payment_transactions(created_at desc);
create index if not exists idx_payment_transactions_gateway_order on payment_transactions(gateway_order_id);
create index if not exists idx_payment_transactions_gateway_payment on payment_transactions(gateway_payment_id);
create index if not exists idx_payment_transactions_status on payment_transactions(status);

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
    sales_person_user_id uuid,
    sales_person_name varchar(255) not null default 'Unassigned',
    created_at timestamp not null
);

alter table invoices add column if not exists coupon_code varchar(100);
alter table invoices add column if not exists sales_person_user_id uuid;
alter table invoices add column if not exists sales_person_name varchar(255) not null default 'Unassigned';
update invoices
set sales_person_name = 'Unassigned'
where sales_person_name is null or trim(sales_person_name) = '';
create index if not exists idx_invoices_sales_person_created_at on invoices (sales_person_user_id, created_at desc);

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
alter table campaigns add column if not exists campaign_name varchar(255);
alter table campaigns add column if not exists campaign_type varchar(50);
alter table campaigns add column if not exists campaign_goal varchar(80);
alter table campaigns add column if not exists offer_mode varchar(80);
alter table campaigns add column if not exists linked_offer_id uuid;
alter table campaigns add column if not exists coupon_code varchar(100);
alter table campaigns add column if not exists category_id uuid;
alter table campaigns add column if not exists product_id uuid;
alter table campaigns add column if not exists offer_title varchar(255);
alter table campaigns add column if not exists discount_type varchar(50);
alter table campaigns add column if not exists discount_value numeric(12, 2);
alter table campaigns add column if not exists start_date date;
alter table campaigns add column if not exists end_date date;
alter table campaigns add column if not exists target_platforms varchar(500);
alter table campaigns add column if not exists language varchar(50);
alter table campaigns add column if not exists tone varchar(50);
alter table campaigns add column if not exists status varchar(50);
alter table campaigns add column if not exists updated_at timestamp;

update campaigns set campaign_name = name where campaign_name is null or trim(campaign_name) = '';
update campaigns set campaign_type = coalesce(campaign_type, 'CUSTOM');
update campaigns set discount_type = coalesce(discount_type, 'NONE');
update campaigns set campaign_goal = coalesce(campaign_goal, case when discount_type is not null and discount_type <> 'NONE' then 'OFFER' else 'AWARENESS' end);
update campaigns set offer_mode = coalesce(offer_mode, case when discount_type is not null and discount_type <> 'NONE' then 'MANUAL' else 'NONE' end);
update campaigns set language = coalesce(language, 'ENGLISH');
update campaigns set tone = coalesce(tone, 'PREMIUM');
update campaigns set status = coalesce(status, case when draft then 'DRAFT' else 'PENDING_APPROVAL' end);
update campaigns set target_platforms = coalesce(target_platforms, channels);
update campaigns set updated_at = coalesce(updated_at, created_at, now());

create index if not exists idx_campaigns_status_created_at on campaigns(status, created_at desc);
create index if not exists idx_campaigns_campaign_type_created_at on campaigns(campaign_type, created_at desc);

create table if not exists campaign_contents (
    id uuid primary key,
    campaign_id uuid not null references campaigns(id) on delete cascade,
    platform varchar(50) not null,
    caption_text text,
    hashtags varchar(2000),
    call_to_action varchar(500),
    image_prompt text,
    image_url text,
    status varchar(50) not null,
    rejection_reason text,
    scheduled_at timestamp,
    published_at timestamp,
    external_post_id varchar(255),
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_campaign_contents_campaign_created_at on campaign_contents(campaign_id, created_at asc);
create index if not exists idx_campaign_contents_status_scheduled_at on campaign_contents(status, scheduled_at asc);

create table if not exists approval_history (
    id uuid primary key,
    campaign_content_id uuid not null references campaign_contents(id) on delete cascade,
    action varchar(50) not null,
    comment text,
    action_by varchar(255) not null,
    action_at timestamp not null
);

create index if not exists idx_approval_history_content_action_at on approval_history(campaign_content_id, action_at desc);

create table if not exists publish_logs (
    id uuid primary key,
    campaign_content_id uuid not null references campaign_contents(id) on delete cascade,
    platform varchar(50) not null,
    request_payload text,
    response_payload text,
    status varchar(100) not null,
    error_message text,
    created_at timestamp not null
);

create index if not exists idx_publish_logs_content_created_at on publish_logs(campaign_content_id, created_at desc);

create table if not exists campaign_analytics (
    id uuid primary key,
    campaign_content_id uuid not null references campaign_contents(id) on delete cascade,
    platform varchar(50) not null,
    impressions bigint not null default 0,
    likes bigint not null default 0,
    comments bigint not null default 0,
    shares bigint not null default 0,
    clicks bigint not null default 0,
    conversions bigint not null default 0,
    fetched_at timestamp not null
);

create index if not exists idx_campaign_analytics_platform_fetched_at on campaign_analytics(platform, fetched_at desc);
create index if not exists idx_campaign_analytics_content_fetched_at on campaign_analytics(campaign_content_id, fetched_at desc);

create table if not exists omnichannel_leads (
    id uuid primary key,
    channel varchar(50) not null,
    external_user_id varchar(255),
    customer_name varchar(255),
    mobile varchar(30),
    source_campaign varchar(255),
    product_interest varchar(500),
    latest_message varchar(2000),
    status varchar(50) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_omnichannel_leads_channel_external on omnichannel_leads(channel, external_user_id);
create index if not exists idx_omnichannel_leads_updated_at on omnichannel_leads(updated_at desc);
create index if not exists idx_omnichannel_leads_status on omnichannel_leads(status);

create table if not exists omnichannel_conversations (
    id uuid primary key,
    lead_id uuid not null references omnichannel_leads(id) on delete cascade,
    channel varchar(50) not null,
    external_thread_id varchar(255),
    status varchar(50) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index if not exists idx_omnichannel_conversations_lead on omnichannel_conversations(lead_id, updated_at desc);
create index if not exists idx_omnichannel_conversations_thread on omnichannel_conversations(channel, external_thread_id);

create table if not exists omnichannel_conversation_messages (
    id uuid primary key,
    conversation_id uuid not null references omnichannel_conversations(id) on delete cascade,
    direction varchar(20) not null,
    message_type varchar(50) not null,
    message_text varchar(4000),
    raw_payload varchar(12000),
    created_at timestamp not null
);

create index if not exists idx_omnichannel_messages_conversation_created on omnichannel_conversation_messages(conversation_id, created_at desc);

create table if not exists social_webhook_events (
    id uuid primary key,
    provider varchar(50) not null,
    event_type varchar(100),
    external_event_id varchar(255),
    signature_valid boolean,
    raw_payload varchar(12000) not null,
    received_at timestamp not null
);

create index if not exists idx_social_webhook_events_provider_received on social_webhook_events(provider, received_at desc);
create index if not exists idx_social_webhook_events_external_event on social_webhook_events(external_event_id);

create table if not exists ai_recommendation_logs (
    id uuid primary key,
    lead_id uuid,
    channel varchar(50) not null,
    search_query varchar(1000),
    filters varchar(2000),
    recommended_product_ids varchar(2000),
    created_at timestamp not null
);

create index if not exists idx_ai_recommendation_logs_lead_created on ai_recommendation_logs(lead_id, created_at desc);
create index if not exists idx_ai_recommendation_logs_channel_created on ai_recommendation_logs(channel, created_at desc);

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
    sales_person boolean not null default false,
    created_at timestamp not null
);

alter table staff_users add column if not exists sales_person boolean not null default false;

create table if not exists staff_user_permissions (
    user_id uuid not null references staff_users(id) on delete cascade,
    permission varchar(100) not null,
    primary key (user_id, permission)
);
