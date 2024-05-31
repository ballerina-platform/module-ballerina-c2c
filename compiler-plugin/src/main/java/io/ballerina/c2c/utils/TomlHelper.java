/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.c2c.utils;

import io.ballerina.projects.TomlDocument;
import io.ballerina.toml.api.Toml;
import io.ballerina.toml.semantic.TomlType;
import io.ballerina.toml.semantic.ast.TomlArrayValueNode;
import io.ballerina.toml.semantic.ast.TomlBooleanValueNode;
import io.ballerina.toml.semantic.ast.TomlLongValueNode;
import io.ballerina.toml.semantic.ast.TomlStringValueNode;
import io.ballerina.toml.semantic.ast.TomlTableNode;
import io.ballerina.toml.semantic.ast.TomlValueNode;
import io.ballerina.toml.semantic.diagnostics.DiagnosticComparator;
import io.ballerina.toml.semantic.diagnostics.TomlDiagnostic;
import io.ballerina.toml.semantic.diagnostics.TomlNodeLocation;
import io.ballerina.toml.syntax.tree.SyntaxTree;
import io.ballerina.tools.diagnostics.Diagnostic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Wrapper defined to provide default value support and dotted key support.
 *
 * @since 2.0.0
 */
public class TomlHelper {

    public static String getString(Toml toml, String key) {
        Optional<TomlValueNode> valueNode = toml.get(key);
        if (valueNode.isEmpty()) {
            return null;
        }
        TomlValueNode tomlValueNode = valueNode.get();
        if (tomlValueNode.kind() == TomlType.STRING) {
            return ((TomlStringValueNode) tomlValueNode).getValue();
        }
        return null;
    }

    public static String getString(Toml toml, String key, String defaultValue) {
        String output = getString(toml, key);
        if (output == null) {
            return defaultValue;
        }
        return output;
    }

    public static List<Long> getNumberArray(Toml toml, String key) {
        Optional<TomlValueNode> valueNode = toml.get(key);
        List<Long> values = new ArrayList<>();
        if (valueNode.isEmpty()) {
            return values;
        }
        TomlValueNode tomlValueNode = valueNode.get();
        if (tomlValueNode.kind() == TomlType.ARRAY) {
            TomlArrayValueNode tomlArrayValueNode = (TomlArrayValueNode) tomlValueNode;
            for (TomlValueNode element : tomlArrayValueNode.elements()) {
                if (element.kind() == TomlType.INTEGER) {
                    TomlLongValueNode longValueNode = (TomlLongValueNode) element;
                    values.add(longValueNode.getValue());
                }
            }
        }
        return values;
    }

    public static Long getLong(Toml toml, String key) {
        Optional<TomlValueNode> valueNode = toml.get(key);
        if (valueNode.isEmpty()) {
            return null;
        }
        TomlValueNode tomlValueNode = valueNode.get();
        if (tomlValueNode.kind() == TomlType.INTEGER) {
            return ((TomlLongValueNode) tomlValueNode).getValue();
        }
        return null;
    }

    public static long getLong(Toml toml, String key, long defaultValue) {
        Long output = getLong(toml, key);
        if (output == null) {
            return defaultValue;
        }
        return output;
    }

    public static boolean getBoolean(Toml toml, String key, boolean defaultValue) {
        Optional<TomlValueNode> valueNode = toml.get(key);
        if (valueNode.isEmpty()) {
            return defaultValue;
        }
        TomlValueNode tomlValueNode = valueNode.get();
        if (tomlValueNode.kind() == TomlType.BOOLEAN) {
            return ((TomlBooleanValueNode) tomlValueNode).getValue();
        }
        return defaultValue;
    }

    public static Toml createK8sTomlFromProject(TomlDocument tomlDocument) {
        TomlTableNode astNode = tomlDocument.toml().rootNode();
        astNode.clearDiagnostics();
        astNode.addSyntaxDiagnostics(reportSyntaxDiagnostics(tomlDocument.syntaxTree()));
        return new Toml(astNode);
    }

    public static Set<Diagnostic> reportSyntaxDiagnostics(SyntaxTree tree) {
        Set<Diagnostic> diagnostics = new TreeSet<>(new DiagnosticComparator());
        for (Diagnostic syntaxDiagnostic : tree.diagnostics()) {
            TomlNodeLocation tomlNodeLocation = new TomlNodeLocation(syntaxDiagnostic.location().lineRange(),
                    syntaxDiagnostic.location().textRange());
            TomlDiagnostic tomlDiagnostic =
                    new TomlDiagnostic(tomlNodeLocation, syntaxDiagnostic.diagnosticInfo(), syntaxDiagnostic.message());
            diagnostics.add(tomlDiagnostic);
        }
        return diagnostics;
    }
}
