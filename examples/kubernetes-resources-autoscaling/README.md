## Kubernetes Autoscaling and Resource Allocation

- This sample shows how to do the autoscaling and resource allocation for kubernetes deployment.


### How to run:

1. Compile the project. Command to deploy kubernetes artifacts will be printed on build success.
```bash
$> bal build
Compiling source
        c2c/scaling:0.0.5

Generating executable

Generating artifacts...

        @kubernetes:Service                      - complete 1/1
        @kubernetes:Deployment                   - complete 1/1
        @kubernetes:HPA                          - complete 1/1
        @kubernetes:Docker                       - complete 2/2 

        Execute the below command to deploy the Kubernetes artifacts: 
        kubectl apply -f /Users/luheerathan/luhee/Ballerina-Project-Files/Test/c2c-test/examples/kubernetes-resources-autoscaling/target/kubernetes/scaling

        Execute the below command to access service via NodePort: 
        kubectl expose deployment scaling-deployment --type=NodePort --name=scaling-svc-local

        target/bin/scaling.jar
```
### How to write:
This segment shows how a c2c segment is mapped into cloud element.  
1. ```Cloud.toml``` segment for autoscaling
   ```toml
	[cloud.deployment.autoscaling]
	min_replicas=2 # Minimum number of replicas of the container alive at a given time
	max_replicas=5 # Maximum number of replicas of the container alive at a given time
	cpu=50 # CPU utilization threshold for spawning a new instance
   ```
2. Kubernetes YAML file segment for autoscaling
   ```yaml
	apiVersion: "autoscaling/v1"
	kind: "HorizontalPodAutoscaler"
	metadata:
	labels:
		app: "scaling"
	name: "scaling-hpa"
	spec:
	maxReplicas: 5
	minReplicas: 2
	scaleTargetRef:
		apiVersion: "apps/v1"
		kind: "Deployment"
		name: "scaling-deployment"
	targetCPUUtilizationPercentage: 50
   ```
   A kubernetes `HorizontalPodAutoscaler` component is created for autoscaling.

1. ```Cloud.toml``` segment for resource allocation
   ```toml
	[[cloud.deployment]
	min_memory="100Mi" # Minimum memory allocated to the container
	max_memory="256Mi" # Maximum memory allocated to the container 
	min_cpu="200m" # Minimum CPU allocated to the container
	max_cpu="500m" # Maximum CPU allocated to the container
   ```
2. Kubernetes YAML file segment for resource allocation
   ```yaml
	apiVersion: "apps/v1"
	kind: "Deployment"
	name: "scaling-deployment"
	spec:
		.
		.
		.
	template:
		.
		.
		.
		containers:
		- image: "anuruddhal/math:sample3"
			.
			.
			.
			resources:
			limits:
				memory: "256Mi"
				cpu: "500m"
			requests:
				memory: "100Mi"
				cpu: "200m"
   ```
A `resources` section is created under `containers` with `limits` and `requests`.
