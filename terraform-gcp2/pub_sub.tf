## External topics
# nabu topic{{ .Values.configMap.nabuPubsubProjectId }}:JobEventQueue
# Add publisher role jobEventQueue

resource "google_pubsub_topic_iam_member" "nabu_job_event_topic_iam_member" {
  project = var.nabu_job_event_pubsub_project
  member = var.service_account
  role   = var.nabu_job_event_pusub_role
  topic  = var.nabu_job_event_pubsub_topic
}


resource "google_pubsub_topic_iam_member" "kingu_netex_export_topic_iam_member" {
  project = var.kingu_pub_sub_project
  member = var.service_account
  role   = var.kingu_netex_export_pusub_role
  topic  = var.kingu_netex_export_topic_name
}

resource "google_pubsub_subscription" "kingu_netex_export_subscription" {
  name =var.kingu_netex_export_subscription
  topic ="projects/${var.kingu_pub_sub_project}/topics/${var.kingu_netex_export_topic_name}"
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


