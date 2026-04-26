# GitHub Deploy for Single EC2

This repo now includes a reusable bootstrap + deploy path for a new AWS account:

- CloudFormation stack: `infra/cloudformation/single-ec2-stack.yaml`
- one-time bootstrap helper: `scripts/cloudformation/deploy-single-ec2-stack.sh`
- push deploy workflow: `.github/workflows/deploy-single-ec2.yml`

## What the stack creates

- VPC
- public subnet
- internet gateway and route table
- security group
- one EC2 instance
- one separate encrypted EBS data volume
- one Elastic IP
- one S3 artifact bucket
- one Secrets Manager secret for app config
- one Secrets Manager secret for local PostgreSQL credentials
- one EC2 IAM role for SSM + secret/artifact access
- one GitHub OIDC deploy role

## What gets stored in Secrets Manager

### App config secret

Holds application env values like:

- `CUSTOMER_JWT_SECRET`
- `PHONEPE_*`
- `TWILIO_*`
- `META_*`
- `AWS_S3_BUCKET`
- `AWS_CLOUDFRONT_DOMAIN`

### Database secret

Holds:

- `POSTGRES_DB`
- `POSTGRES_USER`
- generated `POSTGRES_PASSWORD`

## One-time bootstrap in a new AWS account

1. Copy the example parameter file:

```bash
cp infra/cloudformation/single-ec2-stack.parameters.example.json /tmp/retail-single-ec2.parameters.json
```

2. Edit the values.

3. Deploy the stack using an admin or bootstrap credential for that AWS account:

```bash
./scripts/cloudformation/deploy-single-ec2-stack.sh retail-shop-prod /tmp/retail-single-ec2.parameters.json us-east-1
```

4. Save these stack outputs:

- `GitHubDeployRoleArn`
- `ArtifactBucketName`
- `InstanceId`
- `AppConfigSecretArn`
- `DatabaseSecretArn`

## GitHub repository settings

Add these **repository variables**:

- `AWS_REGION`
- `SINGLE_EC2_STACK_NAME`
- `AWS_GITHUB_DEPLOY_ROLE_ARN`

Recommended values:

- `AWS_REGION=us-east-1`
- `SINGLE_EC2_STACK_NAME=<your stack name>`
- `AWS_GITHUB_DEPLOY_ROLE_ARN=<GitHubDeployRoleArn output>`

No GitHub AWS access key secrets are needed after the OIDC role exists.

## How push deploy works

On push to `main`, the workflow:

1. assumes the GitHub OIDC role
2. reads stack outputs from CloudFormation
3. packages the repo into a release tarball
4. uploads that tarball to the artifact bucket
5. uses SSM Run Command on the EC2 instance
6. downloads the tarball on the instance
7. reads app and database env values from Secrets Manager
8. runs `scripts/ec2/deploy-single-host.sh`

## Updating secrets later

You can update secret values directly in Secrets Manager without changing the repo.

The next push deploy will pull fresh values automatically.

## Important note about first deploy

The instance is created empty. The application is not live until the GitHub workflow runs once, or until you manually push a release bundle to it.

## Important note about availability

This is still a single EC2 host. It is much cheaper and durable, but it is not high availability.
