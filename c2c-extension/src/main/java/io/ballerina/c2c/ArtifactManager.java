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

package io.ballerina.c2c;

import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.handlers.DeploymentHandler;
import io.ballerina.c2c.handlers.DockerHandler;
import io.ballerina.c2c.handlers.HPAHandler;
import io.ballerina.c2c.handlers.JobHandler;
import io.ballerina.c2c.handlers.PersistentVolumeClaimHandler;
import io.ballerina.c2c.handlers.SecretHandler;
import io.ballerina.c2c.handlers.ServiceHandler;
import io.ballerina.c2c.models.DeploymentModel;
import io.ballerina.c2c.models.KubernetesContext;
import io.ballerina.c2c.models.KubernetesDataHolder;
import io.ballerina.c2c.utils.KubernetesUtils;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.ballerinax.docker.generator.utils.DockerGenUtils.extractJarName;

/**
 * Generate and write artifacts to files.
 */
public class ArtifactManager {
    private static final Map<String, String> instructions = new LinkedHashMap<>();
    private static final PrintStream OUT = System.out;
    private KubernetesDataHolder kubernetesDataHolder;

    ArtifactManager() {
        this.kubernetesDataHolder = KubernetesContext.getInstance().getDataHolder();
    }

    /**
     * Returns print instructions.
     *
     * @return instructions.
     */
    public static Map<String, String> getInstructions() {
        return instructions;
    }

    /**
     * Generate kubernetes artifacts.
     *
     * @throws KubernetesPluginException if an error occurs while generating artifacts
     */
    void createArtifacts() throws KubernetesPluginException {
        // add default kubernetes instructions.
        setDefaultKubernetesInstructions();
        OUT.println("\nGenerating artifacts...");
        if (kubernetesDataHolder.getJobModel() != null) {
            new JobHandler().createArtifacts();
            new DockerHandler().createArtifacts();
        } else {
            new ServiceHandler().createArtifacts();
            new SecretHandler().createArtifacts();
            new PersistentVolumeClaimHandler().createArtifacts();
            new DeploymentHandler().createArtifacts();
            new HPAHandler().createArtifacts();
            new DockerHandler().createArtifacts();
        }

        printInstructions();
    }

    private void printInstructions() {
        KubernetesUtils.printInstruction("");
        KubernetesUtils.printInstruction("");
        for (Map.Entry<String, String> instruction : instructions.entrySet()) {
            KubernetesUtils.printInstruction(instruction.getKey());
            KubernetesUtils.printInstruction(instruction.getValue());
            KubernetesUtils.printInstruction("");
        }
    }

    public void populateDeploymentModel() {
        DeploymentModel deploymentModel = kubernetesDataHolder.getDeploymentModel();
        kubernetesDataHolder.setDeploymentModel(deploymentModel);
        String balxFileName = extractJarName(kubernetesDataHolder.getJarPath());
        if (KubernetesUtils.isBlank(deploymentModel.getName())) {
            if (balxFileName != null) {
                deploymentModel.setName(KubernetesUtils.getValidName(balxFileName)
                        + KubernetesConstants.DEPLOYMENT_POSTFIX);
            }
        }
        if (KubernetesUtils.isBlank(deploymentModel.getImage())) {
            deploymentModel.setImage(balxFileName + KubernetesConstants.DOCKER_LATEST_TAG);
        }
        deploymentModel.addLabel(KubernetesConstants.KUBERNETES_SELECTOR_KEY, balxFileName);
        kubernetesDataHolder.setDeploymentModel(deploymentModel);
    }

    /**
     * Set instructions for kubernetes and helm artifacts.
     */
    private void setDefaultKubernetesInstructions() {
        instructions.put("\tExecute the below command to deploy the Kubernetes artifacts: ",
                "\tkubectl apply -f " + this.kubernetesDataHolder.getK8sArtifactOutputPath().toAbsolutePath());

        instructions.put("\tExecute the below command to access service via NodePort: ",
                "\tkubectl expose deployment " + this.kubernetesDataHolder.getDeploymentModel().getName() + " --type" +
                        "=NodePort --name=" + kubernetesDataHolder.getDeploymentModel().getName()
                        .replace(KubernetesConstants.DEPLOYMENT_POSTFIX, "-svc-local"));
    }
}
