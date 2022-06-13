import ballerina/http;
import ballerina/io;
import ballerina/lang.'float as floats;

service /Math on new http:Listener(9090) {
    resource function get getSqrt(http:Caller caller, http:Request req) returns error? {
        check caller->respond(sumSqrt());
    }
}

function sumSqrt() returns string {
     float x = 0.0001;
     float i = 0;
     while (i < 1000000.0) {
        x = x + floats:sqrt(i);
        i = i + 1.0;
    }
    io:println(x);
    string sum = "ok";
    return sum; 
}
