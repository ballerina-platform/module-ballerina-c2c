/*
 * Copyright (c) 2020, WSO2 Inc. (http://wso2.com) All Rights Reserved.
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

import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import io.ballerina.tools.diagnostics.Location;
import org.ballerinalang.model.elements.PackageID;

/**
 * Responsible for storing diagnostics created scanning the code.
 *
 * @since 2.0.0
 */
public class DiagnosticInfo {
    private DiagnosticSeverity severity;
    private PackageID packageID;
    private Location location;
    private String message;

    public DiagnosticInfo(DiagnosticSeverity severity, PackageID packageID,
                          Location location, String message) {
        this.severity = severity;
        this.packageID = packageID;
        this.location = location;
        this.message = message;
    }

    public DiagnosticSeverity getSeverity() {
        return severity;
    }

    public PackageID getPackageID() {
        return packageID;
    }

    public Location getLocation() {
        return location;
    }

    public String getMessage() {
        return message;
    }
}
