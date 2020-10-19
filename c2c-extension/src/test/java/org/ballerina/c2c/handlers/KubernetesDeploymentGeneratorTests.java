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

package org.ballerina.c2c.handlers;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.ballerina.c2c.KubernetesConstants;
import org.ballerina.c2c.exceptions.KubernetesPluginException;
import org.ballerina.c2c.models.DeploymentModel;
import org.ballerina.c2c.utils.Utils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Test deployment generation.
 */
public class KubernetesDeploymentGeneratorTests extends HandlerTestSuite {

    private final String deploymentName = "MyDeployment";
    private final String selector = "hello";
    private final String imageName = "SampleImage:v1.0.0";
    private final String imagePullPolicy = "Always";
    private final int replicas = 5;

    @Test
    public void testDeploymentGeneration() {
        DeploymentModel deploymentModel = new DeploymentModel();
        deploymentModel.setName(deploymentName);
        Map<String, String> labels = new HashMap<>();
        labels.put(KubernetesConstants.KUBERNETES_SELECTOR_KEY, selector);
        deploymentModel.addPort(new ContainerPortBuilder().withContainerPort(9090).build());
        deploymentModel.addPort(new ContainerPortBuilder().withContainerPort(9091).build());
        deploymentModel.addPort(new ContainerPortBuilder().withContainerPort(9092).build());
        deploymentModel.setLabels(labels);
        deploymentModel.setImage(imageName);
        deploymentModel.setImagePullPolicy(imagePullPolicy);
        deploymentModel.setSingleYAML(false);
        EnvVar envVar = new EnvVarBuilder()
                .withName("ENV_VAR")
                .withValue("ENV")
                .build();
        deploymentModel.addEnv(envVar);
        deploymentModel.setReplicas(replicas);
        dataHolder.setDeploymentModel(deploymentModel);
        try {
            new DeploymentHandler().createArtifacts();
            File tempFile = dataHolder.getK8sArtifactOutputPath().resolve("hello_deployment.yaml").toFile();
            Assert.assertTrue(tempFile.exists());
            testGeneratedYAML(tempFile);
            tempFile.deleteOnExit();
        } catch (IOException e) {
            Assert.fail("Unable to write to file");
        } catch (KubernetesPluginException e) {
            Assert.fail("Unable to generate yaml from service");
        }
    }

    private void testGeneratedYAML(File yamlFile) throws IOException {
        Deployment deployment = Utils.loadYaml(yamlFile);
        Assert.assertEquals(deploymentName, deployment.getMetadata().getName());
        Assert.assertEquals(selector, deployment.getMetadata().getLabels().get(KubernetesConstants
                .KUBERNETES_SELECTOR_KEY));
        Assert.assertEquals(replicas, deployment.getSpec().getReplicas().intValue());

        Assert.assertEquals(1, deployment.getSpec().getTemplate().getSpec().getContainers().size());
        Container container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        Assert.assertEquals(imageName, container.getImage());
        Assert.assertEquals(imagePullPolicy, container.getImagePullPolicy());
        Assert.assertEquals(3, container.getPorts().size());
        Assert.assertEquals(1, container.getEnv().size());
    }
}
