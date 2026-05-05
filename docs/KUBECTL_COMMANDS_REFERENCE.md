# Kubectl Commands Reference — Cyberlearnix Server

> **Server:** `145.223.22.177` | **Port:** `9022` | **Main Namespace:** `cyberlearnix`
>
> **SSH Base Command:**
> ```bash
> ssh -i "C:\Users\aasta\.ssh\id_ed25519" root@145.223.22.177 -p 9022 "<kubectl command here>"
> ```

---

## Table of Contents

1. [Pods](#1-pods)
2. [Namespaces](#2-namespaces)
3. [Deployments](#3-deployments)
4. [Services](#4-services)
5. [ConfigMaps & Secrets](#5-configmaps--secrets)
6. [StatefulSets & ReplicaSets](#6-statefulsets--replicasets)
7. [Ingress](#7-ingress)
8. [Nodes](#8-nodes)
9. [Persistent Volumes](#9-persistent-volumes)
10. [Events](#10-events)
11. [ArgoCD](#11-argocd)
12. [Resource Usage (Metrics)](#12-resource-usage-metrics)
13. [All Resources at Once](#13-all-resources-at-once)
14. [Logs](#14-logs)
15. [Describe (Detailed Inspection)](#15-describe-detailed-inspection)
16. [Output Format Tips](#16-output-format-tips)
17. [Cyberlearnix Service Quick Reference](#17-cyberlearnix-service-quick-reference)

---

## 1. Pods

```bash
# All pods in all namespaces
kubectl get pods -A

# Pods in cyberlearnix namespace
kubectl get pods -n cyberlearnix

# With IP, node, and age info
kubectl get pods -n cyberlearnix -o wide

# Watch live (auto-refreshes)
kubectl get pods -n cyberlearnix -w

# Show labels
kubectl get pods -n cyberlearnix --show-labels

# Filter by label
kubectl get pods -n cyberlearnix -l app=admin-service

# Sort by restart count
kubectl get pods -n cyberlearnix --sort-by='.status.containerStatuses[0].restartCount'

# Sort by creation time (oldest first)
kubectl get pods -n cyberlearnix --sort-by=.metadata.creationTimestamp

# Get pod as JSON
kubectl get pod admin-service-787f4bb7dc-c9gtb -n cyberlearnix -o json

# Get pod as YAML
kubectl get pod admin-service-787f4bb7dc-c9gtb -n cyberlearnix -o yaml

# Get just pod names
kubectl get pods -n cyberlearnix -o name
```

---

## 2. Namespaces

```bash
# List all namespaces
kubectl get namespaces
kubectl get ns

# Namespaces on this server:
#   cyberlearnix   — application services
#   argocd         — ArgoCD GitOps
#   cert-manager   — TLS certificate management
#   ingress-nginx  — Nginx ingress controller
#   kube-system    — Kubernetes core components

# Get namespace details
kubectl get ns cyberlearnix -o yaml

# Describe a namespace
kubectl describe ns cyberlearnix
```

---

## 3. Deployments

```bash
# All deployments in cyberlearnix
kubectl get deployments -n cyberlearnix
kubectl get deploy -n cyberlearnix

# All namespaces
kubectl get deploy -A

# With replica status and image
kubectl get deploy -n cyberlearnix -o wide

# Specific deployment as YAML
kubectl get deploy admin-service -n cyberlearnix -o yaml

# Check rollout status
kubectl rollout status deployment/admin-service -n cyberlearnix

# View rollout history
kubectl rollout history deployment/admin-service -n cyberlearnix

# Active deployments on this server:
#   admin-service
#   cms-service
#   course-service
#   enrollment-service
#   form-service
#   gateway-service
#   instructor-service
#   notification-service
#   shop-service
#   user-service
#   redis
```

---

## 4. Services

```bash
# All services in cyberlearnix
kubectl get services -n cyberlearnix
kubectl get svc -n cyberlearnix

# All namespaces
kubectl get svc -A

# With cluster IP and ports
kubectl get svc -n cyberlearnix -o wide

# Specific service details
kubectl get svc gateway-service -n cyberlearnix -o yaml

# Describe a service (endpoints, selectors)
kubectl describe svc gateway-service -n cyberlearnix
```

---

## 5. ConfigMaps & Secrets

```bash
# List ConfigMaps
kubectl get configmaps -n cyberlearnix
kubectl get cm -n cyberlearnix

# Get specific configmap content
kubectl get cm <name> -n cyberlearnix -o yaml

# List all namespaces
kubectl get cm -A

# List Secrets (names only — values are base64 encoded)
kubectl get secrets -n cyberlearnix

# View a specific secret (base64 encoded)
kubectl get secret <name> -n cyberlearnix -o yaml

# Decode a secret value
kubectl get secret <name> -n cyberlearnix -o jsonpath='{.data.<key>}' | base64 --decode

# List secrets in all namespaces
kubectl get secrets -A
```

---

## 6. StatefulSets & ReplicaSets

```bash
# StatefulSets (postgres runs as StatefulSet)
kubectl get statefulsets -n cyberlearnix
kubectl get sts -n cyberlearnix

# Specific statefulset
kubectl get sts postgres -n cyberlearnix -o yaml

# ReplicaSets
kubectl get replicasets -n cyberlearnix
kubectl get rs -n cyberlearnix

# All namespaces
kubectl get sts -A
kubectl get rs -A
```

---

## 7. Ingress

```bash
# All ingress rules
kubectl get ingress -A
kubectl get ing -A

# Ingress in cyberlearnix
kubectl get ingress -n cyberlearnix -o wide

# Detailed ingress rules
kubectl get ingress -n cyberlearnix -o yaml

# Describe ingress
kubectl describe ingress -n cyberlearnix

# Ingress controller pod (nginx)
kubectl get pods -n ingress-nginx -o wide
```

---

## 8. Nodes

```bash
# All nodes
kubectl get nodes

# With IP, OS, kernel info
kubectl get nodes -o wide

# Node resource capacity
kubectl get nodes -o yaml

# Describe node (taints, conditions, allocatable resources)
kubectl describe node <node-name>

# Node resource usage (requires metrics-server — already installed)
kubectl top nodes
```

---

## 9. Persistent Volumes

```bash
# Persistent Volume Claims (namespace-scoped)
kubectl get pvc -n cyberlearnix

# Persistent Volumes (cluster-wide)
kubectl get pv

# Storage classes available
kubectl get storageclass
kubectl get sc

# PVC details
kubectl get pvc -n cyberlearnix -o yaml

# Describe a PVC
kubectl describe pvc <name> -n cyberlearnix
```

---

## 10. Events

```bash
# Events in cyberlearnix namespace
kubectl get events -n cyberlearnix

# Sort by most recent
kubectl get events -n cyberlearnix --sort-by=.lastTimestamp

# Only Warning events
kubectl get events -n cyberlearnix --field-selector type=Warning

# Only Normal events
kubectl get events -n cyberlearnix --field-selector type=Normal

# Events for all namespaces
kubectl get events -A --sort-by=.lastTimestamp

# Events for a specific pod
kubectl get events -n cyberlearnix --field-selector involvedObject.name=admin-service-787f4bb7dc-c9gtb
```

---

## 11. ArgoCD

```bash
# List ArgoCD applications
kubectl get applications -n argocd

# Get application details
kubectl get application -n argocd -o yaml

# ArgoCD pods status
kubectl get pods -n argocd

# ArgoCD services
kubectl get svc -n argocd

# View ArgoCD server logs
kubectl logs -n argocd deployment/argocd-server --tail=50
```

---

## 12. Resource Usage (Metrics)

> Metrics-server is already installed on this cluster.

```bash
# Node CPU and memory usage
kubectl top nodes

# Pod CPU and memory usage in cyberlearnix
kubectl top pods -n cyberlearnix

# All namespaces
kubectl top pods -A

# Sort by CPU
kubectl top pods -n cyberlearnix --sort-by=cpu

# Sort by memory
kubectl top pods -n cyberlearnix --sort-by=memory
```

---

## 13. All Resources at Once

```bash
# Everything in cyberlearnix namespace
kubectl get all -n cyberlearnix

# Everything in all namespaces
kubectl get all -A

# Multiple resource types at once
kubectl get pods,svc,deploy,ingress -n cyberlearnix

# Everything including configmaps, secrets, pvc
kubectl get pods,svc,deploy,sts,cm,secrets,pvc,ingress -n cyberlearnix
```

---

## 14. Logs

```bash
# Last 100 lines of a pod
kubectl logs <pod-name> -n cyberlearnix --tail=100

# Follow live logs
kubectl logs <pod-name> -n cyberlearnix -f

# Logs from previous (crashed) container
kubectl logs <pod-name> -n cyberlearnix --previous

# Logs with timestamps
kubectl logs <pod-name> -n cyberlearnix --timestamps

# Logs since last 1 hour
kubectl logs <pod-name> -n cyberlearnix --since=1h

# Logs for each service on this server:
kubectl logs deployment/admin-service       -n cyberlearnix --tail=50
kubectl logs deployment/user-service        -n cyberlearnix --tail=50
kubectl logs deployment/course-service      -n cyberlearnix --tail=50
kubectl logs deployment/enrollment-service  -n cyberlearnix --tail=50
kubectl logs deployment/cms-service         -n cyberlearnix --tail=50
kubectl logs deployment/form-service        -n cyberlearnix --tail=50
kubectl logs deployment/gateway-service     -n cyberlearnix --tail=50
kubectl logs deployment/instructor-service  -n cyberlearnix --tail=50
kubectl logs deployment/notification-service -n cyberlearnix --tail=50
kubectl logs deployment/shop-service        -n cyberlearnix --tail=50
kubectl logs sts/postgres                   -n cyberlearnix --tail=50
```

---

## 15. Describe (Detailed Inspection)

```bash
# Describe a pod (events, env vars, volumes, conditions)
kubectl describe pod <pod-name> -n cyberlearnix

# Describe a deployment
kubectl describe deployment admin-service -n cyberlearnix

# Describe a service
kubectl describe svc gateway-service -n cyberlearnix

# Describe a node
kubectl describe node <node-name>

# Describe a PVC
kubectl describe pvc <name> -n cyberlearnix

# Describe an ingress
kubectl describe ingress -n cyberlearnix
```

---

## 16. Output Format Tips

| Flag | What You Get |
|------|-------------|
| `-o wide` | Extra columns (IP, node, nominated node) |
| `-o yaml` | Full YAML definition of the resource |
| `-o json` | Full JSON definition of the resource |
| `-o name` | Just the resource names (e.g., `pod/admin-service-xxx`) |
| `-o jsonpath='{.spec.replicas}'` | Extract a specific field |
| `-o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}'` | List all names line by line |
| `--watch` / `-w` | Watch for live changes |
| `--show-labels` | Show all labels |
| `--sort-by=<field>` | Sort by a JSON path field |

### Useful jsonpath examples

```bash
# Get all pod names in cyberlearnix
kubectl get pods -n cyberlearnix -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}'

# Get all container images in use
kubectl get pods -n cyberlearnix -o jsonpath='{range .items[*]}{.metadata.name}{": "}{.spec.containers[0].image}{"\n"}{end}'

# Get node IP
kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}'

# Get all service ports
kubectl get svc -n cyberlearnix -o jsonpath='{range .items[*]}{.metadata.name}{": "}{.spec.ports[*].port}{"\n"}{end}'
```

---

## 17. Cyberlearnix Service Quick Reference

| Service | Type | Namespace |
|---------|------|-----------|
| `admin-service` | Deployment | cyberlearnix |
| `cms-service` | Deployment | cyberlearnix |
| `course-service` | Deployment | cyberlearnix |
| `enrollment-service` | Deployment | cyberlearnix |
| `form-service` | Deployment | cyberlearnix |
| `gateway-service` | Deployment | cyberlearnix |
| `instructor-service` | Deployment | cyberlearnix |
| `notification-service` | Deployment | cyberlearnix |
| `shop-service` | Deployment | cyberlearnix |
| `user-service` | Deployment | cyberlearnix |
| `postgres` | StatefulSet | cyberlearnix |
| `redis` | Deployment | cyberlearnix |

### Handy one-liners for this server

```bash
# Full health check
kubectl get pods -n cyberlearnix -o wide

# Check for any non-running pods
kubectl get pods -A | grep -v Running | grep -v Completed

# Check resource usage
kubectl top pods -n cyberlearnix

# Recent warning events
kubectl get events -n cyberlearnix --field-selector type=Warning --sort-by=.lastTimestamp

# Restart a deployment
kubectl rollout restart deployment/<service-name> -n cyberlearnix

# Scale a deployment
kubectl scale deployment/<service-name> --replicas=2 -n cyberlearnix

# Check ingress rules
kubectl get ingress -n cyberlearnix -o wide
```

---

> **Tip:** Prefix any command with `watch` on Linux to auto-refresh:
> ```bash
> watch kubectl get pods -n cyberlearnix
> ```
