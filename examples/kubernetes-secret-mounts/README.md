## Mount Secret volumes to deployment 

- This sample runs simple ballerina hello world service with secret mounts.

### How to run:

1. Compile the ballerina module. Command to deploy kubernetes artifacts will be printed on build success.
```bash
$> bal build 
Compiling source
	hello/hello:0.0.1

Generating executable

Generating artifacts...

	@kubernetes:Secret 			 - complete 2/2
	@kubernetes:Service 			 - complete 1/1
	@kubernetes:Deployment 			 - complete 1/1
	@kubernetes:HPA 			 - complete 1/1
	@kubernetes:Docker 			 - complete 2/2

	Execute the below command to deploy the Kubernetes artifacts:
	kubectl apply -f /Users/anuruddha/workspace/ballerinax/module-ballerina-c2c/samples/sample12/target/kubernetes/hello

	Execute the below command to access service via NodePort:
	kubectl expose deployment hello-deployment --type=NodePort --name=hello-svc-local

	target/bin/hello.jar
```
### How to write:
This segment shows how a c2c segment is mapped into cloud element.  
1. ```Cloud.toml``` segment
   ```toml
	[[cloud.secret.files]]
	file="./conf/data.txt" # External file path to mount as a secret volume
	mount_path="/home/ballerina/data" # Path of the file within the container

	[[cloud.secret.files]]
	file="./conf/Config.toml" # External file path to mount as a secret volume

   ```
2. Kubernetes YAML file segment
   ```yaml
	apiVersion: "v1"
	kind: "Secret"
	metadata:
	name: "hello-data-txt"
	data:
	data.txt: "TG9yZW0gaXBzdW0gZG9sb3Igc2l0IGFtZXQu"
	---
	apiVersion: "v1"
	kind: "Secret"
	metadata:
	name: "hello-ballerina-conf-secret"
	data:
	Config.toml: "W2hlbGxvLmhlbGxvXQp1c2VycyA9ICJqb2huQGJhbGxlcmluYS5jb20samFuZUBiYWxsZXJpbmEuY29tIgpncm91cHMgPSAiYXBpbSxlc2IiCg=="

   ```
   A kubernetes `Secret` component is created with the content of files.

   ```yaml
	apiVersion: "apps/v1"
	kind: "Deployment"
	name: "hello-deployment"
	spec:
		.
		.
		.
	template:
		.
		.
		.
		containers:
		- image: "anuruddhal/hello-api:sample12"
			.
			.
			.
			volumeMounts:
			- mountPath: "/home/ballerina/data"
			name: "hello-data-txt-volume"
			readOnly: true
			- mountPath: "/home/ballerina/conf/"
			name: "hello-ballerina-conf-secret-volume"
			readOnly: false
		nodeSelector: {}
		volumes:
		- name: "hello-data-txt-volume"
			secret:
			secretName: "hello-data-txt"
		- name: "hello-ballerina-conf-secret-volume"
			secret:
			secretName: "hello-ballerina-conf-secret"
   ```

   `volume` and `volumeMount` are used to mount the secret to the container.