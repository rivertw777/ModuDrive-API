# Edge filtering in front of the ALB (prod: hardened). AWS managed rules plus per-IP rate limits;
# the app's own checks (session, login attempt limits, quotas) stay the real gate.
locals {
  # Raw file bytes: XSS/SQLi body patterns would block anyone uploading an .html or .sql file.
  waf_upload_path = "/api/v1/storage/blocks"
  # Everything reachable without a session that checks a password or a code, or sends a mail.
  waf_login_paths = [
    "/api/v1/auth/login",
    "/api/v1/auth/verify-email/",
    "/api/v1/member/sign-up",
    "/api/v1/member/verify-email/",
  ]
}

resource "aws_wafv2_web_acl" "main" {
  count = var.hardened ? 1 : 0

  name  = var.project
  scope = "REGIONAL"

  default_action {
    allow {}
  }

  rule {
    name     = "ip-reputation"
    priority = 0
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        vendor_name = "AWS"
        name        = "AWSManagedRulesAmazonIpReputationList"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "ip-reputation"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "known-bad-inputs"
    priority = 1
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        vendor_name = "AWS"
        name        = "AWSManagedRulesKnownBadInputsRuleSet"

        # Its body rules would match the bytes of an uploaded log or .ser file; the header and URI
        # rules (Log4J, Java deserialization, ...) still block.
        dynamic "rule_action_override" {
          for_each = ["Log4JRCE_BODY", "JavaDeserializationRCE_BODY"]
          content {
            name = rule_action_override.value
            action_to_use {
              count {}
            }
          }
        }
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "known-bad-inputs"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "common"
    priority = 2
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        vendor_name = "AWS"
        name        = "AWSManagedRulesCommonRuleSet"

        # It blocks bodies over 8 KB; a commit lists every block hash, so big files exceed that.
        # Body size is the app's limit to enforce.
        rule_action_override {
          name = "SizeRestrictions_BODY"
          action_to_use {
            count {}
          }
        }

        scope_down_statement {
          not_statement {
            statement {
              byte_match_statement {
                search_string         = local.waf_upload_path
                positional_constraint = "EXACTLY"
                field_to_match {
                  uri_path {}
                }
                text_transformation {
                  priority = 0
                  type     = "NONE"
                }
              }
            }
          }
        }
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "common"
      sampled_requests_enabled   = true
    }
  }

  rule {
    name     = "sqli"
    priority = 3
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        vendor_name = "AWS"
        name        = "AWSManagedRulesSQLiRuleSet"

        scope_down_statement {
          not_statement {
            statement {
              byte_match_statement {
                search_string         = local.waf_upload_path
                positional_constraint = "EXACTLY"
                field_to_match {
                  uri_path {}
                }
                text_transformation {
                  priority = 0
                  type     = "NONE"
                }
              }
            }
          }
        }
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "sqli"
      sampled_requests_enabled   = true
    }
  }

  # Credential stuffing, 6-digit code guessing and mail bombing across many accounts from one address —
  # the app only limits attempts per account and device.
  rule {
    name     = "login-rate"
    priority = 4
    action {
      block {}
    }
    statement {
      rate_based_statement {
        limit                 = 100
        evaluation_window_sec = 300
        aggregate_key_type    = "IP"
        scope_down_statement {
          or_statement {
            dynamic "statement" {
              for_each = local.waf_login_paths
              content {
                byte_match_statement {
                  search_string         = statement.value
                  positional_constraint = "STARTS_WITH"
                  field_to_match {
                    uri_path {}
                  }
                  # So //api/v1/... or %2F can't slip past the prefix.
                  text_transformation {
                    priority = 0
                    type     = "URL_DECODE"
                  }
                  text_transformation {
                    priority = 1
                    type     = "NORMALIZE_PATH"
                  }
                }
              }
            }
          }
        }
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "login-rate"
      sampled_requests_enabled   = true
    }
  }

  # A flood guard, not a quota: an upload sends one request per 8 MB block, and an office or mobile
  # carrier puts many users behind one address.
  rule {
    name     = "ip-rate"
    priority = 5
    action {
      block {}
    }
    statement {
      rate_based_statement {
        limit                 = 20000
        evaluation_window_sec = 300
        aggregate_key_type    = "IP"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "ip-rate"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = var.project
    sampled_requests_enabled   = true
  }
}

resource "aws_wafv2_web_acl_association" "alb" {
  count = var.hardened ? 1 : 0

  resource_arn = aws_lb.main.arn
  web_acl_arn  = aws_wafv2_web_acl.main[0].arn
}

# WAF only writes to log groups named aws-waf-logs-*.
resource "aws_cloudwatch_log_group" "waf" {
  count = var.hardened ? 1 : 0

  name              = "aws-waf-logs-${var.project}"
  kms_key_id        = local.kms_key_arn
  retention_in_days = 30
}

resource "aws_wafv2_web_acl_logging_configuration" "main" {
  count = var.hardened ? 1 : 0

  resource_arn            = aws_wafv2_web_acl.main[0].arn
  log_destination_configs = [aws_cloudwatch_log_group.waf[0].arn]

  # The session cookie is a bearer credential — never into logs.
  redacted_fields {
    single_header {
      name = "cookie"
    }
  }
}
