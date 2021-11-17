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
package io.ballerina.c2c.tooling.codeaction.diagnostics;

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
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.IdentifierToken;
import io.ballerina.compiler.syntax.tree.ImplicitNewExpressionNode;
import io.ballerina.compiler.syntax.tree.MappingConstructorExpressionNode;
import io.ballerina.compiler.syntax.tree.MappingFieldNode;
import io.ballerina.compiler.syntax.tree.ModuleVariableDeclarationNode;
import io.ballerina.compiler.syntax.tree.NamedArgumentNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.NodeVisitor;
import io.ballerina.compiler.syntax.tree.ParenthesizedArgList;
import io.ballerina.compiler.syntax.tree.PositionalArgumentNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.SimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SpecificFieldNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.compiler.syntax.tree.TypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.TypedBindingPatternNode;
import io.ballerina.tools.diagnostics.Diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Visitor for validation related to code to cloud.
 *
 * @since 2.0.0
 */
public class C2CVisitor extends NodeVisitor {

    private final List<ServiceInfo> services = new ArrayList<>();
    private final Map<String, Node> moduleLevelVariables;
    private final SemanticModel semanticModel;
    private final List<Diagnostic> diagnostics;

    public C2CVisitor(Map<String, Node> moduleLevelVariables, SemanticModel semanticModel,
                      List<Diagnostic> diagnostics) {
        this.moduleLevelVariables = moduleLevelVariables;
        this.semanticModel = semanticModel;
        this.diagnostics = diagnostics;
    }

    public List<ServiceInfo> getServices() {
        return services;
    }

    public List<Diagnostic> getDiagnostics() {
        return diagnostics;
    }

    private String getNameOfIdentifier(Node node) {
        if (node.kind() == SyntaxKind.IDENTIFIER_TOKEN) {
            return ((IdentifierToken) node).text();
        }
        return null;
    }
    
    private String extractString(ExpressionNode expressionNode) {
        if (expressionNode.kind() == SyntaxKind.STRING_LITERAL) {
            String text = ((BasicLiteralNode) expressionNode).literalToken().text();
            return text.substring(1, text.length() - 1);
        }
        this.diagnostics.add(C2CDiagnosticCodes
                .createDiagnostic(C2CDiagnosticCodes.VALUE_STRING_ONLY_SUPPORTED, expressionNode.location()));
        return null;
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

        ListenerInfo listenerInfo = null;
        ExpressionNode expressionNode = serviceDeclarationNode.expressions().get(0);
        if (expressionNode.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
            //External Listener
            //on helloEP
            SimpleNameReferenceNode referenceNode = (SimpleNameReferenceNode) expressionNode;
            String listenerName = referenceNode.name().text();
            Optional<ListenerInfo> httpsListener = this.getHttpsListener(listenerName);
            if (httpsListener.isEmpty()) {
                diagnostics.add(C2CDiagnosticCodes
                        .createDiagnostic(C2CDiagnosticCodes.FAILED_PORT_RETRIEVAL, expressionNode.location()));
                return;
            }
            listenerInfo = httpsListener.get();
        } else {
            //Inline Listener
            ExplicitNewExpressionNode refNode = (ExplicitNewExpressionNode) expressionNode;
            FunctionArgumentNode functionArgumentNode = refNode.parenthesizedArgList().arguments().get(0);
            if (functionArgumentNode.kind() == SyntaxKind.POSITIONAL_ARG) {
                ExpressionNode expression = ((PositionalArgumentNode) functionArgumentNode).expression();
                Optional<ListenerInfo> newListenerInfo = getListenerInfo(servicePath, expression);
                if (newListenerInfo.isEmpty()) {
                    return;
                }
                listenerInfo = newListenerInfo.get();
            }

            //Inline Http config
            if (refNode.parenthesizedArgList().arguments().size() > 1) {
                FunctionArgumentNode secondParamExpression = refNode.parenthesizedArgList().arguments().get(1);
                Optional<HttpsConfig> config = extractKeyStores(secondParamExpression);
                config.ifPresent(listenerInfo::setConfig);
            }
        }
        ServiceInfo serviceInfo = new ServiceInfo(listenerInfo, serviceDeclarationNode, servicePath);
        NodeList<Node> function = serviceDeclarationNode.members();
        for (Node node : function) {
            if (node.kind() == SyntaxKind.RESOURCE_ACCESSOR_DEFINITION) {
                FunctionDefinitionNode functionDefinitionNode = (FunctionDefinitionNode) node;
                String httpMethod = functionDefinitionNode.functionName().text();
                String resourcePath = toAbsoluteServicePath(functionDefinitionNode.relativeResourcePath());
                serviceInfo.addResource(new ResourceInfo(functionDefinitionNode, httpMethod, resourcePath));
            }
            this.visitSyntaxNode(node);
        }
        services.add(serviceInfo);
    }

