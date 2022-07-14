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

import io.ballerina.c2c.diagnostics.NullLocation;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.models.ConfigMapModel;
import io.ballerina.c2c.models.DeploymentModel;
import io.ballerina.c2c.models.JobModel;
import io.ballerina.c2c.models.KubernetesContext;
import io.ballerina.c2c.models.KubernetesDataHolder;
import io.ballerina.c2c.models.KubernetesModel;
import io.ballerina.c2c.models.PersistentVolumeClaimModel;
import io.ballerina.c2c.models.SecretModel;
import io.ballerina.c2c.util.C2CDiagnosticCodes;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.ballerina.c2c.utils.TomlHelper;
import io.ballerina.toml.api.Toml;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HTTPGetAction;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import org.apache.commons.codec.binary.Base64;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static io.ballerina.c2c.KubernetesConstants.BALLERINA_CONF_FILE_NAME;
import static io.ballerina.c2c.KubernetesConstants.BALLERINA_CONF_MOUNT_PATH;
import static io.ballerina.c2c.KubernetesConstants.BALLERINA_HOME;
import static io.ballerina.c2c.KubernetesConstants.BALLERINA_RUNTIME;
import static io.ballerina.c2c.KubernetesConstants.CONFIG_MAP_POSTFIX;
import static io.ballerina.c2c.KubernetesConstants.DEPLOYMENT_POSTFIX;
import static io.ballerina.c2c.KubernetesConstants.SECRET_POSTFIX;
import static io.ballerina.c2c.utils.KubernetesUtils.getValidName;
import static io.ballerina.c2c.utils.KubernetesUtils.isBlank;

/**
 * Class to resolve Cloud.toml file.
 */
public class CloudTomlResolver {

    public static final String CLOUD_DEPLOYMENT = "cloud.deployment.";
    KubernetesDataHolder dataHolder = KubernetesContext.getInstance().getDataHolder();

    public void resolveToml(JobModel jobModel) {
        Toml ballerinaCloud = dataHolder.getBallerinaCloud();
        if (ballerinaCloud != null) {
            // Resolve Env
            resolveEnvToml(jobModel, ballerinaCloud);

            // Resolve settings
            resolveSettingsToml(ballerinaCloud);
        }
    }

    public void resolveToml(DeploymentModel deploymentModel) throws KubernetesPluginException {
        Toml ballerinaCloud = dataHolder.getBallerinaCloud();
        if (ballerinaCloud != null) {
            // Deployment configs
            resolveDeploymentToml(deploymentModel, ballerinaCloud);

            // Resolve settings
            resolveSettingsToml(ballerinaCloud);

            // Resources
            resolveResourcesToml(deploymentModel, ballerinaCloud);

            // Env vars
            resolveEnvToml(deploymentModel, ballerinaCloud);

            // Config.toml files
            resolveConfigMapToml(ballerinaCloud);

            // Config files
            resolveConfigFilesToml(deploymentModel, ballerinaCloud);

            // Secret files
            resolveSecretToml(deploymentModel, ballerinaCloud);

            // Resolve Volumes
            resolveVolumes(deploymentModel, ballerinaCloud);
        }

    }

    private void resolveVolumes(DeploymentModel deploymentModel, Toml ballerinaCloud) {
        List<Toml> volumes = ballerinaCloud.getTables("cloud.deployment.storage.volumes");
        Set<PersistentVolumeClaimModel> persistentVolumeClaimModels = new HashSet<>();
        volumes.forEach(volume -> {
            PersistentVolumeClaimModel pv = new PersistentVolumeClaimModel();
            pv.setName(TomlHelper.getString(volume, "name"));
            pv.setMountPath(TomlHelper.getString(volume, "local_path"));
            pv.setVolumeClaimSizeAmount(TomlHelper.getString(volume, "size"));
            persistentVolumeClaimModels.add(pv);
        });
        deploymentModel.setVolumeClaimModels(persistentVolumeClaimModels);
    }

    private void resolveSettingsToml(Toml ballerinaCloud) {
        dataHolder.setSingleYaml(TomlHelper.getBoolean(ballerinaCloud, "settings.singleYAML", true));
        dataHolder.getDockerModel().setBuildImage(TomlHelper.getBoolean(ballerinaCloud,
                "settings.buildImage", true));
    }

