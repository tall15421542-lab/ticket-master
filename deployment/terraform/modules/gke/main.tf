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

  deletion_protection = false
}

data "google_container_cluster" "default" {
  name = google_container_cluster.default.name
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


