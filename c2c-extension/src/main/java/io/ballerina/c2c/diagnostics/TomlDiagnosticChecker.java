package io.ballerina.c2c.diagnostics;

import io.ballerina.c2c.models.KubernetesContext;
import io.ballerina.c2c.utils.TomlHelper;
import io.ballerina.projects.directory.BuildProject;
import io.ballerina.toml.api.Toml;
import io.ballerina.toml.semantic.ast.TomlLongValueNode;
import io.ballerina.toml.semantic.ast.TomlStringValueNode;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Responsible for validation against ballerina documents.
 *
 * @since 2.0.0
 */
public class TomlDiagnosticChecker {

    public TomlDiagnosticChecker() {
    }

    public List<DiagnosticInfo> validateTomlWithSource(Toml toml, Path projectPath) {
        List<DiagnosticInfo> diagnosticInfoList = new ArrayList<>();
        if (toml == null) {
            return Collections.emptyList();
        }

        ProjectServiceInfo projectServiceInfo = testBuildProjectAPI(projectPath);
        Toml ready = TomlHelper.getTable(toml, "cloud.deployment.probes.readiness");
        if (ready != null) {
            diagnosticInfoList.addAll(validateProbe(projectServiceInfo, ready, ProbeType.READINESS));
        }
        Toml live = TomlHelper.getTable(toml, "cloud.deployment.probes.liveness");
        if (live != null) {
            diagnosticInfoList.addAll(validateProbe(projectServiceInfo, live, ProbeType.LIVENESS));
        }

        return diagnosticInfoList;
    }

    private List<DiagnosticInfo> validateProbe(ProjectServiceInfo projectServiceInfo, Toml probe, ProbeType type) {
        List<DiagnosticInfo> diagnosticInfos = new ArrayList<>();
        long port = ((TomlLongValueNode) probe.get("port")).getValue();
        String path = ((TomlStringValueNode) probe.get("path")).getValue();

        Map<String, List<ListenerInfo>> listenerMap = projectServiceInfo.getListenerMap();
        if (!isListenerPortValid(port, listenerMap)) {
            DiagnosticInfo portDiag = new DiagnosticInfo(DiagnosticSeverity.ERROR,
                    KubernetesContext.getInstance().getCurrentPackage(), probe.get("port").location(),
                    "Invalid " + type.getValue() + " Port");
            DiagnosticInfo pathDiag = new DiagnosticInfo(DiagnosticSeverity.ERROR,
                    KubernetesContext.getInstance().getCurrentPackage(), probe.get("path").location(),
                    "Invalid " + type.getValue() + " Path");
            diagnosticInfos.add(portDiag);
            diagnosticInfos.add(pathDiag);
            return diagnosticInfos;
        }

        Map<String, List<ServiceInfo>> stringServiceInfoMap = projectServiceInfo.getServiceMap();

        for (List<ServiceInfo> serviceList : stringServiceInfoMap.values()) {
            for (ServiceInfo serviceInfo : serviceList) {
                int serviceListenerPort = serviceInfo.getListener().getPort();
                if (serviceListenerPort == port) {
                    String serviceName = serviceInfo.getServiceName().trim();
                    if (!path.startsWith(serviceName)) {
                        DiagnosticInfo diagnosticInfo = new DiagnosticInfo(DiagnosticSeverity.ERROR,
                                KubernetesContext.getInstance().getCurrentPackage(), probe.get("path").location(),
                                "Invalid " + type.getValue() + " Service Path");
                        diagnosticInfos.add(diagnosticInfo);
                        return diagnosticInfos;
                    }

                    boolean resourceFound = false;
                    List<ResourceInfo> resourceInfoList = serviceInfo.getResourceInfo();
                    for (ResourceInfo resourceInfo : resourceInfoList) {
                        String resourcePath = trimResourcePath(serviceName) + "/" +
                                trimResourcePath(resourceInfo.getPath());
                        if (resourcePath.equals(trimResourcePath(path))) {
                            resourceFound = true;
                            break;
                        }
                    }
                    if (!resourceFound) {
                        DiagnosticInfo diagnosticInfo = new DiagnosticInfo(DiagnosticSeverity.ERROR,
                                KubernetesContext.getInstance().getCurrentPackage(), probe.get("path").location(),
                                "Invalid " + type.getValue() + " Resource Path");
                        diagnosticInfos.add(diagnosticInfo);
                    }
                }
            }
        }
        return diagnosticInfos;
    }

    private boolean isListenerPortValid(long port, Map<String, List<ListenerInfo>> listenerMap) {
        for (List<ListenerInfo> listenerList : listenerMap.values()) {
            for (ListenerInfo listener : listenerList) {
                int givenPort = listener.getPort();
                if (givenPort == port) {
                    return true;
                }
                break;
            }
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

    public ProjectServiceInfo testBuildProjectAPI(Path projectPath) {
        System.setProperty("ballerina.home", "/home/anjana/repos/module-ballerina-c2c/c2c-ballerina/build/" +
                "extracted-distribution/jballerina-tools-2.0.0-Preview9-SNAPSHOT");

        BuildProject project = BuildProject.load(projectPath);

        return new ProjectServiceInfo(project);
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
