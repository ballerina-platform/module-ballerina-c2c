## Kubernetes Sample With Custom Listener

- This sample shows how create a sample code to cloud compatible listener.

### How to write:
This segment shows how a c2c segment is mapped into cloud element.  

1. `Ballerina file (mod.bal)`
```bal
public function init(@cloud:Expose int port, ListenerConfiguration config) {
}
```
   `@cloud:Expose int port` is used to specify a port. When a package consumes this listener, this port will be used to generate the Dockerfile, and Kubernetes yamls.
   

### How to run:

1. Compile the project. Command to deploy kubernetes artifacts will be printed on build success.
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
        kubectl apply -f /Users/luheerathan/luhee/Ballerina-Project-Files/Test/c2c-test/samples/sample10/target/kubernetes/hello

        Execute the below command to access service via NodePort: 
        kubectl expose deployment hello-deployment --type=NodePort --name=hello-svc-local

        target/bin/hello.jar
```