    private Optional<ListenerInfo> extractListenerInitializer(String listenerName,
                                                              ImplicitNewExpressionNode initializerNode) {
        ParenthesizedArgList parenthesizedArgList = initializerNode.parenthesizedArgList().get();
        if (parenthesizedArgList.arguments().size() == 0) {
            return Optional.empty();
        }
        FunctionArgumentNode functionArgumentNode = parenthesizedArgList.arguments().get(0);
        ExpressionNode expression = ((PositionalArgumentNode) functionArgumentNode).expression();
        ListenerInfo listenerInfo;
        if (expression.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
            diagnostics.add(C2CDiagnosticCodes
                    .createDiagnostic(C2CDiagnosticCodes.FAILED_PORT_RETRIEVAL, expression.location()));
            return Optional.empty();
        } else {
            BasicLiteralNode basicLiteralNode = (BasicLiteralNode) expression;
            int port = Integer.parseInt(basicLiteralNode.literalToken().text());
            listenerInfo = new ListenerInfo(listenerName, port);
        }
        return Optional.of(listenerInfo);
    }

    private Optional<HttpsConfig> extractKeyStores(FunctionArgumentNode functionArgumentNode) {
        if (functionArgumentNode.kind() == SyntaxKind.POSITIONAL_ARG) {
            PositionalArgumentNode positionalArgumentNode = (PositionalArgumentNode) functionArgumentNode;
            if (positionalArgumentNode.expression().kind() == SyntaxKind.MAPPING_CONSTRUCTOR) {
                return processFieldsInHttpConfig((MappingConstructorExpressionNode)
                        positionalArgumentNode.expression());
            } else if (positionalArgumentNode.expression().kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
                String varName = ((SimpleNameReferenceNode) positionalArgumentNode.expression()).name().text();
                return getHttpsListenerConfig(varName);
            }
        } else if (functionArgumentNode.kind() == SyntaxKind.NAMED_ARG) {
            NamedArgumentNode namedArgumentNode = (NamedArgumentNode) functionArgumentNode;
            ExpressionNode expression = namedArgumentNode.expression();
            if (expression.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
                String varName = ((SimpleNameReferenceNode) expression).name().text();
                return getHttpsListenerConfig(varName);
            } else if (expression.kind() == SyntaxKind.MAPPING_CONSTRUCTOR) {
                return processSecureSocketValue((MappingConstructorExpressionNode) expression);
            }
        }
        return Optional.empty();
    }

