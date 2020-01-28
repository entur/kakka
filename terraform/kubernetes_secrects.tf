resource "kubernetes_secret" "kakka-kartverket-password" {
  metadata {
  name      = "kakka-kartverket-password"
  namespace = var.kube_namespace
  }

  data = {
  "client.password"     = var.kakka-kartverket-password
  }
}
