import ballerina/http;

service http:Service /helloWorld on new http:Listener(9091) {
    resource function get readyz() returns boolean {
        return true;
    }
}

