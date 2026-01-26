#!/bin/bash

#############################################################################
#
# Create an S3 bucket to store artifacts for AWS CloudFormation deployment
#
# Prerequisites:
# 1. AWS CLI installed and configured with necessary permissions
#
# Usage: ./create_aws_s3_bucket.sh <enter-a-bucket-name-to-create> <aws-profile-name> <aws-region>
#
# Example: ./create_aws_s3_bucket.sh my-artifact-bucket default us-east-1
#
#############################################################################

echo "Creating S3 bucket $1 for profile $2 in region $3"
BUCKET_NAME=$1
echo $BUCKET_NAME > bucket-name.txt
aws --profile $2 --region $3 s3 mb s3://$BUCKET_NAME
echo "DONE"