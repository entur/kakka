# Contains main description of bulk of terraform?
terraform {
  required_version = ">= 0.13.2"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.49.0"
    }
  }
}
