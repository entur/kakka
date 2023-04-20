# Contains main description of bulk of terraform?
terraform {
  required_version = ">= 0.13.2"
}
provider "google" {
  version = "~> 3.74.0"
}
provider "kubernetes" {
  version = "~> 2.20.0"
  load_config_file = var.load_config_file
}
