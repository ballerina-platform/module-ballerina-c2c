import ballerina/http;
import ballerina/c2c as _;

@http:ServiceConfig {
    basePath: "/helloWorld"
}
service helloWorld on new http:Listener(9090)  {
    resource function sayHello(http:Caller outboundEP, http:Request request) returns error? {
        http:Response response = new;
        response.setTextPayload("Hello, World from service helloWorld ! \n");
        check outboundEP->respond(response);
    }
}
