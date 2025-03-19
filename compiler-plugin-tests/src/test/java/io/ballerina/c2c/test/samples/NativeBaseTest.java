/*
 * Copyright (c) 2023, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package io.ballerina.c2c.test.samples;

import io.ballerina.c2c.DockerGenConstants;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.test.utils.KubernetesTestUtils;
import io.ballerina.c2c.utils.KubernetesUtils;
import org.apache.commons.io.FilenameUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.ballerina.c2c.KubernetesConstants.DOCKER;

/**
 * Test cases for docker cloud option as a project.
 */
public class NativeBaseTest {

    protected static final Path SAMPLE_DIR = Paths.get(FilenameUtils.separatorsToSystem(
            System.getProperty("sampleDir")));
    private static final Path SOURCE_DIR_PATH = SAMPLE_DIR.resolve("graalvm-base-image");
    private static final Path DOCKER_TARGET_PATH =
            SOURCE_DIR_PATH.resolve("target").resolve(DOCKER).resolve("base_img");

    @Test()
    public void validateCustomBaseImage() throws IOException, InterruptedException, KubernetesPluginException {
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaProject(SOURCE_DIR_PATH), 0);
        File dockerFile = DOCKER_TARGET_PATH.resolve("Dockerfile").toFile();
        String content = Files.readString(dockerFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(dockerFile.exists());
        Assert.assertTrue(content.contains("FROM alpine"));
        Assert.assertTrue(content.contains("FROM " + DockerGenConstants.NATIVE_BUILDER_IMAGE + " as build"));
        KubernetesUtils.deleteDirectory(DOCKER_TARGET_PATH);
    }
}
