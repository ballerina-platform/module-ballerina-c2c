/*
 * Copyright (c) 2023, WSO2 Inc. (http://wso2.com) All Rights Reserved.
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

import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.test.utils.KubernetesTestUtils;
import io.ballerina.c2c.utils.KubernetesUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static io.ballerina.c2c.KubernetesConstants.DOCKER;
import static io.ballerina.c2c.test.utils.KubernetesTestUtils.getCommand;
import static io.ballerina.c2c.test.utils.KubernetesTestUtils.getExposedPorts;

/**
 * Fat jar generation test.
 */
public class SettingsFatJarTest {
    private static final Path SOURCE_DIR_PATH = Paths.get("src", "test", "resources", "settings", "fat_jar");
    private static final Path DOCKER_TARGET_PATH =
            SOURCE_DIR_PATH.resolve("target").resolve(DOCKER).resolve("fat_jar");
    private static final String DOCKER_IMAGE = "fat-jar:latest";

    @Test
    public void testDockerImageBuild() throws IOException, InterruptedException {
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaProject(SOURCE_DIR_PATH), 0);
        File dockerFile = DOCKER_TARGET_PATH.resolve("Dockerfile").toFile();
        PrintStream out = System.out;
        out.println(dockerFile.toPath().toAbsolutePath().toString());
        Assert.assertTrue(dockerFile.exists());
    }

    @Test
    public void validateDockerImage() {
        List<String> ports = getExposedPorts(DOCKER_IMAGE);
        Assert.assertEquals(ports.size(), 1);
        Assert.assertEquals(ports.get(0), "9090/tcp");
        // Validate ballerina.conf in run command
        Assert.assertEquals(getCommand(DOCKER_IMAGE).toString(), "[/bin/sh, -c, java -Xdiag " +
                "-cp \"anjana-fat_jar-0.1.0.jar:jars/*\" 'anjana.fat_jar.0.$_init']");
    }

    @AfterClass
    public void cleanUp() throws KubernetesPluginException {
        KubernetesUtils.deleteDirectory(SOURCE_DIR_PATH.resolve("target"));
    }
}
