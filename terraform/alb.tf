# The only public entry point: ALB → gateway (aws-migration.md 1-4). Everything behind it is in private subnets.
resource "aws_lb" "main" {
  name               = var.project
  load_balancer_type = "application"
  subnets            = module.vpc.public_subnets
  security_groups    = [aws_security_group.alb.id]

  # Free, so every env: drop header names that aren't plain [A-Za-z0-9-] (smuggling vectors, and a
  # client-sent X_USER_ID never reaches the gateway). Desync mitigation stays at its "defensive" default.
  drop_invalid_header_fields = true
  enable_deletion_protection = var.deletion_protection

  # Every request with client IP, status and latency (prod: hardened) — the record WAF and the app
  # logs don't have for requests that never reached the gateway.
  dynamic "access_logs" {
    for_each = var.hardened ? [1] : []
    content {
      bucket  = aws_s3_bucket.logs[0].id
      prefix  = "alb"
      enabled = true
    }
  }

  depends_on = [aws_s3_bucket_policy.logs]
}

resource "aws_lb_target_group" "gateway" {
  name        = "${var.project}-gateway"
  port        = local.service_ports.gateway
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = module.vpc.vpc_id

  # Actuator lives on the management port, not the app port (where /actuator/** is 404).
  health_check {
    port    = "9464"
    path    = "/actuator/health"
    matcher = "200"
  }

  # Draining waits this long for in-flight requests on a stopping task. Uploads and downloads stream,
  # so keep it longer than the 30s a typical API would use.
  deregistration_delay = 60
}

# api.<domain> with a DNS-validated certificate. Without a domain there's only plain HTTP — enough to
# see the stack answer, not to log in (the __Host- session cookie is HTTPS-only).
resource "aws_acm_certificate" "api" {
  count = local.domain_based ? 1 : 0

  domain_name       = "api.${var.domain_name}"
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "api_cert_validation" {
  for_each = local.domain_based ? {
    for o in aws_acm_certificate.api[0].domain_validation_options : o.domain_name => o
  } : {}

  zone_id = aws_route53_zone.main[0].zone_id
  name    = each.value.resource_record_name
  type    = each.value.resource_record_type
  ttl     = 300
  records = [each.value.resource_record_value]
}

resource "aws_acm_certificate_validation" "api" {
  count = local.domain_based ? 1 : 0

  certificate_arn         = aws_acm_certificate.api[0].arn
  validation_record_fqdns = [for r in aws_route53_record.api_cert_validation : r.fqdn]
}

resource "aws_lb_listener" "https" {
  count = local.domain_based ? 1 : 0

  load_balancer_arn = aws_lb.main.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.api[0].certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.gateway.arn
  }
}

# With a domain, port 80 only redirects to HTTPS; without one it serves directly.
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = local.domain_based ? "redirect" : "forward"
    target_group_arn = local.domain_based ? null : aws_lb_target_group.gateway.arn

    dynamic "redirect" {
      for_each = local.domain_based ? [1] : []
      content {
        port        = "443"
        protocol    = "HTTPS"
        status_code = "HTTP_301"
      }
    }
  }
}

resource "aws_route53_record" "api" {
  count = local.domain_based ? 1 : 0

  zone_id = aws_route53_zone.main[0].zone_id
  name    = "api.${var.domain_name}"
  type    = "A"

  alias {
    name                   = aws_lb.main.dns_name
    zone_id                = aws_lb.main.zone_id
    evaluate_target_health = true
  }
}
