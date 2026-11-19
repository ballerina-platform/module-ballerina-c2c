import ballerina/cloud as _;
import ballerina/http;

service /helloWorld on new http:Listener(9090) {
    resource function get sayHello() returns string {
        return "Hello, World from service helloWorld ! \n";
    }
}
