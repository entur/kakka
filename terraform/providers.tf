# Contains main description of bulk of terraform?
terraform {
  required_version = ">= 1.5.0"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 8.12.0"
    }
  }
}
