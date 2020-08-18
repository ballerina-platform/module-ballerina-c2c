import ballerina/c2c as _;
import ballerina/config;
import ballerina/http;
import ballerina/io;

service JokeAPI on new http:Listener(9090) {

    resource function getJoke(http:Caller caller, http:Request req) returns error? {
        check caller->respond(check <@untainted>invokeJokeAPI());
    }

    resource function healthz(http:Caller caller, http:Request req) returns error? {
        http:Response res = new;
        res.statusCode = 200;
        check caller->respond(res);
    }
}

type StringArray string[];

function invokeJokeAPI() returns @tainted json|error {
    string[]|error blockList = config:getAsArray("BlockList").cloneWithType(StringArray);
    if (blockList is string[] && blockList.length() > 0) {
        string blockedItems = "";
        foreach var item in blockList {
            blockedItems = blockedItems.concat(item, ",");
        }
        blockedItems = blockedItems.substring(0, blockedItems.length() - 1);
        io:println("blocked: ", blockedItems);
        http:Client clientEP = new ("https://sv443.net/jokeapi");
        http:Response resp = check clientEP->get("/v2/joke/Programming,Miscellaneous?blacklistFlags=" + blockedItems);
        json payload = check resp.getJsonPayload();
        return payload;
    } else {
        json errorJson = {"Message": "Unable to read block list from ballerina.conf"};
        return errorJson;
    }
}
