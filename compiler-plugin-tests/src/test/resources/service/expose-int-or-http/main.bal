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

import hello.mod;

service on new mod:TestListener ("test", 8080) {
    remote function onEvent(json lead) returns error? {
    }
}

http:Listener helloEP = new(9090);

service on new mod:TestListener ("test", helloEP) {
    remote function onEvent(json lead) returns error? {
    }
}

//fails
service on new mod:TestListener ("test") {
    remote function onEvent(json lead) returns error? {
    }
}

http:Listener helloEP1 = new(9091);
listener mod:TestListener modListener = new("test", helloEP1);

 service on modListener {
    remote function onEvent(json lead) returns error? {
    }
}
