# The domain's hosted zone. After the first apply, point the registrar's name servers at
# name_servers (outputs.tf) — until then nothing under the domain resolves and SES can't verify it.
resource "aws_route53_zone" "main" {
  count = var.domain_name == null ? 0 : 1

  name = var.domain_name
}