    private void resolveDeploymentToml(DeploymentModel deploymentModel, Toml ballerinaCloud) {
        deploymentModel.setReplicas(Math.toIntExact(TomlHelper.getLong(ballerinaCloud, CLOUD_DEPLOYMENT + "replicas",
                deploymentModel.getReplicas())));
        Optional<Toml> probeToml = ballerinaCloud.getTable(CLOUD_DEPLOYMENT + "probes.readiness");
        probeToml.ifPresent(toml -> deploymentModel.setReadinessProbe(resolveProbeToml(toml)));
        probeToml = ballerinaCloud.getTable(CLOUD_DEPLOYMENT + "probes.liveness");
        probeToml.ifPresent(toml -> deploymentModel.setLivenessProbe(resolveProbeToml(toml)));
        deploymentModel.setInternalDomainName(TomlHelper.getString(ballerinaCloud, CLOUD_DEPLOYMENT +
                "internal_domain_name"));
    }

    private void resolveEnvToml(KubernetesModel model, Toml ballerinaCloud) {
        List<Toml> envs = ballerinaCloud.getTables("cloud.config.envs");
        for (Toml env : envs) {
            EnvVar envVar = new EnvVarBuilder()
                    .withName(TomlHelper.getString(env, "name"))
                    .withNewValueFrom()
                    .withNewConfigMapKeyRef()
                    .withKey(TomlHelper.getString(env, KubernetesConstants.KEY_REF))
                    .withName(TomlHelper.getString(env, "config_name"))
                    .endConfigMapKeyRef()
                    .endValueFrom()
                    .build();
            if (isBlank(envVar.getName())) {
                envVar.setName(TomlHelper.getString(env, KubernetesConstants.KEY_REF));
            }
            model.addEnv(envVar);
        }

        List<Toml> secrets = ballerinaCloud.getTables("cloud.secret.envs");
        for (Toml secret : secrets) {
            EnvVar envVar = new EnvVarBuilder()
                    .withName(TomlHelper.getString(secret, "name"))
                    .withNewValueFrom()
                    .withNewSecretKeyRef()
                    .withKey(TomlHelper.getString(secret, KubernetesConstants.KEY_REF))
                    .withName(TomlHelper.getString(secret, "secret_name"))
                    .endSecretKeyRef()
                    .endValueFrom()
                    .build();
            if (isBlank(envVar.getName())) {
                envVar.setName(TomlHelper.getString(secret, KubernetesConstants.KEY_REF));
            }
            model.addEnv(envVar);
        }
    }

    private void resolveResourcesToml(DeploymentModel deploymentModel, Toml deploymentToml) {
        Map<String, Quantity> requests = deploymentModel.getResourceRequirements().getRequests();
        String minMemory = TomlHelper.getString(deploymentToml, CLOUD_DEPLOYMENT + KubernetesConstants.MIN_MEMORY);
        String minCPU = TomlHelper.getString(deploymentToml, CLOUD_DEPLOYMENT + "min_cpu");
        if (minMemory != null) {
            requests.put(KubernetesConstants.MEMORY, new Quantity(minMemory));
        }
        if (minCPU != null) {
            requests.put(KubernetesConstants.CPU, new Quantity(minCPU));
        }
        Map<String, Quantity> limits = deploymentModel.getResourceRequirements().getLimits();
        String maxMemory = TomlHelper.getString(deploymentToml, CLOUD_DEPLOYMENT + "max_memory");
        String maxCPU = TomlHelper.getString(deploymentToml, CLOUD_DEPLOYMENT + "max_cpu");
        if (maxMemory != null) {
            limits.put(KubernetesConstants.MEMORY, new Quantity(maxMemory));
        }
        if (maxCPU != null) {
            limits.put(KubernetesConstants.CPU, new Quantity(maxCPU));
        }
        deploymentModel.getResourceRequirements().setLimits(limits);
        deploymentModel.getResourceRequirements().setRequests(requests);
    }

