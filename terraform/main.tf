# Contains main description of bulk of terraform?
terraform {
  required_version = ">= 0.12"
}

provider "google" {
  version = "~> 2.19"
}
provider "kubernetes" {
  load_config_file = var.load_config_file
}


# Create bucket
resource "google_storage_bucket" "storage_bucket" {
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

# Create database
resource "google_sql_database_instance" "db_instance" {
  name = "kakka-db"
  project = var.cloudsql_project
  region = "europe-west1"
  settings {
    disk_size = 10
    availability_type = var.db_availability_type
    tier = var.db_tier
    location_preference {
      zone = "b"
    }
    backup_configuration {
      enabled = var.db_backup_enabled
    }
    user_labels = var.labels
  }
  database_version = "POSTGRES_9_6"
}

resource "google_sql_database" "db" {
  name = "kakka"
  project = var.cloudsql_project
  instance = google_sql_database_instance.db_instance[0].name
}

resource "google_sql_user" "db-user" {
  name = "kakka"
  project = var.cloudsql_project
  instance = google_sql_database_instance.db_instance[0].name
  password = var.ror-kakka-db-password
}
# Create pubsub config

# Create pubsub topic GeoCoderQueue
resource "google_pubsub_topic" "geocoderqueue" {
  name = "GeoCoderQueue"
  project = var.pubsub_project
  labels = var.labels
}

# Create pubsub subscription GeoCoderQueue
resource "google_pubsub_subscription" "geocoderqueue-subscription" {
  name = google_pubsub_topic.geocoderqueue.name
  topic = google_pubsub_topic.geocoderqueue.name
  project = var.pubsub_project
  labels = var.labels
}

# Create pubsub topic TiamatExportQueue
resource "google_pubsub_topic" "tiamatexportqueue" {
  name = "TiamatExportQueue"
  project = var.pubsub_project
  labels = var.labels
}

# Create pubsub subscription TiamatExportQueue
resource "google_pubsub_subscription" "tiamatexportqueue-subscription" {
  name = google_pubsub_topic.tiamatexportqueue.name
  topic = google_pubsub_topic.tiamatexportqueue.name
  project = var.pubsub_project
  labels = var.labels
}


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

resource "google_pubsub_topic_iam_member" "kakka_pubsub_tiamatexportqueue_iam_member" {
  project = var.pubsub_project
  topic = google_pubsub_topic.tiamatexportqueue.name
  role = var.service_account_pubsub_role
  member = "serviceAccount:${google_service_account.kakka_service_account.email}"
}

resource "google_pubsub_topic_iam_member" "kakka_pubsub_geocoderqueue_iam_member" {
  project = var.pubsub_project
  topic = google_pubsub_topic.geocoderqueue.name
  role = var.service_account_pubsub_role
  member = "serviceAccount:${google_service_account.kakka_service_account.email}"
}

resource "google_storage_bucket_iam_member" "kakka_storage_iam_member" {
  bucket = google_storage_bucket.storage_bucket.name
  role = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.kakka_service_account.email}"
}

resource "google_service_account_key" "kakka_service_account_key" {
  service_account_id = google_service_account.kakka_service_account.name
}

resource "kubernetes_secret" "kakka_service_account_credentials" {
  metadata {
    name = "${var.labels.team}-${var.labels.app}-sa-key"
    namespace = var.kube_namespace
  }
  data = {
    "credentials.json" = "${base64decode(google_service_account_key.kakka_service_account_key.private_key)}"
  }
}
resource "kubernetes_secret" "ror-kakka-db-password" {
  metadata {
    name = "${var.labels.team}-${var.labels.app}-db-password"
    namespace = var.kube_namespace
  }
  data = {
    "password" = var.ror-kakka-db-password
  }
}
