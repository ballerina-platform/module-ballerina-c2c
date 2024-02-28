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
import io.ballerina.c2c.handlers.ChoreoHandler;
import io.ballerina.c2c.handlers.ConfigMapHandler;
import io.ballerina.c2c.handlers.DeploymentHandler;
import io.ballerina.c2c.handlers.DockerHandler;
import io.ballerina.c2c.handlers.HPAHandler;
import io.ballerina.c2c.handlers.JobHandler;
import io.ballerina.c2c.handlers.SecretHandler;
import io.ballerina.c2c.handlers.ServiceHandler;
import io.ballerina.c2c.models.DeploymentModel;
import io.ballerina.c2c.models.DockerModel;
import io.ballerina.c2c.models.KubernetesContext;
import io.ballerina.c2c.models.KubernetesDataHolder;
import io.ballerina.c2c.models.ServiceModel;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generate and write artifacts to files.
 */
public class ArtifactManager {

    private static final Map<String, String> instructions = new LinkedHashMap<>();
    private static final PrintStream OUT = System.out;
    private final KubernetesDataHolder kubernetesDataHolder;

    public ArtifactManager() {
        this.kubernetesDataHolder = KubernetesContext.getInstance().getDataHolder();
    }

    /**
     * Generates artifacts according to the cloud parameter in build options.
     *
     * @param cloudType Value of cloud field in build option.
     * @throws KubernetesPluginException if an error occurs while generating artifacts
     */
    public void createArtifacts(String cloudType, boolean isNative) throws KubernetesPluginException {
        if (isNative) {
            this.kubernetesDataHolder.getDockerModel().setBaseImage(DockerGenConstants.NATIVE_RUNTIME_BASE_IMAGE);
        }

        if (cloudType.equals("k8s")) {
            createKubernetesArtifacts(isNative);
        } else if (cloudType.equals("docker")) {
            createDockerArtifacts(isNative);
        } else {
            createChoreoArtifacts(isNative);
        }
    }

    /**
     * Generate kubernetes artifacts.
     *
     * @throws KubernetesPluginException if an error occurs while generating artifacts
     */
    public void createKubernetesArtifacts(boolean isNative) throws KubernetesPluginException {
        // add default kubernetes instructions.
        setDefaultKubernetesInstructions();
        OUT.println("\nGenerating artifacts\n");
        if (kubernetesDataHolder.getJobModel() != null) {
            new CloudTomlResolver().resolveToml(kubernetesDataHolder.getJobModel());
            new ConfigMapHandler().createArtifacts();
            new SecretHandler().createArtifacts();
            new JobHandler().createArtifacts();
        } else {
            new CloudTomlResolver().resolveToml(kubernetesDataHolder.getDeploymentModel());
            new ServiceHandler().createArtifacts();
            new ConfigMapHandler().createArtifacts();
            new SecretHandler().createArtifacts();
            new DeploymentHandler().createArtifacts();
            new HPAHandler().createArtifacts();
        }
        new DockerHandler(isNative).createArtifacts();
        printInstructions();
    }

    public void createDockerArtifacts(boolean isNative) throws KubernetesPluginException {
        OUT.println("\nGenerating artifacts\n");
        DockerModel dockerModel = getDockerModel(false);
        kubernetesDataHolder.setDockerModel(dockerModel);
        new DockerHandler(isNative).createArtifacts();

        String dockerRunCommand = "docker run -d " + generatePortInstruction(dockerModel.getPorts())
                + dockerModel.getName();
        if (dockerModel.isTest()) { //if it is a test artifact, we also run the docker container
            OUT.println("\nRunning the generated Docker image\n");
            // Run the docker container and remove it after execution
            KubernetesUtils.runCommand("docker run --rm " + dockerModel.getName());
            // Delete the docker image
            KubernetesUtils.deleteDockerImage(dockerModel.getName());
        } else {
            instructions.put("Execute the below command to run the generated Docker image: ",
                    "\t" + dockerRunCommand);
            printInstructions();
        }
    }

    private DockerModel getDockerModel(boolean isTomlSkipped) throws KubernetesPluginException {
        List<ServiceModel> serviceModels = kubernetesDataHolder.getServiceModelList();
        DeploymentModel deploymentModel = kubernetesDataHolder.getDeploymentModel();
        for (ServiceModel serviceModel : serviceModels) {
            ContainerPort containerPort = new ContainerPortBuilder()
                    .withName(serviceModel.getPortName())
                    .withContainerPort(serviceModel.getTargetPort())
                    .withProtocol(KubernetesConstants.KUBERNETES_SVC_PROTOCOL)
                    .build();
            deploymentModel.addPort(containerPort);
        }
        if (!isTomlSkipped) {
            KubernetesUtils.resolveDockerToml(kubernetesDataHolder.getDeploymentModel());
        }
        return KubernetesUtils.getDockerModel(deploymentModel);
    }

    public void createChoreoArtifacts(boolean isNative) throws KubernetesPluginException {
        DockerModel dockerModel = getDockerModel(true);
        dockerModel.setBuildImage(false);
        kubernetesDataHolder.setDockerModel(dockerModel);
        new DockerHandler(isNative).createArtifacts();
        new ChoreoHandler().createArtifacts();
    }

    private String generatePortInstruction(Set<Integer> ports) {
        if (ports.size() == 0) {
            return "";
        }
        StringBuilder output = new StringBuilder();
        for (Integer port : ports) {
            output.append("-p ").append(port).append(":").append(port).append(" ");
        }
        return output.toString();
    }

    private void printInstructions() {
        for (Map.Entry<String, String> instruction : instructions.entrySet()) {
            KubernetesUtils.printInstruction(instruction.getKey());
            KubernetesUtils.printInstruction(instruction.getValue());
            KubernetesUtils.printInstruction("");
        }
    }

    public void populateDeploymentModel() {
        DeploymentModel deploymentModel = kubernetesDataHolder.getDeploymentModel();
        String deploymentName = kubernetesDataHolder.getOutputName();
        if (KubernetesUtils.isBlank(deploymentModel.getName())) {
            if (deploymentName != null) {
                deploymentModel.setName(KubernetesUtils.getValidName(deploymentName)
                        + KubernetesConstants.DEPLOYMENT_POSTFIX);
            }
        }
        if (KubernetesUtils.isBlank(deploymentModel.getImage())) {
            deploymentModel.setImage(deploymentName + KubernetesConstants.DOCKER_LATEST_TAG);
        }
        deploymentModel.addLabel(KubernetesConstants.KUBERNETES_SELECTOR_KEY, deploymentName);
        kubernetesDataHolder.setDeploymentModel(deploymentModel);
    }

    /**
     * Set instructions for kubernetes and helm artifacts.
     */
    private void setDefaultKubernetesInstructions() {
        instructions.put("Execute the below command to deploy the Kubernetes artifacts: ",
                "\tkubectl apply -f " + this.kubernetesDataHolder.getK8sArtifactOutputPath().toAbsolutePath());
        if (!kubernetesDataHolder.getServiceModelList().isEmpty()) {
            instructions.put("Execute the below command to access service via NodePort: ",
                    "\tkubectl expose deployment " + this.kubernetesDataHolder.getDeploymentModel().getName() +
                            " --type=NodePort --name=" + kubernetesDataHolder.getDeploymentModel().getName()
                            .replace(KubernetesConstants.DEPLOYMENT_POSTFIX, "-svc-local"));
        }
    }
}
