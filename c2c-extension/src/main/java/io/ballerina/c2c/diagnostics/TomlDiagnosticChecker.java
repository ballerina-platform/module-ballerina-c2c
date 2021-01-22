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

import io.ballerina.c2c.utils.TomlHelper;
import io.ballerina.projects.Project;
import io.ballerina.toml.api.Toml;
import io.ballerina.toml.semantic.ast.TomlLongValueNode;
import io.ballerina.toml.semantic.ast.TomlStringValueNode;
import io.ballerina.toml.semantic.diagnostics.TomlDiagnostic;
import io.ballerina.toml.semantic.diagnostics.TomlNodeLocation;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;

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

    public List<Diagnostic> validateTomlWithSource(Toml toml, Project project) {
        List<Diagnostic> diagnosticInfoList = new ArrayList<>();
        if (toml == null) {
            return Collections.emptyList();
        }

        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
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

    private List<Diagnostic> validateProbe(ProjectServiceInfo projectServiceInfo, Toml probe, ProbeType type) {
        List<Diagnostic> diagnosticInfos = new ArrayList<>();
        long port = ((TomlLongValueNode) probe.get("port")).getValue();
        String path = ((TomlStringValueNode) probe.get("path")).getValue();

        Map<String, List<ListenerInfo>> listenerMap = projectServiceInfo.getListenerMap();
        if (!isListenerPortValid(port, listenerMap)) {
            Diagnostic portDiag = getTomlDiagnostic(probe.get("port").location(), "C2C001", "error.invalid.port",
                    DiagnosticSeverity.ERROR, "Invalid " + type.getValue() + " Port");
            Diagnostic pathDiag = getTomlDiagnostic(probe.get("path").location(), "C2C002", "error.invalid.path",
                    DiagnosticSeverity.ERROR, "Invalid " + type.getValue() + " Path");
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
                        Diagnostic diag = getTomlDiagnostic(probe.get("path").location(), "C2C003", "error.invalid" +
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
                        Diagnostic diag = getTomlDiagnostic(probe.get("path").location(), "C2C004", "error.invalid" +
                                        ".resource.path", DiagnosticSeverity.ERROR,
                                "Invalid " + type.getValue() + " Resource Path");
                        diagnosticInfos.add(diag);
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
