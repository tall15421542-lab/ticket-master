PARTITIONS_COUNT ?= 1
PERF_TYPE ?= 1-instance-perf
deploy:
	$(info Remember to export the following environment variables)
	$(info export TF_VAR_confluent_cloud_api_key="{Confluent_Cloud_API_Key}")
	$(info export TF_VAR_confluent_cloud_api_secret="{Confluent_Cloud_API_Secret}")
	terraform -chdir=deployment/terraform plan -var "partitions_count=${PARTITIONS_COUNT}"
	terraform -chdir=deployment/terraform apply -auto-approve -var "partitions_count=${PARTITIONS_COUNT}" 
	terraform -chdir=deployment/terraform output -raw cluster_properties > deployment/k8s-configs/base/appConfig/client.properties
	kubectl apply -k deployment/k8s-configs/overlays/"${PERF_TYPE}"
destroy:
	kubectl delete -k deployment/k8s-configs/overlays/"${PERF_TYPE}"
	terraform -chdir=deployment/terraform destroy -target=module.confluent_kafka -auto-approve 
