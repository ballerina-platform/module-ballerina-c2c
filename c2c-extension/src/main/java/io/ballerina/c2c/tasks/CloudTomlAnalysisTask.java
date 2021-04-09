/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerina.c2c.tasks;

import io.ballerina.c2c.diagnostics.TomlDiagnosticChecker;
import io.ballerina.c2c.utils.TomlHelper;
import io.ballerina.projects.CloudToml;
import io.ballerina.projects.Project;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.CompilationAnalysisContext;
import io.ballerina.toml.api.Toml;
import io.ballerina.toml.validator.TomlValidator;
import io.ballerina.toml.validator.schema.Schema;
import io.ballerina.tools.diagnostics.Diagnostic;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Optional;

/**
 * An {@code AnalysisTask} that is triggered for Cloud.toml validation.
 *
 * @since 1.0.0
 */
public class CloudTomlAnalysisTask implements AnalysisTask<CompilationAnalysisContext> {

    @Override
    public void perform(CompilationAnalysisContext compilationAnalysisContext) {
        final Project project = compilationAnalysisContext.currentPackage().project();
        String cloud = project.buildOptions().cloud();
        if (cloud == null || !isSupportedBuildOption(cloud)) {
            return;
        }
        TomlDiagnosticChecker tomlDiagnosticChecker = new TomlDiagnosticChecker(project);
        Optional<CloudToml> cloudToml = project.currentPackage().cloudToml();
        if (cloudToml.isEmpty()) {
            return;
        }
        Toml toml = TomlHelper.createK8sTomlFromProject(cloudToml.get().tomlDocument());
        TomlValidator validator = new TomlValidator(Schema.from(getValidationSchema()));
        validator.validate(toml);
        List<Diagnostic> diagnostics = toml.diagnostics();

        diagnostics.addAll(tomlDiagnosticChecker.validateTomlWithSource(toml));
        diagnostics.forEach(compilationAnalysisContext::reportDiagnostic);
    }

    private String getValidationSchema() {
        try {
            InputStream inputStream =
                    getClass().getClassLoader().getResourceAsStream("c2c-schema.json");
            if (inputStream == null) {
                throw new MissingResourceException("Schema Not found", "c2c-schema.json", "");
            }
            StringWriter writer = new StringWriter();
            IOUtils.copy(inputStream, writer, StandardCharsets.UTF_8.name());
            return writer.toString();
        } catch (IOException e) {
            throw new MissingResourceException("Schema Not found", "c2c-schema.json", "");
        }
    }

    private boolean isSupportedBuildOption(String buildOption) {
        switch (buildOption) {
            case "k8s":
            case "docker":
                return true;
        }
        return false;
    }

}
