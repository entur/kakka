resource "google_storage_bucket_iam_member" "kingu_storage_iam_member" {
  bucket = var.kingu_storage_bucket
  role = var.service_account_bucket_role
  member = var.service_account
}