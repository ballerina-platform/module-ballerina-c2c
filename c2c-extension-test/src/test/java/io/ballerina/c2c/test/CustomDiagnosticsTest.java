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
package io.ballerina.c2c.test;

import io.ballerina.projects.directory.BuildProject;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Iterator;

import static io.ballerina.c2c.test.utils.KubernetesTestUtils.getC2CDiagnostics;

/**
 * Responsible for testing the custom diagnostics implemented by code to cloud module.
 *
 * @since 2.0.0
 */
public class CustomDiagnosticsTest {

    @Test
    public void testValidProject() {
        Path projectPath = Paths.get("src", "test", "resources", "diagnostics", "valid");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 0);
    }

    @Test
    public void testValidMultifileProject() {
        Path projectPath = Paths.get("src", "test", "resources", "diagnostics", "valid-multi-files");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 0);
    }

    @Test
    public void testInvalidInput() {
        Path projectPath = Paths.get("src", "test", "resources", "diagnostics", "invalid-input");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 2);
        Iterator<Diagnostic> iterator = diagnostics.iterator();
        Assert.assertEquals(iterator.next().message(),
                "value for key 'min_cpu' expected to match the regex: ^([+-]?[0-9.]+)([eEinumkKMGTP]*[-+]?[0-9]*)$");
        Assert.assertEquals(iterator.next().message(),
                "value for key 'max_cpu' expected to match the regex: ^([+-]?[0-9.]+)([eEinumkKMGTP]*[-+]?[0-9]*)$");
    }

    @Test
    public void testInvalidSyntax() {
        Path projectPath = Paths.get("src", "test", "resources", "diagnostics", "invalid-syntax");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 1);
        Assert.assertEquals(diagnostics.iterator().next().message(), "missing equal token");
    }

    @Test
    public void testMissingPort() {
        Path projectPath = Paths.get("src", "test", "resources", "diagnostics", "missing-port");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 2);
        Iterator<Diagnostic> iterator = diagnostics.iterator();
        Assert.assertEquals(iterator.next().message(), "Invalid Liveness Probe Port");
        Assert.assertEquals(iterator.next().message(), "Invalid Liveness Probe Path");
    }

    @Test
    public void testInvalidServicePath() {
        Path projectPath = Paths.get("src", "test", "resources", "diagnostics", "invalid-service-path");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 1);
        Assert.assertEquals(diagnostics.iterator().next().message(), "Invalid Liveness Probe Service Path");
    }

    @Test
    public void testInvalidResourcePath() {
        Path projectPath = Paths.get("src", "test", "resources", "diagnostics", "invalid-res-path");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 1);
        Assert.assertEquals(diagnostics.iterator().next().message(), "Invalid Liveness Probe Resource Path");
    }

    @Test
    public void testDefaultConfigValueError() {
        Path projectPath = Paths.get("src", "test", "resources", "diagnostics", "default-config-value");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 1);
        Assert.assertEquals(diagnostics.iterator().next().message(), "configurables with no default value is not " +
                "supported");
    }

    @Test
    public void testDefaultConfigValueWarning() {
        Path projectPath = Paths.get("src", "test", "resources", "diagnostics", "configurable-default-port-warning");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 1);
        Diagnostic diagnostic = diagnostics.iterator().next();
        Assert.assertEquals(diagnostic.diagnosticInfo().severity(), DiagnosticSeverity.WARNING);
        Assert.assertEquals(diagnostic.message(),
                "default value of configurable variable `port` could be overridden in runtime");
    }

    @Test
    public void testInvalidSSLConfigValue() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "invalid-ssl-config");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 2);
        Iterator<Diagnostic> iterator = diagnostics.iterator();
        Diagnostic diagnostic = iterator.next();
        Assert.assertEquals(diagnostic.diagnosticInfo().severity(), DiagnosticSeverity.WARNING);
        Assert.assertEquals(diagnostic.message(),
                "unable to read contents of the file `ballerina`");
        diagnostic = iterator.next();
        Assert.assertEquals(diagnostic.diagnosticInfo().severity(), DiagnosticSeverity.WARNING);
        Assert.assertEquals(diagnostic.message(),
                "https config extraction only supports basic string paths");
    }

    @Test
    public void testFailedPortRetrieval() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "failed-port-retrieval");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 1);
        Iterator<Diagnostic> iterator = diagnostics.iterator();
        Diagnostic diagnostic = iterator.next();
        Assert.assertEquals(diagnostic.diagnosticInfo().severity(), DiagnosticSeverity.WARNING);
        Assert.assertEquals(diagnostic.message(), "failed to retrieve port");
    }

    @Test
    public void testFailedPortRetrievalVarRef() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "client-ssl-config-neg");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 1);
        Iterator<Diagnostic> iterator = diagnostics.iterator();
        Diagnostic diagnostic = iterator.next();
        Assert.assertEquals(diagnostic.diagnosticInfo().severity(), DiagnosticSeverity.WARNING);
        Assert.assertEquals(diagnostic.message(), "failed to retrieve port");
    }

    @Test
    public void testSslNamedArg() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "ssl-ref-named-arg");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 0);
    }

    @Test
    public void testSslListenerClient() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "ssl-listener-and-client");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 0);
    }

    @Test
    public void testSslMutualCerts() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "ssl-mutal-ssl-with-certs");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 0);
    }

    @Test
    public void testSslMutualSsl() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "ssl-mutual-ssl");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 0);
    }

    @Test
    public void testHttp2ssl() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "http2-ssl");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 0);
    }

    @Test
    public void testHttp2Mutualssl() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "http2-mutual-ssl");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 0);
    }

    @Test
    public void testClientNoInit() {
        Path projectPath = Paths.get("src", "test", "resources", "service", "client-no-init");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics = project.currentPackage().getCompilation().diagnosticResult().errors();
        Iterator<Diagnostic> iterator = diagnostics.iterator();
        Assert.assertEquals(iterator.next().message(), "uninitialized variable 'x'");
        Assert.assertEquals(iterator.next().message(), "variable 'y' is not initialized");
    }
}
