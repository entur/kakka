# Contains main description of bulk of terraform?
terraform {
  required_version = ">= 0.13.2"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 4.26"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.32.0"
    }
  }
}