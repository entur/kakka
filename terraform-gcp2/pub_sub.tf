## External topics
# nabu topic{{ .Values.configMap.nabuPubsubProjectId }}:JobEventQueue

data "google_pubsub_topic" "kingu_netex_export_topic" {
  name = var.kingu_netex_export_topic
  project =var.kingu_pubsub_project
}

resource "google_pubsub_subscription" "kingu_netex_export_subscription" {
  name =var.kingu_netex_export_subscription
  topic =data.google_pubsub_topic.kingu_netex_export_topic.name
  filter = "attributes.EnturNetexExportStatus = \"Completed\""
  project = var.pubsub_project
  labels = var.labels
  ack_deadline_seconds = 10
  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "600s"
  }
  # 10h retention
  message_retention_duration = "36000s"

}

### Internal topic/subscriptions

resource "google_pubsub_topic" "kakka_geocoder_topic" {
  name = var.kakka_geocoder_topic
  project = var.pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "kakka_geocoder_subscription" {
  name =var.kakka_geocoder_subscription
  topic = google_pubsub_topic.kakka_geocoder_topic.name
  project = var.pubsub_project
  labels = var.labels
  ack_deadline_seconds = 10
  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "600s"
  }
  message_retention_duration = "600s"
}

resource "google_pubsub_topic" "kakka_tariff_zone_topic" {
  name = var.kakka_tariff_zone_topic
  project = var.pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "kakka_tariff_zone_subscription" {
  name =var.kakka_tariff_zone_subscription
  topic = google_pubsub_topic.kakka_tariff_zone_topic.name
  project = var.pubsub_project
  labels = var.labels
  ack_deadline_seconds = 10
  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "600s"
  }
  message_retention_duration = "600s"
}

resource "google_pubsub_topic" "kakka_geocoder_smoke_test_topic" {
  name = var.kakka_geocoder_smoke_test_topic
  project = var.pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "kakka_geocoder_smoke_test_subscription" {
  name =var.kakka_geocoder_smoke_test_subscription
  topic = google_pubsub_topic.kakka_geocoder_smoke_test_topic.name
  project = var.pubsub_project
  labels = var.labels
  ack_deadline_seconds = 10
  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "600s"
  }
  message_retention_duration = "600s"
}

resource "google_pubsub_topic" "kakka_es_build_job_topic" {
  name = var.kakka_es_build_job_topic
  project = var.pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "kakka_es_build_job_subscription" {
  name =var.kakka_es_build_job_subscription
  topic = google_pubsub_topic.kakka_es_build_job_topic.name
  project = var.pubsub_project
  labels = var.labels
  ack_deadline_seconds = 10
  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "600s"
  }
  message_retention_duration = "600s"
}


