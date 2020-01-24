resource "kubernetes_secret" "marduk-carbon-storage-key" {
    metadata {
    name      = "marduk-carbon-storage-key"
    namespace = var.kube_namespace
    }
    data = {
    "marduk-blobstore-credentials.json" = "${base64decode(google_service_account_key.marduk-carbon-storage-key.private_key)}"
    }
}

resource "kubernetes_secret" "marduk-pubsub-key" {
    metadata {
    name      = "marduk-pubsub-key"
    namespace = var.kube_namespace
    }
    data = {
    "marduk-pubsub-credentials.json" = "${base64decode(google_service_account_key.marduk-pubsub-key.private_key)}"
    }
}

resource "kubernetes_secret" "kakka-service-account" {
  metadata {
  name      = "kakka-service-account"
  namespace = var.kube_namespace
  }
  data = {
  "kakka-service-account.json" = "${base64decode(google_service_account_key.kakka-service-account.private_key)}"
  }
}

resource "kubernetes_secret" "kakka-kartverket-password" {
  metadata {
  name      = "kakka-kartverket-password"
  namespace = var.kube_namespace
  }

  data = {
  "client.password"     = var.kakka-kartverket-password
  }
}

resource "kubernetes_secret" "kakka-keycloak-secret" {
metadata {
name      = "kakka-keycloak-secret"
namespace = var.kube_namespace
  }

  data = {
  "password"     = var.kakka-keycloak-secret
  }
}

resource "kubernetes_secret" "kakka-db-password" {
  metadata {
  name      = "kakka-db-password"
  namespace = var.kube_namespace
  }
  data = {
  "password"     = var.kakka-db-password
  }
}

