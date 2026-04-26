# S3 and CloudFront Image Upload

This app supports two AWS credential modes:

- `dev` or `local`: optional temporary `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` environment variables.
- `prod` on ECS: no keys in the app. The backend uses the ECS task role through the AWS SDK default credential chain.

## API

`POST /api/upload`

Form data:

- `image`: JPG, PNG, or WEBP file.
- `category`: folder prefix such as `jewellery`, `cosmetics`, `offers`, or `branding`.

Response:

```json
{
  "id": "uuid",
  "category": "jewellery",
  "cloudfrontUrl": "https://dxxxxxxxx.cloudfront.net/jewellery/uuid-timestamp.webp",
  "s3Key": "jewellery/uuid-timestamp.webp",
  "createdAt": "2026-04-23T15:30:00"
}
```

## Backend Environment

Required:

```bash
AWS_REGION=us-east-1
AWS_S3_BUCKET=<private-bucket-name>
AWS_CLOUDFRONT_DOMAIN=<cloudfront-domain>
```

Local/dev only:

```bash
SPRING_PROFILES_ACTIVE=local
AWS_ACCESS_KEY_ID=<temporary-access-key>
AWS_SECRET_ACCESS_KEY=<temporary-secret-key>
```

Optional:

```bash
UPLOAD_MAX_BYTES=5242880
UPLOAD_MAX_FILE_SIZE=8MB
UPLOAD_MAX_REQUEST_SIZE=10MB
```

## RDS Schema

The app creates/stores image metadata in:

```sql
create table if not exists image_assets (
    id uuid primary key,
    category varchar(255) not null,
    cloudfront_url varchar(2000) not null,
    s3_key varchar(1000) not null unique,
    content_type varchar(100) not null,
    file_size_bytes bigint not null,
    created_at timestamp not null
);
```

Product image fields still use `image_data_url` for compatibility, but new uploads store the CloudFront URL there instead of base64 data.

## IAM Policy For ECS Task Role

Replace bucket name before attaching to the ECS backend task role:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowImageObjectAccess",
      "Effect": "Allow",
      "Action": [
        "s3:PutObject",
        "s3:GetObject"
      ],
      "Resource": "arn:aws:s3:::<bucket-name>/*"
    }
  ]
}
```

## S3 Bucket

Recommended:

- Block all public access: enabled.
- Versioning: enabled.
- Default encryption: enabled.
- No public bucket policy.

## CloudFront With OAC

1. Create a CloudFront distribution.
2. Origin: the private S3 bucket.
3. Origin Access: use Origin Access Control.
4. Viewer protocol policy: redirect HTTP to HTTPS.
5. Cache policy: optimized caching is fine for immutable image names.
6. Copy the CloudFront domain into `AWS_CLOUDFRONT_DOMAIN`.

## Bucket Policy For CloudFront OAC

Replace placeholders:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "AllowCloudFrontServicePrincipalReadOnly",
      "Effect": "Allow",
      "Principal": {
        "Service": "cloudfront.amazonaws.com"
      },
      "Action": "s3:GetObject",
      "Resource": "arn:aws:s3:::<bucket-name>/*",
      "Condition": {
        "StringEquals": {
          "AWS:SourceArn": "arn:aws:cloudfront::<account-id>:distribution/<distribution-id>"
        }
      }
    }
  ]
}
```

## ECS Task Role Attachment

1. Create or update the backend ECS task role.
2. Attach the least-privilege S3 policy above.
3. Register a new backend task definition revision with that task role.
4. Set backend environment variables:
   - `SPRING_PROFILES_ACTIVE=prod`
   - `AWS_REGION=us-east-1`
   - `AWS_S3_BUCKET=<bucket-name>`
   - `AWS_CLOUDFRONT_DOMAIN=<cloudfront-domain>`
5. Redeploy the backend service.

Do not set `AWS_ACCESS_KEY_ID` or `AWS_SECRET_ACCESS_KEY` in ECS.

## Example Upload

```bash
curl -u admin:admin123 \
  -F "image=@/path/product.webp" \
  -F "category=jewellery" \
  https://<backend-domain>/api/upload
```

The returned `cloudfrontUrl` is what the frontend stores on products and branding images.
