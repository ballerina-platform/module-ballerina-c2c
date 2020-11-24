import ballerina/c2c as _;
import ballerina/http;

service helloWorld on new http:Listener(9090)  {
    resource function sayHello(http:Caller outboundEP, http:Request request) returns error? {
        http:Response response = new;
        response.setTextPayload("Hello, World from service helloWorld ! \n");
        check outboundEP->respond(response);
    }
}
