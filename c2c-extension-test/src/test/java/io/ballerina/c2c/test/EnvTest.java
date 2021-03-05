/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package io.ballerina.c2c.test;

import com.github.dockerjava.api.command.InspectImageResponse;
import io.ballerina.c2c.KubernetesConstants;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.test.utils.KubernetesTestUtils;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static io.ballerina.c2c.KubernetesConstants.DOCKER;
import static io.ballerina.c2c.KubernetesConstants.KUBERNETES;
import static io.ballerina.c2c.test.utils.KubernetesTestUtils.getDockerImage;

/**
 * Test cases for Env Vars.
 */
public class EnvTest {
    private static final Path SOURCE_DIR_PATH = Paths.get("src", "test", "resources", "env");
    private static final Path DOCKER_TARGET_PATH =
            SOURCE_DIR_PATH.resolve("target").resolve(DOCKER).resolve("hello");
    private static final Path KUBERNETES_TARGET_PATH =
            SOURCE_DIR_PATH.resolve("target").resolve(KUBERNETES).resolve("hello");
    private static final String DOCKER_IMAGE = "hello:latest";

    @Test
    public void testEnvVars() throws IOException, InterruptedException {
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaProject(SOURCE_DIR_PATH), 0);

        File dockerFile = DOCKER_TARGET_PATH.resolve("Dockerfile").toFile();
        Assert.assertTrue(dockerFile.exists());
        InspectImageResponse imageInspect = getDockerImage(DOCKER_IMAGE);
        Assert.assertNotNull(imageInspect.getConfig());

        File k8sYaml = KUBERNETES_TARGET_PATH.resolve("hello.yaml").toFile();
        List<HasMetadata> k8sItems = KubernetesTestUtils.loadYaml(k8sYaml);
        Deployment deployment = null;
        for (HasMetadata data : k8sItems) {
            if ("Deployment".equals(data.getKind())) {
                deployment = (Deployment) data;
            }
        }
        assert deployment != null;
        Assert.assertEquals(deployment.getMetadata().getName(), "hello-deployment");
        Assert.assertEquals(deployment.getSpec().getTemplate().getSpec().getContainers().size(), 1);

        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        Assert.assertEquals(container.getImage(), DOCKER_IMAGE);
        Assert.assertEquals(container.getEnv().size(), 1);
        final EnvVar envVar = container.getEnv().get(0);
        Assert.assertEquals(envVar.getName(), "b7a_log_level");
        Assert.assertEquals(envVar.getValueFrom().getConfigMapKeyRef().getName(), "cm-loglevel-linker");
        Assert.assertEquals(envVar.getValueFrom().getConfigMapKeyRef().getKey(), "B7A_LOG_LEVEL");
        Assert.assertEquals(container.getImagePullPolicy(), KubernetesConstants.ImagePullPolicy.IfNotPresent.name());
    }

    @AfterClass
    public void cleanUp() throws KubernetesPluginException {
        KubernetesUtils.deleteDirectory(KUBERNETES_TARGET_PATH);
        KubernetesUtils.deleteDirectory(DOCKER_TARGET_PATH);
        KubernetesTestUtils.deleteDockerImage(DOCKER_IMAGE);
    }
}
