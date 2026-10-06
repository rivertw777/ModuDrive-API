variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "project" {
  type    = string
  default = "modudrive"
}

# WEB and API must share one registrable domain (app.<domain> / api.<domain>): the session cookie is
# SameSite=Strict + host-only, and *.cloudfront.net / *.elb.amazonaws.com are each their own site
# (aws-migration.md 2-11). Null until a domain is bought — everything domain-bound (Route 53 zone,
# SES identity) is skipped until then.
variable "domain_name" {
  type    = string
  default = null
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.small"
}

variable "redis_node_type" {
  type    = string
  default = "cache.t4g.small"
}
