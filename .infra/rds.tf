# PostgreSQL — var.db_instances says how many and which services' databases each holds, var.db_engine
# whether as RDS instances (demo, one for all four) or Aurora clusters (prod, one per service), so file-service's load (uploads, outbox) never
# slows the login path and each can be sized, failed over and upgraded on its own.
#
# Either way every service has its own database and login that can reach only that database (003-aws-migration.md 1-5).
# Terraform makes the instances; the databases and logins are made inside Postgres by
# .docker/postgres/postgres_init.sh, run once per instance as an ECS task (db_init_run_tasks output).
# Tables come from each service's Flyway migrations on startup.

# The stack's first, shared instance is the one that holds file_db: kept, not destroyed and made
# again. In prod the other services' databases then move to their own instances (a data migration).
moved {
  from = aws_db_instance.postgres
  to   = aws_db_instance.postgres["file"]
}

# demo: plain RDS instances.
resource "aws_db_instance" "postgres" {
  for_each = var.db_engine == "rds" ? var.db_instances : {}

  # "file" keeps the stack's first identifier — changing it would replace the instance.
  identifier     = each.key == "file" ? var.project : "${var.project}-${each.key}"
  engine         = "postgres"
  engine_version = "18"
  instance_class = each.value.instance_class

  allocated_storage     = 20
  max_allocated_storage = 200
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = local.kms_key_arn

  # postgres_init.sh connects as this admin. RDS keeps its password in Secrets Manager and rotates it.
  username                      = "modudrive"
  manage_master_user_password   = true
  master_user_secret_kms_key_id = local.kms_key_arn

  db_subnet_group_name   = module.vpc.database_subnet_group_name
  vpc_security_group_ids = [aws_security_group.postgres[each.key].id]
  publicly_accessible    = false

  # A standby in another AZ at two nodes.
  multi_az = each.value.nodes > 1

  backup_retention_period    = 7
  auto_minor_version_upgrade = true
  deletion_protection        = var.deletion_protection
  skip_final_snapshot        = !var.deletion_protection
  final_snapshot_identifier  = var.deletion_protection ? "${var.project}-${each.key}-final" : null
}

# prod: Aurora PostgreSQL clusters. Storage keeps six copies across three AZs and acknowledges a write
# once four have it, so losing (or being partitioned from) an AZ neither loses a committed write nor
# stops writing. The services read and write through the writer endpoint only — every read sees the
# latest commit; the readers are failover targets, promoted in about 30 seconds.
resource "aws_rds_cluster" "postgres" {
  for_each = var.db_engine == "aurora" ? var.db_instances : {}

  cluster_identifier = "${var.project}-${each.key}"
  engine             = "aurora-postgresql"
  engine_version     = "18.4"

  master_username               = "modudrive"
  manage_master_user_password   = true
  master_user_secret_kms_key_id = local.kms_key_arn

  db_subnet_group_name            = module.vpc.database_subnet_group_name
  vpc_security_group_ids          = [aws_security_group.postgres[each.key].id]
  db_cluster_parameter_group_name = aws_rds_cluster_parameter_group.aurora[0].name
  storage_encrypted               = true
  kms_key_id                      = local.kms_key_arn

  backup_retention_period         = 35
  preferred_backup_window         = "18:00-19:00"         # 03:00–04:00 KST
  preferred_maintenance_window    = "sun:19:30-sun:20:30" # clear of the backup window
  copy_tags_to_snapshot           = true
  enabled_cloudwatch_logs_exports = ["postgresql"]

  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = !var.deletion_protection
  final_snapshot_identifier = var.deletion_protection ? "${var.project}-${each.key}-final" : null

  # Minor versions move on their own (auto_minor_version_upgrade); "18.4" is where it starts, not a pin.
  lifecycle {
    ignore_changes = [engine_version]
  }

  depends_on = [aws_cloudwatch_log_group.aurora]
}

# Declared here so the CMK covers it — left to RDS, the export creates it unencrypted and kept forever.
resource "aws_cloudwatch_log_group" "aurora" {
  for_each = var.db_engine == "aurora" ? var.db_instances : {}

  name              = "/aws/rds/cluster/${var.project}-${each.key}/postgresql"
  kms_key_id        = local.kms_key_arn
  retention_in_days = 30
}

# The writer and its readers, one per AZ in turn. Which becomes the writer is whichever is ready
# first (and changes on failover); promotion_tier only orders who's promoted next.
resource "aws_rds_cluster_instance" "postgres" {
  for_each = {
    for node in flatten([
      for name, cluster in(var.db_engine == "aurora" ? var.db_instances : {}) : [
        for i in range(cluster.nodes) : { key = "${name}-${i}", cluster = name, index = i, class = cluster.instance_class }
      ]
    ]) : node.key => node
  }

  identifier         = "${var.project}-${each.key}"
  cluster_identifier = aws_rds_cluster.postgres[each.value.cluster].id
  engine             = aws_rds_cluster.postgres[each.value.cluster].engine
  instance_class     = each.value.class
  availability_zone  = local.azs[each.value.index % length(local.azs)]
  promotion_tier     = each.value.index

  publicly_accessible             = false
  auto_minor_version_upgrade      = true
  performance_insights_enabled    = true
  performance_insights_kms_key_id = local.kms_key_arn
}

# Connections without TLS are refused.
resource "aws_rds_cluster_parameter_group" "aurora" {
  count = var.db_engine == "aurora" ? 1 : 0

  name   = "${var.project}-aurora-postgresql18"
  family = "aurora-postgresql18"

  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }
}

locals {
  # Where each instance (or cluster) answers, and its admin login — the same shape for both engines.
  db_address           = var.db_engine == "aurora" ? { for name, c in aws_rds_cluster.postgres : name => c.endpoint } : { for name, i in aws_db_instance.postgres : name => i.address }
  db_master_secret_arn = var.db_engine == "aurora" ? { for name, c in aws_rds_cluster.postgres : name => c.master_user_secret[0].secret_arn } : { for name, i in aws_db_instance.postgres : name => i.master_user_secret[0].secret_arn }
  db_master_username   = "modudrive"

  # The services with a database, and the instance each one's lives on.
  postgres_clients = flatten([for instance in var.db_instances : instance.clients])
  db_instance_of = merge([
    for name, instance in var.db_instances : { for client in instance.clients : client => name }
  ]...)
}
