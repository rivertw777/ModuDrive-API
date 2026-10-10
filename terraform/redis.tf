# Valkey (Redis-compatible), two of them (006 2-4-6). "redis": auth sessions and login limits, member
# verification codes, mail idempotency (2-5). "storage_redis": storage-service alone — uploaded-block
# records, upload counts, download quotas, archive tokens — so a burst of uploads (up to 25,600 keys
# per user a day) can't fill the memory the sessions live in. TLS only — the app turns it on with
# REDIS_SSL_ENABLED=true.
resource "aws_elasticache_subnet_group" "redis" {
  name       = var.project
  subnet_ids = module.vpc.private_subnets
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = var.project
  description          = "Sessions, verification codes, quotas, mail idempotency"
  engine               = "valkey"
  engine_version       = "8.1"
  node_type            = local.scale.redis_node_type
  # Never evict: ElastiCache's default (volatile-lru) would drop live sessions — everything here has
  # a TTL — and log people out without a word. Full memory fails writes loudly instead.
  parameter_group_name = aws_elasticache_parameter_group.noeviction.name

  # Sessions live here: with one node (test scale) a node restart logs everyone out. Production keeps
  # a replica in the other AZ that takes over on its own.
  num_cache_clusters         = local.scale.redis_nodes
  automatic_failover_enabled = local.scale.redis_nodes > 1
  multi_az_enabled           = local.scale.redis_nodes > 1

  subnet_group_name  = aws_elasticache_subnet_group.redis.name
  security_group_ids = [aws_security_group.redis.id]

  transit_encryption_enabled = true
  at_rest_encryption_enabled = true
  auth_token                 = random_password.redis.result
}

resource "aws_elasticache_parameter_group" "noeviction" {
  name   = "${var.project}-noeviction"
  family = "valkey8"

  parameter {
    name  = "maxmemory-policy"
    value = "noeviction"
  }
}

# One node at every scale: losing it costs uploads in flight their records (the client sends those
# blocks again) and resets download quotas — no one is logged out. Never evicts either: evicting would
# reset download quotas and break zip tokens; full memory fails uploads instead, sessions untouched.
resource "aws_elasticache_replication_group" "storage_redis" {
  replication_group_id = "${var.project}-storage"
  description          = "storage-service: uploaded blocks, upload counts, download quotas"
  engine               = "valkey"
  engine_version       = "8.1"
  node_type            = local.scale.redis_node_type
  num_cache_clusters   = 1
  parameter_group_name = aws_elasticache_parameter_group.noeviction.name

  subnet_group_name  = aws_elasticache_subnet_group.redis.name
  security_group_ids = [aws_security_group.redis.id]

  transit_encryption_enabled = true
  at_rest_encryption_enabled = true
  auth_token                 = random_password.redis.result
}
