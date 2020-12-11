## Module Overview

This module offers an annotation based Kubernetes extension implementation for ballerina. 

### Annotation Usage Sample:

```ballerina
import ballerina/http;
import ballerina/log;
import ballerina/c2c as _;

listener http:Listener helloEP = new(9090);

service /helloWorld on helloEP {
    resource function get sayHello(http:Caller caller, http:Request request) {
        var responseResult = caller->ok("Hello, World from service helloWorld ! ");
        if (responseResult is error) {
            log:printError("error responding back to client.", err = responseResult);
        }
    }
}
```
