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
import io.ballerina.c2c.models.ConfigMapModel;
import io.ballerina.c2c.models.DeploymentModel;
import io.ballerina.c2c.models.PersistentVolumeClaimModel;
import io.ballerina.c2c.models.SecretModel;
import io.ballerina.c2c.util.C2CDiagnosticCodes;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Lifecycle;
import io.fabric8.kubernetes.api.model.LifecycleBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.ballerina.c2c.DockerGenConstants.REGISTRY_SEPARATOR;
import static io.ballerina.c2c.KubernetesConstants.DEPLOYMENT_FILE_POSTFIX;
import static io.ballerina.c2c.utils.KubernetesUtils.resolveDockerToml;

/**
 * Generates kubernetes deployment from annotations.
 */
public class DeploymentHandler extends AbstractArtifactHandler {

    private List<VolumeMount> populateVolumeMounts(DeploymentModel deploymentModel) {
        List<VolumeMount> volumeMounts = new ArrayList<>();
        for (SecretModel secretModel : deploymentModel.getSecretModels()) {
            VolumeMount volumeMount = new VolumeMountBuilder()
                    .withMountPath(secretModel.getMountPath())
                    .withName(secretModel.getName() + "-volume")
                    .withReadOnly(secretModel.isReadOnly())
                    .build();
            volumeMounts.add(volumeMount);
        }
        for (ConfigMapModel configMapModel : deploymentModel.getConfigMapModels()) {
            final String mountPath = configMapModel.getMountPath();
            if (mountPath != null) {
                VolumeMount volumeMount = new VolumeMountBuilder()
                        .withMountPath(mountPath)
                        .withName(configMapModel.getName() + "-volume")
                        .withReadOnly(configMapModel.isReadOnly())
                        .build();

                if (getExtension(mountPath).isPresent()) {
                    // Add file mount as sub paths.
                    final Path fileName = Paths.get(mountPath).getFileName();
                    if (null != fileName) {
                        volumeMount.setSubPath(fileName.toString());
                    }
                }
                volumeMounts.add(volumeMount);
            }
        }
        for (PersistentVolumeClaimModel volumeClaimModel : deploymentModel.getVolumeClaimModels()) {
            VolumeMount volumeMount = new VolumeMountBuilder()
                    .withMountPath(volumeClaimModel.getMountPath())
                    .withName(volumeClaimModel.getName() + "-volume")
                    .withReadOnly(volumeClaimModel.isReadOnly())
                    .build();
            volumeMounts.add(volumeMount);
        }
        return volumeMounts;
    }

