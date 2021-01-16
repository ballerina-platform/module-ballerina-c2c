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

import io.ballerina.toml.api.Toml;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Responsible for code base testing of single file projects.
 */
public class SingleFile {

    @Test
    public void testValidProject() {
        TomlDiagnosticChecker tomlDiagnosticChecker = new TomlDiagnosticChecker();
        Path projectPath = Paths.get("src", "test", "resources", "diagnostics", "valid");

        Path tomlPath = projectPath.resolve("Kubernetes.toml");
        try {
            Toml toml = Toml.read(tomlPath);
            List<DiagnosticInfo> diagnosticInfos = tomlDiagnosticChecker.validateTomlWithSource(toml, projectPath);
            Assert.assertEquals(diagnosticInfos.size(), 0);
        } catch (IOException e) {
            Assert.fail("Toml File Not Found");
        }
    }

    @Test
    public void testMissingPort() {
        TomlDiagnosticChecker tomlDiagnosticChecker = new TomlDiagnosticChecker();
        Path projectPath = Paths.get("src", "test", "resources", "diagnostics", "missing-port");

        Path tomlPath = projectPath.resolve("Kubernetes.toml");
        try {
            Toml toml = Toml.read(tomlPath);
            List<DiagnosticInfo> diagnosticInfos = tomlDiagnosticChecker.validateTomlWithSource(toml, projectPath);
            Assert.assertEquals(diagnosticInfos.size(), 2);
            Assert.assertEquals(diagnosticInfos.get(0).getMessage(), "Invalid Liveness Probe Port");
            Assert.assertEquals(diagnosticInfos.get(1).getMessage(), "Invalid Liveness Probe Path");
        } catch (IOException e) {
            Assert.fail("Toml File Not Found");
        }
    }

    @Test
    public void testInvalidServicePath() {
        TomlDiagnosticChecker tomlDiagnosticChecker = new TomlDiagnosticChecker();
        Path projectPath = Paths.get("src", "test", "resources", "diagnostics", "invalid-service-path");

        Path tomlPath = projectPath.resolve("Kubernetes.toml");
        try {
            Toml toml = Toml.read(tomlPath);
            List<DiagnosticInfo> diagnosticInfos = tomlDiagnosticChecker.validateTomlWithSource(toml, projectPath);
            Assert.assertEquals(diagnosticInfos.size(), 1);
            Assert.assertEquals(diagnosticInfos.get(0).getMessage(), "Invalid Liveness Probe Service Path");
            //TODO replace startsWith and parse better.
        } catch (IOException e) {
            Assert.fail("Toml File Not Found");
        }
    }

    @Test
    public void testInvalidResourcePath() {
        TomlDiagnosticChecker tomlDiagnosticChecker = new TomlDiagnosticChecker();
        Path projectPath = Paths.get("src", "test", "resources", "diagnostics", "invalid-res-path");

        Path tomlPath = projectPath.resolve("Kubernetes.toml");
        try {
            Toml toml = Toml.read(tomlPath);
            List<DiagnosticInfo> diagnosticInfos = tomlDiagnosticChecker.validateTomlWithSource(toml, projectPath);
            Assert.assertEquals(diagnosticInfos.size(), 1);
            Assert.assertEquals(diagnosticInfos.get(0).getMessage(), "Invalid Liveness Probe Resource Path");
            //TODO replace startsWith and parse better.
        } catch (IOException e) {
            Assert.fail("Toml File Not Found");
        }
    }

}
