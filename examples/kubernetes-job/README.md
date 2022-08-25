## Kubernetes Job

- This sample shows how to run a ballerina application as a job in Kubernetes. 

### How to write:
This segment shows how a c2c segment is mapped into cloud element. 

1. `Ballerina code` segment
```bal
@cloud:Task
public function main(string... args) {
    io:println("hello world");
}

```
2. Kubernetes YAML file segment
```yaml
apiVersion: "batch/v1"
kind: "Job"
metadata:
  name: "anjana-main-tas-job"
spec:
  template:
    spec:
      containers:
        - image: "anjana-main_task-0.1.0:latest"
          name: "anjana-main-tas-job"
      restartPolicy: "OnFailure"

```


### How to run:

1. Build the Ballerina file.
```bash
$> bal build --cloud=k8s hello_world_job.bal
Compiling source
        hello_world_job.bal

Generating executable

Generating artifacts...

        @kubernetes:Job                          - complete 1/1
        @kubernetes:Docker                       - complete 2/2 

        Execute the below command to deploy the Kubernetes artifacts: 
        kubectl apply -f /Users/luheerathan/luhee/Ballerina-Project-Files/Test/c2c-test/examples/kubernetes-job/kubernetes

        hello_world_job.jar
```
