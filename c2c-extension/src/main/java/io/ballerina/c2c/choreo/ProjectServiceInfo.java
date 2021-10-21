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
package io.ballerina.c2c.choreo;

import io.ballerina.c2c.diagnostics.ModuleLevelVariableExtractor;
import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeLocation;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.Package;
import io.ballerina.projects.Project;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents service related data of a Project after parsed from Syntax trees.
 *
 * @since 2.0.0
 */
@Getter
public class ProjectServiceInfo {

    private final List<ChoreoServiceInfo> serviceList;

    public ProjectServiceInfo(Project project, List<Diagnostic> diagnostics) {
        this.serviceList = new ArrayList<>();
        Package currentPackage = project.currentPackage();
        Iterable<Module> modules = currentPackage.modules();
        for (Module module : modules) {
            Collection<DocumentId> documentIds = module.documentIds();
            //Retrieve Module level variables and store in hashmap
            Map<String, Node> moduleLevelVariables = new HashMap<>();
            SemanticModel semanticModel = module.getCompilation().getSemanticModel();

            //TODO Remove when build-time api is out 
            //https://github.com/ballerina-platform/module-ballerina-c2c/issues/138
            for (DocumentId doc : documentIds) {
                Document document = module.document(doc);
                Node node = document.syntaxTree().rootNode();
                ModuleLevelVariableExtractor visitor = new ModuleLevelVariableExtractor(moduleLevelVariables);
                node.accept(visitor);
            }

            for (DocumentId doc : documentIds) {
                Document document = module.document(doc);
                Node node = document.syntaxTree().rootNode();
                ChoreoProjectVisitor visitor =
                        new ChoreoProjectVisitor(moduleLevelVariables, semanticModel, diagnostics);
                node.accept(visitor);
                serviceList.addAll(visitor.getServices());
            }
        }

        if (serviceList.size() > 1) {
            NodeLocation location = serviceList.get(1).getNode().location();
            DiagnosticInfo diagnosticInfo = new DiagnosticInfo("C2C_017", "choreo only supports one service at the " +
                    "moment", DiagnosticSeverity.WARNING);
            diagnostics.add(DiagnosticFactory.createDiagnostic(diagnosticInfo, location));
        }
    }
}
