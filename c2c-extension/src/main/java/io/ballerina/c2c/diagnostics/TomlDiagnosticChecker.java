/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.c2c.diagnostics;

import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.models.DeploymentModel;
import io.ballerina.c2c.models.JobModel;
import io.ballerina.c2c.models.KubernetesContext;
import io.ballerina.c2c.models.KubernetesDataHolder;
import io.ballerina.c2c.models.PodAutoscalerModel;
import io.ballerina.c2c.models.SecretModel;
import io.ballerina.c2c.models.ServiceModel;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.ballerina.projects.Project;
import io.ballerina.toml.api.Toml;
import io.ballerina.toml.semantic.ast.TomlLongValueNode;
import io.ballerina.toml.semantic.ast.TomlStringValueNode;
import io.ballerina.toml.semantic.ast.TomlValueNode;
import io.ballerina.toml.semantic.diagnostics.TomlDiagnostic;
import io.ballerina.toml.semantic.diagnostics.TomlNodeLocation;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import org.apache.commons.codec.binary.Base64;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.ballerina.c2c.KubernetesConstants.BALLERINA_HOME;
import static io.ballerina.c2c.KubernetesConstants.DOCKER_CERT_PATH;
import static io.ballerina.c2c.KubernetesConstants.DOCKER_HOST;
import static io.ballerina.c2c.KubernetesConstants.SVC_POSTFIX;
import static io.ballerina.c2c.utils.KubernetesUtils.getValidName;

/**
 * Responsible for validation against ballerina documents.
 *
 * @since 2.0.0
 */
public class TomlDiagnosticChecker {

    Project project;
    ProjectServiceInfo projectServiceInfo;

    public TomlDiagnosticChecker(Project project) {
        this.project = project;
        this.projectServiceInfo = new ProjectServiceInfo(project);
    }

    public void packageAnalysis() throws KubernetesPluginException {
        KubernetesContext.getInstance().setCurrentPackage(KubernetesUtils.getProjectID(project));
        KubernetesDataHolder dataHolder = KubernetesContext.getInstance().getDataHolder();
        dataHolder.setPackageID(KubernetesUtils.getProjectID(project));

        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();
        if (isServicesExist(serviceList)) {
            addDeployments();
            addHPA();
        }
        addServices(serviceList);
        addJobs();
        dataHolder.setCanProcess(true);
    }

    private void addJobs() {
        if (projectServiceInfo.getTask().isPresent()) {
            Task task = projectServiceInfo.getTask().get();
            JobModel jobModel = new JobModel();
            jobModel.setSchedule(task.getSchedule());

            String dockerHost = System.getenv(DOCKER_HOST);
            if (!KubernetesUtils.isBlank(dockerHost)) {
                jobModel.setDockerHost(dockerHost);
            }
            String dockerCertPath = System.getenv(DOCKER_CERT_PATH);
            if (!KubernetesUtils.isBlank(dockerCertPath)) {
                jobModel.setDockerCertPath(dockerCertPath);
            }
            KubernetesContext.getInstance().getDataHolder().setJobModel(jobModel);
        }
    }

    private void addServices(List<ServiceInfo> serviceList) {
        for (ServiceInfo serviceInfo : serviceList) {
            ServiceModel serviceModel = new ServiceModel();
            if (KubernetesUtils.isBlank(serviceModel.getName())) {
                serviceModel.setName(getValidName(serviceInfo.getServicePath()) + SVC_POSTFIX);
            }

            ListenerInfo listener = serviceInfo.getListener();
            int port = listener.getPort();
            if (serviceModel.getPort() == -1) {
                serviceModel.setPort(port);
            }
            if (serviceModel.getTargetPort() == -1) {
                serviceModel.setTargetPort(port);
            }

            serviceModel.setProtocol("http");

            if (listener.getKeyStore().isPresent()) {
                Set<SecretModel> secretModels = null;
                try {
                    secretModels = processSecureSocketAnnotation(listener);
                } catch (KubernetesPluginException e) {
                    e.printStackTrace();
                }
                KubernetesContext.getInstance().getDataHolder().addListenerSecret(listener.getName(), secretModels);
                KubernetesContext.getInstance().getDataHolder().addSecrets(secretModels);
                serviceModel.setProtocol("https");
            }

            KubernetesContext.getInstance().getDataHolder().addServiceModel(serviceModel);
        }
    }

    private void addHPA() {
        PodAutoscalerModel podAutoscalerModel = new PodAutoscalerModel();
        KubernetesContext.getInstance().getDataHolder().setPodAutoscalerModel(podAutoscalerModel);
    }

