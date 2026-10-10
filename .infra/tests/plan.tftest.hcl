# Plans the whole stack against mocked providers — no AWS account, no credentials. Catches what
# validate can't: for_each over the real tfvars, cross-file references, preconditions.
#   terraform init -backend=false
#   for env in demo prod; do terraform test -var-file=envs/$env.tfvars; done
# Mocked values are only what some expression parses (ARNs split on ":", lists sliced).
mock_provider "aws" {
  mock_data "aws_availability_zones" {
    defaults = { names = ["ap-northeast-2a", "ap-northeast-2b", "ap-northeast-2c"] }
  }
  mock_data "aws_caller_identity" {
    defaults = { account_id = "111111111111" }
  }
  mock_data "aws_iam_policy_document" {
    defaults = { json = "{}" }
  }
  mock_resource "aws_prometheus_workspace" {
    defaults = { arn = "arn:aws:aps:ap-northeast-2:111111111111:workspace/ws-1", prometheus_endpoint = "https://aps/ws-1/" }
  }
  mock_resource "aws_kms_key" {
    defaults = { arn = "arn:aws:kms:ap-northeast-2:111111111111:key/k" }
  }
  mock_resource "aws_rds_cluster" {
    defaults = { master_user_secret = [{ secret_arn = "arn:aws:secretsmanager:ap-northeast-2:111111111111:secret:s" }] }
  }
  mock_resource "aws_db_instance" {
    defaults = { master_user_secret = [{ secret_arn = "arn:aws:secretsmanager:ap-northeast-2:111111111111:secret:s" }] }
  }
  mock_resource "aws_memorydb_cluster" {
    defaults = { cluster_endpoint = [{ address = "m.example", port = 6379 }] }
  }
}
mock_provider "random" {}
mock_provider "archive" {}

run "plan" {
  command = plan
  variables {
    image_tag = "test"
  }
}
