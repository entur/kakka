# Contains main description of bulk of terraform?
terraform {
  required_version = ">= 0.12"
}

provider "google" {
  version = "~> 2.19"
  project = var.gcp_project
}
provider "kubernetes" {
  load_config_file = var.load_config_file
}
