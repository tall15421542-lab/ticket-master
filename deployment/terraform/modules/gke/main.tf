resource "google_container_cluster" "default" {
  name     = "gke-autopilot-release-channel"
  location = "asia-east1"

  enable_autopilot = true

  release_channel {
    channel = "REGULAR"
  }

  deletion_protection = false
}
