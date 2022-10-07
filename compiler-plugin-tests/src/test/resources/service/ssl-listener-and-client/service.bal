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

import ballerina/http;
import ballerina/log;

http:ListenerConfiguration sslProtocolServiceConfig = {
    secureSocket: {
        key: {
            path: "src/test/resources/service/security/ballerinaKeystore.p12",
            password: "ballerina"
         },
         protocol: {
             name: http:TLS,
             versions: ["TLSv1.2"]
         }
    }
};

listener http:Listener sslProtocolListener = new(9249, config = sslProtocolServiceConfig);

service /protocol on sslProtocolListener {
    resource function get protocolResource(http:Caller caller, http:Request req) {
        error? result = caller->respond("Hello World!");
        if (result is error) {
           log:printError("Failed to respond", 'error = result);
        }
    }
}

listener http:Listener helloEP = new(9090);

http:ClientConfiguration sslProtocolClientConfig = {
    secureSocket: {
        cert: {
            path: "./security/ballerinaTruststore.p12",
            password: "ballerina"
        },
        protocol: {
            name: http:TLS,
            versions: ["TLSv1.2"]
        }
    }
};

service /hello on helloEP {
    resource function get world() returns string|error {
        int x;
        http:Client clientEP = checkpanic new("https://localhost:9249", sslProtocolClientConfig);
        http:Response|error resp = clientEP->get("/protocol/protocolResource");
        if (resp is http:Response) {
            return "Response Recieved ";
        } else {
            return resp;
        }
    }
}
