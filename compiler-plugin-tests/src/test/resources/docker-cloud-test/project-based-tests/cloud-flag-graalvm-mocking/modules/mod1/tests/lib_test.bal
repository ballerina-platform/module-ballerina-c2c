// Copyright (c) 2024 WSO2 LLC. (http://www.wso2.com).
//
// WSO2 LLC. licenses this file to you under the Apache License,
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

import ballerina/test;

@test:Mock {functionName: "intMult"}
test:MockFunction intMultMockFn = new ();

@test:Config{}
function testMockingInModule() {
    test:when(intMultMockFn).thenReturn(33);
    test:assertEquals(intMult(1, 2), 33, "Mocking failed");

    test:when(intMultMockFn).withArguments(6,6).callOriginal();
    test:assertEquals(intMult(6, 6), 36, "Mocking failed");
}