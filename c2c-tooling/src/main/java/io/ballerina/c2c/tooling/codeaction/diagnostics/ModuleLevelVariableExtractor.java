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

import io.ballerina.compiler.syntax.tree.BindingPatternNode;
import io.ballerina.compiler.syntax.tree.CaptureBindingPatternNode;
import io.ballerina.compiler.syntax.tree.CheckExpressionNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.ListenerDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModuleVariableDeclarationNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeVisitor;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.TypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.TypedBindingPatternNode;

import java.util.Map;
import java.util.Optional;

/**
 * Contains the references for module level variables which is needed for code generation.
 *
 * @since 2.0.0
 */
public class ModuleLevelVariableExtractor extends NodeVisitor {

    private Map<String, Node> moduleLevelVariables;

    public ModuleLevelVariableExtractor(Map<String, Node> moduleLevelVariables) {
        this.moduleLevelVariables = moduleLevelVariables;
    }

    @Override
    public void visit(ModuleVariableDeclarationNode moduleVariableDeclarationNode) {
        TypedBindingPatternNode typedBindingPatternNode = moduleVariableDeclarationNode.typedBindingPattern();
        TypeDescriptorNode typeDescriptorNode = typedBindingPatternNode.typeDescriptor();
        BindingPatternNode variableNode = typedBindingPatternNode.bindingPattern();
        if (variableNode.kind() != SyntaxKind.CAPTURE_BINDING_PATTERN) {
            return;
        }
        CaptureBindingPatternNode captureVariableName = (CaptureBindingPatternNode) variableNode;
        String variableName = captureVariableName.variableName().text();

        if (typeDescriptorNode.kind() == SyntaxKind.INT_TYPE_DESC) {
            moduleLevelVariables.put(variableName, moduleVariableDeclarationNode);
            return;
        }

        if (typeDescriptorNode.kind() != SyntaxKind.QUALIFIED_NAME_REFERENCE) {
            return;
        }
        QualifiedNameReferenceNode qualified = (QualifiedNameReferenceNode) typeDescriptorNode;
        String identifier = qualified.identifier().text();

        if (moduleVariableDeclarationNode.initializer().isEmpty()) {
            return;
        }
        if ("ListenerConfiguration".equals(identifier)) {
            Optional<ExpressionNode> initializer = moduleVariableDeclarationNode.initializer();
            if (initializer.isPresent()) {
                if (initializer.get().kind() != SyntaxKind.MAPPING_CONSTRUCTOR) {
                    return;
                }
                moduleLevelVariables.put(variableName, initializer.get());
                return;
            }
        }

        ExpressionNode initExpression = moduleVariableDeclarationNode.initializer().get();
        if (initExpression.kind() == SyntaxKind.CHECK_EXPRESSION) {
            CheckExpressionNode checkedInit = (CheckExpressionNode) initExpression;
            ExpressionNode expression = checkedInit.expression();
            moduleLevelVariables.put(variableName, expression);
            return;
        }

        if ("Listener".equals(identifier)) {
            Optional<ExpressionNode> initializer = moduleVariableDeclarationNode.initializer();
            initializer.ifPresent(expressionNode -> moduleLevelVariables.put(variableName, expressionNode));
        }
    }

    @Override
    public void visit(ListenerDeclarationNode listenerDeclarationNode) {
        String listenerName = listenerDeclarationNode.variableName().text();
        Optional<TypeDescriptorNode> typeDescriptorNode = listenerDeclarationNode.typeDescriptor();
        if (typeDescriptorNode.isEmpty()) {
            return;
        }
        if (typeDescriptorNode.get().kind() != SyntaxKind.QUALIFIED_NAME_REFERENCE) {
            return;
        }

        Node initializer = listenerDeclarationNode.initializer();
        moduleLevelVariables.put(listenerName, initializer);
    }
}
