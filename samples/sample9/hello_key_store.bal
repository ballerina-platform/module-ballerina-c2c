import ballerina/http;

listener http:Listener helloWorldEP = new(9090, {
    secureSocket: {
        key: {
            path: "./resource/ballerinaKeystore.p12",
            password: "ballerina"
        },
        mutualSsl: {
            verifyClient: http:REQUIRE,
            cert: {
                path: "./resource1/ballerinaKeystore.p12",
                password: "ballerina"
            }
        }
    }
});


service /helloWorld on helloWorldEP {
    resource function get sayHello() returns string {
        return "Hello, World from service helloWorld ! \n";
    }
}
