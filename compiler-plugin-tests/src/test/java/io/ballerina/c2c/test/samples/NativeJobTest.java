/*
 * Copyright (c) 2024, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.q
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

import com.github.dockerjava.api.command.InspectImageResponse;
import io.ballerina.c2c.KubernetesConstants;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.test.utils.KubernetesTestUtils;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static io.ballerina.c2c.KubernetesConstants.DOCKER;
import static io.ballerina.c2c.KubernetesConstants.KUBERNETES;
import static io.ballerina.c2c.test.utils.KubernetesTestUtils.getDockerImage;

/**
 * Test cases for sample 4.
 */
public class NativeJobTest extends SampleTest {

    private static final Path SOURCE_DIR_PATH = SAMPLE_DIR.resolve("kubernetes-native-job");
    private static final Path DOCKER_TARGET_PATH = SOURCE_DIR_PATH.resolve("target").resolve(DOCKER)
            .resolve("hello");
    private static final Path KUBERNETES_TARGET_PATH = SOURCE_DIR_PATH.resolve("target").resolve(KUBERNETES)
            .resolve("hello");
    private static final String DOCKER_IMAGE = "xlight-hello-0.0.1:latest";

    @BeforeClass
    public void compileSample() throws IOException, InterruptedException {

        Assert.assertEquals(KubernetesTestUtils.compileBallerinaProject(SOURCE_DIR_PATH), 0);
        File dockerFile = DOCKER_TARGET_PATH.resolve("Dockerfile").toFile();
        Assert.assertTrue(dockerFile.exists());
        InspectImageResponse imageInspect = getDockerImage(DOCKER_IMAGE);
        Assert.assertNotNull(imageInspect.getConfig());
    }

    @Test
    public void validateDockerfile() throws IOException {

        File dockerFile = DOCKER_TARGET_PATH.resolve("Dockerfile").toFile();
        Assert.assertTrue(dockerFile.exists());
        String content = Files.readString(dockerFile.toPath(), StandardCharsets.UTF_8);
        Assert.assertTrue(content.contains("RUN native-image -jar hello.jar -H:Name=hello --no-fallback " +
                "-H:+StaticExecutableWithDynamicLibC"));
        Assert.assertTrue(content.contains("FROM ghcr.io/graalvm/native-image-community:17-ol8 as build"));
        Assert.assertTrue(content.contains("FROM gcr.io/distroless/base"));
    }

    @Test
    public void validateJob() throws IOException {

        File jobYAML = KUBERNETES_TARGET_PATH.resolve("hello.yaml").toFile();
        Job job = KubernetesTestUtils.loadYaml(jobYAML);
        Assert.assertEquals(job.getMetadata().getName(), "xlight-hello-0-job");
        List<Container> containers = job.getSpec().getTemplate().getSpec().getContainers();
        Assert.assertEquals(containers.size(), 1);
        Container container = containers.get(0);
        Assert.assertEquals(container.getImage(), DOCKER_IMAGE);
        Assert.assertEquals(job.getSpec().getTemplate().getSpec()
                .getRestartPolicy(), KubernetesConstants.RestartPolicy.OnFailure.name());
    }

    @AfterClass
    public void cleanUp() throws KubernetesPluginException {

        KubernetesUtils.deleteDirectory(KUBERNETES_TARGET_PATH);
        KubernetesUtils.deleteDirectory(DOCKER_TARGET_PATH);
        KubernetesTestUtils.deleteDockerImage(DOCKER_IMAGE);
    }
}