    private Optional<HttpsConfig> processFieldsInHttpConfig(MappingConstructorExpressionNode mapping) {
        SeparatedNodeList<MappingFieldNode> fields = mapping.fields();
        for (MappingFieldNode mappingFieldNode : fields) {
            if (mappingFieldNode.kind() != SyntaxKind.SPECIFIC_FIELD) {
                continue;
            }
            SpecificFieldNode specificFieldNode = (SpecificFieldNode) mappingFieldNode;
            Node node = specificFieldNode.fieldName();
            if (node.kind() == SyntaxKind.IDENTIFIER_TOKEN && ((IdentifierToken) node).text().equals("secureSocket")) {
                if (specificFieldNode.valueExpr().isPresent() &&
                        specificFieldNode.valueExpr().get().kind() == SyntaxKind.MAPPING_CONSTRUCTOR) {
                    MappingConstructorExpressionNode mappingConstructorExpressionNode =
                            (MappingConstructorExpressionNode) specificFieldNode.valueExpr().get();
                    return (processSecureSocketValue(mappingConstructorExpressionNode));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<HttpsConfig> processSecureSocketValue(MappingConstructorExpressionNode mappingConstructorNode) {
        SeparatedNodeList<MappingFieldNode> socketChilds = mappingConstructorNode.fields();
        HttpsConfig httpsConfig = new HttpsConfig();
        for (MappingFieldNode child : socketChilds) {
            if (child.kind() != SyntaxKind.SPECIFIC_FIELD) {
                continue;
            }
            SpecificFieldNode specificField = (SpecificFieldNode) child;
            String fieldName = getNameOfIdentifier(specificField.fieldName());
            if ("key".equals(fieldName)) {
                SecureSocketConfig secureSocket = getSecureSocketConfig(specificField.valueExpr().get());
                httpsConfig.setSecureSocketConfig(secureSocket);
            } else if ("mutualSsl".equals(fieldName)) {
                MutualSSLConfig mutualSSLConfig = getMutualSSLConfig(specificField.valueExpr().get());
                httpsConfig.setMutualSSLConfig(mutualSSLConfig);
            } else if ("cert".equals(fieldName)) {
                MutualSSLConfig mutualSSLConfig = getMutualSSLConfig(specificField.valueExpr().get());
                httpsConfig.setMutualSSLConfig(mutualSSLConfig);
            }
        }
        return Optional.of(httpsConfig);
    }

    private MutualSSLConfig getMutualSSLConfig(ExpressionNode expressionNode) {
        MutualSSLConfig mutualSSLConfig = new MutualSSLConfig();
        if (expressionNode.kind() == SyntaxKind.STRING_LITERAL) {
            mutualSSLConfig.setPath(extractString(expressionNode));
            return mutualSSLConfig;
        }
        MappingConstructorExpressionNode expressionNode1 = (MappingConstructorExpressionNode) expressionNode;
        SeparatedNodeList<MappingFieldNode> fields = expressionNode1.fields();
        for (MappingFieldNode field : fields) {
            if (field.kind() != SyntaxKind.SPECIFIC_FIELD) {
                continue;
            }
            SpecificFieldNode specificFieldNode = (SpecificFieldNode) field;
            String nameOfIdentifier = getNameOfIdentifier(specificFieldNode.fieldName());
            if ("cert".equals(nameOfIdentifier)) {
                ExpressionNode certField = specificFieldNode.valueExpr().get();
                if (certField.kind() == SyntaxKind.MAPPING_CONSTRUCTOR) {
                    for (MappingFieldNode mappingFieldNode : ((MappingConstructorExpressionNode) certField).fields()) {
                        if (mappingFieldNode.kind() != SyntaxKind.SPECIFIC_FIELD) {
                            continue;
                        }
                        SpecificFieldNode certSpecificField = (SpecificFieldNode) mappingFieldNode;
                        String fieldName = getNameOfIdentifier(certSpecificField.fieldName());
                        if ("path".equals(fieldName)) {
                            mutualSSLConfig.setPath(extractString(certSpecificField.valueExpr().get()));
                        }
                    }
                } else {
                    mutualSSLConfig.setPath(extractString(certField));
                }
            } else if ("path".equals(nameOfIdentifier)) {
                mutualSSLConfig.setPath(extractString(specificFieldNode.valueExpr().get()));
            }
        }
        return mutualSSLConfig;
    }

    private SecureSocketConfig getSecureSocketConfig(ExpressionNode expressionNode) {
        MappingConstructorExpressionNode expressionNode1 = (MappingConstructorExpressionNode) expressionNode;
        SeparatedNodeList<MappingFieldNode> fields = expressionNode1.fields();
        SecureSocketConfig secureSocketConfig = new SecureSocketConfig();
        for (MappingFieldNode field : fields) {
            if (field.kind() != SyntaxKind.SPECIFIC_FIELD) {
                continue;
            }
            SpecificFieldNode specificFieldNode = (SpecificFieldNode) field;
            String nameOfIdentifier = getNameOfIdentifier(specificFieldNode.fieldName());
            if (("certFile").equals(nameOfIdentifier)) {
                secureSocketConfig.setCertFile(extractString(specificFieldNode.valueExpr().get()));
            } else if ("keyFile".equals(nameOfIdentifier)) {
                secureSocketConfig.setKeyFile(extractString(specificFieldNode.valueExpr().get()));
            } else if ("path".equals(nameOfIdentifier)) {
                secureSocketConfig.setPath(extractString(specificFieldNode.valueExpr().get()));
            }
        }
        return secureSocketConfig;
    }

    private Optional<ListenerInfo> getListenerInfo(String path, ExpressionNode expression) {
        if (expression.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
            //on new http:Listener(port)
            SimpleNameReferenceNode referenceNode = (SimpleNameReferenceNode) expression;
            String variableName = referenceNode.name().text();
            Optional<Integer> port = getPortNumberFromVariable(variableName);
            if (port.isEmpty()) {
                Optional<ListenerInfo> httpsListener = this.getHttpsListener(variableName);
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
                return Optional.of(new ListenerInfo(path, portNumber));
            }
        } else {
            //on new http:Listener(9091)
            int port = Integer.parseInt(((BasicLiteralNode) expression).literalToken().text());
            return Optional.of(new ListenerInfo(path, port));
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
                    Optional<ListenerInfo> listenerInfo =
                            getPortValueFromSTForCustomListener(servicePath, serviceDeclarationNode, i);
                    if (listenerInfo.isEmpty()) {
                        diagnostics.add(C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.FAILED_PORT_RETRIEVAL,
                                parameterSymbol.location()));
                        continue;
                    }
                    this.services.add(new ServiceInfo(listenerInfo.get(), serviceDeclarationNode, servicePath));
                }
            }
        }
    }

    private Optional<ListenerInfo> getPortValueFromSTForCustomListener(String path, ServiceDeclarationNode serviceNode,
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
                case "websocket":
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

    private Optional<HttpsConfig> getHttpsListenerConfig(String variableName) {
        Node node = this.moduleLevelVariables.get(variableName);
        if (node == null || node.kind() != SyntaxKind.MAPPING_CONSTRUCTOR) {
            return Optional.empty();
        }
        MappingConstructorExpressionNode initializer = (MappingConstructorExpressionNode) node;
        return processFieldsInHttpConfig(initializer);
    }

    private Optional<ListenerInfo> getHttpsListener(String variableName) {
        Node node = this.moduleLevelVariables.get(variableName);
        if (node == null || !(node.kind() == SyntaxKind.IMPLICIT_NEW_EXPRESSION)) {
            return Optional.empty();
        }

        ImplicitNewExpressionNode init = (ImplicitNewExpressionNode) node;
        Optional<ListenerInfo> listenerInfo = extractListenerInitializer(variableName, init);
        if (listenerInfo.isEmpty() || init.parenthesizedArgList().isEmpty()) {
            return listenerInfo;
        }
        ParenthesizedArgList parenthesizedArgList = init.parenthesizedArgList().get();
        ListenerInfo listener = listenerInfo.get();
        if (parenthesizedArgList.arguments().size() > 1) {
            Optional<HttpsConfig> config = extractKeyStores(parenthesizedArgList.arguments().get(1));
            config.ifPresent(listener::setConfig);
        }
        return Optional.of(listener);
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
