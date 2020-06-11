resource "kubernetes_secret" "kakka_service_account_credentials" {
  metadata {
    name = "${var.labels.team}-${var.labels.app}-sa-key"
    namespace = var.kube_namespace
    labels = var.labels
  }
  data = {
    "credentials.json" = "${base64decode(google_service_account_key.kakka_service_account_key.private_key)}"
  }
}
resource "kubernetes_secret" "ror-kakka-secrets" {
  metadata {
    name = "${var.labels.team}-${var.labels.app}-secrets"
    namespace = var.kube_namespace
    labels = var.labels
  }
  data = {
    "ror-kakka-db-password" = var.ror-kakka-db-password
    "ror-kakka-kartverket-password" = var.ror-kakka-kartverket-password
    "ror-kakka-keycloak-secret" = var.ror-kakka-keycloak-secret
  }
}