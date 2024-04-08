import ballerina/io;
import ballerina/cloud;

@cloud:Task
public function main(string... args) {
    io:println("hello world");
}
