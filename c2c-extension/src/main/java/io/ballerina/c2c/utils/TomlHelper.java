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
import io.ballerina.toml.semantic.ast.TomlBooleanValueNode;
import io.ballerina.toml.semantic.ast.TomlDoubleValueNodeNode;
import io.ballerina.toml.semantic.ast.TomlLongValueNode;
import io.ballerina.toml.semantic.ast.TomlStringValueNode;
import io.ballerina.toml.semantic.ast.TomlTableNode;
import io.ballerina.toml.semantic.ast.TomlValueNode;
import io.ballerina.toml.semantic.diagnostics.DiagnosticComparator;
import io.ballerina.toml.semantic.diagnostics.TomlDiagnostic;
import io.ballerina.toml.semantic.diagnostics.TomlNodeLocation;
import io.ballerina.toml.syntax.tree.SyntaxTree;
import io.ballerina.tools.diagnostics.Diagnostic;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Wrapper defined to provide default value support and dotted key support.
 *
 * @since 2.0.0
 */
public class TomlHelper {

    public static String getString(Toml toml, String key) {
        TomlStringValueNode value = getFromDottedString(toml, key);
        return value != null ? value.getValue() : null;
    }

    public static String getString(Toml toml, String key, String defaultValue) {
        TomlStringValueNode value = getFromDottedString(toml, key);
        return value == null ? defaultValue : value.getValue();
    }

    public static Long getLong(Toml toml, String key) {
        TomlLongValueNode value = getFromDottedString(toml, key);
        return value != null ? value.getValue() : null;
    }

    public static long getLong(Toml toml, String key, long defaultValue) {
        TomlLongValueNode value = getFromDottedString(toml, key);
        return value == null ? defaultValue : value.getValue();
    }

    public static Boolean getBoolean(Toml toml, String key) {
        TomlBooleanValueNode value = getFromDottedString(toml, key);
        return value != null ? value.getValue() : false;
    }

    public static boolean getBoolean(Toml toml, String key, boolean defaultValue) {
        TomlBooleanValueNode value = getFromDottedString(toml, key);
        return value == null ? defaultValue : value.getValue();
    }

    public static Double getDouble(Toml toml, String key) {
        TomlDoubleValueNodeNode value = getFromDottedString(toml, key);
        return value != null ? value.getValue() : null;
    }

    public static double getDouble(Toml toml, String key, double defaultValue) {
        TomlDoubleValueNodeNode value = getFromDottedString(toml, key);
        return value == null ? defaultValue : value.getValue();
    }

    public static Toml getTable(Toml toml, String dottedKey) {
        String[] split = dottedKey.split("\\.");
        Toml parent = toml;
        for (String key : split) {
            parent = parent.getTable(key);
            if (parent == null) {
                return null;
            }
        }
        return parent;
    }

    public static List<Toml> getTables(Toml toml, String dottedKey) {
        String[] split = dottedKey.split("\\.");
        String lastKey = split[split.length - 1];
        split = Arrays.copyOf(split, split.length - 1);
        Toml parent = toml;
        for (String key : split) {
            parent = parent.getTable(key);
            if (parent == null) {
                return null;
            }
        }
        return parent.getTables(lastKey);
    }

    public static <T extends TomlValueNode> T getFromDottedString(Toml rootTable, String dottedKey) {
        String[] split = dottedKey.split("\\.");
        String lastKey = split[split.length - 1];
        split = Arrays.copyOf(split, split.length - 1);
        Toml parent = rootTable;
        for (String key : split) {
            parent = parent.getTable(key);
            if (parent == null) {
                return null;
            }
        }
        return parent.get(lastKey);
    }

    public static Toml createK8sTomlFromProject(TomlDocument tomlDocument) {
        TomlTableNode astNode = tomlDocument.tomlAstNode();
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