    private Optional<String> getExtension(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));
    }

    private Container generateContainer(DeploymentModel deploymentModel, List<ContainerPort> containerPorts) {
        String dockerRegistry = deploymentModel.getRegistry();
        String deploymentImageName = deploymentModel.getImage();
        if (null != dockerRegistry && !"".equals(dockerRegistry)) {
            deploymentImageName = dockerRegistry + REGISTRY_SEPARATOR + deploymentImageName;
        }
        Lifecycle preStop = new LifecycleBuilder()
                .withNewPreStop()
                .withNewExec()
                .withCommand("sleep", "15")
                .endExec()
                .endPreStop()
                .build();

        return new ContainerBuilder()
                .withName(deploymentModel.getName())
                .withImage(deploymentImageName)
                .withPorts(containerPorts)
                .withEnv(deploymentModel.getEnvVars())
                .withVolumeMounts(populateVolumeMounts(deploymentModel))
                .withLivenessProbe(deploymentModel.getLivenessProbe())
                .withReadinessProbe(deploymentModel.getReadinessProbe())
                .withResources(deploymentModel.getResourceRequirements())
                .withLifecycle(preStop)
                .build();
    }

    private List<Volume> populateVolume(DeploymentModel deploymentModel) {
        List<Volume> volumes = new ArrayList<>();
        for (SecretModel secretModel : deploymentModel.getSecretModels()) {
            Volume volume = new VolumeBuilder()
                    .withName(secretModel.getName() + "-volume")
                    .withNewSecret()
                    .withSecretName(secretModel.getName())
                    .endSecret()
                    .build();

            if (secretModel.getDefaultMode() > 0) {
                volume.getSecret().setDefaultMode(secretModel.getDefaultMode());
            }
            volumes.add(volume);
        }
        for (ConfigMapModel configMapModel : deploymentModel.getConfigMapModels()) {
            Volume volume = new VolumeBuilder()
                    .withName(configMapModel.getName() + "-volume")
                    .withNewConfigMap()
                    .withName(configMapModel.getName())
                    .endConfigMap()
                    .build();

            if (configMapModel.getDefaultMode() > 0) {
                volume.getConfigMap().setDefaultMode(configMapModel.getDefaultMode());
            }
            volumes.add(volume);
        }
        for (PersistentVolumeClaimModel volumeClaimModel : deploymentModel.getVolumeClaimModels()) {
            Volume volume = new VolumeBuilder()
                    .withName(volumeClaimModel.getName() + "-volume")
                    .withNewPersistentVolumeClaim()
                    .withClaimName(volumeClaimModel.getName())
                    .endPersistentVolumeClaim()
                    .build();
            volumes.add(volume);
        }
        return volumes;
    }

    private List<LocalObjectReference> getImagePullSecrets(DeploymentModel deploymentModel) {
        List<LocalObjectReference> imagePullSecrets = new ArrayList<>();
        for (String imagePullSecret : deploymentModel.getImagePullSecrets()) {
            imagePullSecrets.add(new LocalObjectReferenceBuilder().withName(imagePullSecret).build());
        }
        return imagePullSecrets;
    }

    /**
     * Generate kubernetes deployment definition from annotation.
     *
     * @param deploymentModel @{@link DeploymentModel} definition
     * @throws KubernetesPluginException If an error occurs while generating artifact.
     */
    private void generate(DeploymentModel deploymentModel) throws KubernetesPluginException {
        List<ContainerPort> containerPorts = null;
        if (deploymentModel.getPorts() != null) {
            containerPorts = deploymentModel.getPorts();
        }
        Container container = generateContainer(deploymentModel, containerPorts);
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName(deploymentModel.getName())
                .withLabels(deploymentModel.getLabels())
                .withAnnotations(deploymentModel.getAnnotations())
                .withNamespace(dataHolder.getNamespace())
                .endMetadata()
                .withNewSpec()
                .withNewSelector()
                .withMatchLabels(deploymentModel.getLabels())
                .endSelector()
                .withStrategy(deploymentModel.getStrategy())
                .withReplicas(deploymentModel.getReplicas())
                .withNewTemplate()
                .withNewMetadata()
                .addToLabels(deploymentModel.getLabels())
                .addToAnnotations(deploymentModel.getPodAnnotations())
                .endMetadata()
                .withNewSpec()
                .withContainers(container)
                .withImagePullSecrets(getImagePullSecrets(deploymentModel))
                .withVolumes(populateVolume(deploymentModel))
                .withNodeSelector(deploymentModel.getNodeSelector())
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        try {
            String deploymentContent = KubernetesUtils.asYaml(deployment);
            String outputFileName = DEPLOYMENT_FILE_POSTFIX + KubernetesConstants.YAML;
            if (dataHolder.isSingleYaml()) {
                outputFileName = deployment.getMetadata().getName() + KubernetesConstants.YAML;
            }
            KubernetesUtils.writeToFile(deploymentContent, outputFileName);
        } catch (IOException e) {
            Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.ARTIFACT_GEN_FAILED,
                    new NullLocation(), "deployment" , deploymentModel.getName());
            throw new KubernetesPluginException(diagnostic);
        }
    }



    @Override
    public void createArtifacts() throws KubernetesPluginException {
        DeploymentModel deploymentModel = dataHolder.getDeploymentModel();
        deploymentModel.setPodAutoscalerModel(dataHolder.getPodAutoscalerModel());
        deploymentModel.setSecretModels(dataHolder.getSecretModelSet());
        deploymentModel.setConfigMapModels(dataHolder.getConfigMapModelSet());
        if (null != deploymentModel.getLivenessProbe() &&
                deploymentModel.getLivenessProbe().getHttpGet().getPort().getIntVal() == 0) {
            //set first port as liveness port
            if (deploymentModel.getPorts().size() == 0) {
                Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.INVALID_PROBE,
                        new NullLocation(), "liveness");
                throw new KubernetesPluginException(diagnostic);
            }
            deploymentModel.getLivenessProbe().getHttpGet().setPort(new
                    IntOrString(deploymentModel.getPorts().iterator().next().getContainerPort()));
        }

        if (null != deploymentModel.getReadinessProbe() &&
                deploymentModel.getReadinessProbe().getHttpGet().getPort().getIntVal() == 0) {
            //set first port as readiness port
            if (deploymentModel.getPorts().size() == 0) {
                Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.INVALID_PROBE,
                        new NullLocation(), "readiness");
                throw new KubernetesPluginException(diagnostic);
            }
            deploymentModel.getReadinessProbe().getHttpGet().setPort(new
                    IntOrString(deploymentModel.getPorts().iterator().next().getContainerPort()));
        }
        resolveDockerToml(deploymentModel);
        generate(deploymentModel);
        dataHolder.setDockerModel(KubernetesUtils.getDockerModel(deploymentModel));
        OUT.println("\t@kubernetes:Deployment");
    }
}

