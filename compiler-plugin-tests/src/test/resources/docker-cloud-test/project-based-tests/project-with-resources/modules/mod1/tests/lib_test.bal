import ballerina/io;
import ballerina/test;

// Before Suite Function

@test:Config{}
function testModuleTestResourceFile() {
    string|io:Error fileContent = io:fileReadString("modules/mod1/tests/resources/some_file.txt");

    if (fileContent is io:Error) {
        if (fileContent is io:FileNotFoundError) {
            test:assertFail("File not found");
        } else {
            test:assertFail("Error reading file");
        }
    }

    test:assertTrue(fileContent.includes("module test resource file"));    
}
