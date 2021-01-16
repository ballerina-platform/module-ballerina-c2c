import ballerina/http;
import ballerina/log;
import ballerina/config;
import ballerina/c2c as _;

service /helloWorld on new http:Listener(9090) {
    resource function get sayHello(http:Caller caller, http:Request request) {
        http:Response response = new;
        response.setTextPayload("Hello, World from service helloWorld ! ");
        var responseResult = caller->ok(response);
        if (responseResult is error) {
            log:printError("error responding back to client.", err = responseResult);
        }
    }

    resource function get readyz(http:Caller caller, http:Request req) {
        json j = {
            message:getConfigValue("Hello")
        };
        http:Response res = new;
        res.setJsonPayload(j);
        //Reply to the client with the response.
        var result = caller->respond(res);
        if (result is error) {
           log:printError("Error in responding", err = result);
        }
    }
}

function getConfigValue(string key) returns (string) {
    return config:getAsString(key, "Invalid User");
}
