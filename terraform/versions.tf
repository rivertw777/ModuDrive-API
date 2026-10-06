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
  }

  # State lives in an S3 bucket made once by hand (it can't manage itself). Values come from
  # backend.hcl: terraform init -backend-config=backend.hcl. use_lockfile locks in S3 itself — no
  # DynamoDB table needed (Terraform 1.10+).
  backend "s3" {
    key          = "modudrive/terraform.tfstate"
    use_lockfile = true
    encrypt      = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.project
      ManagedBy = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}
