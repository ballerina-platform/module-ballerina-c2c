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
package io.ballerina.c2c.tooling.codeaction.providers.kubernetes;

import io.ballerina.c2c.tooling.codeaction.toml.ProjectServiceInfoHolder;
import io.ballerina.c2c.tooling.toml.CommonUtil;
import io.ballerina.c2c.tooling.toml.Probe;
import io.ballerina.c2c.tooling.toml.TomlSyntaxTreeUtil;
import io.ballerina.c2c.util.ProjectServiceInfo;
import io.ballerina.c2c.util.ServiceInfo;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.projects.Project;
import org.ballerinalang.langserver.commons.CodeActionContext;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Abstract class for handling Invalid resource related code actions.
 *
 * @since 2.0.0
 */
public abstract class AbstractInvalidResourceCodeAction extends ProbeBasedDiagnosticAction {

    public List<CodeAction> addResourceToService(Diagnostic diagnostic, CodeActionContext ctx, Probe probe) {
        Optional<Project> project = ctx.workspace().project(ctx.filePath());
        ProjectServiceInfo projectServiceInfo =
                ProjectServiceInfoHolder.getInstance(ctx.languageServercontext()).getProjectInfo(project.orElseThrow());
        List<ServiceInfo> serviceList = projectServiceInfo.getServiceList();
        List<CodeAction> codeActionList = new ArrayList<>();
        for (ServiceInfo service : serviceList) {
            String filePath = service.getNode().syntaxTree().filePath();
            int port = service.getListener().getPort();
            if (probe.getPort().getValue() == port) {
                Path balFilePath = ctx.workspace().projectRoot(ctx.filePath()).resolve(filePath);
                NodeList<Node> members = service.getNode().members();
                Node lastResource = members.get(members.size() - 1);
                Position position = new Position(lastResource.lineRange().endLine().line() + 1, 0);

                CodeAction action = new CodeAction();
                action.setKind(CodeActionKind.QuickFix);
                String importText = generateProbeFunctionText(service, probe);
                List<TextEdit> edits = Collections.singletonList(
                        new TextEdit(new Range(position, position), importText));
                action.setEdit(new WorkspaceEdit(Collections.singletonList(Either.forLeft(
                        new TextDocumentEdit(
                                new VersionedTextDocumentIdentifier(balFilePath.toUri().toString(), null),
                                edits)))));
                action.setTitle("Add Resource to Service");
                List<Diagnostic> cursorDiagnostics = new ArrayList<>();
                cursorDiagnostics.add(diagnostic);
                action.setDiagnostics(cursorDiagnostics);
                codeActionList.add(action);
                break;
            }
        }

        return codeActionList;
    }

    private String generateProbeFunctionText(ServiceInfo service, Probe probe) {
        String tomlPath = TomlSyntaxTreeUtil.trimResourcePath(probe.getPath().getValue());
        String serviceName = TomlSyntaxTreeUtil.trimResourcePath(service.getServicePath());
        String serviceResourcePath;
        if (tomlPath.equals(serviceName)) {
            serviceResourcePath = ".";
        } else if (serviceName.equals("")) {
            serviceResourcePath = tomlPath;
        } else if (tomlPath.startsWith(serviceName)) {
            serviceResourcePath = tomlPath.substring(serviceName.length() + 1);
        } else {
            serviceResourcePath = tomlPath;
        }
        return String.format("    resource function get %s() returns boolean {%s        return true;%s    }%s",
                serviceResourcePath, CommonUtil.LINE_SEPARATOR, CommonUtil.LINE_SEPARATOR, CommonUtil.LINE_SEPARATOR);
    }
}
