#Enviroment variables
variable "gcp_project" {
  description = "The GCP project id"
}

variable "cloudsql_project" {
  description = "GCP project of sql database"
}

variable "pubsub_project" {
  description = "GCP project of pubsub topic"
}


variable "kube_namespace" {
  description = "The Kubernetes namespace"
}

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

variable "service_account_bucket_role" {
  description = "Role of the Service Account - more about roles https://cloud.google.com/storage/docs/access-control/iam-roles"
  default = "roles/storage.objectViewer"
}

variable "service_account_cloudsql_role" {
  description = "Role of the Service Account - more about roles https://cloud.google.com/pubsub/docs/access-control"
  default = "roles/cloudsql.client"
}

variable "service_account_pubsub_role" {
  description = "Role of the Service Account - more about roles https://cloud.google.com/pubsub/docs/access-control"
  default = "roles/pubsub.editor"
}

variable "load_config_file" {
  description = "Do not load kube config file"
  default = false
}

variable ror-kakka-db-password {
  description = "Kakka database password"
}

variable "ror-kakka-kartverket-password" {
  description = "Kakka kartverket password"
}

variable "ror-kakka-keycloak-secret" {
  description = "Kakka keycloak secret"
}

variable "ror-kakka-auth0-secret" {
  description = "Auth0 secret"
}
variable "kakka_storage_bucket" {
  description = "kakka storage bucket"
}

variable "kakka_target_storage_bucket" {
  description = "kakka target storage bucket"
}