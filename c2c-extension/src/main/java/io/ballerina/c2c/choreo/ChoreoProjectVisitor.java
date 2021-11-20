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

import io.ballerina.c2c.util.C2CDiagnosticCodes;
import io.ballerina.compiler.api.ModuleID;
import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.AnnotationSymbol;
import io.ballerina.compiler.api.symbols.ClassSymbol;
import io.ballerina.compiler.api.symbols.MethodSymbol;
import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.compiler.api.symbols.ParameterSymbol;
import io.ballerina.compiler.api.symbols.ServiceDeclarationSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.SymbolKind;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeReferenceTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.ballerina.compiler.syntax.tree.BasicLiteralNode;
import io.ballerina.compiler.syntax.tree.ExplicitNewExpressionNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.FunctionArgumentNode;
import io.ballerina.compiler.syntax.tree.IdentifierToken;
import io.ballerina.compiler.syntax.tree.ImplicitNewExpressionNode;
import io.ballerina.compiler.syntax.tree.ModuleVariableDeclarationNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.NodeVisitor;
import io.ballerina.compiler.syntax.tree.ParenthesizedArgList;
import io.ballerina.compiler.syntax.tree.PositionalArgumentNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.SimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.compiler.syntax.tree.TypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.TypedBindingPatternNode;
import io.ballerina.tools.diagnostics.Diagnostic;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Visitor for validation related to code to cloud.
 *
 * @since 2.0.0
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ChoreoProjectVisitor extends NodeVisitor {

    private final List<ChoreoServiceInfo> services = new ArrayList<>();
    private final Map<String, Node> moduleLevelVariables;
    private final SemanticModel semanticModel;
    private final List<Diagnostic> diagnostics;

    public ChoreoProjectVisitor(Map<String, Node> moduleLevelVariables, SemanticModel semanticModel,
                                List<Diagnostic> diagnostics) {
        this.moduleLevelVariables = moduleLevelVariables;
        this.semanticModel = semanticModel;
        this.diagnostics = diagnostics;
    }

    @Override
    public void visit(ServiceDeclarationNode serviceDeclarationNode) {
        ServiceDeclarationSymbol symbol =
                (ServiceDeclarationSymbol) semanticModel.symbol(serviceDeclarationNode).orElseThrow();
        List<TypeSymbol> typeSymbols = symbol.listenerTypes();
        if (typeSymbols.isEmpty()) {
            return;
        }
        String servicePath = toAbsoluteServicePath(serviceDeclarationNode.absoluteResourcePath());
        TypeSymbol typeSymbol = typeSymbols.get(0);
        if (!isC2CNativelySupportedListener(typeSymbol)) {
            processCustomExposedAnnotatedListeners(typeSymbol, servicePath, serviceDeclarationNode);
            return;
        }

        ChoreoListenerInfo choreoListenerInfo = null;
        ExpressionNode expressionNode = serviceDeclarationNode.expressions().get(0);
        if (expressionNode.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
            //External Listener
            //on helloEP
            SimpleNameReferenceNode referenceNode = (SimpleNameReferenceNode) expressionNode;
            String listenerName = referenceNode.name().text();
            Optional<ChoreoListenerInfo> httpsListener = this.getHttpsListener(listenerName);
            if (httpsListener.isEmpty()) {
                diagnostics.add(C2CDiagnosticCodes
                        .createDiagnostic(C2CDiagnosticCodes.FAILED_PORT_RETRIEVAL, expressionNode.location()));
                return;
            }
            choreoListenerInfo = httpsListener.get();
        } else {
            //Inline Listener
            ExplicitNewExpressionNode refNode = (ExplicitNewExpressionNode) expressionNode;
            FunctionArgumentNode functionArgumentNode = refNode.parenthesizedArgList().arguments().get(0);
            if (functionArgumentNode.kind() == SyntaxKind.POSITIONAL_ARG) {
                ExpressionNode expression = ((PositionalArgumentNode) functionArgumentNode).expression();
                Optional<ChoreoListenerInfo> newListenerInfo = getListenerInfo(servicePath, expression);
                if (newListenerInfo.isEmpty()) {
                    return;
                }
                choreoListenerInfo = newListenerInfo.get();
            }
        }
        services.add(new ChoreoServiceInfo(choreoListenerInfo, serviceDeclarationNode, servicePath));
    }

    private Optional<ChoreoListenerInfo> extractListenerInitializer(String listenerName,
                                                                    ImplicitNewExpressionNode initializerNode) {
        ParenthesizedArgList parenthesizedArgList = initializerNode.parenthesizedArgList().get();
        if (parenthesizedArgList.arguments().size() == 0) {
            return Optional.empty();
        }
        FunctionArgumentNode functionArgumentNode = parenthesizedArgList.arguments().get(0);
        ExpressionNode expression = ((PositionalArgumentNode) functionArgumentNode).expression();
        ChoreoListenerInfo choreoListenerInfo;
        if (expression.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
            diagnostics.add(C2CDiagnosticCodes
                    .createDiagnostic(C2CDiagnosticCodes.FAILED_PORT_RETRIEVAL, expression.location()));
            return Optional.empty();
        } else {
            BasicLiteralNode basicLiteralNode = (BasicLiteralNode) expression;
            int port = Integer.parseInt(basicLiteralNode.literalToken().text());
            choreoListenerInfo = new ChoreoListenerInfo(listenerName, port);
        }
        return Optional.of(choreoListenerInfo);
    }

    private Optional<ChoreoListenerInfo> getListenerInfo(String path, ExpressionNode expression) {
        if (expression.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
            //on new http:Listener(port)
            SimpleNameReferenceNode referenceNode = (SimpleNameReferenceNode) expression;
            String variableName = referenceNode.name().text();
            Optional<Integer> port = getPortNumberFromVariable(variableName);
            if (port.isEmpty()) {
                Optional<ChoreoListenerInfo> httpsListener = this.getHttpsListener(variableName);
                if (httpsListener.isEmpty()) {
                    diagnostics.add(C2CDiagnosticCodes
                            .createDiagnostic(C2CDiagnosticCodes.FAILED_PORT_RETRIEVAL, expression.location()));
                    return Optional.empty();
                }
                return httpsListener;
            } else {
                int portNumber = port.get();
                if (portNumber == 0) {
                    return Optional.empty();
                }
                return Optional.of(new ChoreoListenerInfo(path, portNumber));
            }
        } else {
            //on new http:Listener(9091)
            int port = Integer.parseInt(((BasicLiteralNode) expression).literalToken().text());
            return Optional.of(new ChoreoListenerInfo(path, port));
        }
    }

    private void processCustomExposedAnnotatedListeners(TypeSymbol typeSymbol, String servicePath,
                                                        ServiceDeclarationNode serviceDeclarationNode) {
        if (typeSymbol.typeKind() != TypeDescKind.TYPE_REFERENCE) {
            return;
        }
        Symbol typeDefinition = ((TypeReferenceTypeSymbol) typeSymbol).definition();
        if (typeDefinition.kind() != SymbolKind.CLASS) {
            return;
        }
        ClassSymbol classSymbol = (ClassSymbol) typeDefinition;
        if (classSymbol.initMethod().isEmpty()) {
            return;
        }
        // Get the init method of the custom listener because thats where the @cloud:Expose is at.
        // Ex - public function init(@cloud:Expose int port, ListenerConfiguration config) {
        MethodSymbol initSymbol = classSymbol.initMethod().get();
        Optional<List<ParameterSymbol>> paramsList = initSymbol.typeDescriptor().params();
        if (paramsList.isEmpty()) {
            return;
        }
        List<ParameterSymbol> params = paramsList.get();
        for (int i = 0, getSize = params.size(); i < getSize; i++) {
            ParameterSymbol parameterSymbol = params.get(i);
            for (AnnotationSymbol annotation : parameterSymbol.annotations()) {
                Optional<ModuleSymbol> module = annotation.getModule();
                if (module.isEmpty()) {
                    continue;
                }
                ModuleSymbol moduleSymbol = module.get();
                ModuleID id = moduleSymbol.id();
                if (!id.moduleName().equals("cloud")) {
                    continue;
                }
                if (id.orgName().equals("ballerina")) {
                    //@cloud:Expose int port
                    //This is a valid custom listener param for c2c. Next, we need to get the value passed to this 
                    // param. We need to access the syntax tree to get the value as semantic api doesn't have values.
                    Optional<ChoreoListenerInfo> listenerInfo =
                            getPortValueFromSTForCustomListener(servicePath, serviceDeclarationNode, i);
                    if (listenerInfo.isEmpty()) {
                        diagnostics.add(C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.FAILED_PORT_RETRIEVAL,
                                parameterSymbol.location()));
                        continue;
                    }
                    this.services.add(new ChoreoServiceInfo(listenerInfo.get(), serviceDeclarationNode, servicePath));
                }
            }
        }
    }

    private Optional<ChoreoListenerInfo> getPortValueFromSTForCustomListener(String path,
                                                                             ServiceDeclarationNode serviceNode,
                                                                             int paramNo) {
        ExpressionNode expressionNode = serviceNode.expressions().get(0);
        if (expressionNode.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
            //on listener
            SimpleNameReferenceNode referenceNode = (SimpleNameReferenceNode) expressionNode;
            String listenerName = referenceNode.name().text();
            Node node = this.moduleLevelVariables.get(listenerName);
            if (node == null || !(node.kind() == SyntaxKind.IMPLICIT_NEW_EXPRESSION)) {
                return Optional.empty();
            }
            ImplicitNewExpressionNode init = (ImplicitNewExpressionNode) node;
            return extractListenerInitializer(listenerName, init);
        } else {
            ExplicitNewExpressionNode refNode = (ExplicitNewExpressionNode) expressionNode;
            SeparatedNodeList<FunctionArgumentNode> arguments = refNode.parenthesizedArgList().arguments();
            if (arguments.size() > paramNo) {
                FunctionArgumentNode functionArgumentNode = arguments.get(paramNo);
                if (functionArgumentNode.kind() == SyntaxKind.POSITIONAL_ARG) {
                    ExpressionNode expression = ((PositionalArgumentNode) functionArgumentNode).expression();
                    return getListenerInfo(path, expression);
                }
            }
        }
        return Optional.empty();
    }

    private boolean isC2CNativelySupportedListener(TypeSymbol typeSymbol) {
        //To process http:Listener|error recursively.
        if (typeSymbol.typeKind() == TypeDescKind.UNION) {
            UnionTypeSymbol unionTypeSymbol = (UnionTypeSymbol) typeSymbol;
            List<TypeSymbol> typeSymbols = unionTypeSymbol.memberTypeDescriptors();
            for (TypeSymbol symbol : typeSymbols) {
                if (symbol.typeKind() != TypeDescKind.ERROR) {
                    return isC2CNativelySupportedListener(symbol);
                }
            }
        }
        if (typeSymbol.typeKind() == TypeDescKind.TYPE_REFERENCE) {
            Optional<ModuleSymbol> module = typeSymbol.getModule();
            if (module.isEmpty() || module.get().getName().isEmpty()) {
                return false;
            }
            String moduleName = module.get().getName().get();
            switch (moduleName) {
                case "http":
                case "grpc":
                case "graphql":
                case "tcp":
                case "udp":
                    //TODO add other stdlib
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }

    private String toAbsoluteServicePath(NodeList<Node> servicePathNodes) {
        StringBuilder absoluteServicePath = new StringBuilder();
        for (Node serviceNode : servicePathNodes) {
            if (serviceNode.kind() == SyntaxKind.SLASH_TOKEN) {
                absoluteServicePath.append("/");
            } else if (serviceNode.kind() == SyntaxKind.DOT_TOKEN) {
                absoluteServicePath.append(".");
            } else if (serviceNode.kind() == SyntaxKind.IDENTIFIER_TOKEN) {
                IdentifierToken token = (IdentifierToken) serviceNode;
                absoluteServicePath.append(token.text());
            }
        }
        return absoluteServicePath.toString();
    }

    private Optional<ChoreoListenerInfo> getHttpsListener(String variableName) {
        Node node = this.moduleLevelVariables.get(variableName);
        if (node == null || !(node.kind() == SyntaxKind.IMPLICIT_NEW_EXPRESSION)) {
            return Optional.empty();
        }

        ImplicitNewExpressionNode init = (ImplicitNewExpressionNode) node;
        return extractListenerInitializer(variableName, init);
    }

    private Optional<Integer> getPortNumberFromVariable(String variableName) {
        Node node = this.moduleLevelVariables.get(variableName);
        if (node == null || node.kind() != SyntaxKind.MODULE_VAR_DECL) {
            return Optional.empty();
        }
        ModuleVariableDeclarationNode moduleVariableDeclarationNode = (ModuleVariableDeclarationNode) node;
        TypedBindingPatternNode typedBindingPatternNode = moduleVariableDeclarationNode.typedBindingPattern();
        TypeDescriptorNode typeDescriptorNode = typedBindingPatternNode.typeDescriptor();
        if (typeDescriptorNode.kind() != SyntaxKind.INT_TYPE_DESC) {
            return Optional.empty();
        }
        if (moduleVariableDeclarationNode.initializer().isEmpty()) {
            return Optional.empty();
        }
        ExpressionNode expressionNode = moduleVariableDeclarationNode.initializer().get();
        if (expressionNode.kind() == SyntaxKind.REQUIRED_EXPRESSION) {
            diagnostics.add(C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.CONFIGURABLE_NO_DEFAULT,
                    moduleVariableDeclarationNode.location()));
            return Optional.of(0);
        }

        if (expressionNode.kind() != SyntaxKind.NUMERIC_LITERAL) {
            return Optional.empty();
        }

        for (Token qualifier : moduleVariableDeclarationNode.qualifiers()) {
            if ("configurable".equals(qualifier.text())) {
                diagnostics.add(C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.CONFIGURABLE_OVERRIDE,
                        moduleVariableDeclarationNode.location(), variableName));
            }
        }

        BasicLiteralNode basicLiteralNode = (BasicLiteralNode) expressionNode;
        return Optional.of(Integer.parseInt(basicLiteralNode.literalToken().text()));
    }
}
