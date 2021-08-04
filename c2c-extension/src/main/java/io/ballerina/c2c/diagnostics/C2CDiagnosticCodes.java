
/*
 * Copyright (c) 2021 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerina.c2c.diagnostics;

import io.ballerina.tools.diagnostics.DiagnosticSeverity;

import static io.ballerina.tools.diagnostics.DiagnosticSeverity.ERROR;
import static io.ballerina.tools.diagnostics.DiagnosticSeverity.WARNING;

/**
 * {@code DiagnosticCodes} is used to hold diagnostic codes.
 */
public enum C2CDiagnosticCodes {
    C2C_001("C2C_001", "failed to read path contents", ERROR),
    C2C_002("C2C_002", "failed to retrieve port", ERROR),
    C2C_003("C2C_003", "https config extraction only supports basic string paths", ERROR),
    C2C_005("C2C_005", "configurables with no default value is not supported", ERROR),
    C2C_006("C2C_006", "default value of configurable variable `%s` could be overridden in runtime", WARNING);

    private final String code;
    private final String message;
    private final DiagnosticSeverity severity;

    C2CDiagnosticCodes(String code, String message, DiagnosticSeverity severity) {
        this.code = code;
        this.message = message;
        this.severity = severity;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public DiagnosticSeverity getSeverity() {
        return severity;
    }
}
