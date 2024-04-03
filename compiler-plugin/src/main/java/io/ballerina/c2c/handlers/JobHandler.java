/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.ballerina.c2c.exceptions.DockerGenException;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.models.ConfigMapModel;
import io.ballerina.c2c.models.DockerModel;
import io.ballerina.c2c.models.JobModel;
import io.ballerina.c2c.models.KubernetesContext;
import io.ballerina.c2c.models.KubernetesDataHolder;
import io.ballerina.c2c.models.SecretModel;
import io.ballerina.c2c.util.C2CDiagnosticCodes;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.batch.v1.CronJob;
import io.fabric8.kubernetes.api.model.batch.v1.CronJobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static io.ballerina.c2c.utils.DockerGenUtils.extractJarName;
import static io.ballerina.c2c.utils.KubernetesUtils.generateConfigMapVolumeMounts;
import static io.ballerina.c2c.utils.KubernetesUtils.resolveDockerToml;

/**
 * Job generator.
 */
public class JobHandler extends AbstractArtifactHandler {


    private void generate(JobModel jobModel) throws KubernetesPluginException {
        try {
            String jobContent;
            if (KubernetesUtils.isBlank(jobModel.getSchedule())) {
                jobContent = KubernetesUtils.asYaml(getJob(jobModel));
            } else {
                jobContent = KubernetesUtils.asYaml(getCronJob(jobModel));
            }
            String outputFileName = KubernetesConstants.JOB_FILE_POSTFIX + KubernetesConstants.YAML;
            if (dataHolder.isSingleYaml()) {
                outputFileName = getJob(jobModel).getMetadata().getName() + KubernetesConstants.YAML;
            }
            KubernetesUtils.writeToFile(jobContent, outputFileName);
        } catch (IOException e) {
            Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.ARTIFACT_GEN_FAILED,
                    new NullLocation(), "job" , jobModel.getName());
            throw new KubernetesPluginException(diagnostic);
        }

    }


    private Container generateContainer(JobModel jobModel) {
        return new ContainerBuilder()
                .withName(jobModel.getName())
                .withImage(jobModel.getImage())
                .withEnv(jobModel.getEnvVars())
                .withVolumeMounts(populateVolumeMounts())
                .build();
    }

    private Job getJob(JobModel jobModel) {
        JobBuilder jobBuilder = new JobBuilder()
                .withNewMetadata()
                .withName(jobModel.getName())
                .withNamespace(dataHolder.getNamespace())
                .endMetadata()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .withRestartPolicy(jobModel.getRestartPolicy())
                .withContainers(generateContainer(jobModel))
                .withImagePullSecrets(getImagePullSecrets(jobModel))
                .withVolumes(populateVolume())
                .endSpec()
                .endTemplate()
                .endSpec();
        return jobBuilder.build();
    }

    private List<LocalObjectReference> getImagePullSecrets(JobModel jobModel) {
        List<LocalObjectReference> imagePullSecrets = new ArrayList<>();
        for (String imagePullSecret : jobModel.getImagePullSecrets()) {
            imagePullSecrets.add(new LocalObjectReferenceBuilder().withName(imagePullSecret).build());
        }
        return imagePullSecrets;
    }

    private CronJob getCronJob(JobModel jobModel) {
        return new CronJobBuilder()
                .withNewMetadata()
                .withName(jobModel.getName())
                .endMetadata()
                .withNewSpec()
                .withSchedule(jobModel.getSchedule())
                .withNewJobTemplate()
                .withNewSpec()
                .withNewTemplate()
                .withNewSpec()
                .withRestartPolicy(jobModel.getRestartPolicy())
                .withContainers(generateContainer(jobModel))
                .withVolumes(populateVolume())
                .endSpec()
                .endTemplate()
                .endSpec()
                .endJobTemplate()
                .endSpec()
                .build();
    }


    @Override
    public void createArtifacts() throws KubernetesPluginException {
        try {
            String balxFileName = extractJarName(dataHolder.getJarPath());
            JobModel jobModel = dataHolder.getJobModel();
            if (KubernetesUtils.isBlank(jobModel.getName())) {
                jobModel.setName(KubernetesUtils.getValidName(balxFileName) + KubernetesConstants.JOB_POSTFIX);
            }
            if (KubernetesUtils.isBlank(jobModel.getImage())) {
                jobModel.setImage(balxFileName + KubernetesConstants.DOCKER_LATEST_TAG);
            }
            jobModel.addLabel(KubernetesConstants.KUBERNETES_SELECTOR_KEY, balxFileName);
            resolveDockerToml(jobModel);
            generate(jobModel);
            //generate dockerfile and docker image
            dataHolder.setDockerModel(getDockerModel(jobModel));
            OUT.println("\t@kubernetes:Job");
        } catch (DockerGenException e) {
            Diagnostic diagnostic =
                    C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.DOCKER_FAILED, new NullLocation());
            throw new KubernetesPluginException(diagnostic);
        }
    }

    private DockerModel getDockerModel(JobModel jobModel) throws DockerGenException {
        final KubernetesDataHolder dataHolder = KubernetesContext.getInstance().getDataHolder();
        DockerModel dockerModel = dataHolder.getDockerModel();
        String dockerImage = jobModel.getImage();
        String imageTag = dockerImage.substring(dockerImage.lastIndexOf(":") + 1);
        dockerImage = dockerImage.substring(0, dockerImage.lastIndexOf(":"));
        dockerModel.setRegistry(jobModel.getRegistry());
        dockerModel.setName(dockerImage);
        dockerModel.setTag(imageTag);
        dockerModel.setEntryPoint(jobModel.getEntryPoint());
        dockerModel.setJarFileName(extractJarName(this.dataHolder.getJarPath()) + KubernetesConstants.EXECUTABLE_JAR);
        dockerModel.setService(false);
        dockerModel.setBuildImage(jobModel.isBuildImage());
        dockerModel.setPkgId(dataHolder.getPackageID());
        dockerModel.setCopyFiles(jobModel.getCopyFiles());
        dockerModel.setPkgId(this.dataHolder.getPackageID());
        return dockerModel;
    }

    private List<VolumeMount> populateVolumeMounts() {
        List<VolumeMount> volumeMounts = new ArrayList<>();
        volumeMounts.addAll(KubernetesUtils.generateSecretVolumeMounts(dataHolder.getSecretModelSet()));
        volumeMounts.addAll(generateConfigMapVolumeMounts(dataHolder.getConfigMapModelSet()));
        return volumeMounts;
    }

    private List<Volume> populateVolume() {
        List<Volume> volumes = new ArrayList<>();
        for (SecretModel secretModel : dataHolder.getSecretModelSet()) {
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
        for (ConfigMapModel configMapModel : dataHolder.getConfigMapModelSet()) {
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
        return volumes;
    }
}
