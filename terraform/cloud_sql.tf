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
      zone = "europe-west1-b"
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
  instance = google_sql_database_instance.db_instance.name
}

resource "google_sql_user" "db-user" {
  name = "kakka"
  project = var.cloudsql_project
  instance = google_sql_database_instance.db_instance.name
  password = var.ror-kakka-db-password
}