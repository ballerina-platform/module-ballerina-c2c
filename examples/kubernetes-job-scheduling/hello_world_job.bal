import ballerina/io;
import ballerina/cloud;

@cloud:Task {
  schedule: {
        minutes: "*/2",
        hours: "*",
        dayOfMonth: "*",
        monthOfYear: "*",
        daysOfWeek: "*"
  }
}
public function main(string... args) {
    io:println("hello world");
}