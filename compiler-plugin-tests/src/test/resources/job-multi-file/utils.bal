import ballerina/io;

// Sorts after main.bal alphabetically -- a regression test for the case where the
// @cloud:Task annotation on main() must not be clobbered by a later file in the
// same module that carries no task/service of its own.
function utilFunction() {
    io:println("util");
}
