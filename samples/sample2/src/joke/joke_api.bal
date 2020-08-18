import ballerina/c2c as _;
import ballerina/http;

service JokeAPI on new http:Listener(9090) {

    resource function getJoke(http:Caller caller, http:Request req) returns error? {
        check caller->respond(check <@untainted>invokeJokeAPI());
    }
}

function invokeJokeAPI() returns @tainted json|error {
    http:Client clientEP = new ("https://sv443.net/jokeapi");
    http:Response resp = check clientEP->get("/v2/joke/Programming,Miscellaneous?blacklistFlags=nsfw,religious,racist");
    json payload = check resp.getJsonPayload();
    return payload;
}
