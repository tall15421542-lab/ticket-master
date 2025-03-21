output "cluster_properties" {
  value = <<-EOT
bootstrap.servers=${confluent_kafka_cluster.basic.bootstrap_endpoint}
security.protocol=SASL_SSL
sasl.jaas.config=org.apache.kafka.common.security.plain.PlainLoginModule required username='${confluent_api_key.app-manager-kafka-api-key.id}' password='${confluent_api_key.app-manager-kafka-api-key.secret}';
sasl.mechanism=PLAIN
basic.auth.credentials.source=USER_INFO
basic.auth.user.info=${confluent_api_key.app-manager-schema-registry-api-key.id}:${confluent_api_key.app-manager-schema-registry-api-key.secret}
schema.registry.url=${data.confluent_schema_registry_cluster.info.rest_endpoint}
  EOT

  sensitive = true
}