    private void addDeployments() {
        DeploymentModel deploymentModel = new DeploymentModel();

        String dockerHost = System.getenv(DOCKER_HOST);
        if (!KubernetesUtils.isBlank(dockerHost)) {
            deploymentModel.setDockerHost(dockerHost);
        }
        String dockerCertPath = System.getenv(DOCKER_CERT_PATH);
        if (!KubernetesUtils.isBlank(dockerCertPath)) {
            deploymentModel.setDockerCertPath(dockerCertPath);
        }
        KubernetesContext.getInstance().getDataHolder().setDeploymentModel(deploymentModel);
    }

    private boolean isServicesExist(List<ServiceInfo> serviceList) {
        return serviceList.size() > 0;
    }

    /**
     * Extract key-store/trust-store file location from listener.
     *
     * @param listenerInfo Listener info
     * @return List of @{@link SecretModel} objects
     */
    private Set<SecretModel> processSecureSocketAnnotation(ListenerInfo listenerInfo)
            throws KubernetesPluginException {
        Set<SecretModel> secrets = new HashSet<>();

        Optional<Store> keyStore = listenerInfo.getKeyStore();
        Optional<Store> trustStore = listenerInfo.getTrustStore();

        if (keyStore.isPresent() && trustStore.isPresent()) {
            String keyStoreFile = keyStore.get().getPath();
            String trustStoreFile = trustStore.get().getPath();
            if (getMountPath(keyStoreFile).equals(getMountPath(trustStoreFile))) {
                // trust-store and key-store mount to same path
                String keyStoreContent = readSecretFile(keyStoreFile);
                String trustStoreContent = readSecretFile(trustStoreFile);
                SecretModel secretModel = new SecretModel();
                secretModel.setName(getValidName(listenerInfo.getName()) + "-secure-socket");
                secretModel.setMountPath(getMountPath(keyStoreFile));
                Map<String, String> dataMap = new HashMap<>();
                dataMap.put(String.valueOf(Paths.get(keyStoreFile).getFileName()), keyStoreContent);
                dataMap.put(String.valueOf(Paths.get(trustStoreFile).getFileName()), trustStoreContent);
                secretModel.setData(dataMap);
                secrets.add(secretModel);
                return secrets;
            }
        }
        if (keyStore.isPresent()) {
            String keyStoreFile = keyStore.get().getPath();
            String keyStoreContent = readSecretFile(keyStoreFile);
            SecretModel secretModel = new SecretModel();
            secretModel.setName(getValidName(listenerInfo.getName()) + "-keystore");
            secretModel.setMountPath(getMountPath(keyStoreFile));
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put(String.valueOf(Paths.get(keyStoreFile).getFileName()), keyStoreContent);
            secretModel.setData(dataMap);
            secrets.add(secretModel);
        }
        if (trustStore.isPresent()) {
            String trustStoreFile = trustStore.get().getPath();
            String trustStoreContent = readSecretFile(trustStoreFile);
            SecretModel secretModel = new SecretModel();
            secretModel.setName(getValidName(listenerInfo.getName()) + "-truststore");
            secretModel.setMountPath(getMountPath(trustStoreFile));
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put(String.valueOf(Paths.get(trustStoreFile).getFileName()), trustStoreContent);
            secretModel.setData(dataMap);
            secrets.add(secretModel);
        }
        return secrets;
    }

    private String readSecretFile(String filePath) throws KubernetesPluginException {
        if (filePath.contains("${ballerina.home}")) {
            // Resolve variable locally before reading file.
            String ballerinaHome = System.getProperty("ballerina.home");
            filePath = filePath.replace("${ballerina.home}", ballerinaHome);
        }
        Path dataFilePath = Paths.get(filePath);
        return Base64.encodeBase64String(KubernetesUtils.readFileContent(dataFilePath));
    }

    private String getMountPath(String mountPath) throws KubernetesPluginException {
        Path parentPath = Paths.get(mountPath).getParent();
        if (parentPath != null && ".".equals(parentPath.toString())) {
            // Mounts to the same path overriding the source file.
            throw new KubernetesPluginException("Invalid path: " + mountPath + ". " +
                    "Providing relative path in the same level as source file is not supported with code2cloud." +
                    "Please create a subfolder and provide the relative path. " +
                    "eg: './security/ballerinaKeystore.p12'");
        }
        if (!Paths.get(mountPath).isAbsolute()) {
            mountPath = BALLERINA_HOME + File.separator + mountPath;
        }
        return String.valueOf(Paths.get(mountPath).getParent());
    }


