# Mail (003-aws-migration.md 1-9). The configuration set and the Send-event pipeline exist regardless of the domain:
# mail-service sends every message with ConfigurationSetName=mail-events, and without it SES rejects
# the send (400 ConfigurationSetDoesNotExist) and every mail ends up in the DLQ.
resource "aws_sesv2_configuration_set" "mail_events" {
  configuration_set_name = "mail-events"
}

# SES → SNS → the mail-ses-events queue, raw (no SNS envelope): one Send event per mail, tagged with
# its delivery id, so mail-service can tell a send that timed out but went through from one that didn't.
resource "aws_sns_topic" "mail_ses_events" {
  name = "mail-ses-events"
}

resource "aws_sesv2_configuration_set_event_destination" "send_to_sns" {
  configuration_set_name = aws_sesv2_configuration_set.mail_events.configuration_set_name
  event_destination_name = "send-to-sns"

  event_destination {
    enabled              = true
    matching_event_types = ["SEND"]

    sns_destination {
      topic_arn = aws_sns_topic.mail_ses_events.arn
    }
  }
}

resource "aws_sns_topic_subscription" "mail_ses_events" {
  topic_arn            = aws_sns_topic.mail_ses_events.arn
  protocol             = "sqs"
  endpoint             = aws_sqs_queue.queue["mail-ses-events"].arn
  raw_message_delivery = true
}

# Only this topic may write to the queue — a forged Send event would mark an unsent mail as sent (#518).
resource "aws_sqs_queue_policy" "mail_ses_events" {
  queue_url = aws_sqs_queue.queue["mail-ses-events"].id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "sns.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.queue["mail-ses-events"].arn
      Condition = { ArnEquals = { "aws:SourceArn" = aws_sns_topic.mail_ses_events.arn } }
    }]
  })
}

# Sending identity — needs the domain. Easy DKIM's three CNAMEs verify it; a custom MAIL FROM
# (mail.<domain>) makes SPF align with the From domain. Still in the SES sandbox until AWS approves a
# production-access request (by hand, not Terraform): until then only verified addresses receive mail.
resource "aws_sesv2_email_identity" "domain" {
  count = var.domain_name == null ? 0 : 1

  email_identity         = var.domain_name
  configuration_set_name = aws_sesv2_configuration_set.mail_events.configuration_set_name
}

resource "aws_route53_record" "ses_dkim" {
  count = var.domain_name == null ? 0 : 3

  zone_id = aws_route53_zone.main[0].zone_id
  name    = "${aws_sesv2_email_identity.domain[0].dkim_signing_attributes[0].tokens[count.index]}._domainkey.${var.domain_name}"
  type    = "CNAME"
  ttl     = 1800
  records = ["${aws_sesv2_email_identity.domain[0].dkim_signing_attributes[0].tokens[count.index]}.dkim.amazonses.com"]
}

resource "aws_sesv2_email_identity_mail_from_attributes" "domain" {
  count = var.domain_name == null ? 0 : 1

  email_identity   = aws_sesv2_email_identity.domain[0].email_identity
  mail_from_domain = "mail.${var.domain_name}"
}

resource "aws_route53_record" "ses_mail_from_mx" {
  count = var.domain_name == null ? 0 : 1

  zone_id = aws_route53_zone.main[0].zone_id
  name    = "mail.${var.domain_name}"
  type    = "MX"
  ttl     = 1800
  records = ["10 feedback-smtp.${var.region}.amazonses.com"]
}

resource "aws_route53_record" "ses_mail_from_spf" {
  count = var.domain_name == null ? 0 : 1

  zone_id = aws_route53_zone.main[0].zone_id
  name    = "mail.${var.domain_name}"
  type    = "TXT"
  ttl     = 1800
  records = ["v=spf1 include:amazonses.com ~all"]
}
