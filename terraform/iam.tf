# Create Service account and secretes
resource "google_service_account" "kakka_service_account" {
  account_id = "${var.labels.team}-${var.labels.app}-sa"
  display_name = "${var.labels.team}-${var.labels.app} service account"
  project = var.gcp_project
}

resource "google_project_iam_member" "kakka_cloudsql_iam_member" {
  project = var.cloudsql_project
  role = var.service_account_cloudsql_role
  member = "serviceAccount:${google_service_account.kakka_service_account.email}"
}

resource "google_project_iam_member" "kakka_pubsub_iam_member" {
  project = var.pubsub_project
  role = var.service_account_pubsub_role
  member = "serviceAccount:${google_service_account.kakka_service_account.email}"
}

resource "google_storage_bucket_iam_member" "kakka_storage_iam_member" {
  bucket = var.kakka_storage_bucket
  role = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.kakka_service_account.email}"
}

resource "google_storage_bucket_iam_member" "kingu_storage_iam_member" {
  bucket = var.kingu_storage_bucket
  role = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.kakka_service_account.email}"
}
resource "google_storage_bucket_iam_member" "kakka_target_storage_iam_member" {
  bucket = var.kakka_target_storage_bucket
  role = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.kakka_service_account.email}"
}

resource "google_service_account_key" "kakka_service_account_key" {
  service_account_id = google_service_account.kakka_service_account.name
}
