#!/bin/sh
set -eu

if [ "$#" -lt 2 ]; then
  echo "Usage: $0 <stack-name> <parameters-json-file> [region]"
  exit 1
fi

STACK_NAME="$1"
PARAMETERS_FILE="$2"
AWS_REGION="${3:-us-east-1}"
ROOT_DIR="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
TEMPLATE_FILE="$ROOT_DIR/infra/cloudformation/single-ec2-stack.yaml"

if [ ! -f "$PARAMETERS_FILE" ]; then
  echo "Parameters file not found: $PARAMETERS_FILE"
  exit 1
fi

PARAM_OVERRIDES="$(jq -r '.[] | "\(.ParameterKey)=\(.ParameterValue)"' "$PARAMETERS_FILE" | tr '\n' ' ')"

if [ -z "$PARAM_OVERRIDES" ]; then
  echo "No parameters found in $PARAMETERS_FILE"
  exit 1
fi

aws cloudformation deploy \
  --region "$AWS_REGION" \
  --stack-name "$STACK_NAME" \
  --template-file "$TEMPLATE_FILE" \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides $PARAM_OVERRIDES

aws cloudformation describe-stacks \
  --region "$AWS_REGION" \
  --stack-name "$STACK_NAME" \
  --query 'Stacks[0].Outputs[].[OutputKey,OutputValue]' \
  --output table
