  variable "gcp_project" {
    description = "The GCP project id"
  }

    variable "kube_context" {
    description = "The Kubernetes context id"
    default     = null
  }

    variable "kube_namespace" {
    description = "The Kubernetes namespace"
  }

  variable "region" {
      description = "GCP default region"
      default     = "europe-west1"
  }

  variable "zone" {
      description = "GCP default zone"
      default     = "europe-west1-d"
  }

  variable kakka-kartverket-password {
    description = "kakka-kartverket-password"
  }
