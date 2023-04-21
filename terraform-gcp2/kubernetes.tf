resource "kubernetes_secret" "ror-kakka-secrets" {
  metadata {
    name = "${var.labels.team}-${var.labels.app}-secrets"
    namespace = var.kube_namespace
    labels = var.labels
  }
  data = {
    "ror-kakka-db-password" = var.ror-kakka-db-password
    "ror-kakka-auth0-secret" = var.ror-kakka-auth0-secret
  }
}
