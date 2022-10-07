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

import ballerina/io;
import ballerina/grpc;

string certPath = "./resources/public.crt";
string keyPath = "./resources/private.key";

listener grpc:Listener ep = new (8443,
    secureSocket = {
        key: {
            certFile: certPath,
            keyFile: keyPath
        }
    }
);

@grpc:ServiceDescriptor {descriptor: ROOT_DESCRIPTOR_HELLO, descMap: getDescriptorMapHello()}
service "HelloWorld" on ep {

    remote function hello(string value) returns string|error {
        io:print(value);
        return string `Hello ${value}`;
    }
}
