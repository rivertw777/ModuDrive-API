-- Every upload stores its blocks under a fresh s3Path, so it identifies one upload: a retried
-- upload-complete callback finds the version it already created instead of adding a second one.
alter table file_version add constraint uk_file_version_s3_path unique (s3_path);
