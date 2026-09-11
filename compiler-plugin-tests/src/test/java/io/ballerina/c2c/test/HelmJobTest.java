/*
 * Copyright (c) 2026, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

import io.ballerina.c2c.KubernetesConstants;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.test.utils.KubernetesTestUtils;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Test cases for the {@code --cloud=helm} target against a {@code @cloud:Task} scheduled job
 * (i.e. a CronJob, not a Deployment/Service) -- a distinct code path from {@link HelmTest} with
 * no Service/HPA/replicas concept.
 */
public class HelmJobTest {

    private static final Path SOURCE_DIR_PATH = Paths.get("src", "test", "resources", "helm-job");
    private static final Path CHART_PATH = SOURCE_DIR_PATH.resolve("target")
            .resolve(KubernetesConstants.HELM).resolve("hello");
    private static final String DOCKER_IMAGE = "anuruddhal/hello-api:helm-job-test";

    @BeforeClass
    public void compileSample() throws IOException, InterruptedException {
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaProject(SOURCE_DIR_PATH), 0);
    }

    @Test
    public void testChartStructure() {
        Assert.assertTrue(Files.isRegularFile(CHART_PATH.resolve("Chart.yaml")));
        Assert.assertTrue(Files.isRegularFile(CHART_PATH.resolve("values.yaml")));
        Assert.assertTrue(Files.isRegularFile(CHART_PATH.resolve("templates").resolve("_helpers.tpl")));
        Assert.assertTrue(Files.isRegularFile(CHART_PATH.resolve("templates").resolve("cronjob.yaml")),
                "a scheduled @cloud:Task should render templates/cronjob.yaml, not job.yaml");
        Assert.assertTrue(Files.isRegularFile(CHART_PATH.resolve("templates").resolve("serviceaccount.yaml")));
        // A job-based chart has no Service/HPA/replicas concept.
        Assert.assertFalse(Files.exists(CHART_PATH.resolve("templates").resolve("service.yaml")));
        Assert.assertFalse(Files.exists(CHART_PATH.resolve("templates").resolve("hpa.yaml")));
    }

    @Test(dependsOnMethods = "testChartStructure")
    public void testValuesYamlDefaults() throws IOException {
        Map<String, Object> values = new Yaml().load(Files.readString(CHART_PATH.resolve("values.yaml")));
        Map<?, ?> image = (Map<?, ?>) values.get("image");
        Assert.assertEquals(image.get("repository"), "anuruddhal/hello-api");
        Assert.assertEquals(image.get("tag"), "helm-job-test");
        Assert.assertFalse(values.containsKey("replicaCount"));
        Assert.assertFalse(values.containsKey("autoscaling"));
    }

    @Test(dependsOnMethods = "testChartStructure")
    public void testHelmLint() throws IOException, InterruptedException {
        Assert.assertEquals(KubernetesTestUtils.helmLint(CHART_PATH), 0, "helm lint reported issues");
    }

    @Test(dependsOnMethods = "testChartStructure")
    public void testHelmTemplateRendersValidCronJob() throws IOException, InterruptedException {
        String rendered = KubernetesTestUtils.helmTemplate(CHART_PATH);
        List<HasMetadata> resources;
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            resources = client.load(new ByteArrayInputStream(rendered.getBytes(StandardCharsets.UTF_8))).items();
        }

        CronJob cronJob = (CronJob) resources.stream()
                .filter(r -> "CronJob".equals(r.getKind()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No CronJob found in rendered chart"));
        Assert.assertEquals(cronJob.getSpec().getSchedule(), "* * * * *");
        Assert.assertEquals(cronJob.getMetadata().getNamespace(), "default");
        Assert.assertEquals(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getSpec()
                .getContainers().get(0).getImage(), "anuruddhal/hello-api:helm-job-test");
        Assert.assertEquals(cronJob.getSpec().getJobTemplate().getSpec().getTemplate().getSpec()
                .getRestartPolicy(), "OnFailure");

        // Overriding the image tag at render time should not require rebuilding the image.
        String overridden = KubernetesTestUtils.helmTemplate(CHART_PATH, "--set", "image.tag=v2");
        Assert.assertTrue(overridden.contains("anuruddhal/hello-api:v2"));
    }

    @AfterClass
    public void cleanUp() throws KubernetesPluginException {
        KubernetesUtils.deleteDirectory(SOURCE_DIR_PATH.resolve("target"));
        KubernetesTestUtils.deleteDockerImage(DOCKER_IMAGE);
    }
}
