terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.67"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.9"
    }
    # Zips the Discord forwarder Lambda (monitoring.tf).
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.7"
    }
  }

  # State lives in an S3 bucket made once by hand in each environment's account (it can't manage
  # itself). Values come from envs/<env>.backend.hcl:
  #   terraform init -reconfigure -backend-config=envs/<env>.backend.hcl
  # use_lockfile locks in S3 itself — no DynamoDB table needed (Terraform 1.10+).
  backend "s3" {
    key          = "modudrive/terraform.tfstate"
    use_lockfile = true
    encrypt      = true
  }
}

provider "aws" {
  region              = var.region
  allowed_account_ids = var.account_id == null ? null : [var.account_id]

  default_tags {
    tags = {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}
