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
package io.ballerina.c2c.diagnostics;

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.compiler.api.symbols.ServiceDeclarationSymbol;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.ballerina.compiler.syntax.tree.AnnotationNode;
import io.ballerina.compiler.syntax.tree.BasicLiteralNode;
import io.ballerina.compiler.syntax.tree.ExplicitNewExpressionNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.FunctionArgumentNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.IdentifierToken;
import io.ballerina.compiler.syntax.tree.ImplicitNewExpressionNode;
import io.ballerina.compiler.syntax.tree.MappingConstructorExpressionNode;
import io.ballerina.compiler.syntax.tree.MappingFieldNode;
import io.ballerina.compiler.syntax.tree.MetadataNode;
import io.ballerina.compiler.syntax.tree.ModuleVariableDeclarationNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.NodeVisitor;
import io.ballerina.compiler.syntax.tree.ParenthesizedArgList;
import io.ballerina.compiler.syntax.tree.PositionalArgumentNode;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.SimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SpecificFieldNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.TypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.TypedBindingPatternNode;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private Task task = null;

    public C2CVisitor(Map<String, Node> moduleLevelVariables, SemanticModel semanticModel,
                      List<Diagnostic> diagnostics) {
        this.moduleLevelVariables = moduleLevelVariables;
        this.semanticModel = semanticModel;
        this.diagnostics = diagnostics;
    }

    @Override
    public void visit(FunctionDefinitionNode functionDefinitionNode) {
        Optional<MetadataNode> metadata = functionDefinitionNode.metadata();
        String funcName = functionDefinitionNode.functionName().text();
        if (!funcName.equals("main")) {
            return;
        }
        if (metadata.isEmpty()) {
            return;
        }
        processFunctionAnnotation(metadata.get());
    }

    private void processFunctionAnnotation(MetadataNode metadataNode) {
        NodeList<AnnotationNode> annotations = metadataNode.annotations();
        for (AnnotationNode annotationNode : annotations) {
            Node node = annotationNode.annotReference();
            if (node.kind() != SyntaxKind.QUALIFIED_NAME_REFERENCE) {
                continue;
            }
            QualifiedNameReferenceNode node1 = (QualifiedNameReferenceNode) node;
            String modulePrefix = node1.modulePrefix().text();
            String name = node1.identifier().text();
            if (modulePrefix.equals("cloud") && name.equals("Task")) {
                processTaskAnnotationValue(annotationNode);
            }
        }
    }

    private void processTaskAnnotationValue(AnnotationNode annotationNode) {
        if (annotationNode.annotValue().isEmpty()) {
            return;
        }
        MappingConstructorExpressionNode mappingConstructorExpressionNode = annotationNode.annotValue().get();
        SeparatedNodeList<MappingFieldNode> fields = mappingConstructorExpressionNode.fields();
        for (MappingFieldNode field : fields) {
            if (field.kind() != SyntaxKind.SPECIFIC_FIELD) {
                continue;
            }
            SpecificFieldNode specificField = (SpecificFieldNode) field;
            if ("schedule".equals(getNameOfIdentifier(specificField.fieldName()))) {
                Optional<ExpressionNode> expressionNode = specificField.valueExpr();
                expressionNode.ifPresent(this::processTaskScheduleBlock);
            }
        }
    }

    private void processTaskScheduleBlock(ExpressionNode expressionNode) {
        if (expressionNode.kind() != SyntaxKind.MAPPING_CONSTRUCTOR) {
            return;
        }
        MappingConstructorExpressionNode expressionNode1 = (MappingConstructorExpressionNode) expressionNode;
        SeparatedNodeList<MappingFieldNode> scheduleFields = expressionNode1.fields();
        String minutes = null, hours = null, dayOfMonth = null, monthOfYear = null, daysOfWeek = null;
        for (MappingFieldNode timeField : scheduleFields) {
            if (timeField.kind() == SyntaxKind.SPECIFIC_FIELD) {
                SpecificFieldNode timeSpecificField = (SpecificFieldNode) timeField;
                String identifier = getNameOfIdentifier(timeSpecificField.fieldName());
                timeSpecificField.valueExpr();
                switch (Objects.requireNonNull(identifier)) {
                    case "minutes":
                        minutes = extractString(timeSpecificField.valueExpr().get());
                        break;
                    case "hours":
                        hours = extractString(timeSpecificField.valueExpr().get());
                        break;
                    case "dayOfMonth":
                        dayOfMonth = extractString(timeSpecificField.valueExpr().get());
                        break;
                    case "monthOfYear":
                        monthOfYear = extractString(timeSpecificField.valueExpr().get());
                        break;
                    case "daysOfWeek":
                        daysOfWeek = extractString(timeSpecificField.valueExpr().get());
                        break;
                    default:
                        break;
                }
            }
        }
        this.task = new Task(minutes, hours, dayOfMonth, monthOfYear, daysOfWeek);
    }

    private Optional<ListenerInfo> extractListenerInitializer(String listenerName, Node initializer) {
        if (initializer.kind() != SyntaxKind.IMPLICIT_NEW_EXPRESSION) {
            return Optional.empty();
        }
        ImplicitNewExpressionNode initializerNode = (ImplicitNewExpressionNode) initializer;
        ParenthesizedArgList parenthesizedArgList = initializerNode.parenthesizedArgList().get();
        if (parenthesizedArgList.arguments().size() == 0) {
            return Optional.empty();
        }
        FunctionArgumentNode functionArgumentNode = parenthesizedArgList.arguments().get(0);
        ExpressionNode expression = ((PositionalArgumentNode) functionArgumentNode).expression();
        ListenerInfo listenerInfo;
        if (expression.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
            DiagnosticInfo diagnosticInfo = new DiagnosticInfo("C2C002", "failed to retrieve port",
                    DiagnosticSeverity.ERROR);
            diagnostics.add(DiagnosticFactory.createDiagnostic(diagnosticInfo, expression.location()));
            return Optional.empty();
        } else {
            BasicLiteralNode basicLiteralNode = (BasicLiteralNode) expression;
            int port = Integer.parseInt(basicLiteralNode.literalToken().text());
            listenerInfo = new ListenerInfo(listenerName, port);
        }
        if (parenthesizedArgList.arguments().size() > 1) {
            Optional<HttpsConfig> config = extractKeyStores(parenthesizedArgList.arguments().get(1));
            config.ifPresent(listenerInfo::setConfig);
        }
        return Optional.of(listenerInfo);
    }

    private Optional<HttpsConfig> extractKeyStores(FunctionArgumentNode functionArgumentNode1) {
        if (functionArgumentNode1.kind() != SyntaxKind.POSITIONAL_ARG) {
            return Optional.empty();
        }
        PositionalArgumentNode positionalArgumentNode = (PositionalArgumentNode) functionArgumentNode1;
        if (positionalArgumentNode.expression().kind() == SyntaxKind.MAPPING_CONSTRUCTOR) {
            return processFieldsInHttpConfig((MappingConstructorExpressionNode) positionalArgumentNode.expression());
        } else if (positionalArgumentNode.expression().kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
            String varName = ((SimpleNameReferenceNode) positionalArgumentNode.expression()).name().text();
            return getHttpsListenerConfig(varName);
        } else {
            return Optional.empty();
        }
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
            }
        }
        return Optional.of(httpsConfig);
    }

    private MutualSSLConfig getMutualSSLConfig(ExpressionNode expressionNode) {
        MappingConstructorExpressionNode expressionNode1 = (MappingConstructorExpressionNode) expressionNode;
        SeparatedNodeList<MappingFieldNode> fields = expressionNode1.fields();
        MutualSSLConfig mutualSSLConfig = new MutualSSLConfig();
        for (MappingFieldNode field : fields) {
            if (field.kind() != SyntaxKind.SPECIFIC_FIELD) {
                continue;
            }
            SpecificFieldNode specificFieldNode = (SpecificFieldNode) field;
            String nameOfIdentifier = getNameOfIdentifier(specificFieldNode.fieldName());
            if ("cert".equals(nameOfIdentifier)) {
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

    private String extractString(ExpressionNode expressionNode) {
        if (expressionNode.kind() == SyntaxKind.STRING_LITERAL) {
            String text = ((BasicLiteralNode) expressionNode).literalToken().text();
            return text.substring(1, text.length() - 1);
        }
        return null;
    }

    private String getNameOfIdentifier(Node node) {
        if (node.kind() == SyntaxKind.IDENTIFIER_TOKEN) {
            return ((IdentifierToken) node).text();
        }
        return null;
    }

    @Override
    public void visit(ServiceDeclarationNode serviceDeclarationNode) {
        ServiceDeclarationSymbol symbol =
                (ServiceDeclarationSymbol) semanticModel.symbol(serviceDeclarationNode).orElseThrow();
        List<TypeSymbol> typeSymbols = symbol.listenerTypes();
        if (typeSymbols.isEmpty() || !isC2CSupportedListener(typeSymbols.get(0))) {
            return;
        }

        ListenerInfo listenerInfo = null;
        String servicePath = toAbsoluteServicePath(serviceDeclarationNode.absoluteResourcePath());
        ExpressionNode expressionNode = serviceDeclarationNode.expressions().get(0);
        if (expressionNode.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
            //External Listener
            //on helloEP
            SimpleNameReferenceNode referenceNode = (SimpleNameReferenceNode) expressionNode;
            String listenerName = referenceNode.name().text();
            Optional<ListenerInfo> httpsListener = this.getHttpsListener(listenerName);
            if (httpsListener.isEmpty()) {
                DiagnosticInfo diagnosticInfo = new DiagnosticInfo("C2C002", "failed to retrieve port",
                        DiagnosticSeverity.ERROR);
                diagnostics.add(DiagnosticFactory.createDiagnostic(diagnosticInfo, expressionNode.location()));
                return;
            }
            listenerInfo = httpsListener.get();
        } else {
            //Inline Listener
            ExplicitNewExpressionNode refNode = (ExplicitNewExpressionNode) expressionNode;
            FunctionArgumentNode functionArgumentNode = refNode.parenthesizedArgList().arguments().get(0);
            if (functionArgumentNode.kind() == SyntaxKind.POSITIONAL_ARG) {
                ExpressionNode expression = ((PositionalArgumentNode) functionArgumentNode).expression();
                if (expression.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
                    //on new graphql:Listener(httpListener)
                    SimpleNameReferenceNode referenceNode = (SimpleNameReferenceNode) expression;
                    String variableName = referenceNode.name().text();
                    Optional<Integer> port = getPortNumberFromVariable(variableName);
                    if (port.isEmpty()) {
                        Optional<ListenerInfo> httpsListener = this.getHttpsListener(variableName);
                        if (httpsListener.isEmpty()) {
                            DiagnosticInfo diagnosticInfo = new DiagnosticInfo("C2C002", "failed to retrieve port",
                                    DiagnosticSeverity.ERROR);
                            diagnostics.add(DiagnosticFactory.createDiagnostic(diagnosticInfo, expression.location()));
                            return;
                        }
                        listenerInfo = httpsListener.get();
                    } else {
                        int portNumber = port.get();
                        if (portNumber == 0) {
                            return;
                        }
                        listenerInfo = new ListenerInfo(servicePath, portNumber);
                    }
                } else {
                    //on new http:Listener(9091)
                    int port = Integer.parseInt(((BasicLiteralNode) expression).literalToken().text());
                    listenerInfo = new ListenerInfo(servicePath, port);
                }
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
        }
        services.add(serviceInfo);
    }

    private boolean isC2CSupportedListener(TypeSymbol typeSymbol) {
        if (typeSymbol.typeKind() == TypeDescKind.UNION) {
            UnionTypeSymbol unionTypeSymbol = (UnionTypeSymbol) typeSymbol;
            List<TypeSymbol> typeSymbols = unionTypeSymbol.memberTypeDescriptors();
            for (TypeSymbol symbol : typeSymbols) {
                if (symbol.typeKind() != TypeDescKind.ERROR) {
                    return isC2CSupportedListener(symbol);
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

    public List<ServiceInfo> getServices() {
        return services;
    }

    public Task getTask() {
        return task;
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
            DiagnosticInfo diagnosticInfo = new DiagnosticInfo("C2C001", "configurables with no default value is not " +
                    "supported", DiagnosticSeverity.ERROR);
            diagnostics.add(DiagnosticFactory.createDiagnostic(diagnosticInfo,
                    moduleVariableDeclarationNode.location()));
            return Optional.of(0);
        }

        if (expressionNode.kind() != SyntaxKind.NUMERIC_LITERAL) {
            return Optional.empty();
        }
        BasicLiteralNode basicLiteralNode = (BasicLiteralNode) expressionNode;
        return Optional.of(Integer.parseInt(basicLiteralNode.literalToken().text()));
    }
}
