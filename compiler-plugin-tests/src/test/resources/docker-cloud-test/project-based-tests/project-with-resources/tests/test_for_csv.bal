import ballerina/io;
import ballerina/test;

type Character record {|
    string name;
    int age;
    string salary;
    string gender;
|};

@test:Config{}
function testReadingResourceCsv() {
    //read the file
    Character[]|io:Error characters = io:fileReadCsv("tests/resources/data_file.csv");

    if (characters is io:Error) {
        if (characters is io:FileNotFoundError) {
            test:assertFail("File not found");
        } else {
            test:assertFail("Error reading file");
        }
    }

    test:assertTrue(characters.length() == 3, "Character count mismatch");
    test:assertEquals(characters[0].name, "Goku", "Name mismatch");
    test:assertEquals(characters[2].gender, "F", "Gender mismatch");
}
