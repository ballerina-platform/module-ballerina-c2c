import ballerina/io;
import ballerina/http;

configurable string greeting = "meow";

listener http:Listener helloWorldEP = new(9090);

service /helloWorld on helloWorldEP {

    resource function get data() returns string|error {
        io:println(greeting);
        return io:fileReadString("./data/data.txt");
    }
}

