/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerina.c2c.test.codeactions.diagnostic;

import org.testng.annotations.DataProvider;

/**
 * Test case for code action related to correcting the probe ports.
 *
 * @since 2.0.0
 */
public class InvalidPort extends CodeActionTest {

    @DataProvider(name = "codeaction-data-provider")
    @Override
    public Object[][] dataProvider() {
        return new Object[][]{
                { "fix-port.json", "Cloud.toml" }
        };
    }

    @Override
    public String getResourceDir() {
        return "fix-invalid-port";
    }
}
