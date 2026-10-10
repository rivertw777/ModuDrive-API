# PostgreSQL instances — var.db_instances says how many and which services' databases each holds.
# demo runs one for all four, prod one per service, so file-service's load (uploads, outbox) never
# slows the login path and each can be sized, failed over and upgraded on its own.
#
# Either way every service has its own database and login that can reach only that database (2-4).
# Terraform makes the instances; the databases and logins are made inside Postgres by
# .docker/postgres/postgres_init.sh, run once per instance as an ECS task (db_init_run_tasks output).
# Tables come from each service's Flyway migrations on startup.

# The stack's first, shared instance is the one that holds file_db: kept, not destroyed and made
# again. In prod the other services' databases then move to their own instances (a data migration).
moved {
  from = aws_db_instance.postgres
  to   = aws_db_instance.postgres["file"]
}

resource "aws_db_instance" "postgres" {
  for_each = var.db_instances

  # "file" keeps the stack's first identifier — changing it would replace the instance.
  identifier     = each.key == "file" ? var.project : "${var.project}-${each.key}"
  engine         = "postgres"
  engine_version = "18"
  instance_class = each.value.instance_class

  allocated_storage     = 20
  max_allocated_storage = 200
  storage_type          = "gp3"
  storage_encrypted     = true

  # postgres_init.sh connects as this admin. RDS keeps its password in Secrets Manager and rotates it.
  username                    = "modudrive"
  manage_master_user_password = true

  db_subnet_group_name   = module.vpc.database_subnet_group_name
  vpc_security_group_ids = [aws_security_group.postgres[each.key].id]
  publicly_accessible    = false

  # A standby in the other AZ; Aurora is the next step past that (2-4).
  multi_az = each.value.multi_az

  backup_retention_period    = 7
  auto_minor_version_upgrade = true
  deletion_protection        = var.deletion_protection
  skip_final_snapshot        = !var.deletion_protection
  final_snapshot_identifier  = var.deletion_protection ? "${var.project}-${each.key}-final" : null
}

locals {
  # The services with a database, and the instance each one's lives on.
  postgres_clients = flatten([for instance in var.db_instances : instance.clients])
  db_instance_of = merge([
    for name, instance in var.db_instances : { for client in instance.clients : client => name }
  ]...)
}
