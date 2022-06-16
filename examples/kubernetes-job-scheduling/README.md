## Kubernetes Cronjob Scheduling

- This sample shows how to run a ballerina application based on kubernetes cronjob scheduling.   

### How to write:
This segment shows how a c2c segment is mapped into cloud element. 

1. `Ballerina code` segment
```bal
@cloud:Task {
schedule: {
        minutes: "*/2",
        hours: "*",
        dayOfMonth: "*",
        monthOfYear: "*",
        daysOfWeek: "*"
        }
}
```
2. Kubernetes YAML file segment
```yaml
apiVersion: "batch/v1"
kind: "CronJob"
metadata:
  name: "hello-world-job-job"
spec:
  jobTemplate:
    spec:
      template:
        spec:
          containers:
          - image: "hello_world_job:latest"
            name: "hello-world-job-job"
          restartPolicy: "OnFailure"
  schedule: "*/2 * * * *"
```
   A kubernetes `Cronjob` componenet is generated with specified schedule.


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
        kubectl apply -f /Users/luheerathan/luhee/Ballerina-Project-Files/Test/c2c-test/examples/kubernetes-job-scheduling/kubernetes

        hello_world_job.jar
```
