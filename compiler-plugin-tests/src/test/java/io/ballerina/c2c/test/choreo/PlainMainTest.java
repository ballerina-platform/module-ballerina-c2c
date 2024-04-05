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
package io.ballerina.c2c.test.choreo;

import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.models.ChoreoModel;
import io.ballerina.c2c.models.PortModel;
import io.ballerina.c2c.test.utils.KubernetesTestUtils;
import io.ballerina.c2c.utils.KubernetesUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static io.ballerina.c2c.KubernetesConstants.CHOREO;
import static io.ballerina.c2c.KubernetesConstants.DOCKER;

/**
 * Contains the tests for choreo project which has no services.
 *
 * @since 2.0.0
 */
public class PlainMainTest {

    private static final Path SOURCE_DIR_PATH = Paths.get("src", "test", "resources", "choreo", "plain-main");
    private static final Path TARGET_DIR_PATH = SOURCE_DIR_PATH.resolve("target");
    private static final Path DOCKER_TARGET_PATH = TARGET_DIR_PATH.resolve(DOCKER).resolve("hello");
    private static final Path CHOREO_TARGET_PATH = TARGET_DIR_PATH.resolve(CHOREO).resolve("hello");

    @BeforeClass
    public void compileSample() throws IOException, InterruptedException {
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaProject(SOURCE_DIR_PATH), 0);
        File artifactYaml = CHOREO_TARGET_PATH.resolve("choreo.yaml").toFile();
        Assert.assertTrue(artifactYaml.exists());
    }

    @Test
    public void validateChoreoYaml() throws IOException {
        String content = Files.readString(CHOREO_TARGET_PATH.resolve("choreo.yaml"), StandardCharsets.US_ASCII);
        Yaml yaml = new Yaml(new Constructor(ChoreoModel.class, new LoaderOptions()));
        ChoreoModel model = yaml.load(content);
        List<PortModel> ports = model.getPorts();
        Assert.assertEquals(ports.size(), 0);
    }

    @Test
    public void validateDockerfile() throws IOException {
        File dockerFile = DOCKER_TARGET_PATH.resolve("Dockerfile").toFile();
        String dockerFileContent = new String(Files.readAllBytes(dockerFile.toPath()));
        Assert.assertTrue(dockerFileContent.contains("ENTRYPOINT [\"java\",\"-Xdiag\",\"-cp\"," +
                        "\"hello-hello-0.0.1.jar:jars/*\",\"hello.hello.0.$_init\"]"));
        Assert.assertTrue(dockerFileContent.contains("USER ballerina"));
        Assert.assertTrue(dockerFile.exists());
    }

    @AfterClass
    public void cleanUp() throws KubernetesPluginException {
        KubernetesUtils.deleteDirectory(CHOREO_TARGET_PATH);
        KubernetesUtils.deleteDirectory(DOCKER_TARGET_PATH);
    }
}
