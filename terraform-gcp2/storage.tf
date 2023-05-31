resource "google_storage_bucket_iam_member" "kingu_storage_iam_member" {
  bucket = var.kingu_storage_bucket
  role = var.service_account_bucket_role
  member = var.service_account
}

# Create bucket

resource "google_storage_bucket" "storage_bucket" {
  name               = "${var.bucket_instance_prefix}-${var.bucket_instance_suffix}"
  force_destroy      = var.force_destroy
  location           = var.location
  project            = var.storage_project
  storage_class      = var.storage_class
  labels             = var.labels
  uniform_bucket_level_access = true
  versioning {
    enabled = var.versioning
  }
  logging {
    log_bucket        = var.log_bucket
    log_object_prefix = "${var.bucket_instance_prefix}-${var.bucket_instance_suffix}"
  }
}
# Create folder in a bucket

resource "google_storage_bucket_object" "content_folder" {
  count = length(var.folder_names)
  name          = "${var.folder_names[count.index]}/"
  content       = "Not really a directory, but it's empty."
  bucket        = google_storage_bucket.storage_bucket.name
}