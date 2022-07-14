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

http:ListenerConfiguration mutualSslServiceConf = {
    secureSocket: {
        key: {
            path: "src/test/resources/service/security/ballerinaKeystore.p12",
            password: "ballerina"
        },
        mutualSsl: {
            verifyClient: http:REQUIRE,
            cert: {
                path: "src/test/resources/service/security/ballerinaTruststore.p12",
                password: "ballerina"
            }
        },
        protocol: {
            name: "TLS",
            versions: ["TLSv1.2"]
        },
        ciphers: ["TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"],
        handshakeTimeout: 20,
        sessionTimeout: 30
    }
};

listener http:Listener echo15 = new(9116, mutualSslServiceConf);

service /helloWorld15 on echo15 {
    
    resource function get .(http:Caller caller, http:Request req) {
        string expectedCert = "MIIDdzCCAl+gAwIBAgIEfP3e8zANBgkqhkiG9w0BAQsFADBkMQswCQYDVQQGEwJVUzELMAkGA1UECBMCQ0ExF"
                        + "jAUBgNVBAcTDU1vdW50YWluIFZpZXcxDTALBgNVBAoTBFdTTzIxDTALBgNVBAsTBFdTTzIxEjAQBgNVBAMTCWxvY2Fsa"
                        + "G9zdDAeFw0xNzEwMjQwNTQ3NThaFw0zNzEwMTkwNTQ3NThaMGQxCzAJBgNVBAYTAlVTMQswCQYDVQQIEwJDQTEWMBQGA"
                        + "1UEBxMNTW91bnRhaW4gVmlldzENMAsGA1UEChMEV1NPMjENMAsGA1UECxMEV1NPMjESMBAGA1UEAxMJbG9jYWxob3N0M"
                        + "IIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAgVyi6fViVLiZKEnw59xzNi1lcYh6z9dZnug+F9gKqFIgmdcPe"
                        + "+qtS7gZc1jYTjWMCbx13sFLkZqNHeDUadpmtKo3TDduOl1sqM6cz3yXb6L34k/leh50mzIPNmaaXxd3vOQoK4OpkgO1n"
                        + "32mh6+tkp3sbHmfYqDQrkVK1tmYNtPJffSCLT+CuIhnJUJco7N0unax+ySZN67/AX++sJpqAhAIZJzrRi6ueN3RFCIxY"
                        + "DXSMvxrEmOdn4gOC0o1Ar9u5Bp9N52sqqGbN1x6jNKi3bfUj122Hu5e+Y9KOmfbchhQil2P81cIi30VKgyDn5DeWEuDo"
                        + "Yredk4+6qAZrxMw+wIDAQABozEwLzAOBgNVHQ8BAf8EBAMCBaAwHQYDVR0OBBYEFNmtrQ36j6tUGhKrfW9qWWE7KFzMM"
                        + "A0GCSqGSIb3DQEBCwUAA4IBAQAv3yOwgbtOu76eJMl1BCcgTFgaMUBZoUjK9Un6HGjKEgYz/YWSZFlY/qH5rT01DWQev"
                        + "UZB626d5ZNdzSBZRlpsxbf9IE/ursNHwHx9ua6fB7yHUCzC1ZMp1lvBHABi7wcA+5nbV6zQ7HDmBXFhJfbgH1iVmA1Kc"
                        + "vDeBPSJ/scRGasZ5q2W3IenDNrfPIUhD74tFiCiqNJO91qD/LO+++3XeZzfPh8NRKkiPX7dB8WJ3YNBuQAvgRWTISpSS"
                        + "XLmqMb+7MPQVgecsepZdk8CwkRLxh3RKPJMjigmCgyvkSaoDMKAYC3iYjfUTiJ57UeqoSl0IaOFJ0wfZRFh+UytlDZa";
        http:Response res = new;
        if (req.mutualSslHandshake["status"] == "passed") {
            string? cert = req.mutualSslHandshake["base64EncodedCert"];
            if (cert is string) {
                if (cert == expectedCert) {
                    res.setTextPayload("hello world");
                } else {
                    res.setTextPayload("Expected cert not found");
                }
            } else {
                res.setTextPayload("Cert not found");
            }
        }
        checkpanic caller->respond(res);
    }
}

listener http:Listener echoDummy15 = new(9117);
listener http:Listener helloEP = new(9090);

service /echoDummyService15 on echoDummy15 {

    resource function post .(http:Caller caller, http:Request req) {
        http:Response res = new;
        res.setTextPayload("hello world");
        checkpanic caller->respond(res);
    }
}

http:ClientConfiguration mutualSslClientConf1 = {
    secureSocket: {
        key: {
            path: "src/test/resources/service/security/ballerinaKeystore.p12",
            password: "ballerina"
        },
        cert: {
            path: "src/test/resources/service/security/ballerinaTruststore.p12",
            password: "ballerina"
        },
        protocol: {
            name: http:SSL,
            versions: ["TLSv1.2"]
        },
        ciphers: ["TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"],
        handshakeTimeout: 20,
        sessionTimeout: 30
    }
};


http:ClientConfiguration mutualSslClientConf2 = {
    secureSocket: {
        key: {
            path: "src/test/resources/service/security/ballerinaKeystore.p12",
            password: "ballerina"
        },
        protocol: {
            name: http:SSL,
            versions: ["TLSv1.2"]
        },
        ciphers: ["TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"],
        handshakeTimeout: 20,
        sessionTimeout: 30
    }
};


http:ClientConfiguration mutualSslClientConf3 = {
    secureSocket: {
        enable: false,
        key: {
            path: "src/test/resources/service/security/ballerinaKeystore.p12",
            password: "ballerina"
        },
        protocol: {
            name: http:SSL,
            versions: ["TLSv1.2"]
        },
        ciphers: ["TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA"],
        handshakeTimeout: 20,
        sessionTimeout: 30
    }
};

service /hello on helloEP {
    resource function get one() returns string|error {
        http:Client httpClient = checkpanic new("https://localhost:9116", mutualSslClientConf1);
        http:Response|error resp = httpClient->get("/helloWorld15/");
        if (resp is http:Response) {
            var payload = resp.getTextPayload();
            if (payload is string) {
                return payload;
            } else {
                return payload;
            }
        } else {
            return resp.message();
        }
    }
    
    resource function get two() returns string|error {
        http:Client httpClient = checkpanic new("https://localhost:9116", mutualSslClientConf2);
        http:Response|error resp = httpClient->get("/helloWorld15/");
    if (resp is error) {
        return resp.message();
    } else {
        return "Expected mutual SSL error not found";
    }
    }
    
    resource function get three() returns string|error {
        http:Client httpClient = checkpanic new("https://localhost:9116", mutualSslClientConf3);
        http:Response|error resp = httpClient->get("/helloWorld15/");
        if (resp is http:Response) {
            var payload = resp.getTextPayload();
            return payload;
        } else {
            return resp.message();
        }
    }
}
