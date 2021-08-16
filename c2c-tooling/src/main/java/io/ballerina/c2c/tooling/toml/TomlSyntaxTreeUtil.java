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
package io.ballerina.c2c.tooling.toml;

import io.ballerina.toml.syntax.tree.SeparatedNodeList;
import io.ballerina.toml.syntax.tree.ValueNode;

/**
 * Utility class used as a helper for Toml Syntax tree related actions.
 *
 * @since 2.0.0
 */
public class TomlSyntaxTreeUtil {

    public static String toDottedString(SeparatedNodeList<ValueNode> nodeList) {
        StringBuilder output = new StringBuilder();
        for (ValueNode valueNode : nodeList) {
            String valueString = valueNode.toString().trim();
            output.append(".").append(valueString);
        }
        return output.substring(1).trim();
    }

    public static String trimResourcePath(String resourcePath) {
        resourcePath = resourcePath.trim();
        if (resourcePath.startsWith("/")) {
            resourcePath = resourcePath.substring(1);
        }
        if (resourcePath.endsWith("/")) {
            resourcePath = resourcePath.substring(0, resourcePath.length() - 1);
        }
        return resourcePath;
    }
}
