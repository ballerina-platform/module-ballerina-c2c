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

package io.ballerina.c2c.test.samples;

import io.ballerina.c2c.KubernetesConstants;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.test.utils.KubernetesTestUtils;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static io.ballerina.c2c.KubernetesConstants.DOCKER;
import static io.ballerina.c2c.KubernetesConstants.KUBERNETES;
import static io.ballerina.c2c.test.utils.KubernetesTestUtils.deployK8s;
import static io.ballerina.c2c.test.utils.KubernetesTestUtils.getCommand;
import static io.ballerina.c2c.test.utils.KubernetesTestUtils.getExposedPorts;
import static io.ballerina.c2c.test.utils.KubernetesTestUtils.loadImage;

/**
 * Test cases for sample 2.
 */
public class Sample2Test extends SampleTest {

    private static final Path SOURCE_DIR_PATH = SAMPLE_DIR.resolve("kubernetes-yaml-with-ballerina-project");
    private static final Path DOCKER_TARGET_PATH =
            SOURCE_DIR_PATH.resolve("target").resolve(DOCKER).resolve("hello");
    private static final Path KUBERNETES_TARGET_PATH =
            SOURCE_DIR_PATH.resolve("target").resolve(KUBERNETES).resolve("hello");
    private static final String DOCKER_IMAGE = "anuruddhal/hello-api:sample2";
    private static final Path INGRESS_PATH =
            Paths.get("src", "test", "resources", "sample2");
    private Deployment deployment;
    private Service service;

    @BeforeClass
    public void compileSample() throws IOException, InterruptedException {
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaProject(SOURCE_DIR_PATH)
                , 0);
        File artifactYaml = KUBERNETES_TARGET_PATH.resolve("hello.yaml").toFile();
        Assert.assertTrue(artifactYaml.exists());
        KubernetesClient client = new DefaultKubernetesClient();
        List<HasMetadata> k8sItems = client.load(new FileInputStream(artifactYaml)).get();
        for (HasMetadata data : k8sItems) {
            switch (data.getKind()) {
                case "Deployment":
                    deployment = (Deployment) data;
                    break;
                case "Service":
                    service = (Service) data;
                    break;
                case "Secret":
                case "HorizontalPodAutoscaler":
                    break;
                default:
                    Assert.fail("Unexpected k8s resource found: " + data.getKind());
                    break;
            }
        }
    }

    @Test
    public void validateDeployment() {
        Assert.assertNotNull(deployment);
        Assert.assertEquals(deployment.getMetadata().getName(), "hello-deployment");
        Assert.assertEquals(deployment.getSpec().getReplicas().intValue(), 1);
        Assert.assertEquals(deployment.getMetadata().getLabels().get(KubernetesConstants
                .KUBERNETES_SELECTOR_KEY), "hello");
        Assert.assertEquals(deployment.getSpec().getTemplate().getSpec().getContainers().size(), 1);

        // Assert Containers
        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        Assert.assertEquals(container.getImage(), DOCKER_IMAGE);
        Assert.assertEquals(container.getPorts().size(), 1);
    }

    @Test
    public void validateService() {
        Assert.assertNotNull(service);
        Assert.assertEquals(1, service.getMetadata().getLabels().size());
        Assert.assertEquals("hello-svc", service.getMetadata().getName());
        Assert.assertEquals("ClusterIP", service.getSpec().getType());
        Assert.assertEquals(1, service.getSpec().getPorts().size());
        Assert.assertEquals(9090, service.getSpec().getPorts().get(0).getPort().intValue());
        Assert.assertEquals(9090, service.getSpec().getPorts().get(0).getTargetPort().getIntVal().intValue());
        Assert.assertEquals("TCP", service.getSpec().getPorts().get(0).getProtocol());
        Assert.assertEquals("port-1-hello-sv", service.getSpec().getPorts().get(0).getName());
    }

    @Test
    public void validateDockerfile() {
        File dockerFile = DOCKER_TARGET_PATH.resolve("Dockerfile").toFile();
        Assert.assertTrue(dockerFile.exists());
    }

    @Test
    public void validateDockerImage() {
        List<String> ports = getExposedPorts(DOCKER_IMAGE);
        Assert.assertEquals(ports.size(), 1);
        Assert.assertEquals(ports.get(0), "9090/tcp");
        // Validate ballerina.conf in run command
        Assert.assertEquals(getCommand(DOCKER_IMAGE).toString(), "[/bin/sh, -c, java -Xdiag " +
                "-cp \"hello-hello-0.0.1.jar:jars/*\" 'hello/hello/0/$_init']");
    }

    @Test(groups = {"integration"})
    public void deploySample() throws IOException, InterruptedException {
        Assert.assertEquals(0, loadImage(DOCKER_IMAGE));
        Assert.assertEquals(0, deployK8s(KUBERNETES_TARGET_PATH));
        Assert.assertEquals(0, deployK8s(INGRESS_PATH));
        Assert.assertTrue(KubernetesTestUtils.validateService(
                "http://c2c.deployment.test/helloWorld/sayHello",
                "Hello, World from service helloWorld ! \n"));
        KubernetesTestUtils.deleteK8s(KUBERNETES_TARGET_PATH);
        KubernetesTestUtils.deleteK8s(INGRESS_PATH);
    }

    @AfterClass
    public void cleanUp() throws KubernetesPluginException, IOException, InterruptedException {
        KubernetesUtils.deleteDirectory(KUBERNETES_TARGET_PATH);
        KubernetesUtils.deleteDirectory(DOCKER_TARGET_PATH);
        KubernetesTestUtils.deleteDockerImage(DOCKER_IMAGE);
    }
}
