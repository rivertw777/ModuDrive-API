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

# The image every service runs — CI passes the commit SHA (terraform apply -var image_tag=<sha>), so a
# deploy is a plan you can read. Repositories are immutable: a tag always means the same image.
variable "image_tag" {
  type = string
}

# Per service: Fargate size and how far autoscaling may go. Tasks start at min and scale on CPU.
variable "services" {
  type = map(object({
    cpu    = number
    memory = number
    min    = number
    max    = number
  }))
  default = {
    gateway      = { cpu = 512, memory = 1024, min = 2, max = 6 }
    member       = { cpu = 512, memory = 1024, min = 1, max = 4 }
    auth         = { cpu = 512, memory = 1024, min = 2, max = 6 }
    file         = { cpu = 512, memory = 1024, min = 2, max = 6 }
    storage      = { cpu = 1024, memory = 2048, min = 2, max = 6 }
    mail         = { cpu = 512, memory = 1024, min = 1, max = 2 }
    notification = { cpu = 512, memory = 1024, min = 1, max = 2 }
  }
}
