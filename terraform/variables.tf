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

# The image every service runs — CI passes the commit SHA (terraform apply -var image_tag=<sha>), so a
# deploy is a plan you can read. Repositories are immutable: a tag always means the same image.
variable "image_tag" {
  type = string
}

# Which size to run: "test" (smallest that runs the whole system, for trying the stack out) or
# "production" (sized and made redundant for the MAU 5M target). Every value that differs between the
# two lives in scale.tf — switch the whole set at once, never piecemeal.
variable "scale" {
  type    = string
  default = "test"

  validation {
    condition     = contains(["test", "production"], var.scale)
    error_message = "scale is \"test\" or \"production\"."
  }
}
