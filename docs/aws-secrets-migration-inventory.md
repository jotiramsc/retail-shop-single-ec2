# AWS Secrets Migration Inventory

This project uses two AWS Secrets Manager secrets for the single-EC2 deployment.
Do not migrate removed providers such as Twilio, Gupshup, PhonePe, or Leonardo.

## Secret Names

Use the same friendly names in the new AWS account if possible:

| Purpose | Secret name |
| --- | --- |
| Application runtime config | `retail-shop/single/app-config` |
| Local PostgreSQL credentials | `retail-shop/single/postgres` |

The CloudFormation template creates these as:

| Purpose | Template name expression |
| --- | --- |
| Application runtime config | `${ProjectName}/${EnvironmentName}/app-config` |
| Local PostgreSQL credentials | `${ProjectName}/${EnvironmentName}/postgres` |

Last known old-account ARNs from the previous deployment outputs:

| Purpose | Old ARN |
| --- | --- |
| Application runtime config | `arn:aws:secretsmanager:us-east-1:037227595452:secret:retail-shop/single/app-config-uaYy2O` |
| Local PostgreSQL credentials | `arn:aws:secretsmanager:us-east-1:037227595452:secret:retail-shop/single/postgres-RaSZ8N` |

## `retail-shop/single/app-config` JSON Keys

Required baseline:

```json
{
  "PORT": "8080",
  "SPRING_PROFILES_ACTIVE": "prod",
  "DB_INIT_MODE": "never",
  "CORS_ALLOWED_ORIGINS": "https://kpskrishnai.com,https://www.kpskrishnai.com",
  "STAFF_JWT_SECRET": "",
  "CUSTOMER_JWT_SECRET": "",
  "CHECKOUT_TAX_PERCENT": "0",
  "CHECKOUT_DELIVERY_CHARGE": "0",
  "CHECKOUT_FREE_DELIVERY_MIN_ORDER": "0",
  "PAYMENT_PROVIDER": "RAZORPAY",
  "RAZORPAY_KEY_ID": "",
  "RAZORPAY_KEY_SECRET": "",
  "RAZORPAY_WEBHOOK_SECRET": "",
  "AWS_REGION": "us-east-1",
  "AWS_S3_BUCKET": "",
  "AWS_CLOUDFRONT_DOMAIN": "",
  "GOOGLE_CLIENT_ID": "",
  "GOOGLE_MAPS_API_KEY": "",
  "MARKETING_OPENAI_API_KEY": "",
  "MARKETING_OPENAI_MODEL": "gpt-4.1-mini",
  "MARKETING_OPENAI_IMAGE_MODEL": "gpt-image-1.5",
  "MARKETING_OPENAI_IMAGE_SIZE": "1024x1024",
  "MARKETING_OPENAI_IMAGE_QUALITY": "medium",
  "META_GRAPH_VERSION": "v23.0",
  "META_ACCESS_TOKEN": "",
  "META_WHATSAPP_BUSINESS_ACCOUNT_ID": "",
  "META_WHATSAPP_PHONE_NUMBER_ID": "",
  "META_WHATSAPP_OTP_TEMPLATE_NAME": "",
  "META_WHATSAPP_OTP_TEMPLATE_LANGUAGE": "en_US",
  "FB_PAGE_ID": "",
  "FB_PAGE_ACCESS_TOKEN": "",
  "IG_BUSINESS_ACCOUNT_ID": "",
  "META_APP_ID": "",
  "META_APP_SECRET": "",
  "META_FB_EXCHANGE_TOKEN": "",
  "META_TOKEN_REFRESH_ENABLED": "true",
  "META_TOKEN_REFRESH_INTERVAL_MS": "3888000000",
  "META_TOKEN_REFRESH_INITIAL_DELAY_MS": "60000",
  "OMNICHANNEL_WEBSITE_BASE_URL": "https://kpskrishnai.com",
  "OMNICHANNEL_WEBHOOK_VERIFY_TOKEN": "",
  "OMNICHANNEL_WEBHOOK_SECRET": "",
  "OMNICHANNEL_DEFAULT_COUPON_CODE": "",
  "OMNICHANNEL_MAX_PRODUCT_CARDS": "6"
}
```

Optional alias keys supported by the app, but not required when the canonical keys above are present:

```text
MARKETING_META_ACCESS_TOKEN
MARKETING_FACEBOOK_PAGE_ID
MARKETING_FACEBOOK_PAGE_ACCESS_TOKEN
MARKETING_INSTAGRAM_BUSINESS_ACCOUNT_ID
MARKETING_META_GRAPH_VERSION
MARKETING_WHATSAPP_PHONE_NUMBER_ID
FB_APP_ID
FB_APP_SECRET
FB_EXCHANGE_TOKEN
```

Avoid long-lived AWS user keys on EC2. Prefer the instance role. Only use these locally or outside AWS:

```text
AWS_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY
```

## `retail-shop/single/postgres` JSON Keys

```json
{
  "POSTGRES_DB": "retail_shop",
  "POSTGRES_USER": "retail_user",
  "POSTGRES_PASSWORD": ""
}
```

The deploy script derives these runtime database env vars from the Postgres secret:

```text
DB_URL=jdbc:postgresql://retail-postgres:5432/${POSTGRES_DB}
DB_USERNAME=${POSTGRES_USER}
DB_PASSWORD=${POSTGRES_PASSWORD}
```

## New Account Recreation Commands

Create empty placeholders first, then fill the real values in the AWS console or with `put-secret-value`.

```bash
aws secretsmanager create-secret \
  --region us-east-1 \
  --name retail-shop/single/app-config \
  --secret-string file://app-config.json

aws secretsmanager create-secret \
  --region us-east-1 \
  --name retail-shop/single/postgres \
  --secret-string file://postgres-secret.json
```

After creating the new stack, pass the new secret ARNs to the EC2 deploy command or use the CloudFormation outputs:

```text
AppConfigSecretArn
DatabaseSecretArn
```

