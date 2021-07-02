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
import io.ballerina.c2c.handlers.ConfigMapHandler;
import io.ballerina.c2c.handlers.SecretHandler;
import io.ballerina.c2c.models.ConfigMapModel;
import io.ballerina.c2c.models.DeploymentModel;
import io.ballerina.c2c.models.KubernetesContext;
import io.ballerina.c2c.models.KubernetesDataHolder;
import io.ballerina.c2c.models.SecretModel;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.ballerina.c2c.utils.TomlHelper;
import io.ballerina.toml.api.Toml;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

    public void resolveToml() throws KubernetesPluginException {
        Toml ballerinaCloud = dataHolder.getBallerinaCloud();
        if (ballerinaCloud != null) {
            DeploymentModel deploymentModel = dataHolder.getDeploymentModel();

            // Deployment configs
            resolveDeploymentToml(deploymentModel, ballerinaCloud);

            // Resolve settings
            resolveSettingsToml(ballerinaCloud);

            // Resources
            resolveResourcesToml(deploymentModel, ballerinaCloud);

            // Env vars
            resolveEnvToml(deploymentModel, ballerinaCloud);

            // Config files
            resolveConfigMapToml(deploymentModel, ballerinaCloud);

            // Secret files
            resolveSecretToml(deploymentModel, ballerinaCloud);
        }

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

    private void resolveEnvToml(DeploymentModel deploymentModel, Toml ballerinaCloud) {
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
            deploymentModel.addEnv(envVar);
        }

        List<Toml> secrets = ballerinaCloud.getTables("cloud.secrets.envs");
        for (Toml secret : secrets) {
            EnvVar envVar = new EnvVarBuilder()
                    .withName(TomlHelper.getString(secret, "name"))
                    .withNewValueFrom()
                    .withNewSecretKeyRef()
                    .withKey(TomlHelper.getString(secret, KubernetesConstants.KEY_REF))
                    .withName(TomlHelper.getString(secret, "secret"))
                    .endSecretKeyRef()
                    .endValueFrom()
                    .build();
            if (isBlank(envVar.getName())) {
                envVar.setName(TomlHelper.getString(secret, KubernetesConstants.KEY_REF));
            }
            deploymentModel.addEnv(envVar);
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

    private void resolveConfigMapToml(DeploymentModel deploymentModel, Toml toml) throws KubernetesPluginException {
        List<Toml> configFiles = toml.getTables("cloud.config.files");
        if (configFiles.size() != 0) {
            final String deploymentName = deploymentModel.getName().replace(DEPLOYMENT_POSTFIX, "");

            for (Toml configFile : configFiles) {
                Path path = Paths.get(Objects.requireNonNull(TomlHelper.getString(configFile, "file")));
                if (path.endsWith(BALLERINA_CONF_FILE_NAME)) {
                    // Resolve Config.toml
                    ConfigMapModel configMapModel = getBallerinaConfConfigMap(path.toString(), deploymentName);
                    dataHolder.addConfigMaps(Collections.singleton(configMapModel));
                    continue;
                }
                Path mountPath = Paths.get(Objects.requireNonNull(TomlHelper.getString(configFile, "mount_path")));
                final Path fileName = validatePaths(path, mountPath);
                ConfigMapModel configMapModel = new ConfigMapModel();
                configMapModel.setName(deploymentName + "-" + getValidName(fileName.toString()));
                configMapModel.setData(getDataForConfigMap(path.toString()));
                configMapModel.setMountPath(mountPath.toString());
                dataHolder.addConfigMaps(Collections.singleton(configMapModel));
            }
            new ConfigMapHandler().createArtifacts();
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
        new SecretHandler().createArtifacts();
    }

    private Path validatePaths(Path path, Path mountPath) throws KubernetesPluginException {
        final Path homePath = Paths.get(BALLERINA_HOME);
        final Path runtimePath = Paths.get(BALLERINA_RUNTIME);
        final Path confPath = Paths.get(BALLERINA_CONF_MOUNT_PATH);
        if (mountPath.equals(homePath)) {
            throw new KubernetesPluginException("Cloud.toml error mount_path " +
                    "cannot be ballerina home: " +
                    BALLERINA_HOME);
        }
        if (mountPath.equals(runtimePath)) {
            throw new KubernetesPluginException("Cloud.toml error mount_path " +
                    "cannot be ballerina runtime: " +
                    BALLERINA_RUNTIME);
        }
        if (mountPath.equals(confPath)) {
            throw new KubernetesPluginException("Cloud.toml error mount path " +
                    "cannot be ballerina conf file mount " +
                    "path: " + BALLERINA_CONF_MOUNT_PATH);
        }
        final Path fileName = path.getFileName();
        if (fileName == null) {
            throw new KubernetesPluginException("Cloud.toml error invalid path without file name " +
                    BALLERINA_CONF_MOUNT_PATH);
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

