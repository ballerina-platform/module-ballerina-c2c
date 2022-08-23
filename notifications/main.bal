// Copyright (c) 2022 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
import ballerina/regex;
import ballerina/os;

final http:Client chatWebhookAPI = check new ("https://chat.googleapis.com");

type EnvError distinct error;

const string image = "choreosharedc2cballerinatapir.azurecr.io/ballerina/jvm-runtime";
const string icon = "https://img.icons8.com/fluent/48/000000/update.png";

public function main() returns error? {
    string spaceId = os:getEnv("SPACE_ID");
    if (spaceId == "") {
        return error EnvError("Env variable SPACE_ID not found");
    }
    string messageKey = os:getEnv("MESSAGE_KEY");
    if (messageKey == "") {
        return error EnvError("Env variable MESSAGE_KEY not found");
    }
    string token = os:getEnv("CHAT_TOKEN");
    if (token == "") {
        return error EnvError("Env variable CHAT_TOKEN not found");
    }

    string tags = os:getEnv("TAGS");
    if (tags == "") {
        return error EnvError("Env variable TAGS not found");
    }

    tags = regex:replaceAll(tags, ",", "\n");

    json body = {
        "cards": [
            {
                "header": {
                    "title": "Image: " + image,
                    "subtitle": "The following image tags has been updated",
                    "imageUrl": icon
                },
                "sections": [
                    {
                        "header": "Tags",
                        "widgets": [
                            {
                                "keyValue": {
                                    "content": tags
                                }
                            }
                        ]
                    }
                ]
            }
        ]
    };

    http:Response resp = check chatWebhookAPI->post("/v1/spaces/" + spaceId + "/messages?key=" + messageKey + "token=" + token, body);
    if (resp.statusCode == 200) {
        log:printInfo("Notification sent");
    } else {
        log:printWarn("Notification request failed " + resp.statusCode.toString());
    }
}
