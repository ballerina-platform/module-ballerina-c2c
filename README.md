# Ballerina Code2cloud Extension
 
Code2cloud extension implementation for ballerina. 

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Daily build](https://github.com/ballerina-platform/module-ballerina-c2c/workflows/Daily%20build/badge.svg)](https://github.com/ballerina-platform/module-ballerina-c2c/actions?query=workflow%3A%22Daily+build%22)
[![Build master branch](https://github.com/ballerina-platform/module-ballerina-c2c/workflows/Build%20master%20branch/badge.svg)](https://github.com/ballerina-platform/module-ballerina-c2c/actions?query=workflow%3A%22Build+master+branch%22)

## Features:
- Kubernetes deployment support. 
- Kubernetes service support.
- Kubernetes liveness probe support
- Kubernetes readiness probe support
- Kubernetes horizontal pod autoscaler support.
- Docker image generation. 
- Dockerfile generation. 
- Kubernetes config map support.

**Refer [samples](samples) for more info.**

## How to build

1. Download and install JDK 11
1. Export github personal access token & user name as environment variables.
   ```bash
       export packagePAT=<Token>
       export packageUser=<username>
   ```
1. (optional) Specify the Java home path for JDK 11 ie;
    ```bash
        export JAVA_HOME=/Library/Java/JavaVirtualMachines/adoptopenjdk-11.jdk/Contents/Home/
    ```
1. Install Docker
1. Get a clone or download the source from this repository (https://github.com/ballerina-platform/module-ballerina-c2c)
1. Run the Gradle command ``gradle build`` from within the ``module-ballerina-c2c`` directory.
1. Copy ``build/c2c-ballerina-***.jar`` file to ``<BALLERINA_HOME>/bre/lib`` directory.

### Enabling debug logs
- Use the "BAL_DOCKER_DEBUG=true" environment variable to enable docker related debug logs when building the ballerina
source(s).
- Use the "BAL_KUBERNETES_DEBUG=true" environment variable to enable kubernetes related debug logs when building the 
ballerina source(s).

### Usage Sample:

```ballerina
import ballerina/http;
import ballerina/log;

listener http:Listener helloEP = new(9090);

service /helloWorld on helloEP {
    resource function get sayHello(http:Caller caller, http:Request request) {
        http:Response response = new;
        response.setTextPayload("Hello, World from service helloWorld ! ");
        var responseResult = caller->ok(response);
        if (responseResult is error) {
            log:printError("error responding back to client.", err = responseResult);
        }
    }
}
```

The kubernetes artifacts will be created in following structure.
```bash
$> tree
├── docker
│   └── Dockerfile
|-kubernetes
    └── hello-world.yaml    	
```
