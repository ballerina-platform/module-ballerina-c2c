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

package io.ballerina.c2c.test.docker;

import com.github.dockerjava.api.command.InspectImageResponse;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.test.utils.KubernetesTestUtils;
import io.ballerina.c2c.utils.KubernetesUtils;
import org.apache.commons.io.FileUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static io.ballerina.c2c.KubernetesConstants.DOCKER;
import static io.ballerina.c2c.KubernetesConstants.KUBERNETES;
import static io.ballerina.c2c.test.utils.KubernetesTestUtils.getDockerImage;

/**
 * Test cases for Docker CMD.
 */
public class CmdTest {

    private static final Path SOURCE_DIR_PATH = Paths.get("src", "test", "resources", "docker", "cmd");
    private static final Path DOCKER_TARGET_PATH = SOURCE_DIR_PATH.resolve("target").resolve(DOCKER).resolve("hello");
    private static final Path KUBERNETES_TARGET_PATH = SOURCE_DIR_PATH.resolve("target").resolve(KUBERNETES);
    private static final String DOCKER_IMAGE_JOB = "anuruddhal/cmd:v1";

    @Test
    public void testCustomDockerCMD() throws IOException, InterruptedException {
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaProject(SOURCE_DIR_PATH), 0);

        File dockerFile = DOCKER_TARGET_PATH.resolve("Dockerfile").toFile();
        Assert.assertTrue(dockerFile.exists());
        InspectImageResponse imageInspect = getDockerImage(DOCKER_IMAGE_JOB);
        Assert.assertNotNull(imageInspect.getConfig());
        Assert.assertTrue(FileUtils.readFileToString(dockerFile, StandardCharsets.UTF_8)
                .contains("FROM ballerina/jre8:v1"));
        Assert.assertEquals(Arrays.toString(imageInspect.getConfig().getCmd()),
                "[/bin/sh, -c, java -Xdiag -cp 'hello-hello-0.0.1.jar:jars/*' '$_init' --b7a.http.accesslog" +
                        ".console=true]");
    }

    @AfterClass
    public void cleanUp() throws KubernetesPluginException {
        KubernetesUtils.deleteDirectory(KUBERNETES_TARGET_PATH);
        KubernetesUtils.deleteDirectory(DOCKER_TARGET_PATH);
        KubernetesTestUtils.deleteDockerImage(DOCKER_IMAGE_JOB);
    }
}
