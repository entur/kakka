# Contains main description of bulk of terraform?
terraform {
  required_version = ">= 0.13.2"
}
provider "google" {
  version = "~> 4.62.0"
}
provider "kubernetes" {
  version = "~> 1.13.4"
  load_config_file = var.load_config_file
}
