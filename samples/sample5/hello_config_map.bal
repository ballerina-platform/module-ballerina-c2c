import ballerina/config;
import ballerina/c2c as _;
import ballerina/io;
import ballerina/http;
import ballerina/log;

listener http:Listener helloWorldEP = new(9090, {
    secureSocket: {
        keyStore: {
            path: "./security/ballerinaKeystore.p12",
            password: "ballerina"
        },
        trustStore: {
            path: "./security/ballerinaTruststore.p12",
            password: "ballerina"
        }
    }
});

service /helloWorld on helloWorldEP {
    resource function get config/[string user](http:Caller caller, http:Request request) returns @tainted error? {
        http:Response response = new;
        string userId = getConfigValue(user, "userid");
        string groups = getConfigValue(user, "groups");
        string payload = "{userId: " + userId + ", groups: " + groups + "}";
        response.setTextPayload(payload + "\n");
        var responseResult = caller->ok(response);
        if (responseResult is error) {
            log:printError("error responding back to client.", err = responseResult);
        }
    }

    resource function get data(http:Caller caller, http:Request request) {
        http:Response response = new;
        string payload = <@untainted> readFile("./data/data.txt");
        response.setTextPayload("Data: " + <@untainted> payload + "\n");
        var responseResult = caller->ok(response);
        if (responseResult is error) {
            log:printError("error responding back to client.", responseResult);
        }
    }
}

function getConfigValue(string instanceId, string property) returns (string) {
    string key = <@untainted> instanceId + "." + <@untainted> property;
    return config:getAsString(key, "Invalid User");
}

function readFile(string filePath) returns @tainted string {
    io:ReadableByteChannel bchannel = checkpanic io:openReadableFile(filePath);
    io:ReadableCharacterChannel cChannel = new io:ReadableCharacterChannel(bchannel, "UTF-8");

    var readOutput = cChannel.read(50);
    if (readOutput is string) {
        return readOutput;
    } else {
        return "Error: Unable to read file";
    }
}
