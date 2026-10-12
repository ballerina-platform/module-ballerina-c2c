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
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
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
 * Test cases for the {@code --cloud=helm} target: verifies the generated chart's structure and
 * that {@code helm lint}/{@code helm template} render it into exactly what {@code --cloud=k8s}
 * would have deployed for the same fixture, including with values overridden at render time.
 */
public class HelmTest {

    private static final Path SOURCE_DIR_PATH = Paths.get("src", "test", "resources", "helm");
    private static final Path CHART_PATH = SOURCE_DIR_PATH.resolve("target")
            .resolve(KubernetesConstants.HELM).resolve("hello");
    private static final String DOCKER_IMAGE = "anuruddhal/hello-api:helm-test";

    @BeforeClass
    public void compileSample() throws IOException, InterruptedException {
        Assert.assertEquals(KubernetesTestUtils.compileBallerinaProject(SOURCE_DIR_PATH), 0);
    }

    @Test
    public void testChartStructure() {
        Assert.assertTrue(Files.isRegularFile(CHART_PATH.resolve("Chart.yaml")));
        Assert.assertTrue(Files.isRegularFile(CHART_PATH.resolve("values.yaml")));
        Assert.assertTrue(Files.isRegularFile(CHART_PATH.resolve(".helmignore")));
        Assert.assertTrue(Files.isRegularFile(CHART_PATH.resolve("templates").resolve("_helpers.tpl")));
        Assert.assertTrue(Files.isRegularFile(CHART_PATH.resolve("templates").resolve("deployment.yaml")));
        Assert.assertTrue(Files.isRegularFile(CHART_PATH.resolve("templates").resolve("service.yaml")));
        Assert.assertTrue(Files.isRegularFile(CHART_PATH.resolve("templates").resolve("hpa.yaml")));
        Assert.assertTrue(Files.isRegularFile(
                CHART_PATH.resolve("templates").resolve("poddisruptionbudget.yaml")));
        Assert.assertTrue(Files.isRegularFile(CHART_PATH.resolve("templates").resolve("serviceaccount.yaml")));
        Assert.assertTrue(Files.isRegularFile(
                CHART_PATH.resolve("templates").resolve("configmap-hello-config-map.yaml")));
        Assert.assertTrue(Files.isRegularFile(
                CHART_PATH.resolve("files").resolve("hello-config-map").resolve("Config.toml")));
    }

    @Test(dependsOnMethods = "testChartStructure")
    public void testChartYaml() throws IOException {
        Map<String, Object> chart = new Yaml().load(Files.readString(CHART_PATH.resolve("Chart.yaml")));
        Assert.assertEquals(chart.get("apiVersion"), "v2");
        Assert.assertEquals(chart.get("name"), "hello");
        Assert.assertEquals(chart.get("version"), "0.0.1");
    }

    @Test(dependsOnMethods = "testChartStructure")
    public void testValuesYamlDefaults() throws IOException {
        Map<String, Object> values = new Yaml().load(Files.readString(CHART_PATH.resolve("values.yaml")));
        Assert.assertEquals(values.get("replicaCount"), 1);
        Map<?, ?> image = (Map<?, ?>) values.get("image");
        Assert.assertEquals(image.get("repository"), "anuruddhal/hello-api");
        Assert.assertEquals(image.get("tag"), "helm-test");
        Map<?, ?> service = (Map<?, ?>) values.get("service");
        Assert.assertEquals(service.get("type"), "ClusterIP");
        Map<?, ?> autoscaling = (Map<?, ?>) values.get("autoscaling");
        Assert.assertEquals(autoscaling.get("enabled"), Boolean.TRUE);
        Assert.assertEquals(autoscaling.get("minReplicas"), 2);
        Assert.assertEquals(autoscaling.get("maxReplicas"), 4);
        Assert.assertEquals(autoscaling.get("targetCPUUtilizationPercentage"), 60);
        Map<?, ?> pdb = (Map<?, ?>) values.get("podDisruptionBudget");
        Assert.assertEquals(pdb.get("enabled"), Boolean.TRUE);
        Assert.assertEquals(pdb.get("maxUnavailable"), 1);
    }

    @Test(dependsOnMethods = "testChartStructure")
    public void testHelmLint() throws IOException, InterruptedException {
        Assert.assertEquals(KubernetesTestUtils.helmLint(CHART_PATH), 0, "helm lint reported issues");
    }

