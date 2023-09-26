// Copyright (c) 2023 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import ballerina/log;
import ballerina/http;

isolated service /registerAPI on internalLs {

    isolated resource function post .(string apiName, string candidateGateway)
    returns http:Created|http:InternalServerError {
        do {
            log:printInfo("Registering API: " + apiName + " with gateway: " + candidateGateway);
        } on fail {
            return http:INTERNAL_SERVER_ERROR;
        }
        log:printInfo("Registered API: " + apiName + " with gateway: " + candidateGateway);
        return http:CREATED;
    }
}

configurable int INTERNAL_SERVICE_PORT = 9090;
configurable string ADMIN_SERVICE_HOST = "localhost:9443";
configurable string CERT_PATH = "./resources/wso2carbon.cer";
configurable string ADMIN_SERVICE_SCOPES = "apim:tenant_theme_manage apim:tier_manage apim:tier_view openid";
configurable string TOKEN_URL = "https://localhost:9443/oauth2/token";
configurable string CLIENT_ID = "xxxxxxx";
configurable string CLIENT_SECRET = "xxxxxxx";

final http:Client apimAdmin = check new (ADMIN_SERVICE_HOST, auth = {
    tokenUrl: TOKEN_URL,
    clientId: CLIENT_ID,
    clientSecret: CLIENT_SECRET,
    scopes: ADMIN_SERVICE_SCOPES,
    clientConfig: {
        secureSocket: {cert: CERT_PATH}
    }
}, secureSocket = {cert: CERT_PATH});

public listener http:Listener internalLs = new (INTERNAL_SERVICE_PORT);
