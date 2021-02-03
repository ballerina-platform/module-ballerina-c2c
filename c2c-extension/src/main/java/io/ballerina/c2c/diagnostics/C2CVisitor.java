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

import io.ballerina.compiler.syntax.tree.AnnotationNode;
import io.ballerina.compiler.syntax.tree.BasicLiteralNode;
import io.ballerina.compiler.syntax.tree.BindingPatternNode;
import io.ballerina.compiler.syntax.tree.CaptureBindingPatternNode;
import io.ballerina.compiler.syntax.tree.CheckExpressionNode;
import io.ballerina.compiler.syntax.tree.ExplicitNewExpressionNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.FunctionArgumentNode;
import io.ballerina.compiler.syntax.tree.FunctionDefinitionNode;
import io.ballerina.compiler.syntax.tree.IdentifierToken;
import io.ballerina.compiler.syntax.tree.ImplicitNewExpressionNode;
import io.ballerina.compiler.syntax.tree.ListenerDeclarationNode;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Visitor for validation related to code to cloud.
 *
 * @since 2.0.0
 */
public class C2CVisitor extends NodeVisitor {

    private List<ListenerInfo> listeners = new ArrayList<>();
    private List<ServiceInfo> services = new ArrayList<>();
    private Task task = null;

    @Override
    public void visit(ModuleVariableDeclarationNode moduleVariableDeclarationNode) {
        TypedBindingPatternNode typedBindingPatternNode = moduleVariableDeclarationNode.typedBindingPattern();
        TypeDescriptorNode typeDescriptorNode = typedBindingPatternNode.typeDescriptor();
        if (typeDescriptorNode.kind() != SyntaxKind.QUALIFIED_NAME_REFERENCE) {
            return;
        }
        QualifiedNameReferenceNode qualified = (QualifiedNameReferenceNode) typeDescriptorNode;
        String moduleName = qualified.modulePrefix().text();
        String identifier = qualified.identifier().text();
        if (!moduleName.equals("http") || !identifier.equals("Listener")) {
            return;
        }
        BindingPatternNode variableNode = typedBindingPatternNode.bindingPattern();
        if (variableNode.kind() != SyntaxKind.CAPTURE_BINDING_PATTERN) {
            return;
        }
        CaptureBindingPatternNode captureVariableName = (CaptureBindingPatternNode) variableNode;
        String variableName = captureVariableName.variableName().text();
        if (moduleVariableDeclarationNode.initializer().isEmpty()) {
            return;
        }
        ExpressionNode initExpression = moduleVariableDeclarationNode.initializer().get();
        if (initExpression.kind() == SyntaxKind.CHECK_EXPRESSION) {
            CheckExpressionNode checkedInit = (CheckExpressionNode) initExpression;
            ExpressionNode expression = checkedInit.expression();
            extractListenerInitializer(variableName, expression);
        }
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
            if (getNameOfIdentifier(specificField.fieldName()).equals("schedule")) {
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
                switch (identifier) {
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
                        continue;
                }
            }
        }
        this.task = new Task(minutes, hours, dayOfMonth, monthOfYear, daysOfWeek);
    }

    @Override
    public void visit(ListenerDeclarationNode listenerDeclarationNode) {
        String listenerName = listenerDeclarationNode.variableName().text();
        Node initializer = listenerDeclarationNode.initializer();
        extractListenerInitializer(listenerName, initializer);
    }

    private void extractListenerInitializer(String listenerName, Node initializer) {
        if (initializer.kind() != SyntaxKind.IMPLICIT_NEW_EXPRESSION) {
            return;
        }
        ImplicitNewExpressionNode initializerNode = (ImplicitNewExpressionNode) initializer;
        ParenthesizedArgList parenthesizedArgList = initializerNode.parenthesizedArgList().get();
        if (parenthesizedArgList.arguments().size() == 0) {
            return;
        }
        FunctionArgumentNode functionArgumentNode = parenthesizedArgList.arguments().get(0);
        ExpressionNode expression = ((PositionalArgumentNode) functionArgumentNode).expression();
        int port = Integer.parseInt(((BasicLiteralNode) expression).literalToken().text());
        ListenerInfo listenerInfo = new ListenerInfo(listenerName, port);
        if (parenthesizedArgList.arguments().size() > 1) {
            extractKeyStores(parenthesizedArgList.arguments().get(1), listenerInfo);
        }
        listeners.add(listenerInfo);
    }

    private void extractKeyStores(FunctionArgumentNode functionArgumentNode1, ListenerInfo listenerInfo) {
        if (functionArgumentNode1.kind() != SyntaxKind.POSITIONAL_ARG) {
            return;
        }
        PositionalArgumentNode positionalArgumentNode = (PositionalArgumentNode) functionArgumentNode1;
        if (positionalArgumentNode.expression().kind() != SyntaxKind.MAPPING_CONSTRUCTOR) {
            return;
        }
        processFieldsInHttpConfig(listenerInfo, (MappingConstructorExpressionNode) positionalArgumentNode.expression());
    }

    private void processFieldsInHttpConfig(ListenerInfo listenerInfo, MappingConstructorExpressionNode mapping) {
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
                    processSecureSocketValue(listenerInfo, mappingConstructorExpressionNode);
                }
            }
        }
    }

    private void processSecureSocketValue(ListenerInfo listenerInfo,
                                          MappingConstructorExpressionNode mappingConstructorExpressionNode) {
        SeparatedNodeList<MappingFieldNode> socketChilds =
                mappingConstructorExpressionNode.fields();
        for (MappingFieldNode child : socketChilds) {
            if (child.kind() != SyntaxKind.SPECIFIC_FIELD) {
                continue;
            }
            SpecificFieldNode specificField = (SpecificFieldNode) child;
            String fieldName = getNameOfIdentifier(specificField.fieldName());
            if (fieldName.equals("keyStore")) {
                Optional<Store> store = getStore(specificField.valueExpr().get());
                store.ifPresent(listenerInfo::setKeyStore);
            } else if (fieldName.equals("trustStore")) {
                Optional<Store> store = getStore(specificField.valueExpr().get());
                store.ifPresent(listenerInfo::setTrustStore);
            }
        }
    }

    private Optional<Store> getStore(ExpressionNode expressionNode) {
        if (expressionNode.kind() != SyntaxKind.MAPPING_CONSTRUCTOR) {
            return Optional.empty();
        }
        MappingConstructorExpressionNode expressionNode1 = (MappingConstructorExpressionNode) expressionNode;
        SeparatedNodeList<MappingFieldNode> fields = expressionNode1.fields();
        String path = null;
        String password = null;
        for (MappingFieldNode field : fields) {
            if (field.kind() != SyntaxKind.SPECIFIC_FIELD) {
                continue;
            }
            SpecificFieldNode specificFieldNode = (SpecificFieldNode) field;
            String nameOfIdentifier = getNameOfIdentifier(specificFieldNode.fieldName());
            if (nameOfIdentifier.equals("path")) {
                path = extractString(specificFieldNode.valueExpr().get());
            } else if (nameOfIdentifier.equals("password")) {
                password = extractString(specificFieldNode.valueExpr().get());
            }
        }

        if (path != null && password != null) {
            return Optional.of(new Store(path, password));
        }
        return Optional.empty();
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
        ListenerInfo listenerInfo = null;
        String servicePath = toAbsoluteServicePath(serviceDeclarationNode.absoluteResourcePath());
        ExpressionNode expressionNode = serviceDeclarationNode.expressions().get(0);
        if (expressionNode.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
            //External Listener
            //on helloEP
            SimpleNameReferenceNode referenceNode = (SimpleNameReferenceNode) expressionNode;
            String listenerName = referenceNode.name().text();
            listenerInfo = this.getListener(listenerName);
        } else {
            //Inline Listener
            ExplicitNewExpressionNode refNode = (ExplicitNewExpressionNode) expressionNode;
            FunctionArgumentNode functionArgumentNode = refNode.parenthesizedArgList().arguments().get(0);
            if (functionArgumentNode.kind() == SyntaxKind.POSITIONAL_ARG) {
                ExpressionNode expression = ((PositionalArgumentNode) functionArgumentNode).expression();
                if (expression.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
                    //on new graphql:Listener(httpListener)
                    SimpleNameReferenceNode referenceNode = (SimpleNameReferenceNode) expression;
                    String listenerName = referenceNode.name().text();
                    listenerInfo = this.getListener(listenerName);
                } else {
                    //on new http:Listener(9091)
                    int port = Integer.parseInt(((BasicLiteralNode) expression).literalToken().text());
                    listenerInfo = new ListenerInfo(servicePath, port);
                }
            }

            //Inline Http config
            if (refNode.parenthesizedArgList().arguments().size() > 1) {
                FunctionArgumentNode secondParamExpression = refNode.parenthesizedArgList().arguments().get(1);
                extractKeyStores(secondParamExpression, listenerInfo);
            }
            this.listeners.add(listenerInfo);
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

    private String toAbsoluteServicePath(NodeList<Node> servicePathNodes) {
        StringBuilder absoluteServicePath = new StringBuilder();
        for (Node serviceNode : servicePathNodes) {
            if (serviceNode.kind() == SyntaxKind.SLASH_TOKEN) {
                absoluteServicePath.append("/");
            } else {
                IdentifierToken token = (IdentifierToken) serviceNode;
                absoluteServicePath.append(token.text());
            }
        }
        return absoluteServicePath.toString();
    }

    private ListenerInfo getListener(String name) {
        for (ListenerInfo info : this.listeners) {
            if (info.getName().equals(name)) {
                return info;
            }
        }
        return new ListenerInfo(name, 0);
    }

    public List<ListenerInfo> getListeners() {
        return listeners;
    }

    public List<ServiceInfo> getServices() {
        return services;
    }

    public Task getTask() {
        return task;
    }
}
