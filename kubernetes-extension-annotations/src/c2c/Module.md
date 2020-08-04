## Module Overview

This module offers an annotation based Kubernetes extension implementation for ballerina. 

### Annotation Usage Sample:

```ballerina
import ballerina/http;
import ballerina/log;
import ballerina/c2c as _;

listener http:Listener helloEP = new(9090);

@http:ServiceConfig {
    basePath: "/helloWorld"
}
service helloWorld on helloEP {
    resource function sayHello(http:Caller caller, http:Request request) {
        http:Response response = new;
        response.setTextPayload("Hello, World from service helloWorld ! ");
        var responseResult = caller->respond(response);
        if (responseResult is error) {
            log:printError("error responding back to client.", err = responseResult);
        }
    }
}
```
