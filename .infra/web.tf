# ModuDrive-WEB: the built SPA (dist/) in a private bucket, served by CloudFront at app.<domain>
# (003-aws-migration.md 1-11). Domain-bound like the API's HTTPS: nothing here until domain_name is set.

# CloudFront only takes certificates from us-east-1.
provider "aws" {
  alias               = "us_east_1"
  region              = "us-east-1"
  allowed_account_ids = var.account_id == null ? null : [var.account_id]

  default_tags {
    tags = {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

locals {
  web_domain = local.domain_based ? "app.${var.domain_name}" : null

  # Mirrors contentSecurityPolicy() in ModuDrive-WEB vite.config.ts — change both together.
  web_csp = local.domain_based ? join("; ", [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self' https://fonts.googleapis.com",
    "font-src 'self' https://fonts.gstatic.com",
    "img-src 'self' blob: data:",
    "media-src 'self' blob:",
    "connect-src 'self' https://api.${var.domain_name}",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'",
  ]) : null
}

# Holds only the public build output, so it can be emptied and replaced freely (no prevent_destroy).
resource "aws_s3_bucket" "web" {
  count = local.domain_based ? 1 : 0

  bucket        = "${var.project}-web-${data.aws_caller_identity.current.account_id}"
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "web" {
  count = local.domain_based ? 1 : 0

  bucket                  = aws_s3_bucket.web[0].id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# Only this distribution reads the bucket (origin access control); nobody reads it directly.
resource "aws_s3_bucket_policy" "web" {
  count = local.domain_based ? 1 : 0

  bucket = aws_s3_bucket.web[0].id
  policy = data.aws_iam_policy_document.web_bucket[0].json
}

data "aws_iam_policy_document" "web_bucket" {
  count = local.domain_based ? 1 : 0

  statement {
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.web[0].arn}/*"]
    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.web[0].arn]
    }
  }
}

resource "aws_acm_certificate" "web" {
  count    = local.domain_based ? 1 : 0
  provider = aws.us_east_1

  domain_name       = local.web_domain
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "web_cert_validation" {
  for_each = local.domain_based ? {
    for o in aws_acm_certificate.web[0].domain_validation_options : o.domain_name => o
  } : {}

  zone_id = aws_route53_zone.main[0].zone_id
  name    = each.value.resource_record_name
  type    = each.value.resource_record_type
  records = [each.value.resource_record_value]
  ttl     = 300
}

resource "aws_acm_certificate_validation" "web" {
  count    = local.domain_based ? 1 : 0
  provider = aws.us_east_1

  certificate_arn         = aws_acm_certificate.web[0].arn
  validation_record_fqdns = [for r in aws_route53_record.web_cert_validation : r.fqdn]
}

resource "aws_cloudfront_origin_access_control" "web" {
  count = local.domain_based ? 1 : 0

  name                              = "${var.project}-web"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

resource "aws_cloudfront_response_headers_policy" "web" {
  count = local.domain_based ? 1 : 0

  name = "${var.project}-web"

  security_headers_config {
    content_security_policy {
      content_security_policy = local.web_csp
      override                = true
    }
    strict_transport_security {
      access_control_max_age_sec = 31536000
      include_subdomains         = true
      override                   = true
    }
    content_type_options {
      override = true
    }
    frame_options {
      frame_option = "DENY"
      override     = true
    }
    referrer_policy {
      referrer_policy = "strict-origin-when-cross-origin"
      override        = true
    }
  }
}

data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

resource "aws_cloudfront_distribution" "web" {
  count = local.domain_based ? 1 : 0

  enabled             = true
  is_ipv6_enabled     = true
  aliases             = [local.web_domain]
  default_root_object = "index.html"
  http_version        = "http2and3"
  # North America, Europe and Asia (Korea included) — the cheapest class that serves Korea locally.
  price_class = "PriceClass_200"

  origin {
    origin_id                = "web"
    domain_name              = aws_s3_bucket.web[0].bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.web[0].id
  }

  default_cache_behavior {
    target_origin_id           = "web"
    viewer_protocol_policy     = "redirect-to-https"
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    compress                   = true
    cache_policy_id            = data.aws_cloudfront_cache_policy.caching_optimized.id
    response_headers_policy_id = aws_cloudfront_response_headers_policy.web[0].id
  }

  # SPA routes (/files/123 ...) aren't objects: S3 answers 403 for a missing key behind OAC, so both
  # fall back to index.html and the router takes it from there.
  custom_error_response {
    error_code         = 403
    response_code      = 200
    response_page_path = "/index.html"
  }
  custom_error_response {
    error_code         = 404
    response_code      = 200
    response_page_path = "/index.html"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    acm_certificate_arn      = aws_acm_certificate_validation.web[0].certificate_arn
    ssl_support_method       = "sni-only"
    minimum_protocol_version = "TLSv1.2_2021"
  }
}

resource "aws_route53_record" "web" {
  for_each = local.domain_based ? toset(["A", "AAAA"]) : toset([])

  zone_id = aws_route53_zone.main[0].zone_id
  name    = local.web_domain
  type    = each.key

  alias {
    name                   = aws_cloudfront_distribution.web[0].domain_name
    zone_id                = aws_cloudfront_distribution.web[0].hosted_zone_id
    evaluate_target_health = false
  }
}

# ModuDrive-WEB's deploy workflow assumes this through GitHub's OIDC: upload dist/ and invalidate the
# cache, nothing else. Trusts only that repository's <env> GitHub environment.
data "aws_iam_policy_document" "github_web_deploy_assume" {
  count = local.domain_based ? 1 : 0

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.web_github_repository}:environment:${var.environment}"]
    }
  }
}

resource "aws_iam_role" "github_web_deploy" {
  count = local.domain_based ? 1 : 0

  name               = "${var.project}-github-web-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_web_deploy_assume[0].json
}

data "aws_iam_policy_document" "github_web_deploy" {
  count = local.domain_based ? 1 : 0

  statement {
    actions   = ["s3:ListBucket"]
    resources = [aws_s3_bucket.web[0].arn]
  }
  statement {
    actions   = ["s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.web[0].arn}/*"]
  }
  statement {
    actions   = ["cloudfront:CreateInvalidation", "cloudfront:GetInvalidation"]
    resources = [aws_cloudfront_distribution.web[0].arn]
  }
}

resource "aws_iam_role_policy" "github_web_deploy" {
  count = local.domain_based ? 1 : 0

  role   = aws_iam_role.github_web_deploy[0].id
  policy = data.aws_iam_policy_document.github_web_deploy[0].json
}
