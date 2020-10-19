import ballerina/io;
import ballerina/c2c;

@c2c:Task {
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
