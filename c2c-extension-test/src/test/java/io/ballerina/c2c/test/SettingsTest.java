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

import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.test.utils.KubernetesTestUtils;
import io.ballerina.c2c.utils.KubernetesUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.ballerina.c2c.KubernetesConstants.DOCKER;
import static io.ballerina.c2c.KubernetesConstants.KUBERNETES;
import static io.ballerina.c2c.test.utils.KubernetesTestUtils.getDockerImage;

/**
 * Settings test.
 */
public class SettingsTest {
    private static final Path SOURCE_DIR_PATH = Paths.get("src", "test", "resources", "settings", "multi-yaml");
    private static final Path DOCKER_TARGET_PATH =
            SOURCE_DIR_PATH.resolve("target").resolve(DOCKER).resolve("hello");
    private static final String DOCKER_IMAGE = "hello-api:v1";

    @BeforeClass
    public void testMultipleYaml() throws IOException, InterruptedException {
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaProject(SOURCE_DIR_PATH)
                , 0);
        final Path artifactPath = SOURCE_DIR_PATH.resolve("target").resolve(KUBERNETES).resolve("hello");
        Assert.assertTrue(Files.exists(artifactPath.resolve("hello_deployment.yaml")));
        Assert.assertTrue(Files.exists(artifactPath.resolve("hello_config_map.yaml")));
        Assert.assertTrue(Files.exists(artifactPath.resolve("hello_hpa.yaml")));
        Assert.assertTrue(Files.exists(artifactPath.resolve("hello_secret.yaml")));
        Assert.assertTrue(Files.exists(artifactPath.resolve("hello_svc.yaml")));
    }


    @Test
    public void testDockerImageBuild() throws KubernetesPluginException {
        File dockerFile = DOCKER_TARGET_PATH.resolve("Dockerfile").toFile();
        Assert.assertTrue(dockerFile.exists());
        try {
            InspectImageResponse imageInspect = getDockerImage(DOCKER_IMAGE);
            Assert.fail();
        } catch (NotFoundException ignored) {
            Assert.assertTrue(true);
        }
    }

    @AfterClass
    public void cleanUp() throws KubernetesPluginException {
        KubernetesUtils.deleteDirectory(SOURCE_DIR_PATH.resolve("target"));
    }

}
