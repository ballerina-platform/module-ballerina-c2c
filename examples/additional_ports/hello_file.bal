import ballerina/http;

configurable int port = ?;

listener http:Listener helloEP = new (port);

service /helloWorld on helloEP {
    resource function get sayHello() returns string|error? {
        return "Kubernetes!";
    }
}
