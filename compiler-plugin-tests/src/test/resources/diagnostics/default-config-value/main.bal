import ballerina/http;
import ballerina/log;

configurable int port = ?;

service /helloWorld on new http:Listener(port) {
    resource function get sayHello(http:Caller caller, http:Request request) {
        http:Response response = new;
        response.setTextPayload("Hello, World from service helloWorld !");
        var responseResult = caller->respond(response);
        if (responseResult is error) {
            log:printError("error responding back to client.", 'error = responseResult);
        }
    }
}
