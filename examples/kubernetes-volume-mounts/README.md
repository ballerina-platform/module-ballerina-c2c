## Mount Volumes

- This sample shows how to mount volumes.

### How to write:
This segment shows how a c2c segment is mapped into cloud element.  
1. ```Cloud.toml``` segment
```toml
[[cloud.deployment.storage.volumes]]
name="my-pvc" # Name of the volume 
local_path="/home/ballerina/data" # Path of the volume
size="1G" # Size of the volume
```
   
2. Kubernetes YAML file segment
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
    .
    .
    .
    spec:
      containers:
      - image: "anuruddhal/hello-api:v11"
        .
        .
        .
        volumeMounts:
        - mountPath: "/home/ballerina/data"
          name: "my-pvc-volume"
          readOnly: false
      nodeSelector: {}
      volumes:
      - name: "my-pvc-volume"
        persistentVolumeClaim:
          claimName: "my-pvc"
```

   `volume` and `volumeMount` are used to create a persistent volume to the container.


### How to run:

1. Compile the ballerina module. Command to deploy kubernetes artifacts will be printed on build success.
```bash
$> bal build
Compiling source
        hello/hello:0.0.1

Generating executable

Generating artifacts...

        @kubernetes:Service                      - complete 1/1
        @kubernetes:Deployment                   - complete 1/1
        @kubernetes:HPA                          - complete 1/1
        @kubernetes:Docker                       - complete 2/2 

        Execute the below command to deploy the Kubernetes artifacts: 
        kubectl apply -f /Users/luheerathan/luhee/Ballerina-Project-Files/Test/c2c-test/examples/kubernetes-volume-mounts/target/kubernetes/hello

        Execute the below command to access service via NodePort: 
        kubectl expose deployment hello-deployment --type=NodePort --name=hello-svc-local

        target/bin/hello.jar
```
