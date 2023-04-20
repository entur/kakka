# Contains main description of bulk of terraform?
terraform {
  required_version = ">= 0.13.2"
}

provider "google" {
  version = "~> 4.26"
}
provider "kubernetes" {
  version = "~> 2.20.0"
}

resource "google_sql_database_instance" "db_instance" {
  name = "kakka-db"
  database_version = "POSTGRES_13"
  project = var.cloudsql_project
  region = var.db_region

  settings {
    location_preference {
      zone = var.db_zone
    }
    tier = var.db_tier
    user_labels = var.labels
    availability_type = var.db_availability
    backup_configuration {
      enabled = true
      // 01:00 UTC
      start_time = "01:00"
    }
    maintenance_window {
      // Sunday
      day = 7
      // 02:00 UTC
      hour = 2
    }
    ip_configuration {
      require_ssl = true
    }
  }
}

resource "google_sql_database" "db" {
  name = "kakka"
  project = var.cloudsql_project
  instance = google_sql_database_instance.db_instance.name
}

resource "google_sql_user" "db-user" {
  name = var.ror-kakka-db-username
  project = var.cloudsql_project
  instance = google_sql_database_instance.db_instance.name
  password = var.ror-kakka-db-password
}

resource "kubernetes_secret" "ror-kakka-secrets" {
  metadata {
    name = "${var.labels.team}-${var.labels.app}-secrets"
    namespace = var.kube_namespace
    labels = var.labels
  }
  data = {
    "ror-kakka-db-password" = var.ror-kakka-db-password
    "ror-kakka-auth0-secret" = var.ror-kakka-auth0-secret
  }
}