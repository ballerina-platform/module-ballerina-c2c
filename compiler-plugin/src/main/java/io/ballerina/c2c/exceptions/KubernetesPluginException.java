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

package io.ballerina.c2c.exceptions;

import io.ballerina.tools.diagnostics.Diagnostic;

/**
 * Custom exception for kubernetes artifact generation errors.
 */
public class KubernetesPluginException extends Exception {
    private final Diagnostic diagnostic;
    private final boolean skipPrintTrace;

    public KubernetesPluginException(Diagnostic diagnostic) {
        super(diagnostic.message());
        this.diagnostic = diagnostic;
        this.skipPrintTrace = false;
    }

    public KubernetesPluginException(Diagnostic diagnostic, boolean skipPrintTrace) {
        super(diagnostic.message());
        this.diagnostic = diagnostic;
        this.skipPrintTrace = skipPrintTrace;
    }

    public Diagnostic getDiagnostic() {
        return diagnostic;
    }

    public boolean isSkipPrintTrace() {
        return this.skipPrintTrace;
    }
}
