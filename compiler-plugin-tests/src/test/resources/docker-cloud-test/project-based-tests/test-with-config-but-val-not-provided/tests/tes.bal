import ballerina/test;

configurable int myVal = ?;

@test:Config{}
function failingTest(){
	test:assertEquals(myVal, 2, "it is not equal!");
}
