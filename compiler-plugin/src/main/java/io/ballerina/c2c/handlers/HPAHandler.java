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

package io.ballerina.c2c.handlers;

import io.ballerina.c2c.KubernetesConstants;
import io.ballerina.c2c.diagnostics.NullLocation;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.models.DeploymentModel;
import io.ballerina.c2c.models.PodAutoscalerModel;
import io.ballerina.c2c.util.C2CDiagnosticCodes;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.ballerina.c2c.utils.TomlHelper;
import io.ballerina.toml.api.Toml;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v1.HorizontalPodAutoscalerBuilder;

import java.io.IOException;

/**
 * Generates kubernetes Horizontal Pod Autoscaler from annotations.
 */
public class HPAHandler extends AbstractArtifactHandler {

    private void generate(PodAutoscalerModel podAutoscalerModel) throws KubernetesPluginException {
        HorizontalPodAutoscaler horizontalPodAutoscaler = new HorizontalPodAutoscalerBuilder()
                .withNewMetadata()
                .withName(podAutoscalerModel.getName())
                .withNamespace(dataHolder.getNamespace())
                .withLabels(podAutoscalerModel.getLabels())
                .endMetadata()
                .withNewSpec()
                .withMaxReplicas(podAutoscalerModel.getMaxReplicas())
                .withMinReplicas(podAutoscalerModel.getMinReplicas())
                .withTargetCPUUtilizationPercentage(podAutoscalerModel.getCpuPercentage())
                .withNewScaleTargetRef("apps/v1", "Deployment", podAutoscalerModel.getDeployment())
                .endSpec()
                .build();
        try {
            String hpaContent = KubernetesUtils.asYaml(horizontalPodAutoscaler);
            String outputFileName = KubernetesConstants.HPA_FILE_POSTFIX + KubernetesConstants.YAML;
            if (dataHolder.isSingleYaml()) {
                outputFileName = horizontalPodAutoscaler.getMetadata().getName() + KubernetesConstants.YAML;
            }
            KubernetesUtils.writeToFile(hpaContent, outputFileName);
        } catch (IOException e) {
            Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.ARTIFACT_GEN_FAILED,
                    new NullLocation(), "autoscaler" , podAutoscalerModel.getName());
            throw new KubernetesPluginException(diagnostic);
        }
    }


    private void resolveToml(PodAutoscalerModel hpa) {
        Toml ballerinaCloud = dataHolder.getBallerinaCloud();
        if (ballerinaCloud != null) {
            final String autoscaling = "cloud.deployment.autoscaling.";
            hpa.setMaxReplicas(Math.toIntExact(TomlHelper.getLong(ballerinaCloud, autoscaling + "max_replicas",
                    (long) hpa.getMaxReplicas())));
            hpa.setMinReplicas(Math.toIntExact(TomlHelper.getLong(ballerinaCloud, autoscaling + "min_replicas",
                    (long) hpa.getMinReplicas())));
            hpa.setCpuPercentage(Math.toIntExact(TomlHelper.getLong(ballerinaCloud, autoscaling + "cpu",
                    (long) hpa.getCpuPercentage())));
        }
    }

    @Override
    public void createArtifacts() throws KubernetesPluginException {
        DeploymentModel deploymentModel = dataHolder.getDeploymentModel();
        PodAutoscalerModel podAutoscalerModel = deploymentModel.getPodAutoscalerModel();
        if (!isHPAEnabled(podAutoscalerModel)) {
            return;
        }
        String balxFileName = dataHolder.getOutputName();
        podAutoscalerModel.addLabel(KubernetesConstants.KUBERNETES_SELECTOR_KEY, balxFileName);
        podAutoscalerModel.setDeployment(deploymentModel.getName());
        if (podAutoscalerModel.getMaxReplicas() == 0) {
            podAutoscalerModel.setMaxReplicas(deploymentModel.getReplicas() + 1);
        }
        if (podAutoscalerModel.getMinReplicas() == 0) {
            podAutoscalerModel.setMinReplicas(deploymentModel.getReplicas());
        }
        if (podAutoscalerModel.getName() == null || podAutoscalerModel.getName().length() == 0) {
            podAutoscalerModel.setName(KubernetesUtils.getValidName(balxFileName + KubernetesConstants.HPA_POSTFIX));
        }
        resolveToml(podAutoscalerModel);
        generate(podAutoscalerModel);
        OUT.println("\t@kubernetes:HPA");
    }

    private boolean isHPAEnabled(PodAutoscalerModel podAutoscalerModel) {
        if (podAutoscalerModel == null) {
            return false;
        }
        Toml ballerinaCloud = dataHolder.getBallerinaCloud();
        if (ballerinaCloud == null) {
            return true; //since the default is hpa enabled
        }
        return TomlHelper.getBoolean(ballerinaCloud, "cloud.deployment.autoscaling.enable", true);
    }
}
