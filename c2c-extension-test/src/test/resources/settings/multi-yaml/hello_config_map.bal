// Copyright (c) 2021 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/io;
import ballerina/http;
import ballerina/log;

listener http:Listener helloWorldEP = new(9090, {
    secureSocket: {
        key: {
            certFile: "./resource/public.crt",
            keyFile: "./resource/private.key"
        }
    }
});

configurable string users = ?;
configurable string groups = ?;

service /helloWorld on helloWorldEP {
    resource function get config(http:Caller caller, http:Request request) returns @tainted error? {
        http:Response response = new;
        string payload = "Configuration: " + users + " " + groups;
        response.setTextPayload(payload + "\n");
        var responseResult = caller->respond(response);
        if (responseResult is error) {
            log:printError("error responding back to client.", 'error = responseResult);
        }
    }

    resource function get data(http:Caller caller, http:Request request) {
        http:Response response = new;
        string payload = <@untainted> readFile("./data/data.txt");
        response.setTextPayload("Data: " + <@untainted> payload + "\n");
        var responseResult = caller->respond(response);
        if (responseResult is error) {
            log:printError("error responding back to client.", 'error = responseResult);
        }
    }
}

function readFile(string filePath) returns @tainted string {
    io:ReadableByteChannel bchannel = checkpanic io:openReadableFile(filePath);
    io:ReadableCharacterChannel cChannel = new io:ReadableCharacterChannel(bchannel, "UTF-8");

    var readOutput = cChannel.read(50);
    if (readOutput is string) {
        return readOutput;
    } else {
        return "Error: Unable to read file";
    }
}
