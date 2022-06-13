## Readiness and Liveness Probes.

- This sample runs hello world service with liveness and readiness probes.
```toml
[cloud.deployment.probes.readiness]
port=9090
path="/helloWorld/readyz"

[cloud.deployment.probes.liveness]
port=9090
path="/helloWorld/healthz"
```

### How to run:

1. Compile the project. Command to deploy kubernetes artifacts will be printed on build success.
```bash
$> bal build
Compiling source
	hello/hello:0.0.1

Creating balas
	target/bala/hello-2020r2-any-0.0.1.bala

Running Tests

	hello/hello:0.0.1
	No tests found


Generating executables
	target/bin/hello.jar

Generating artifacts...

	@kubernetes:Service 			 - complete 1/1
	@kubernetes:Deployment 			 - complete 1/1
	@kubernetes:HPA 			 - complete 1/1
	@kubernetes:Docker 			 - complete 2/2

	Execute the below command to deploy the Kubernetes artifacts:
	kubectl apply -f /Users/anuruddha/workspace/ballerinax/module-ballerina-c2c/samples/sample6/target/kubernetes/hello

	Execute the below command to access service via NodePort:
	kubectl expose deployment hello-hello-0-0-deployment --type=NodePort --name=hello-hello-0-0-svc-local
```
### How to write:
This segment shows how a c2c segment is mapped into cloud element.  
1. ```Cloud.toml``` segment
   ```toml
	[cloud.deployment.probes.readiness]
	port=9090 # Port of the readiness probe endpoint 
	path="/helloWorld/readyz" # Endpoint of the readiness probe

	[cloud.deployment.probes.liveness]
	port=9090 # Port of the liveness probe endpoint
	path="/helloWorld/healthz" # Endpoint of the liveness probe
   ```
2. Kubernetes YAML file segment
   ```yaml
	apiVersion: "apps/v1"
	kind: "Deployment"
	name: "hello-deployment"
	template:
		.
		.
		.
		spec:
		containers:
		- image: "hello-api:sample6"
				.
				.
				.
			livenessProbe:
			httpGet:
				path: "/helloWorld/healthz"
				port: 9090
			initialDelaySeconds: 30
				.
				.
				.
			readinessProbe:
			httpGet:
				path: "/helloWorld/readyz"
				port: 9090
			initialDelaySeconds: 30
   ```

 `livenessProbe` and `readinessProbe` segments are created within the kubernetes component.