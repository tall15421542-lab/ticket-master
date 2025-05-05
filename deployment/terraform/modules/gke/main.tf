resource "google_container_cluster" "default" {
  name     = "gke-autopilot-release-channel"
  location = "asia-east1"

  enable_autopilot = true

  release_channel {
    channel = "REGULAR"
  }

  gateway_api_config {
    channel = "CHANNEL_STANDARD"
  }

  private_cluster_config {
    enable_private_nodes = true
    enable_private_endpoint = false
  }

  deletion_protection = false
}

data "google_container_cluster" "default" {
  name = google_container_cluster.default.name
}

resource "google_compute_router" "router" {
  project = "ticket-master-tall15421542"
  name    = "nat-router"
  network = "default"
  region  = "asia-east1"
}

module "cloud-nat" {
  source                             = "terraform-google-modules/cloud-nat/google"
  version                            = "~> 5.0"
  project_id                         = "ticket-master-tall15421542"
  region                             = "asia-east1"
  router                             = google_compute_router.router.name
  name                               = "nat-config"
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"
}

resource "google_compute_subnetwork" "network-for-l7lb" {
  provider = google-beta

  name          = "l7lb-subnetwork"
  ip_cidr_range = "10.0.0.0/22"
  region        = data.google_container_cluster.default.location
  purpose       = "REGIONAL_MANAGED_PROXY"
  role          = "ACTIVE"
  network       = data.google_container_cluster.default.network
}

data "google_project" "default" {}

resource "google_project_iam_member" "log_writer" {
  project = "ticket-master-tall15421542"
  role    = "roles/logging.logWriter"
  member  = "principal://iam.googleapis.com/projects/${data.google_project.default.number}/locations/global/workloadIdentityPools/${data.google_project.default.project_id}.svc.id.goog/subject/ns/opentelemetry/sa/opentelemetry-collector"
}

resource "google_project_iam_member" "metric_writer" {
  project = "ticket-master-tall15421542"
  role    = "roles/monitoring.metricWriter"
  member  = "principal://iam.googleapis.com/projects/${data.google_project.default.number}/locations/global/workloadIdentityPools/${data.google_project.default.project_id}.svc.id.goog/subject/ns/opentelemetry/sa/opentelemetry-collector"
}

resource "google_project_iam_member" "trace_agent" {
  project = "ticket-master-tall15421542"
  role    = "roles/cloudtrace.agent"
  member  = "principal://iam.googleapis.com/projects/${data.google_project.default.number}/locations/global/workloadIdentityPools/${data.google_project.default.project_id}.svc.id.goog/subject/ns/opentelemetry/sa/opentelemetry-collector"
}