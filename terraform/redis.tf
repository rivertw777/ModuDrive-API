# Valkey (Redis-compatible) clusters by purpose — var.redis_clusters says which, and which services use
# each (006 2-4-6). demo runs one for everything (like local), prod one per purpose:
#   auth    — sessions, login limits, new-device codes (auth-service)
#   member  — sign-up verification codes (member-service)
#   mail    — consumer idempotency (mail-service, no DB of its own)
#   storage — uploaded-block records, upload counts, download quotas, zip tokens (storage-service)
# TLS only — the app turns it on with REDIS_SSL_ENABLED=true.
resource "aws_elasticache_subnet_group" "redis" {
  name       = var.project
  subnet_ids = module.vpc.private_subnets
}

# The two clusters this stack had before it went by purpose: kept, not destroyed and made again
# (which would log everyone out).
moved {
  from = aws_elasticache_replication_group.redis
  to   = aws_elasticache_replication_group.redis["auth"]
}

moved {
  from = aws_elasticache_replication_group.storage_redis
  to   = aws_elasticache_replication_group.redis["storage"]
}

# demo: ElastiCache for Valkey.
resource "aws_elasticache_replication_group" "redis" {
  for_each = var.redis_engine == "elasticache" ? var.redis_clusters : {}

  # "auth" keeps the stack's first id — changing it would replace the cluster that holds the sessions.
  replication_group_id = each.key == "auth" ? var.project : "${var.project}-${each.key}"
  description          = "Valkey for ${join(", ", each.value.clients)}"
  engine               = "valkey"
  engine_version       = "8.1"
  node_type            = each.value.node_type
  # Never evict: everything here has a TTL, so ElastiCache's default (volatile-lru) would silently drop
  # live sessions, verification codes, download quotas or zip tokens. Full memory fails writes loudly.
  parameter_group_name = aws_elasticache_parameter_group.noeviction.name

  num_cache_clusters         = each.value.nodes
  automatic_failover_enabled = each.value.nodes > 1
  multi_az_enabled           = each.value.nodes > 1

  subnet_group_name  = aws_elasticache_subnet_group.redis.name
  security_group_ids = [aws_security_group.redis[each.key].id]

  transit_encryption_enabled = true
  at_rest_encryption_enabled = true
  auth_token                 = random_password.redis[each.key].result
  # A changed token (storage's, when it got its own) is rotated in: old and new both work until the
  # tasks restart with the new one, then the old one is dropped on the next change.
  auth_token_update_strategy = "ROTATE"
}

resource "aws_elasticache_parameter_group" "noeviction" {
  name   = "${var.project}-noeviction"
  family = "valkey8"

  parameter {
    name  = "maxmemory-policy"
    value = "noeviction"
  }
}

locals {
  # The services that use Redis, and the cluster each one talks to.
  redis_clients = flatten([for cluster in var.redis_clusters : cluster.clients])
  redis_cluster_of = merge([
    for name, cluster in var.redis_clusters : { for client in cluster.clients : client => name }
  ]...)
  redis_host = merge([
    for name, cluster in var.redis_clusters : {
      for client in cluster.clients : client => (var.redis_engine == "memorydb"
        ? aws_memorydb_cluster.redis[name].cluster_endpoint[0].address
      : aws_elasticache_replication_group.redis[name].primary_endpoint_address)
    }
  ]...)
}

# prod: MemoryDB for Valkey. A write is acknowledged only once it's in the Multi-AZ transaction log,
# so a primary lost (or cut off) and replaced by a replica loses nothing: the sessions, verification
# codes, upload records and quotas here stay exactly as written. Cluster mode — the app keeps keys
# used together in one hash slot (spec 006 2-4-6) and follows topology changes (application-redis.yml).
resource "aws_memorydb_cluster" "redis" {
  for_each = var.redis_engine == "memorydb" ? var.redis_clusters : {}

  name                   = "${var.project}-${each.key}"
  description            = "Valkey for ${join(", ", each.value.clients)}"
  engine                 = "valkey"
  engine_version         = "7.3"
  node_type              = each.value.node_type
  num_shards             = each.value.shards
  num_replicas_per_shard = each.value.nodes - 1
  # Never evict, like ElastiCache above.
  parameter_group_name = aws_memorydb_parameter_group.noeviction[0].name

  acl_name           = aws_memorydb_acl.redis[each.key].name
  subnet_group_name  = aws_memorydb_subnet_group.redis[0].name
  security_group_ids = [aws_security_group.redis[each.key].id]
  tls_enabled        = true

  snapshot_retention_limit   = 35
  snapshot_window            = "17:00-18:00"         # 02:00–03:00 KST
  maintenance_window         = "sun:20:30-sun:21:30" # after Aurora's
  auto_minor_version_upgrade = true

  lifecycle {
    ignore_changes = [engine_version]
  }
}

resource "aws_memorydb_subnet_group" "redis" {
  count = var.redis_engine == "memorydb" ? 1 : 0

  name       = var.project
  subnet_ids = module.vpc.private_subnets
}

resource "aws_memorydb_parameter_group" "noeviction" {
  count = var.redis_engine == "memorydb" ? 1 : 0

  name   = "${var.project}-noeviction"
  family = "memorydb_valkey7"

  parameter {
    name  = "maxmemory-policy"
    value = "noeviction"
  }
}

# One ACL user per cluster, with that cluster's token as its password — like ElastiCache's AUTH
# token, a service holds only its own cluster's (secrets.tf).
resource "aws_memorydb_user" "redis" {
  for_each = var.redis_engine == "memorydb" ? var.redis_clusters : {}

  user_name     = "${var.project}-${each.key}"
  access_string = "on ~* &* +@all"

  authentication_mode {
    type      = "password"
    passwords = [random_password.redis[each.key].result]
  }
}

resource "aws_memorydb_acl" "redis" {
  for_each = var.redis_engine == "memorydb" ? var.redis_clusters : {}

  name       = "${var.project}-${each.key}"
  user_names = [aws_memorydb_user.redis[each.key].user_name]
}
