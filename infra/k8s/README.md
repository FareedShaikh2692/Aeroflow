# Kubernetes manifests

Stateless services only. PostgreSQL, Redis and Kafka are expected as managed services
(e.g. RDS/Cloud SQL, ElastiCache/Memorystore, MSK/Confluent) or installed via Helm (Bitnami charts).

```bash
kubectl apply -f 00-namespace-config.yaml   # edit the ConfigMap; create the Secret out-of-band
kubectl apply -f 10-services.yaml           # replace ghcr.io/OWNER with your registry
kubectl apply -f 20-ingress-hpa.yaml
kubectl -n aeroflow rollout status deploy/booking-service
```

Readiness/liveness use Spring Boot Actuator probes; pods run as non-root with a read-only root filesystem.
Booking-service has an HPA (CPU 70%) and a PodDisruptionBudget because it is the write-hot path.
