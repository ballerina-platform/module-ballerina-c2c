
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
package io.ballerina.c2c.util;

import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import io.ballerina.tools.diagnostics.Location;

import static io.ballerina.tools.diagnostics.DiagnosticSeverity.ERROR;
import static io.ballerina.tools.diagnostics.DiagnosticSeverity.WARNING;

/**
 * {@code DiagnosticCodes} is used to hold diagnostic codes.
 */
public enum C2CDiagnosticCodes {    
    PATH_CONTENT_READ_FAILED("C2C_001", "unable to read contents of the file `%s`", ERROR),
    FAILED_PORT_RETRIEVAL("C2C_002", "failed to retrieve port", WARNING),
    VALUE_STRING_ONLY_SUPPORTED("C2C_003", "https config extraction only supports basic string paths", WARNING),
    CONFIGURABLE_NO_DEFAULT("C2C_005", "default value is not specified for the configurable variable `%s`. cloud " +
            "artifacts will not be generated for this variable", WARNING),
    CONFIGURABLE_OVERRIDE("C2C_006", "default value of configurable variable `%s` could be overridden in runtime",
            WARNING),
    DIRECTORY_DELETE_FAILED("C2C_007", "unable to delete directory: `%s`", WARNING),
    DOCKER_FAILED("C2C_008", "error occurred creating docker image", ERROR),
    INVALID_MOUNT_PATH("C2C_009", "Invalid mount path: `%s`. " +
            "Providing relative path in the same level as source file is not supported with code2cloud." +
            "Please create a sub folder and provide the relative path. " +
            "eg: './security/ballerinaKeystore.p12'", WARNING),
    ARTIFACT_GEN_FAILED("C2C_010", "error while generating yaml file for `%s`: `%s`", WARNING),
    ONLY_ONE_BALLERINA_CONFIG_ALLOWED("C2C_011", "only one ballerina config is allowed", ERROR),
    INVALID_PROBE("C2C_012", "unable to detect port for `%s` probe.", ERROR),
    INVALID_CONFIG_NAME("C2C_013", "invalid config file name", ERROR),
    INVALID_MOUNT_PATH_CLOUD("C2C_014", "Cloud.toml error mount_path cannot be `%s`: `%s`", ERROR),
    EMPTY_PATH_CLOUD("C2C_015", "Cloud.toml error invalid path without file name `%s`", ERROR),
    PATH_CONTENT_READ_FAILED_WARN("C2C_006", "unable to read contents of the file `%s`", WARNING),
    FAILED_VARIABLE_RETRIEVAL("C2C_016", "unable to retrieve the value of variable `%s`", WARNING),
    INVALID_CONFIG_FILE_NAME_TAKEN("C2C_017", "duplicate file under the same config map", ERROR),
    ;

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

    public static Diagnostic createDiagnostic(C2CDiagnosticCodes diagnostic, Location location, Object... args) {
        DiagnosticInfo diagnosticInfo = new DiagnosticInfo(diagnostic.getCode(),
                String.format(diagnostic.getMessage(), args), diagnostic.getSeverity());
        return DiagnosticFactory.createDiagnostic(diagnosticInfo, location);
    }
}
