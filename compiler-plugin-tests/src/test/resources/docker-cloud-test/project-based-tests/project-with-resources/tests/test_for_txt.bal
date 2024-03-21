import ballerina/io;
import ballerina/test;

@test:Config{}
function testReadingResourceTxt() {
    //read the file
    string|io:Error fileContent = io:fileReadString("tests/resources/text_file.txt");

    if (fileContent is io:Error) {
        if (fileContent is io:FileNotFoundError) {
            test:assertFail("File not found");
        } else {
            test:assertFail("Error reading file");
        }
    }

    test:assertTrue(fileContent.includes("Hello, this is a text file."));
}
