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

package org.ballerinax.kubernetes.handlers;


import com.moandjiezana.toml.Toml;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.HTTPGetAction;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Lifecycle;
import io.fabric8.kubernetes.api.model.LifecycleBuilder;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.LocalObjectReferenceBuilder;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Toleration;
import io.fabric8.kubernetes.api.model.TolerationBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.ballerinax.docker.generator.exceptions.DockerGenException;
import org.ballerinax.docker.generator.models.DockerModel;
import org.ballerinax.kubernetes.exceptions.KubernetesPluginException;
import org.ballerinax.kubernetes.models.ConfigMapModel;
import org.ballerinax.kubernetes.models.DeploymentModel;
import org.ballerinax.kubernetes.models.EnvVarValueModel;
import org.ballerinax.kubernetes.models.KubernetesContext;
import org.ballerinax.kubernetes.models.KubernetesDataHolder;
import org.ballerinax.kubernetes.models.PersistentVolumeClaimModel;
import org.ballerinax.kubernetes.models.PodTolerationModel;
import org.ballerinax.kubernetes.models.SecretModel;
import org.ballerinax.kubernetes.models.ServiceAccountTokenModel;
import org.ballerinax.kubernetes.utils.KubernetesUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.ballerinax.docker.generator.DockerGenConstants.REGISTRY_SEPARATOR;
import static org.ballerinax.docker.generator.utils.DockerGenUtils.extractJarName;
import static org.ballerinax.kubernetes.KubernetesConstants.BALLERINA_CONF_FILE_NAME;
import static org.ballerinax.kubernetes.KubernetesConstants.BALLERINA_CONF_MOUNT_PATH;
import static org.ballerinax.kubernetes.KubernetesConstants.BALLERINA_HOME;
import static org.ballerinax.kubernetes.KubernetesConstants.BALLERINA_RUNTIME;
import static org.ballerinax.kubernetes.KubernetesConstants.CONFIG_MAP_POSTFIX;
import static org.ballerinax.kubernetes.KubernetesConstants.DEPLOYMENT_FILE_POSTFIX;
import static org.ballerinax.kubernetes.KubernetesConstants.DEPLOYMENT_POSTFIX;
import static org.ballerinax.kubernetes.KubernetesConstants.EXECUTABLE_JAR;
import static org.ballerinax.kubernetes.KubernetesConstants.YAML;
import static org.ballerinax.kubernetes.utils.KubernetesUtils.getValidName;
import static org.ballerinax.kubernetes.utils.KubernetesUtils.populateEnvVar;

/**
 * Generates kubernetes deployment from annotations.
 */
public class DeploymentHandler extends AbstractArtifactHandler {

