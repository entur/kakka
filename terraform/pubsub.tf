# Create pubsub config

module "pubsub_geocoder_queue" {
  source  = "terraform-google-modules/pubsub/google"
  version = "1.3.0"
  topic              = "GeoCoderQueue"
  project_id         = var.pubsub_project
  pull_subscriptions = [
    {
      name = "GeoCoderQueue"
    }
  ]
  topic_labels = var.labels
}

module "pubsub_tiamatexport_queue" {
  source  = "terraform-google-modules/pubsub/google"
  version = "1.3.0"
  topic              = "TiamatExportQueue"
  project_id         = var.pubsub_project
  pull_subscriptions = [
    {
      name = "TiamatExportQueue"
    }
  ]
  topic_labels = var.labels
}

module "pubsub_osm_queue" {
  source  = "terraform-google-modules/pubsub/google"
  version = "1.3.0"
  topic              = "GeoCoderOsmUpdateNotificationQueue"
  project_id         = var.pubsub_project
  pull_subscriptions = [
    {
      name = "GeoCoderOsmUpdateNotificationQueue"
    }
  ]
  topic_labels = var.labels
}