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

package io.ballerina.c2c.test;

import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.test.utils.KubernetesTestUtils;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
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
import static io.ballerina.c2c.test.utils.KubernetesTestUtils.getEntryPoint;

/**
 * Test cases for Multiple Config files.
 */
public class MixedConfigJobTest {

    private static final Path SOURCE_DIR_PATH = Paths.get("src", "test", "resources", "mix-config-secret-map-job");
    private static final Path DOCKER_TARGET_PATH =
            SOURCE_DIR_PATH.resolve("target").resolve(DOCKER).resolve("mix_configs_job");
    private static final Path KUBERNETES_TARGET_PATH =
            SOURCE_DIR_PATH.resolve("target").resolve(KUBERNETES).resolve("mix_configs_job");
    private static final String DOCKER_IMAGE = "xlight05/complete-config-job:latest";
    private Job job;
    private ConfigMap ballerinaConf;
    private ConfigMap extraConf;

    private Secret mysqlSecret;
    private Secret extraSecret;

    @BeforeClass
    public void compileSample() throws IOException, InterruptedException {
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaProject(SOURCE_DIR_PATH), 0);
        File artifactYaml = KUBERNETES_TARGET_PATH.resolve("mix_configs_job.yaml").toFile();
        Assert.assertTrue(artifactYaml.exists());
        KubernetesClient client = new KubernetesClientBuilder().build();
        List<HasMetadata> k8sItems = client.load(new FileInputStream(artifactYaml)).items();
        for (HasMetadata data : k8sItems) {
            switch (data.getKind()) {
                case "Job":
                    job = (Job) data;
                    break;
                case "ConfigMap":
                    switch (data.getMetadata().getName()) {
                        case "config-config-map":
                            ballerinaConf = (ConfigMap) data;
                            break;
                        case "extra-config-map":
                            extraConf = (ConfigMap) data;
                            break;
                        default:
                            break;
                    }
                    break;
                case "Secret":
                    switch (data.getMetadata().getName()) {
                        case "config-secret":
                            mysqlSecret = (Secret) data;
                            break;
                        case "extra-secrets":
                            extraSecret = (Secret) data;
                            break;
                        default:
                            break;
                    }
                    break;
                default:
                    Assert.fail("Unexpected k8s resource found: " + data.getKind());
                    break;
            }
        }
    }

    @Test
    public void validateDeployment() {
        Assert.assertNotNull(job);
        Assert.assertEquals(job.getMetadata().getName(), "anjana-mix-conf-job");
        Assert.assertEquals(job.getSpec().getTemplate().getSpec().getVolumes().size(), 4);
        Assert.assertEquals(job.getSpec().getTemplate().getSpec().getContainers().size(), 1);

        // Assert Containers
        Container container = job.getSpec().getTemplate().getSpec().getContainers().get(0);
        Assert.assertEquals(container.getVolumeMounts().size(), 4);
        Assert.assertEquals(container.getImage(), DOCKER_IMAGE);
        Assert.assertEquals(container.getEnv().size(), 1);

        // Validate config file
        Assert.assertEquals(container.getEnv().get(0).getName(), "BAL_CONFIG_FILES");
        Assert.assertEquals(container.getEnv().get(0).getValue(),
                "/home/ballerina/conf/Config.toml:/home/ballerina/conf1/Config1.toml:" +
                        "/home/ballerina/secrets/mysql-secrets.toml:/home/ballerina/secrets1/additional-secrets.toml:");
    }

    @Test
    public void validateConfigMap() {
        // Assert ballerina.conf config map
        Assert.assertNotNull(ballerinaConf);
        Assert.assertEquals(1, ballerinaConf.getData().size());
        Assert.assertTrue(ballerinaConf.getData().containsKey("Config.toml"));

        Assert.assertNotNull(extraConf);
        Assert.assertEquals(1, extraConf.getData().size());
        Assert.assertTrue(extraConf.getData().containsKey("Config1.toml"));
    }

    @Test
    public void validateSecretConfig() {
        // Assert ballerina.conf config map
        Assert.assertNotNull(extraSecret);
        Assert.assertEquals(1, extraSecret.getData().size());
        Assert.assertTrue(extraSecret.getData().containsKey("additional-secrets.toml"));

        Assert.assertNotNull(mysqlSecret);
        Assert.assertEquals(1, mysqlSecret.getData().size());
        Assert.assertTrue(mysqlSecret.getData().containsKey("mysql-secrets.toml"));
    }

    @Test
    public void validateDockerfile() {
        File dockerFile = DOCKER_TARGET_PATH.resolve("Dockerfile").toFile();
        Assert.assertTrue(dockerFile.exists());
    }

    @Test
    public void validateDockerImage() {
        Assert.assertEquals(getEntryPoint(DOCKER_IMAGE).toString(), "[java, -XX:+ExitOnOutOfMemoryError, " +
                "-Xdiag, -cp, anjana-mix_configs_job-0.1.0.jar:jars/*, anjana.mix_configs_job.0.$_init]");
    }

    @AfterClass
    public void cleanUp() throws KubernetesPluginException {
        KubernetesUtils.deleteDirectory(KUBERNETES_TARGET_PATH);
        KubernetesUtils.deleteDirectory(DOCKER_TARGET_PATH);
        KubernetesTestUtils.deleteDockerImage(DOCKER_IMAGE);
    }
}
