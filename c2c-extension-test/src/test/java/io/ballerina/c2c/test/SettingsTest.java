/*
 * Copyright (c) 2021, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ballerina.c2c.test;

import io.ballerina.c2c.test.utils.KubernetesTestUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.ballerina.c2c.KubernetesConstants.KUBERNETES;

/**
 * Settings test.
 */
public class SettingsTest {

    @Test

    public void testMultipleYaml() throws IOException, InterruptedException {
        Path projectPath = Paths.get("src", "test", "resources", "settings", "multi-yaml");
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaProject(projectPath)
                , 0);
        final Path artifactPath = projectPath.resolve("target").resolve(KUBERNETES).resolve("hello");
        Assert.assertTrue(Files.exists(artifactPath.resolve("hello_deployment.yaml")));
        Assert.assertTrue(Files.exists(artifactPath.resolve("hello_config_map.yaml")));
        Assert.assertTrue(Files.exists(artifactPath.resolve("hello_hpa.yaml")));
        Assert.assertTrue(Files.exists(artifactPath.resolve("hello_secret.yaml")));
        Assert.assertTrue(Files.exists(artifactPath.resolve("hello_svc.yaml")));
    }

}
