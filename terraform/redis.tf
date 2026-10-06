# Valkey (Redis-compatible): auth sessions and login limits, member verification codes, storage
# download quotas, mail idempotency (2-5). TLS only — the app turns it on with REDIS_SSL_ENABLED=true.
resource "aws_elasticache_subnet_group" "redis" {
  name       = var.project
  subnet_ids = module.vpc.private_subnets
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = var.project
  description          = "Sessions, verification codes, quotas, mail idempotency"
  engine               = "valkey"
  node_type            = var.redis_node_type

  # ponytail: one node — a restart logs everyone out (sessions live here). Two nodes with
  # automatic_failover_enabled once that's not acceptable.
  num_cache_clusters = 1

  subnet_group_name  = aws_elasticache_subnet_group.redis.name
  security_group_ids = [aws_security_group.redis.id]

  transit_encryption_enabled = true
  at_rest_encryption_enabled = true
  auth_token                 = random_password.redis.result
}
