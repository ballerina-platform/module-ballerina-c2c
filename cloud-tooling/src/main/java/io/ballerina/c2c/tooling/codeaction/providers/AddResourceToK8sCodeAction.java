/*
 * Copyright (c) 2021, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ballerina.c2c.tooling.codeaction.providers;

import io.ballerina.c2c.tooling.toml.CommonUtil;
import io.ballerina.c2c.tooling.toml.TomlSyntaxTreeUtil;
import io.ballerina.c2c.util.ListenerInfo;
import io.ballerina.c2c.util.ProjectServiceInfo;
import io.ballerina.c2c.util.ServiceInfo;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.NonTerminalNode;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.projects.CloudToml;
import io.ballerina.projects.Project;
import io.ballerina.projects.util.ProjectConstants;
import io.ballerina.toml.syntax.tree.DocumentMemberDeclarationNode;
import io.ballerina.toml.syntax.tree.DocumentNode;
import io.ballerina.toml.syntax.tree.SyntaxTree;
import io.ballerina.toml.syntax.tree.TableNode;
import org.ballerinalang.annotation.JavaSPIService;
import org.ballerinalang.langserver.commons.CodeActionContext;
import org.ballerinalang.langserver.commons.codeaction.spi.RangeBasedCodeActionProvider;
import org.ballerinalang.langserver.commons.codeaction.spi.RangeBasedPositionDetails;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Code Action for adding a resource into Cloud.toml.
 *
 * @since 2.0.0
 */
@JavaSPIService("org.ballerinalang.langserver.commons.codeaction.spi.LSCodeActionProvider")
public class AddResourceToK8sCodeAction implements RangeBasedCodeActionProvider {

    @Override
    public List<SyntaxKind> getSyntaxKinds() {
        return Collections.singletonList(SyntaxKind.RESOURCE_ACCESSOR_DEFINITION);
    }

    @Override
    public String getName() {
        return "Add Resource To K8s Code Action";
    }

    @Override
    public List<CodeAction> getCodeActions(CodeActionContext context,
                                           RangeBasedPositionDetails positionDetails) {
        NonTerminalNode matchedNode = positionDetails.matchedCodeActionNode();
        if (matchedNode.kind() != SyntaxKind.RESOURCE_ACCESSOR_DEFINITION) {
            return Collections.emptyList();
        }

        Path k8sPath = context.workspace().projectRoot(context.filePath()).resolve(ProjectConstants.CLOUD_TOML);
        Project project = context.workspace().project(context.filePath()).orElseThrow();
        if (!project.buildOptions().cloud().equals("k8s")) {
            return Collections.emptyList();
        }

        Optional<CloudToml> cloudToml = project.currentPackage().cloudToml();

        if (cloudToml.isEmpty()) {
            return Collections.emptyList();
        }

        SyntaxTree tomlSyntaxTree = cloudToml.get().tomlDocument().syntaxTree();
        DocumentNode documentNode = tomlSyntaxTree.rootNode();

        List<ProbeType> probs = getAvailableProbes(documentNode);
        List<CodeAction> codeActionList = new ArrayList<>();
        for (ProbeType probe : probs) {
            FunctionDefinitionNode functionDefinitionNode = (FunctionDefinitionNode) matchedNode;
            String resourcePath = toAbsoluteServicePath(functionDefinitionNode.relativeResourcePath());

            ServiceDeclarationNode serviceDeclarationNode = (ServiceDeclarationNode) functionDefinitionNode.parent();
            String servicePath = toAbsoluteServicePath(serviceDeclarationNode.absoluteResourcePath());
            List<Integer> ports = getPortOfService(project, servicePath);
            if (ports.isEmpty()) {
                continue;
            }
            // As only one probes are supported
            String importText = generateProbeText(probe, ports.get(0), servicePath, resourcePath);
            int endLine = documentNode.members().get(documentNode.members().size() - 1).lineRange().endLine().line();
            Position position = new Position(endLine + 1, 0);
            List<TextEdit> edits = Collections.singletonList(
                    new TextEdit(new Range(position, position), importText));
            CodeAction action = CommonUtil.createQuickFixCodeAction("Add as " + probe.tableName + " probe", edits,
                    k8sPath.toUri().toString());
            codeActionList.add(action);
        }
        return codeActionList;
    }

    private List<ProbeType> getAvailableProbes(DocumentNode documentNode) {
        List<ProbeType> probs = new ArrayList<>();
        probs.add(ProbeType.LIVENESS);
        probs.add(ProbeType.READINESS);
        for (DocumentMemberDeclarationNode member : documentNode.members()) {
            if (member.kind() == io.ballerina.toml.syntax.tree.SyntaxKind.TABLE) {
                TableNode tableNode = (TableNode) member;
                String tableName = TomlSyntaxTreeUtil.toDottedString(tableNode.identifier().value());
                if (tableName.equals("cloud.deployment.probes.liveness")) {
                    probs.remove(ProbeType.LIVENESS);
                }
                if (tableName.equals("cloud.deployment.probes.readiness")) {
                    probs.remove(ProbeType.READINESS);
                }
            }
        }
        return probs;
    }

    private List<Integer> getPortOfService(Project project, String servicePath) {
        List<Integer> ports = new ArrayList<>();
        ProjectServiceInfo projectServiceInfo = new ProjectServiceInfo(project);
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();
        for (ServiceInfo serviceInfo : serviceList) {
            if (serviceInfo.getServicePath().equals(servicePath)) {
                List<ListenerInfo> listeners = serviceInfo.getListeners();
                for (ListenerInfo listenerInfo : listeners) {
                    ports.add(listenerInfo.getPort());
                }
            }
        }
        return ports;
    }

    private String toAbsoluteServicePath(NodeList<Node> servicePathNodes) {
        StringBuilder absoluteServicePath = new StringBuilder();
        for (Node serviceNode : servicePathNodes) {
            absoluteServicePath.append(serviceNode.toString());
        }
        return absoluteServicePath.toString().trim();
    }

    private String generateProbeText(ProbeType probeType, int port, String servicePath, String resourcePath) {
        if (resourcePath.equals(".")) {
            resourcePath = "";
        } else {
            resourcePath = "/" + resourcePath;
        }
        if (servicePath.equals("/")) {
            return CommonUtil.LINE_SEPARATOR + "[cloud.deployment.probes." + probeType.tableName + "]" +
                    CommonUtil.LINE_SEPARATOR + "port = " + port + CommonUtil.LINE_SEPARATOR +
                    "path = \"" + resourcePath + "\"" + CommonUtil.LINE_SEPARATOR;
        }
        return CommonUtil.LINE_SEPARATOR + "[cloud.deployment.probes." + probeType.tableName + "]" +
                CommonUtil.LINE_SEPARATOR + "port = " + port + CommonUtil.LINE_SEPARATOR +
                "path = \"" + servicePath + resourcePath + "\"" + CommonUtil.LINE_SEPARATOR;
    }

    enum ProbeType {
        LIVENESS("liveness"),
        READINESS("readiness");

        final String tableName;

        ProbeType(String tableName) {
            this.tableName = tableName;
        }
    }
}
