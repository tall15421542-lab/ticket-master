resource "confluent_environment" "development" {
  display_name = "development"

  stream_governance {
    package = "ESSENTIALS"
  }
}

resource "confluent_kafka_cluster" "basic" {
  display_name = "ticket-master"
  availability = "SINGLE_ZONE"
  cloud        = "GCP"
  region       = "asia-east1"
  basic {}
  environment {
    id = confluent_environment.development.id
  }
}

resource "confluent_service_account" "app-manager" {
  display_name = "app-manager"
  description  = "Service account to manage 'ticket-master' Kafka cluster"
}

resource "confluent_role_binding" "app-manager-kafka-cluster-admin" {
  principal   = "User:${confluent_service_account.app-manager.id}"
  role_name   = "CloudClusterAdmin"
  crn_pattern = confluent_kafka_cluster.basic.rbac_crn
}

resource "confluent_api_key" "app-manager-kafka-api-key" {
  display_name = "app-manager-kafka-api-key"
  description  = "Kafka API Key that is owned by 'app-manager' service account"
  owner {
    id          = confluent_service_account.app-manager.id
    api_version = confluent_service_account.app-manager.api_version
    kind        = confluent_service_account.app-manager.kind
  }

  managed_resource {
    id          = confluent_kafka_cluster.basic.id
    api_version = confluent_kafka_cluster.basic.api_version
    kind        = confluent_kafka_cluster.basic.kind

    environment {
      id = confluent_environment.development.id
    }
  }

  depends_on = [
    confluent_role_binding.app-manager-kafka-cluster-admin
  ]
}

resource "confluent_kafka_topic" "create_event" {
  kafka_cluster {
    id = confluent_kafka_cluster.basic.id
  }
  topic_name       = "command.event.create_event"
  partitions_count = var.partitions_count
  rest_endpoint    = confluent_kafka_cluster.basic.rest_endpoint
  credentials {
    key    = confluent_api_key.app-manager-kafka-api-key.id
    secret = confluent_api_key.app-manager-kafka-api-key.secret
  }
}

resource "confluent_kafka_topic" "create_reservation" {
  kafka_cluster {
    id = confluent_kafka_cluster.basic.id
  }
  topic_name       = "command.reservation.create_reservation"
  partitions_count = var.partitions_count
  rest_endpoint    = confluent_kafka_cluster.basic.rest_endpoint
  credentials {
    key    = confluent_api_key.app-manager-kafka-api-key.id
    secret = confluent_api_key.app-manager-kafka-api-key.secret
  }
}

resource "confluent_kafka_topic" "reservation" {
  kafka_cluster {
    id = confluent_kafka_cluster.basic.id
  }
  topic_name       = "state.user.reservation"
  partitions_count = var.partitions_count
  rest_endpoint    = confluent_kafka_cluster.basic.rest_endpoint
  credentials {
    key    = confluent_api_key.app-manager-kafka-api-key.id
    secret = confluent_api_key.app-manager-kafka-api-key.secret
  }
}

resource "confluent_kafka_topic" "reserve_seat" {
  kafka_cluster {
    id = confluent_kafka_cluster.basic.id
  }
  topic_name       = "command.event.reserve_seat"
  partitions_count = var.partitions_count
  rest_endpoint    = confluent_kafka_cluster.basic.rest_endpoint
  credentials {
    key    = confluent_api_key.app-manager-kafka-api-key.id
    secret = confluent_api_key.app-manager-kafka-api-key.secret
  }
}

resource "confluent_kafka_topic" "area_status" {
  kafka_cluster {
    id = confluent_kafka_cluster.basic.id
  }
  topic_name       = "state.event.area_status"
  partitions_count = var.partitions_count
  rest_endpoint    = confluent_kafka_cluster.basic.rest_endpoint
  credentials {
    key    = confluent_api_key.app-manager-kafka-api-key.id
    secret = confluent_api_key.app-manager-kafka-api-key.secret
  }
}

resource "confluent_kafka_topic" "reservation_result" {
  kafka_cluster {
    id = confluent_kafka_cluster.basic.id
  }
  topic_name       = "response.reservation.result"
  partitions_count = var.partitions_count
  rest_endpoint    = confluent_kafka_cluster.basic.rest_endpoint
  credentials {
    key    = confluent_api_key.app-manager-kafka-api-key.id
    secret = confluent_api_key.app-manager-kafka-api-key.secret
  }
}

resource "confluent_role_binding" "app-manager-environment-admin" {
  principal   = "User:${confluent_service_account.app-manager.id}"
  role_name   = "EnvironmentAdmin"
  crn_pattern = confluent_environment.development.resource_name
}

resource "confluent_api_key" "app-manager-schema-registry-api-key" {
  display_name = "app-manager-schema-registry-api-key"
  description  = "Schema Registry API Key that is owned by 'app-manager' service account"
  owner {
    id          = confluent_service_account.app-manager.id
    api_version = confluent_service_account.app-manager.api_version
    kind        = confluent_service_account.app-manager.kind
  }

  managed_resource {
    id          = data.confluent_schema_registry_cluster.info.id
    api_version = data.confluent_schema_registry_cluster.info.api_version
    kind        = data.confluent_schema_registry_cluster.info.kind

    environment {
      id = confluent_environment.development.id
    }
  }

  depends_on = [
    confluent_role_binding.app-manager-environment-admin
  ]
}

data "confluent_schema_registry_cluster" "info" {
  environment {
    id = confluent_environment.development.id
  }
}
