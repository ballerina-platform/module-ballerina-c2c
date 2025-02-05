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
package io.ballerina.c2c.util;

import io.ballerina.compiler.api.ModuleID;
import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.AnnotationSymbol;
import io.ballerina.compiler.api.symbols.ClassSymbol;
import io.ballerina.compiler.api.symbols.MethodSymbol;
import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.compiler.api.symbols.ParameterKind;
import io.ballerina.compiler.api.symbols.ParameterSymbol;
import io.ballerina.compiler.api.symbols.ServiceDeclarationSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.SymbolKind;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeReferenceTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.ballerina.compiler.syntax.tree.AnnotationNode;
import io.ballerina.compiler.syntax.tree.BasicLiteralNode;
import io.ballerina.compiler.syntax.tree.CaptureBindingPatternNode;
import io.ballerina.compiler.syntax.tree.CheckExpressionNode;
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
import io.ballerina.compiler.syntax.tree.NamedArgumentNode;
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
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.compiler.syntax.tree.TypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.TypedBindingPatternNode;
import io.ballerina.compiler.syntax.tree.UnionTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.VariableDeclarationNode;
import io.ballerina.tools.diagnostics.Diagnostic;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Visitor for validation related to code to cloud.
 *
 * @since 2.0.0
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class C2CVisitor extends NodeVisitor {

    private final List<ServiceInfo> services = new ArrayList<>();
    private final List<ClientInfo> clientInfos = new ArrayList<>();
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
    public void visit(ModuleVariableDeclarationNode moduleVariableDeclarationNode) {
        // To Parse http:Client Config
        //http:Client nettyEP = check new("https://netty:8688", {
        //     secureSocket: {
        //        cert: {
        //            path: "./security/ballerinaTruststore.p12",
        //            password: "ballerina"
        //   }
        // }
        Optional<ExpressionNode> initializer = moduleVariableDeclarationNode.initializer();
        if (initializer.isEmpty()) {
            return; //Module level vars always needs to be initialized, validated by compiler
        }
        extractHttpClientConfig(moduleVariableDeclarationNode.typedBindingPattern(), initializer.get());
    }

    @Override
    public void visit(VariableDeclarationNode variableDeclarationNode) {
        // To Parse http:Client Config
        //http:Client nettyEP = check new("https://netty:8688", {
        //     secureSocket: {
        //        cert: {
        //            path: "./security/ballerinaTruststore.p12",
        //            password: "ballerina"
        //   }
        // }
        Optional<ExpressionNode> initializer = variableDeclarationNode.initializer();
        if (initializer.isEmpty()) {
            return;
        }
        extractHttpClientConfig(variableDeclarationNode.typedBindingPattern(), initializer.get());
    }

    private void extractHttpClientConfig(TypedBindingPatternNode typedBindingPatternNode, ExpressionNode initializer) {
        TypeDescriptorNode typeDescriptorNode = typedBindingPatternNode.typeDescriptor();
        if (!isSupportedClientVariable(typeDescriptorNode)) {
            return;
        }
        ExpressionNode refNode;
        if (initializer.kind() == SyntaxKind.CHECK_EXPRESSION) {
            CheckExpressionNode checkedInit = (CheckExpressionNode) initializer;
            refNode = checkedInit.expression();
        } else {
            refNode = initializer;
        }
        if (refNode.kind() != SyntaxKind.IMPLICIT_NEW_EXPRESSION) {
            return;
        }
        ImplicitNewExpressionNode newExprInit = (ImplicitNewExpressionNode) refNode;
        newExprInit.parenthesizedArgList().ifPresent(parenthesizedArgList -> {
            SeparatedNodeList<FunctionArgumentNode> argList = parenthesizedArgList.arguments();
            if (argList.size() <= 1) {
                return;
            }
            Optional<HttpsConfig> config = extractKeyStores(argList.get(1));
            config.ifPresent(httpsConfig -> {
                String name = ((CaptureBindingPatternNode) typedBindingPatternNode.bindingPattern())
                        .variableName().text();
                clientInfos.add(new ClientInfo(name, httpsConfig));
            });
        });
    }

    private boolean isSupportedClientVariable(TypeDescriptorNode typeDescriptorNode) {
        if (typeDescriptorNode.kind() == SyntaxKind.UNION_TYPE_DESC) {
            UnionTypeDescriptorNode unionType = (UnionTypeDescriptorNode) typeDescriptorNode;
            return isSupportedClientVariable(unionType.rightTypeDesc()) ||
                    isSupportedClientVariable(unionType.leftTypeDesc());
        }
        if (typeDescriptorNode.kind() == SyntaxKind.QUALIFIED_NAME_REFERENCE) {
            QualifiedNameReferenceNode qualified = (QualifiedNameReferenceNode) typeDescriptorNode;
            Optional<Symbol> symbol = semanticModel.symbol(typeDescriptorNode);
            if (symbol.isEmpty()) {
                return false;
            }
            Optional<ModuleSymbol> module = symbol.get().getModule();
            if (module.isEmpty()) {
                return false;
            }
            ModuleID moduleId = module.get().id();
            return "Client".equals(qualified.identifier().text()) && "ballerina".equals(moduleId.orgName()) &&
                    "http".equals(moduleId.moduleName());
        }
        return false;
    }

    @Override
    public void visit(FunctionDefinitionNode functionDefinitionNode) {
        // Handling Job/Task scheduling.
        // @cloud:Task {
        //  schedule: {
        //        minutes: "*/2",
        //        hours: "*",
        //        dayOfMonth: "*",
        //        monthOfYear: "*",
        //        daysOfWeek: "*"
        //  }
        //}
        // public function main(string... args) {
        //}
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
                this.task = new Task();
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

    private String getNameOfIdentifier(Node node) {
        if (node.kind() == SyntaxKind.IDENTIFIER_TOKEN) {
            return ((IdentifierToken) node).text();
        }
        return null;
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
        this.task = new ScheduledTask(minutes, hours, dayOfMonth, monthOfYear, daysOfWeek);
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
        if (!isC2CNativelySupportedListener(typeSymbols)) {
            processCustomExposedAnnotatedListeners(typeSymbols, servicePath, serviceDeclarationNode);
            return;
        }
        List<ListenerInfo> listenerInfos = extractListeners(serviceDeclarationNode, servicePath);
        if (listenerInfos.size() == 0) {
            return;
        }
        ServiceInfo serviceInfo = new ServiceInfo(listenerInfos, serviceDeclarationNode, servicePath);
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

    private List<ListenerInfo> extractListeners(ServiceDeclarationNode serviceDeclarationNode, String servicePath) {
        SeparatedNodeList<ExpressionNode> expressions = serviceDeclarationNode.expressions();
        List<ListenerInfo> listeners = new ArrayList<>();
        for (ExpressionNode expressionNode : expressions) {
            ListenerInfo listenerInfo = null;
            if (expressionNode.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
                //External Listener
                //on helloEP
                SimpleNameReferenceNode referenceNode = (SimpleNameReferenceNode) expressionNode;
                String listenerName = referenceNode.name().text();
                Optional<ListenerInfo> httpsListener = this.getHttpsListener(listenerName);
                if (httpsListener.isEmpty()) {
                    diagnostics.add(C2CDiagnosticCodes
                            .createDiagnostic(C2CDiagnosticCodes.FAILED_PORT_RETRIEVAL, expressionNode.location()));
                    return Collections.emptyList();
                }
                listenerInfo = httpsListener.get();
            } else {
                if (expressionNode.kind() != SyntaxKind.EXPLICIT_NEW_EXPRESSION) {
                    return Collections.emptyList();
                }
                //Inline Listener
                ExplicitNewExpressionNode refNode = (ExplicitNewExpressionNode) expressionNode;
                FunctionArgumentNode functionArgumentNode = refNode.parenthesizedArgList().arguments().get(0);
                if (functionArgumentNode.kind() == SyntaxKind.POSITIONAL_ARG) {
                    ExpressionNode expression = ((PositionalArgumentNode) functionArgumentNode).expression();
                    Optional<ListenerInfo> newListenerInfo = getListenerInfo(servicePath, expression);
                    if (newListenerInfo.isEmpty()) {
                        return Collections.emptyList();
                    }
                    listenerInfo = newListenerInfo.get();

                    //Inline Http config
                    if (refNode.parenthesizedArgList().arguments().size() > 1) {
                        FunctionArgumentNode secondParamExpression = refNode.parenthesizedArgList().arguments().get(1);
                        Optional<HttpsConfig> config = extractKeyStores(secondParamExpression);
                        config.ifPresent(listenerInfo::setConfig);
                    }
                }
            }
            if (listenerInfo != null) {
                listeners.add(listenerInfo);
            }
        }
        return listeners;
    }

    private Optional<ListenerInfo> extractListenerInitializer(String listenerName,
                                                              ImplicitNewExpressionNode initializerNode, int paramNo) {
        ParenthesizedArgList parenthesizedArgList = initializerNode.parenthesizedArgList().get();
        SeparatedNodeList<FunctionArgumentNode> arguments = parenthesizedArgList.arguments();
        if (arguments.size() == 0) {
            return Optional.empty();
        }
        if (arguments.size() > paramNo) {
            FunctionArgumentNode functionArgumentNode = arguments.get(paramNo);
            ExpressionNode expression = ((PositionalArgumentNode) functionArgumentNode).expression();
            if (expression.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
                return getListenerInfo(listenerName, expression);
            } else if (expression instanceof BasicLiteralNode) {
                BasicLiteralNode basicLiteralNode = (BasicLiteralNode) expression;
                int port = Integer.parseInt(basicLiteralNode.literalToken().text());
                return Optional.of(new ListenerInfo(listenerName, port));
            }
        }
        return Optional.empty();
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
                Optional<SecureSocketConfig>
                        secureSocket = getSecureSocketConfig(specificField.valueExpr().get());
                if (secureSocket.isEmpty()) {
                    return Optional.empty();
                }
                httpsConfig.setSecureSocketConfig(secureSocket.get());
            } else if ("mutualSsl".equals(fieldName)) {
                Optional<MutualSSLConfig>
                        mutualSSLConfig = getMutualSSLConfig(specificField.valueExpr().get());
                if (mutualSSLConfig.isEmpty()) {
                    return Optional.empty();
                }
                httpsConfig.setMutualSSLConfig(mutualSSLConfig.get());
            } else if ("cert".equals(fieldName)) {
                Optional<MutualSSLConfig>
                        mutualSSLConfig = getMutualSSLConfig(specificField.valueExpr().get());
                if (mutualSSLConfig.isEmpty()) {
                    return Optional.empty();
                }
                httpsConfig.setMutualSSLConfig(mutualSSLConfig.get());
            }
        }
        return Optional.of(httpsConfig);
    }

    private Optional<MutualSSLConfig> getMutualSSLConfig(ExpressionNode expressionNode) {
        MutualSSLConfig mutualSSLConfig = new MutualSSLConfig();
        if (expressionNode.kind() == SyntaxKind.STRING_LITERAL) {
            mutualSSLConfig.setPath(extractString(expressionNode));
            return Optional.of(mutualSSLConfig);
        }
        if (expressionNode.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
            String name = ((SimpleNameReferenceNode) expressionNode).name().text();
            diagnostics.add(C2CDiagnosticCodes
                    .createDiagnostic(C2CDiagnosticCodes.FAILED_VARIABLE_RETRIEVAL, expressionNode.location(), name));
            return Optional.empty();
        }
        if (expressionNode.kind() != SyntaxKind.MAPPING_CONSTRUCTOR) {
            return Optional.empty();
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
        return Optional.of(mutualSSLConfig);
    }

    private Optional<SecureSocketConfig> getSecureSocketConfig(ExpressionNode expressionNode) {
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
        if (secureSocketConfig.getPath() == null && secureSocketConfig.getCertFile() == null &&
                secureSocketConfig.getKeyFile() == null) {
            return Optional.empty();
        }
        return Optional.of(secureSocketConfig);
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

    private void processCustomExposedAnnotatedListeners(List<TypeSymbol> typeSymbols, String servicePath,
                                                        ServiceDeclarationNode serviceDeclarationNode) {
        List<ListenerInfo> listenerInfos = new ArrayList<>();
        for (int listenerIndex = 0; listenerIndex < typeSymbols.size(); listenerIndex++) {
            TypeSymbol typeSymbol = typeSymbols.get(listenerIndex);
            if (typeSymbol.typeKind() != TypeDescKind.TYPE_REFERENCE) {
                continue;
            }
            Symbol typeDefinition = ((TypeReferenceTypeSymbol) typeSymbol).definition();
            if (typeDefinition.kind() != SymbolKind.CLASS) {
                continue;
            }
            ClassSymbol classSymbol = (ClassSymbol) typeDefinition;
            if (classSymbol.initMethod().isEmpty()) {
                continue;
            }
            // Get the init method of the custom listener because thats where the @cloud:Expose is at.
            // Ex - public function init(@cloud:Expose int port, ListenerConfiguration config) {
            MethodSymbol initSymbol = classSymbol.initMethod().get();
            Optional<List<ParameterSymbol>> paramsList = initSymbol.typeDescriptor().params();
            if (paramsList.isEmpty()) {
                continue;
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
                        // param. We need to access the syntax tree to get the values.
                        Optional<ListenerInfo> listenerInfo = getPortValueFromSTForCustomListener(servicePath,
                                serviceDeclarationNode, i, listenerIndex);
                        if (listenerInfo.isEmpty()) {
                            if (parameterSymbol.paramKind() == ParameterKind.DEFAULTABLE) {
                                diagnostics.add(C2CDiagnosticCodes
                                        .createDiagnostic(C2CDiagnosticCodes.FAILED_DEFAULTABLE_PORT_RETRIEVAL,
                                                parameterSymbol.location()));
                            } else {
                                diagnostics.add(C2CDiagnosticCodes
                                        .createDiagnostic(C2CDiagnosticCodes.FAILED_PORT_RETRIEVAL,
                                                parameterSymbol.location()));
                            }
                            continue;
                        }
                        listenerInfos.add(listenerInfo.get());
                    }
                }
            }
        }
        if (!listenerInfos.isEmpty()) {
            this.services.add(new ServiceInfo(listenerInfos, serviceDeclarationNode, servicePath));
        }
    }

    private Optional<ListenerInfo> getPortValueFromSTForCustomListener(String path, ServiceDeclarationNode serviceNode,
                                                                       int paramNo, int listenerIndex) {
        ExpressionNode expressionNode = serviceNode.expressions().get(listenerIndex);
        if (expressionNode.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
            //on listener
            SimpleNameReferenceNode referenceNode = (SimpleNameReferenceNode) expressionNode;
            String listenerName = referenceNode.name().text();
            Node node = this.moduleLevelVariables.get(listenerName);
            if (node == null || !(node.kind() == SyntaxKind.IMPLICIT_NEW_EXPRESSION)) {
                return Optional.empty();
            }
            ImplicitNewExpressionNode init = (ImplicitNewExpressionNode) node;
            return extractListenerInitializer(listenerName, init, paramNo);
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

    private boolean isC2CNativelySupportedListener(List<TypeSymbol> typeSymbols) {
        for (TypeSymbol typeSymbol : typeSymbols) {
            if (isC2CNativelySupportedListener(typeSymbol)) {
                return true;
            }
        }
        return false;
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
                case "websub":
                case "websubhub":
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
        Optional<ListenerInfo> listenerInfo = extractListenerInitializer(variableName, init, 0);
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
            diagnostics.add(C2CDiagnosticCodes
                    .createDiagnostic(C2CDiagnosticCodes.CONFIGURABLE_NO_DEFAULT,
                            moduleVariableDeclarationNode.location(), variableName));
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
