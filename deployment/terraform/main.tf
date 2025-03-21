terraform {
  required_providers {
    confluent = {
      source  = "confluentinc/confluent"
      version = "2.22.0"
    }
  }
}

provider "confluent" {
  cloud_api_key    = var.confluent_cloud_api_key
  cloud_api_secret = var.confluent_cloud_api_secret
}

module "confluent_kafka" {
  source = "./modules/kafka"

  partitions_count = var.partitions_count

  providers = {
    confluent = confluent
  }
}

// Authenticated by credential file created by using the gcloud auth application-default login
provider "google" {
  project     = "ticket-master-tall15421542"
  region      = "asia-east1"
}

module "kubernetes" {
    source = "./modules/gke"
}

