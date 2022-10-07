/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.c2c.test.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;

/**
 * Contains create Cloud toml command tests.
 * 
 * @since 2.0.0
 */
public class CreateCloudTomlCommandTest extends AbstractCommandExecutionTest {

    public static final String COMMAND = "ballerina.create.cloud.exec";
    private static final Logger log = LoggerFactory.getLogger(CreateCloudTomlCommandTest.class);

    @Test(dataProvider = "create-cloud-toml-data-provider")
    public void testCreateCloudTomlCommand(String config, String source) throws IOException {
        performTest(config, source, COMMAND);
    }

    @DataProvider(name = "create-cloud-toml-data-provider")
    public Object[][] createCloudTomlDataProvider() {
        log.info("Test workspace/executeCommand for command {}", COMMAND);
        return new Object[][] {
                {"create_cloud_toml_cmd.json", "create_cloud_toml_cmd.bal"},
        };
    }

    @Override
    protected String getSourceRoot() {
        return "create-cloud-toml";
    }
}