    public List<Diagnostic> validateTomlWithSource(Toml toml) {
        List<Diagnostic> diagnosticInfoList = new ArrayList<>();
        if (toml == null) {
            return Collections.emptyList();
        }

        ProjectServiceInfo projectService = new ProjectServiceInfo(project);
        Optional<Toml> ready = toml.getTable("cloud.deployment.probes.readiness");
        ready.ifPresent(value -> diagnosticInfoList.addAll(validateProbe(projectService, value, ProbeType.READINESS)));
        Optional<Toml> live = toml.getTable("cloud.deployment.probes.liveness");
        live.ifPresent(value -> diagnosticInfoList.addAll(validateProbe(projectService, value, ProbeType.LIVENESS)));


        return diagnosticInfoList;
    }

    private List<Diagnostic> validateProbe(ProjectServiceInfo projectServiceInfo, Toml probe, ProbeType type) {
        List<Diagnostic> diagnosticInfos = new ArrayList<>();
        if (probe.get("port").isEmpty() || probe.get("path").isEmpty()) {
            return Collections.emptyList();
        }
        TomlValueNode portNode = probe.get("port").get();
        TomlValueNode pathNode = probe.get("path").get();
        long port = ((TomlLongValueNode) portNode).getValue();
        String path = ((TomlStringValueNode) pathNode).getValue();

        List<ListenerInfo> listenerList = projectServiceInfo.getListenerList();
        if (!isListenerPortValid(port, listenerList)) {
            Diagnostic portDiag = getTomlDiagnostic(portNode.location(), "C2C001", "error.invalid.port",
                    DiagnosticSeverity.ERROR, "Invalid " + type.getValue() + " Port");
            Diagnostic pathDiag = getTomlDiagnostic(pathNode.location(), "C2C002", "error.invalid.path",
                    DiagnosticSeverity.ERROR, "Invalid " + type.getValue() + " Path");
            diagnosticInfos.add(portDiag);
            diagnosticInfos.add(pathDiag);
            return diagnosticInfos;
        }

        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();
        for (ServiceInfo serviceInfo : serviceList) {
            int serviceListenerPort = serviceInfo.getListener().getPort();
            if (serviceListenerPort == port) {
                String serviceName = serviceInfo.getServicePath().trim();
                if (!path.startsWith(serviceName)) {
                    Diagnostic diag = getTomlDiagnostic(pathNode.location(), "C2C003", "error.invalid" +
                            ".service.path", DiagnosticSeverity.ERROR, "Invalid " + type.getValue() + " " +
                            "Service Path");
                    diagnosticInfos.add(diag);
                    return diagnosticInfos;
                }

                boolean resourceFound = false;
                List<ResourceInfo> resourceInfoList = serviceInfo.getResourceInfo();
                for (ResourceInfo resourceInfo : resourceInfoList) {
                    String balResourceName = trimResourcePath(resourceInfo.getPath());
                    String resourcePath = trimResourcePath(serviceName) + "/" + balResourceName;
                    if (balResourceName.equals(".")) {
                        resourcePath = trimResourcePath(serviceName);
                    }
                    if (resourcePath.equals(trimResourcePath(path))) {
                        resourceFound = true;
                        break;
                    }
                }
                if (!resourceFound) {
                    Diagnostic diag = getTomlDiagnostic(pathNode.location(), "C2C004", "error.invalid" +
                                    ".resource.path", DiagnosticSeverity.ERROR,
                            "Invalid " + type.getValue() + " Resource Path");
                    diagnosticInfos.add(diag);
                }
            }
        }
        return diagnosticInfos;
    }

    private boolean isListenerPortValid(long port, List<ListenerInfo> listenerList) {
        for (ListenerInfo listener : listenerList) {
            int givenPort = listener.getPort();
            if (givenPort == port) {
                return true;
            }
            break;
        }
        return false;
    }

    private String trimResourcePath(String resourcePath) {
        resourcePath = resourcePath.trim();
        if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }
        if (resourcePath.endsWith("/")) {
            resourcePath = resourcePath.substring(0, resourcePath.length() - 1);
        }
        return resourcePath;
    }

    private TomlDiagnostic getTomlDiagnostic(TomlNodeLocation location, String code, String template,
                                             DiagnosticSeverity severity, String message) {
        io.ballerina.tools.diagnostics.DiagnosticInfo
                diagnosticInfo = new io.ballerina.tools.diagnostics.DiagnosticInfo(code, template, severity);
        return new TomlDiagnostic(location, diagnosticInfo, message);
    }

    enum ProbeType {
        READINESS("Readiness Probe"),
        LIVENESS("Liveness Probe");

        private String value;

        ProbeType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
