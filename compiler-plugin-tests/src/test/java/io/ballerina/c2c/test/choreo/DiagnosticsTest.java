/*
 * Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package io.ballerina.c2c.test.choreo;

import io.ballerina.projects.directory.BuildProject;
import io.ballerina.tools.diagnostics.Diagnostic;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

import static io.ballerina.c2c.test.utils.KubernetesTestUtils.getC2CDiagnostics;

/**
 * Contains the tests for choreo specific diagnostics.
 *
 * @since 2.0.0
 */
public class DiagnosticsTest {

    @Test
    public void testProjectWithCloudToml() {
        Path projectPath = Paths.get("src", "test", "resources", "choreo", "project-cloud");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 1);
        Assert.assertEquals(diagnostics.iterator().next().message(),
                "Cloud.toml is not supported and will be ignored in choreo");
    }

    @Test
    public void testProjectWithMultipleSvc() {
        Path projectPath = Paths.get("src", "test", "resources", "choreo", "multi-svc");
        BuildProject project = BuildProject.load(projectPath);
        Collection<Diagnostic> diagnostics =
                getC2CDiagnostics(project.currentPackage().getCompilation().diagnosticResult().diagnostics());
        Assert.assertEquals(diagnostics.size(), 1);
        Assert.assertEquals(diagnostics.iterator().next().message(), "choreo only supports one service at the moment");
    }
}
