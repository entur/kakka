variable "labels" {
  description = "Labels used in all resources"
  type = map(string)
  default = {
    manager = "terraform"
    team = "ror"
    slack = "talk-ror"
    app = "kakka"
  }
}

variable "cloudsql_project" {
  description = "GCP project of sql database"
}
variable "db_region" {
  description = "GCP  region"
  default = "europe-west1"
}
variable "db_zone" {
  description = "GCP zone"
  default = "europe-west1-b"
}

variable "db_tier" {
  description = "Database instance tier"
  default = "db-custom-1-3840"
}

variable "db_availability" {
  description = "Database availability"
  default = "ZONAL"
}
variable "ror-kakka-db-username" {
  description = "kakka database username"
}
variable ror-kakka-db-password {
  description = "Kakka database password"
}

variable "kube_namespace" {
  default = "kakka"
}
variable "ror-kakka-auth0-secret" {
  description = "Auth0 secret"
}