    @Test(dependsOnMethods = "testChartStructure")
    public void testHelmTemplateDefaultValuesMatchesK8sTarget() throws IOException, InterruptedException {
        String rendered = KubernetesTestUtils.helmTemplate(CHART_PATH);
        List<HasMetadata> resources = loadResources(rendered);

        Deployment deployment = (Deployment) findByKind(resources, "Deployment");
        Assert.assertEquals(deployment.getSpec().getReplicas().intValue(), 1);
        Assert.assertEquals(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage(),
                "anuruddhal/hello-api:helm-test");
        Assert.assertEquals(deployment.getMetadata().getNamespace(), "default");
        Assert.assertEquals(deployment.getSpec().getSelector().getMatchLabels().get("app"), "hello",
                "the plain 'app' selector label should be present alongside app.kubernetes.io/*, "
                        + "for compatibility with externally-authored selectors");

        Service service = (Service) findByKind(resources, "Service");
        Assert.assertEquals(service.getSpec().getType(), "ClusterIP");
        Assert.assertEquals(service.getSpec().getPorts().get(0).getPort().intValue(), 9090);
        Assert.assertEquals(service.getSpec().getSelector().get("app"), "hello");

        HorizontalPodAutoscaler hpa = (HorizontalPodAutoscaler) findByKind(resources, "HorizontalPodAutoscaler");
        Assert.assertEquals(hpa.getSpec().getMinReplicas().intValue(), 2);
        Assert.assertEquals(hpa.getSpec().getMaxReplicas().intValue(), 4);

        PodDisruptionBudget pdb = (PodDisruptionBudget) findByKind(resources, "PodDisruptionBudget");
        Assert.assertEquals(pdb.getSpec().getMaxUnavailable().getIntVal().intValue(), 1);
        Assert.assertEquals(pdb.getSpec().getSelector().getMatchLabels().get("app"), "hello");
    }

    @Test(dependsOnMethods = "testChartStructure")
    public void testPodDisruptionBudgetCanBeDisabled() throws IOException, InterruptedException {
        String rendered = KubernetesTestUtils.helmTemplate(CHART_PATH, "--set", "podDisruptionBudget.enabled=false");
        List<HasMetadata> resources = loadResources(rendered);
        Assert.assertTrue(resources.stream().noneMatch(r -> "PodDisruptionBudget".equals(r.getKind())),
                "podDisruptionBudget.enabled=false should omit the PodDisruptionBudget");
    }

    @Test(dependsOnMethods = "testChartStructure")
    public void testHelmTemplateValuesOverrideTakesEffect() throws IOException, InterruptedException {
        String rendered = KubernetesTestUtils.helmTemplate(CHART_PATH,
                "--set", "replicaCount=5",
                "--set", "image.tag=overridden-tag",
                "--set", "autoscaling.enabled=false");
        List<HasMetadata> resources = loadResources(rendered);

        Deployment deployment = (Deployment) findByKind(resources, "Deployment");
        Assert.assertEquals(deployment.getSpec().getReplicas().intValue(), 5);
        Assert.assertEquals(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage(),
                "anuruddhal/hello-api:overridden-tag");

        Assert.assertTrue(resources.stream().noneMatch(r -> "HorizontalPodAutoscaler".equals(r.getKind())),
                "autoscaling.enabled=false should omit the HPA");
    }

    @Test(dependsOnMethods = "testChartStructure")
    public void testConfigMapValuesDrivenConfig() throws IOException, InterruptedException {
        String defaultRendered = KubernetesTestUtils.helmTemplate(CHART_PATH);
        Assert.assertTrue(defaultRendered.contains("localhost"),
                "Config.toml's {{ .Values.dbHost | default \"localhost\" }} should resolve to the default");

        String overridden = KubernetesTestUtils.helmTemplate(CHART_PATH, "--set", "dbHost=prod-db.internal");
        Assert.assertTrue(overridden.contains("prod-db.internal"),
                "overriding dbHost should flow into the rendered ConfigMap's Config.toml content");
    }

    private static List<HasMetadata> loadResources(String renderedYaml) {
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            return client.load(new ByteArrayInputStream(renderedYaml.getBytes(StandardCharsets.UTF_8))).items();
        }
    }

    private static HasMetadata findByKind(List<HasMetadata> resources, String kind) {
        return resources.stream()
                .filter(r -> kind.equals(r.getKind()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + kind + " found in rendered chart"));
    }

    @AfterClass
    public void cleanUp() throws KubernetesPluginException {
        KubernetesUtils.deleteDirectory(SOURCE_DIR_PATH.resolve("target"));
        KubernetesTestUtils.deleteDockerImage(DOCKER_IMAGE);
    }
}
