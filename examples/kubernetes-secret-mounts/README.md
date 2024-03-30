## Mount Secret volumes to deployment 

- This sample runs simple ballerina hello world service with secret mounts.

### How to write:
This segment shows how a c2c segment is mapped into cloud element.  
1. ```Cloud.toml``` segment
```toml
[[cloud.secret.files]]
file="./data/data.txt" # Path to the secret file
mount_dir="./data" # Path of the file within the container

[[cloud.config.secrets]]
file="./data/Config.toml" # Path to the secret Config.toml

```
2. Kubernetes YAML file segment
```yaml
apiVersion: "v1"
kind: "Secret"
metadata:
  name: "config-secret"
data:
  Config.toml: "W2hlbGxvLmhlbGxvXQpncmVldGluZyA9ICJoZWxsbyIK"
---
apiVersion: "v1"
kind: "Secret"
metadata:
  name: "hello-data-txt-secret0"
data:
  data.txt: "TG9yZW0gaXBzdW0gZG9sb3Igc2l0IGFtZXQu"
---
```
   A kubernetes `Secret` component is created with the content of files.

```yaml
apiVersion: "apps/v1"
kind: "Deployment"
metadata:
  .
  .
  .
spec:
  .
  .
  .
  template:
    metadata:
      labels:
        app: "hello"
    spec:
      containers:
      .
      .
      .
      volumeMounts:
      - mountPath: "/home/ballerina/secrets/"
        name: "config-secret-volume"
        readOnly: true
      - mountPath: "/home/ballerina/./data/data.txt"
        name: "hello-data-txt-secret0-volume"
        readOnly: true
        subPath: "data.txt"
      volumes:
        - name: "config-secret-volume"
          secret:
            secretName: "config-secret"
        - name: "hello-data-txt-secret0-volume"
          secret:
            secretName: "hello-data-txt-secret0"
```

   `volume` and `volumeMount` are used to mount the secret to the container.
   

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
