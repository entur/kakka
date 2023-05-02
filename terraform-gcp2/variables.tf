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

variable "kingu_netex_export_topic" {
  description = "kingu netex export topic name"
}

variable "pubsub_project" {
  description = "app pubsub project name"
}

variable "kingu_netex_export_subscription" {
  default = "ror.kakka.inbound.subscription.kingu.netex.export"
}

variable "kakka_tariff_zone_topic" {
  default = "ror.kakka.outbound.topic.tariff.zone.file.queue"
}
variable "kakka_tariff_zone_subscription" {
  default = "ror.kakka.inbound.subscription.tariff.zone.file.queue"
}

variable "kakka_geocoder_smoke_test_topic" {
  default = "ror.kakka.outbound.topic.geocoder.smoke.test"
}
variable "kakka_geocoder_smoke_test_subscription" {
  default = "ror.kakka.inbound.subscription.geocoder.smoke.test"
}

variable "kakka_es_build_job_topic" {
  default = "ror.kakka.outbound.topic.es.build.job"
}
variable "kakka_es_build_job_subscription" {
  default = "ror.kakka.inbound.subscription.es.build.job"
}

variable "kakka_geocoder_subscription" {
  default = "ror.kakka.inbound.subscription.geocoder"
}
variable "kakka_geocoder_topic" {
  default = "ror.kakka.outbound.topic.geocoder"
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

