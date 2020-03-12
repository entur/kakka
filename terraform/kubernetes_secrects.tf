resource "kubernetes_secret" "kakka-kartverket-password" {
  metadata {
  name      = "kakka-kartverket-password"
  namespace = var.kube_namespace
  }

  data = {
  "client.password"     = var.kakka-kartverket-password
  }
}

locals {
  sa_name = "${var.labels.app}-${var.bucket_instance_suffix}"
}

resource "null_resource" "is_environment_valid" {
     count = length(local.sa_name) < 31 ? 1 : "SA name cannot be longer than 30, consider shortening your suffix?"
}

  # Create bucket
resource "google_storage_bucket" "storage_bucket" {
  name               = "${var.labels.team}-${var.labels.app}-${var.bucket_instance_suffix}"
  force_destroy      = var.force_destroy
  location           = var.location
  project            = var.gcp_project
  storage_class      = var.storage_class
  bucket_policy_only = var.bucket_policy_only
  labels             = var.labels

  versioning {
  enabled = var.versioning
  }
  logging {
    log_bucket        = var.log_bucket
    log_object_prefix = "${var.labels.team}-${var.labels.app}-${var.bucket_instance_suffix}"
  }
}

resource "random_id" "protector" {
  count       = var.prevent_destroy ? 1 : 0
  byte_length = 8
  keepers = {
  protector = google_storage_bucket.storage_bucket.id
  }
  lifecycle {
  prevent_destroy = true
  }
}

# Create Service account
resource "google_service_account" "storage_bucket_service_account" {
  # this can be maximum 30 chars long - shorten namespace staging => sta and do not list teamname
  account_id   = local.sa_name
  display_name = "Service Account for ${var.labels.team}: ${var.labels.app} app bucket"
  project      = var.gcp_project
}

# Create key for service account
resource "google_service_account_key" "storage_bucket_service_account_key" {
  service_account_id = google_service_account.storage_bucket_service_account.name
}

# Add SA key to kubernetes
resource "kubernetes_secret" "storage_bucket_service_account_credentials" {
  depends_on = [
  google_storage_bucket.storage_bucket
  ]
  metadata {
  name      = "${var.labels.team}-${var.labels.app}-${var.bucket_instance_suffix}-credentials"
  namespace = var.kube_namespace
  }
  data = {
  "credentials.json" = "${base64decode(google_service_account_key.storage_bucket_service_account_key.private_key)}"
  }
}

# add service account as member to the bucket
resource "google_storage_bucket_iam_member" "storage_bucket_iam_member" {
  bucket = google_storage_bucket.storage_bucket.name
  role   = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.storage_bucket_service_account.email}"
}