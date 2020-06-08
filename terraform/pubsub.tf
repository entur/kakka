# Create pubsub config

module "pubsub_geocoder_queue" {
  source  = "terraform-google-modules/pubsub/google"
  version = "1.3.0"
  # insert the 3 required variables here
  topic              = "GeoCoderQueue"
  project_id         = var.cloudsql_project
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
  # insert the 3 required variables here
  topic              = "TiamatExportQueue"
  project_id         = var.cloudsql_project
  pull_subscriptions = [
    {
      name = "TiamatExportQueue"
    }
  ]
  topic_labels = var.labels
}