    private void resolveConfigMapToml(Toml toml) throws KubernetesPluginException {
        List<Toml> configFiles = toml.getTables("cloud.config.files");
        for (Toml configFile : configFiles) {
            ConfigMapModel configMapModel = new ConfigMapModel();
            String defaultValue = getValidName(BALLERINA_CONF_FILE_NAME.replace(".toml", "")) + CONFIG_MAP_POSTFIX;
            String name = TomlHelper.getString(configFile, "name", defaultValue);

            Path path = Paths.get(Objects.requireNonNull(TomlHelper.getString(configFile, "file")));
            // Resolve Config.toml
            Path fileName = path.getFileName();
            if (fileName == null) {
                Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.INVALID_CONFIG_NAME,
                        new NullLocation());
                throw new KubernetesPluginException(diagnostic);
            }
            Path dataFilePath = path;
            if (!path.isAbsolute()) {
                dataFilePath = KubernetesContext.getInstance().getDataHolder().getSourceRoot().resolve(dataFilePath)
                        .normalize();
            }
            String content = new String(KubernetesUtils.readFileContent(dataFilePath), StandardCharsets.UTF_8);

            Optional<ConfigMapModel> configMap = getConfigMapModel(name);
            if (configMap.isEmpty()) {
                configMapModel.setName(name);
                configMapModel.setMountPath(BALLERINA_CONF_MOUNT_PATH);
                Map<String, String> dataMap = new HashMap<>();
                dataMap.put(fileName.toString(), content);
                configMapModel.setData(dataMap);
                configMapModel.setBallerinaConf(true);
                configMapModel.setReadOnly(false);
                dataHolder.addConfigMaps(Collections.singleton(configMapModel));
            } else {
                ConfigMapModel existingConfigMap = configMap.get();
                Map<String, String> data = existingConfigMap.getData();
                if (data.containsKey(fileName.toString())) {
                    Diagnostic diagnostic =
                            C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.INVALID_CONFIG_FILE_NAME_TAKEN,
                                    new NullLocation());
                    throw new KubernetesPluginException(diagnostic);
                }
                data.put(fileName.toString(), content);
            }
        }
    }

    private Optional<ConfigMapModel> getConfigMapModel(String name) {
        Set<ConfigMapModel> configMapModelSet = dataHolder.getConfigMapModelSet();
        for (ConfigMapModel configMapModel : configMapModelSet) {
            if (configMapModel.getName().equals(name)) {
                return Optional.of(configMapModel);
            }
        }
        return Optional.empty();
    }

    public void resolveConfigFilesToml(DeploymentModel deploymentModel, Toml toml) throws KubernetesPluginException {
        List<Toml> configFiles = toml.getTables("cloud.config.maps");
        if (configFiles.size() != 0) {
            final String deploymentName = deploymentModel.getName().replace(DEPLOYMENT_POSTFIX, "");
            for (Toml configFile : configFiles) {
                Path path = Paths.get(Objects.requireNonNull(TomlHelper.getString(configFile, "file")));
                Path mountPath = Paths.get(Objects.requireNonNull(TomlHelper.getString(configFile, "mount_path")));
                final Path fileName = validatePaths(path, mountPath);
                ConfigMapModel configMapModel = new ConfigMapModel();
                configMapModel.setName(deploymentName + "-" + getValidName(fileName.toString()));
                configMapModel.setData(getDataForConfigMap(path.toString()));
                configMapModel.setMountPath(mountPath.toString());
                configMapModel.setBallerinaConf(false);
                dataHolder.addConfigMaps(Collections.singleton(configMapModel));
            }
        }
    }

    private void resolveSecretToml(DeploymentModel deploymentModel, Toml toml) throws KubernetesPluginException {
        List<Toml> secrets = toml.getTables("cloud.secret.files");
        if (secrets.size() != 0) {
            final String deploymentName = deploymentModel.getName().replace(DEPLOYMENT_POSTFIX, "");

            for (Toml secret : secrets) {
                Path path = Paths.get(Objects.requireNonNull(TomlHelper.getString(secret, "file")));
                if (path.endsWith(BALLERINA_CONF_FILE_NAME)) {
                    // Resolve ballerina.conf
                    SecretModel secretModel = getBallerinaConfSecret(path.toString(), deploymentName);
                    dataHolder.addSecrets(Collections.singleton(secretModel));
                    continue;
                }
                Path mountPath = Paths.get(Objects.requireNonNull(TomlHelper.getString(secret, "mount_path")));
                final Path fileName = validatePaths(path, mountPath);
                SecretModel secretModel = new SecretModel();
                secretModel.setName(deploymentName + "-" + getValidName(fileName.toString()));
                secretModel.setData(getDataForSecret(path.toString()));
                secretModel.setMountPath(mountPath.toString());
                dataHolder.addSecrets(Collections.singleton(secretModel));
            }
        }
    }

    private Path validatePaths(Path path, Path mountPath) throws KubernetesPluginException {
        final Path homePath = Paths.get(BALLERINA_HOME);
        final Path runtimePath = Paths.get(BALLERINA_RUNTIME);
        final Path confPath = Paths.get(BALLERINA_CONF_MOUNT_PATH);
        if (mountPath.equals(homePath)) {
            Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.INVALID_MOUNT_PATH_CLOUD,
                    new NullLocation(), "ballerina home", BALLERINA_HOME);
            throw new KubernetesPluginException(diagnostic);
        }
        if (mountPath.equals(runtimePath)) {
            Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.INVALID_MOUNT_PATH_CLOUD,
                    new NullLocation(), "ballerina runtime", BALLERINA_RUNTIME);
            throw new KubernetesPluginException(diagnostic);
        }
        if (mountPath.equals(confPath)) {
            Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.INVALID_MOUNT_PATH_CLOUD,
                    new NullLocation(), "ballerina conf file mount", BALLERINA_CONF_MOUNT_PATH);
            throw new KubernetesPluginException(diagnostic);
        }
        final Path fileName = path.getFileName();
        if (fileName == null) {
            Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.EMPTY_PATH_CLOUD,
                    new NullLocation(), BALLERINA_CONF_MOUNT_PATH);
            throw new KubernetesPluginException(diagnostic);
        }
        return fileName;
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

    private Map<String, String> getDataForSecret(String path) throws KubernetesPluginException {
        Map<String, String> dataMap = new HashMap<>();
        Path dataFilePath = Paths.get(path);
        if (!dataFilePath.isAbsolute()) {
            dataFilePath = KubernetesContext.getInstance().getDataHolder().getSourceRoot().resolve(dataFilePath);
        }
        String key = String.valueOf(dataFilePath.getFileName());
        String content = Base64.encodeBase64String(KubernetesUtils.readFileContent(dataFilePath));
        dataMap.put(key, content);
        return dataMap;
    }

    private SecretModel getBallerinaConfSecret(String configFilePath, String serviceName) throws
            KubernetesPluginException {
        //create a new secret map model with ballerina conf
        SecretModel secretModel = new SecretModel();
        secretModel.setName(getValidName(serviceName) + "-ballerina-conf" + SECRET_POSTFIX);
        secretModel.setMountPath(BALLERINA_CONF_MOUNT_PATH);
        Path dataFilePath = Paths.get(configFilePath);
        if (!dataFilePath.isAbsolute()) {
            dataFilePath = KubernetesContext.getInstance().getDataHolder().getSourceRoot().resolve(dataFilePath)
                    .normalize();
        }
        String content = Base64.encodeBase64String(KubernetesUtils.readFileContent(dataFilePath));
        Map<String, String> dataMap = new HashMap<>();
        dataMap.put(BALLERINA_CONF_FILE_NAME, content);
        secretModel.setData(dataMap);
        secretModel.setBallerinaConf(configFilePath);
        secretModel.setReadOnly(false);
        return secretModel;
    }

    private Probe resolveProbeToml(Toml probeToml) {
        //Resolve Probe.
        Probe probe = new ProbeBuilder().build();
        HTTPGetAction httpGet = new HTTPGetAction();
        final Long port = TomlHelper.getLong(probeToml, "port");
        if (port != null) {
            httpGet.setPort(new IntOrString(Math.toIntExact(port)));
        }
        httpGet.setPath(TomlHelper.getString(probeToml, "path"));
        probe.setInitialDelaySeconds(30);
        probe.setHttpGet(httpGet);
        return probe;
    }

}

