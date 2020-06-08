# Create bucket
resource "google_storage_bucket" "kakka_storage_bucket" {
  name = "${var.labels.team}-${var.labels.app}-${var.bucket_instance_suffix}"
  force_destroy = var.force_destroy
  location = var.location
  project = var.storage_project
  storage_class = var.storage_class
  bucket_policy_only = var.bucket_policy_only
  labels = var.labels

  versioning {
    enabled = var.versioning
  }
  logging {
    log_bucket = var.log_bucket
    log_object_prefix = "${var.labels.team}-${var.labels.app}-${var.bucket_instance_suffix}"
  }
}
# Create folder in a bucket
#"es-data","geocoder","geojson","kartverket","osm","tiamat"
resource "google_storage_bucket_object" "es-data" {
  name          = "es-data/"
  content       = "Not really a directory, but it's empty."
  bucket        = google_storage_bucket.kakka_storage_bucket.name
}

resource "google_storage_bucket_object" "geocoder" {
  name          = "geocoder/"
  content       = "Not really a directory, but it's empty."
  bucket        = google_storage_bucket.kakka_storage_bucket.name
}

resource "google_storage_bucket_object" "geojson" {
  name          = "geojson/"
  content       = "Not really a directory, but it's empty."
  bucket        = google_storage_bucket.kakka_storage_bucket.name
}

resource "google_storage_bucket_object" "kartverket" {
  name          = "kartverket/"
  content       = "Not really a directory, but it's empty."
  bucket        = google_storage_bucket.kakka_storage_bucket.name
}
resource "google_storage_bucket_object" "osm" {
  name          = "osm/"
  content       = "Not really a directory, but it's empty."
  bucket        = google_storage_bucket.kakka_storage_bucket.name
}
resource "google_storage_bucket_object" "tiamat" {
  name          = "tiamat/"
  content       = "Not really a directory, but it's empty."
  bucket        = google_storage_bucket.kakka_storage_bucket.name
}