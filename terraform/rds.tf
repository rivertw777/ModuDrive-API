# One instance, four databases — one per service, each with its own login that can reach only its own
# database (2-4). Terraform makes the instance; the databases and logins are made inside Postgres by
# .docker/postgres/postgres_init.sh, run once as an ECS task after ECS exists (step 2). Tables come
# from each service's Flyway migrations on startup.
resource "aws_db_instance" "postgres" {
  identifier     = var.project
  engine         = "postgres"
  engine_version = "18"
  instance_class = var.db_instance_class

  allocated_storage     = 20
  max_allocated_storage = 200
  storage_type          = "gp3"
  storage_encrypted     = true

  # postgres_init.sh connects as this admin. RDS keeps its password in Secrets Manager and rotates it.
  username                    = "modudrive"
  manage_master_user_password = true

  db_subnet_group_name   = module.vpc.database_subnet_group_name
  vpc_security_group_ids = [aws_security_group.postgres.id]
  publicly_accessible    = false

  # ponytail: single-AZ to start (2-4). multi_az = true, then Aurora, as traffic grows.
  multi_az = false

  backup_retention_period    = 7
  auto_minor_version_upgrade = true
  deletion_protection        = true
  skip_final_snapshot        = false
  final_snapshot_identifier  = "${var.project}-final"
}
