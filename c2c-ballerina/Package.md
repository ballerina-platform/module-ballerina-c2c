## Module Overview

This module offers an annotation based Kubernetes extension implementation for ballerina. 

### Annotation Usage Sample:

```ballerina
import ballerina/http;
import ballerina/log;

listener http:Listener helloEP = new(9090);

service /helloWorld on helloEP {
    resource function get sayHello(http:Caller caller, http:Request request) {
        var responseResult = caller->respond("Hello, World from service helloWorld ! ");
        if (responseResult is error) {
            log:printError("error responding back to client.", 'error = responseResult);
        }
    }
}
```

Build the above program with build-option `--cloud = k8s` to generate kubernetes artifacts.
```bash

$> bal build --cloud=k8s hello_world.bal

Compiling source
	hello_world.bal

Generating executable

Generating artifacts...

	@kubernetes:Service 			 - complete 1/1
	@kubernetes:Deployment 			 - complete 1/1
	@kubernetes:HPA 			 - complete 1/1
	@kubernetes:Docker 			 - complete 2/2

	Execute the below command to deploy the Kubernetes artifacts:
	kubectl apply -f /Users/anuruddha/workspace/ballerinax/module-ballerina-c2c/samples/sample1/kubernetes

	Execute the below command to access service via NodePort:
	kubectl expose deployment hello-world-deployment --type=NodePort --name=hello-world-svc-local
```