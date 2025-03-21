output "cluster_properties" {
  value = module.confluent_kafka.cluster_properties

  sensitive = true
}