    public static final String CLOUD_DEPLOYMENT = "cloud.deployment.";

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
            if (configMapModel.getMountPath() != null) {
                VolumeMount volumeMount = new VolumeMountBuilder()
                        .withMountPath(configMapModel.getMountPath())
                        .withName(configMapModel.getName() + "-volume")
                        .withReadOnly(configMapModel.isReadOnly())
                        .build();
                volumeMounts.add(volumeMount);
            }
        }
        for (ServiceAccountTokenModel volumeClaimModel : deploymentModel.getServiceAccountTokenModel()) {
            VolumeMount volumeMount = new VolumeMountBuilder()
                    .withMountPath(volumeClaimModel.getMountPath())
                    .withName(volumeClaimModel.getName() + "-volume")
                    .build();
            volumeMounts.add(volumeMount);
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

    private List<Container> generateInitContainer(DeploymentModel deploymentModel) throws KubernetesPluginException {
        List<Container> initContainers = new ArrayList<>();
        for (String dependsOn : deploymentModel.getDependsOn()) {
            String serviceName = KubernetesContext.getInstance().getServiceName(dependsOn);
            List<String> commands = new ArrayList<>();
            commands.add("sh");
            commands.add("-c");
            commands.add("until nslookup " + serviceName + "; do echo waiting for " + serviceName + "; sleep 2; done;");
            initContainers.add(new ContainerBuilder()
                    .withName("wait-for-" + serviceName)
                    .withImage("busybox")
                    .withCommand(commands)
                    .build());
        }
        return initContainers;
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
                .withImagePullPolicy(deploymentModel.getImagePullPolicy())
                .withPorts(containerPorts)
                .withEnv(populateEnvVar(deploymentModel.getEnv()))
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

        for (ServiceAccountTokenModel volumeClaimModel : deploymentModel.getServiceAccountTokenModel()) {
            Volume volume = new VolumeBuilder()
                    .withName(volumeClaimModel.getName() + "-volume")
                    .withNewProjected()
                    .withSources()
                    .addNewSource()
                    .withNewServiceAccountToken()
                    .withAudience(volumeClaimModel.getAudience())
                    .withPath(volumeClaimModel.getName() + "-volume")
                    .withExpirationSeconds((long) volumeClaimModel.getExpirationSeconds())
                    .endServiceAccountToken()
                    .endSource()
                    .endProjected()
                    .build();
            volumes.add(volume);
        }
        return volumes;
    }

    private List<Toleration> populatePodTolerations(List<PodTolerationModel> podTolerationModels) {
        List<Toleration> tolerations = null;

        if (null != podTolerationModels && podTolerationModels.size() > 0) {
            tolerations = new LinkedList<>();
            for (PodTolerationModel podTolerationModel : podTolerationModels) {
                Toleration toleration = new TolerationBuilder()
                        .withKey(podTolerationModel.getKey())
                        .withOperator(podTolerationModel.getOperator())
                        .withValue(podTolerationModel.getValue())
                        .withEffect(podTolerationModel.getEffect())
                        .withTolerationSeconds((long) podTolerationModel.getTolerationSeconds())
                        .build();

                tolerations.add(toleration);
            }
        }

        return tolerations;
    }

    private List<LocalObjectReference> getImagePullSecrets(DeploymentModel deploymentModel) {
        List<LocalObjectReference> imagePullSecrets = new ArrayList<>();
        for (String imagePullSecret : deploymentModel.getImagePullSecrets()) {
            imagePullSecrets.add(new LocalObjectReferenceBuilder().withName(imagePullSecret).build());
        }
        return imagePullSecrets;
    }

    private void resolveToml() throws KubernetesPluginException {
        Toml ballerinaCloud = dataHolder.getBallerinaCloud();
        if (ballerinaCloud != null) {
            DeploymentModel deploymentModel = dataHolder.getDeploymentModel();
            deploymentModel.setReplicas(Math.toIntExact(ballerinaCloud.getLong(CLOUD_DEPLOYMENT + "replicas",
                    (long) deploymentModel.getReplicas())));
            resolveResources(deploymentModel, ballerinaCloud);
            Toml probeToml = ballerinaCloud.getTable(CLOUD_DEPLOYMENT + "probes.readiness");
            if (probeToml != null) {
                deploymentModel.setReadinessProbe(resolveProbeToml(probeToml));
            }
            probeToml = ballerinaCloud.getTable(CLOUD_DEPLOYMENT + "probes.liveness");
            if (probeToml != null) {
                deploymentModel.setLivenessProbe(resolveProbeToml(probeToml));
            }
            Toml configToml = ballerinaCloud.getTable("cloud.config");
            if (configToml != null) {
                Map<String, String> data = new HashMap<>();
                String configMapName =
                        deploymentModel.getName().replace(DEPLOYMENT_POSTFIX, CONFIG_MAP_POSTFIX);
                // Env vars
                for (Map.Entry<String, Object> e : configToml.entrySet().stream()
                        .filter(entry -> !"files".equals(entry.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)).entrySet()) {
                    String k = e.getKey();
                    data.put(k, e.getValue().toString());
                    EnvVarValueModel.ConfigMapKeyValue configMapKeyRefModel =
                            new EnvVarValueModel.ConfigMapKeyValue();
                    configMapKeyRefModel.setKey(k);
                    configMapKeyRefModel.setName(configMapName);
                    Map<String, EnvVarValueModel> envVarMap = new LinkedHashMap<>();
                    EnvVarValueModel envVarValue = new EnvVarValueModel(configMapKeyRefModel);
                    envVarMap.put(k, envVarValue);
                    deploymentModel.setEnv(envVarMap);
                }
                ConfigMapModel configMapModel = new ConfigMapModel();
                configMapModel.setData(data);
                configMapModel.setName(configMapName);
                dataHolder.addConfigMaps(Collections.singleton(configMapModel));
                // Config files
                resolveConfigMap(deploymentModel, configToml);
            }
        }

    }

    private void resolveResources(DeploymentModel deploymentModel, Toml deploymentToml) {
        Map<String, Quantity> requests = deploymentModel.getResourceRequirements().getRequests();
        String minMemory = deploymentToml.getString(CLOUD_DEPLOYMENT + "min_memory");
        String minCPU = deploymentToml.getString(CLOUD_DEPLOYMENT + "min_cpu");
        if (minMemory != null) {
            requests.put("memory", new Quantity(minMemory));
        }
        if (minCPU != null) {
            requests.put("cpu", new Quantity(minCPU));
        }
        Map<String, Quantity> limits = deploymentModel.getResourceRequirements().getLimits();
        String maxMemory = deploymentToml.getString("max_memory");
        String maxCPU = deploymentToml.getString("max_cpu");
        if (maxMemory != null) {
            limits.put("memory", new Quantity(maxMemory));
        }
        if (maxCPU != null) {
            limits.put("cpu", new Quantity(maxCPU));
        }
    }

    private void resolveConfigMap(DeploymentModel deploymentModel, Toml envVars) throws KubernetesPluginException {
        Toml configFiles = envVars.getTable("files");
        if (configFiles != null) {
            final String deploymentName = deploymentModel.getName().replace(DEPLOYMENT_POSTFIX, "");
            Toml ballerinaConf = configFiles.getTable("ballerina.conf");
            if (ballerinaConf != null) {
                // Resolve ballerina.conf
                ConfigMapModel configMapModel = getBallerinaConfConfigMap(ballerinaConf.getString("file"),
                        deploymentName);
                dataHolder.addConfigMaps(Collections.singleton(configMapModel));
            }

            for (Map.Entry<String, Object> e : configFiles.entrySet().stream()
                    .filter(entry -> !"ballerina".equals(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)).entrySet()) {
                String k = e.getKey();
                String path = configFiles.getTable(k).getString("file");
                // validate mount path is not set to ballerina home or ballerina runtime
                final Path mountPath = Paths.get(configFiles.getTable(k).getString("mount_path"));
                final Path homePath = Paths.get(BALLERINA_HOME);
                final Path runtimePath = Paths.get(BALLERINA_RUNTIME);
                final Path confPath = Paths.get(BALLERINA_CONF_MOUNT_PATH);
                if (mountPath.equals(homePath)) {
                    throw new KubernetesPluginException("@kubernetes:ConfigMap{} mount path " +
                            "cannot be ballerina home: " +
                            BALLERINA_HOME);
                }
                if (mountPath.equals(runtimePath)) {
                    throw new KubernetesPluginException("@kubernetes:ConfigMap{} mount path " +
                            "cannot be ballerina runtime: " +
                            BALLERINA_RUNTIME);
                }
                if (mountPath.equals(confPath)) {
                    throw new KubernetesPluginException("@kubernetes:ConfigMap{} mount path " +
                            "cannot be ballerina conf file mount " +
                            "path: " + BALLERINA_CONF_MOUNT_PATH);
                }
                ConfigMapModel configMapModel = new ConfigMapModel();
                configMapModel.setName(deploymentName + "-" + getValidName(k));
                configMapModel.setData(getDataForConfigMap(path));
                configMapModel.setMountPath(mountPath.toString());
                dataHolder.addConfigMaps(Collections.singleton(configMapModel));
            }
            new ConfigMapHandler().createArtifacts();
        }
    }

    private Map<String, String> getDataForConfigMap(String path) throws KubernetesPluginException {
        Map<String, String> dataMap = new HashMap<>();
        Path dataFilePath = Paths.get(path);
        if (!dataFilePath.isAbsolute()) {
            dataFilePath = KubernetesContext.getInstance().getDataHolder().getSourceRoot().resolve(dataFilePath);
        }
        String key = String.valueOf(dataFilePath.getFileName());
        String content = new String(KubernetesUtils.readFileContent(dataFilePath), StandardCharsets.UTF_8);
        dataMap.put(key, content);
        return dataMap;
    }

    private ConfigMapModel getBallerinaConfConfigMap(String configFilePath, String serviceName) throws
            KubernetesPluginException {
        //create a new config map model with ballerina conf
        ConfigMapModel configMapModel = new ConfigMapModel();
        configMapModel.setName(getValidName(serviceName) + "-ballerina-conf" + CONFIG_MAP_POSTFIX);
        configMapModel.setMountPath(BALLERINA_CONF_MOUNT_PATH);
        Path dataFilePath = Paths.get(configFilePath);
        if (!dataFilePath.isAbsolute()) {
            dataFilePath = KubernetesContext.getInstance().getDataHolder().getSourceRoot().resolve(dataFilePath)
                    .normalize();
        }
        String content = new String(KubernetesUtils.readFileContent(dataFilePath), StandardCharsets.UTF_8);
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put(BALLERINA_CONF_FILE_NAME, content);
        configMapModel.setData(dataMap);
        configMapModel.setBallerinaConf(configFilePath);
        configMapModel.setReadOnly(false);
        return configMapModel;
    }

    private Probe resolveProbeToml(Toml probeToml) {
        //Resolve Probe.
        Probe probe = new ProbeBuilder().build();
        HTTPGetAction httpGet = new HTTPGetAction();
        int defaultPort = dataHolder.getDeploymentModel().getPorts().iterator().next().getContainerPort();
        httpGet.setPort(new IntOrString(defaultPort));
        final Long port = probeToml.getLong("port");
        if (port != null) {
            httpGet.setPort(new IntOrString(Math.toIntExact(port)));
        }
        httpGet.setPath(probeToml.getString("path"));
        probe.setInitialDelaySeconds(30);
        probe.setHttpGet(httpGet);
        return probe;
    }

    /**
     * Generate kubernetes deployment definition from annotation.
     *
     * @param deploymentModel @{@link DeploymentModel} definition
     * @throws KubernetesPluginException If an error occurs while generating artifact.
     */
    private void generate(DeploymentModel deploymentModel) throws KubernetesPluginException {
        resolveToml();
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
                .withServiceAccountName(deploymentModel.getServiceAccountName())
                .withContainers(container)
                .withImagePullSecrets(getImagePullSecrets(deploymentModel))
                .withInitContainers(generateInitContainer(deploymentModel))
                .withVolumes(populateVolume(deploymentModel))
                .withTolerations(populatePodTolerations(deploymentModel.getPodTolerations()))
                .withNodeSelector(deploymentModel.getNodeSelector())
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();

        try {
            String deploymentContent = Serialization.asYaml(deployment);
            KubernetesUtils.writeToFile(deploymentContent, DEPLOYMENT_FILE_POSTFIX + YAML);
        } catch (IOException e) {
            String errorMessage = "error while generating yaml file for deployment: " + deploymentModel.getName();
            throw new KubernetesPluginException(errorMessage, e);
        }
    }

    private void resolveDockerToml(DeploymentModel deploymentModel) {
        final String containerImage = "container.image";
        Toml toml = dataHolder.getBallerinaCloud();
        if (toml != null) {
            DockerModel dockerModel = dataHolder.getDockerModel();
            dockerModel.setName(toml.getString(containerImage + ".name",
                    deploymentModel.getName().replace(DEPLOYMENT_POSTFIX, "")));
            dockerModel.setRegistry(toml.getString(containerImage + ".repository", dockerModel.getRegistry()));
            dockerModel.setTag(toml.getString(containerImage + ".tag", dockerModel.getTag()));
            dockerModel.setBaseImage(toml.getString(containerImage + ".base", dockerModel.getBaseImage()));
            dataHolder.getDeploymentModel().setImage
                    (dockerModel.getRegistry() + "/" + dockerModel.getName() + ":" + dockerModel.getTag());
        }
    }

    @Override
    public void createArtifacts() throws KubernetesPluginException {
        try {
            DeploymentModel deploymentModel = dataHolder.getDeploymentModel();
            deploymentModel.setPodAutoscalerModel(dataHolder.getPodAutoscalerModel());
            deploymentModel.setSecretModels(dataHolder.getSecretModelSet());
            deploymentModel.setConfigMapModels(dataHolder.getConfigMapModelSet());
            deploymentModel.setVolumeClaimModels(dataHolder.getVolumeClaimModelSet());
            if (null != deploymentModel.getLivenessProbe() &&
                    deploymentModel.getLivenessProbe().getHttpGet().getPort().getIntVal() == 0) {
                //set first port as liveness port
                if (deploymentModel.getPorts().size() == 0) {
                    throw new KubernetesPluginException("unable to detect port for liveness probe." +
                            "missing @kubernetes:Service annotation on listener.");
                }
                deploymentModel.getLivenessProbe().getHttpGet().setPort(new
                        IntOrString(deploymentModel.getPorts().iterator().next().getContainerPort()));
            }

            if (null != deploymentModel.getReadinessProbe() &&
                    deploymentModel.getReadinessProbe().getHttpGet().getPort().getIntVal() == 0) {
                //set first port as readiness port
                if (deploymentModel.getPorts().size() == 0) {
                    throw new KubernetesPluginException("unable to detect port for readiness probe. " +
                            "missing @kubernetes:Service annotation on listener.");
                }
                deploymentModel.getReadinessProbe().getHttpGet().setPort(new
                        IntOrString(deploymentModel.getPorts().iterator().next().getContainerPort()));
            }
            resolveDockerToml(deploymentModel);
            generate(deploymentModel);
            OUT.println();
            OUT.print("\t@kubernetes:Deployment \t\t\t - complete 1/1");
            dataHolder.setDockerModel(getDockerModel(deploymentModel));
        } catch (DockerGenException e) {
            throw new KubernetesPluginException("error occurred creating docker image.", e);
        }
    }


    /**
     * Create docker artifacts.
     *
     * @param deploymentModel Deployment model
     */
    private DockerModel getDockerModel(DeploymentModel deploymentModel) throws DockerGenException {
        final KubernetesDataHolder dataHolder = KubernetesContext.getInstance().getDataHolder();
        DockerModel dockerModel = dataHolder.getDockerModel();
        String dockerImage = deploymentModel.getImage();
        String imageTag = "latest";
        if (dockerImage.contains(":")) {
            imageTag = dockerImage.substring(dockerImage.lastIndexOf(":") + 1);
            dockerImage = dockerImage.substring(0, dockerImage.lastIndexOf(":"));
        }
        dockerModel.setPkgId(dataHolder.getPackageID());
        dockerModel.setBaseImage(deploymentModel.getBaseImage());
        dockerModel.setRegistry(deploymentModel.getRegistry());
        dockerModel.setName(dockerImage);
        dockerModel.setTag(imageTag);
        dockerModel.setEnableDebug(false);
        dockerModel.setUsername(deploymentModel.getUsername());
        dockerModel.setPassword(deploymentModel.getPassword());
        dockerModel.setPush(deploymentModel.isPush());
        dockerModel.setDockerConfig(deploymentModel.getDockerConfigPath());
        dockerModel.setCmd(deploymentModel.getCmd());
        dockerModel.setJarFileName(extractJarName(this.dataHolder.getJarPath()) + EXECUTABLE_JAR);
        dockerModel.setPorts(deploymentModel.getPorts().stream()
                .map(ContainerPort::getContainerPort)
                .collect(Collectors.toSet()));
        dockerModel.setUberJar(deploymentModel.isUberJar());
        dockerModel.setService(true);
        dockerModel.setDockerHost(deploymentModel.getDockerHost());
        dockerModel.setDockerCertPath(deploymentModel.getDockerCertPath());
        dockerModel.setBuildImage(deploymentModel.isBuildImage());
        dockerModel.addCommandArg(deploymentModel.getCommandArgs());
        dockerModel.setCopyFiles(deploymentModel.getCopyFiles());
        return dockerModel;
    }
}

