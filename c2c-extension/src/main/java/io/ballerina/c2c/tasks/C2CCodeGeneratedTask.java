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

package io.ballerina.c2c.tasks;

import io.ballerina.c2c.ArtifactManager;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.models.KubernetesContext;
import io.ballerina.c2c.models.KubernetesDataHolder;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.ballerina.projects.CloudToml;
import io.ballerina.projects.JBallerinaBackend;
import io.ballerina.projects.JarLibrary;
import io.ballerina.projects.JvmTarget;
import io.ballerina.projects.PackageCompilation;
import io.ballerina.projects.Project;
import io.ballerina.projects.plugins.CompilerLifecycleEventContext;
import io.ballerina.projects.plugins.CompilerLifecycleTask;
import io.ballerina.toml.api.Toml;
import org.ballerinalang.model.elements.PackageID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.ballerina.c2c.KubernetesConstants.DOCKER;
import static io.ballerina.c2c.KubernetesConstants.KUBERNETES;
import static io.ballerina.c2c.utils.DockerGenUtils.extractJarName;
import static io.ballerina.c2c.utils.KubernetesUtils.printError;

/*
    Code generated Task for c2c projects.
 */

/**
 * A {@code CompilerLifecycleTask} Code generated Task for c2c projects.
 *
 * @since 1.0.0
 */
public class C2CCodeGeneratedTask implements CompilerLifecycleTask<CompilerLifecycleEventContext> {

    private static final Logger pluginLog = LoggerFactory.getLogger(C2CCodeGeneratedTask.class);
    private final KubernetesDataHolder dataHolder = KubernetesContext.getInstance().getDataHolder();

    @Override
    public void perform(CompilerLifecycleEventContext compilerLifecycleEventContext) {
        final Project project = compilerLifecycleEventContext.currentPackage().project();
        String cloud = project.buildOptions().cloud();
        if (cloud == null || !KubernetesUtils.isBuildOptionDockerOrK8s(cloud)) {
            return;
        }
        Optional<Path> executablePath = compilerLifecycleEventContext.getGeneratedArtifactPath();
        final PackageID currentPackage = KubernetesContext.getInstance().getCurrentPackage();
        executablePath.ifPresent(path -> {
            String executableJarName = "$anon".equals(currentPackage.orgName.value) ? path.getFileName().toString() :
                    currentPackage.orgName.value + "-" + currentPackage.name.value +
                            "-" + currentPackage.version.value + ".jar";
            String outputName = "$anon".equals(currentPackage.orgName.value) ? extractJarName(path.getFileName()) :
                    currentPackage.name.value;
            dataHolder.setOutputName(outputName);
            addDependencyJars(compilerLifecycleEventContext.compilation(), executableJarName);
            dataHolder.setSourceRoot(executablePath.get().getParent()
                    .getParent().getParent());
            codeGeneratedInternal(currentPackage,
                    path, compilerLifecycleEventContext.currentPackage().cloudToml(),
                    compilerLifecycleEventContext.currentPackage().project().buildOptions().cloud());
        });
    }

    public void codeGeneratedInternal(PackageID packageId, Path executableJarFile, Optional<CloudToml> cloudToml,
                                      String buildType) {
        KubernetesContext.getInstance().setCurrentPackage(packageId);
        dataHolder.setPackageID(packageId);
        executableJarFile = executableJarFile.toAbsolutePath();
        if (null != executableJarFile.getParent() && Files.exists(executableJarFile.getParent())) {
            // artifacts location for a single bal file.
            Path kubernetesOutputPath = executableJarFile.getParent().resolve(KUBERNETES);
            Path dockerOutputPath = executableJarFile.getParent().resolve(DOCKER);
            if (null != executableJarFile.getParent().getParent().getParent() &&
                    Files.exists(executableJarFile.getParent().getParent().getParent())) {
                // if executable came from a ballerina project
                Path projectRoot = executableJarFile.getParent().getParent().getParent();
                if (Files.exists(projectRoot.resolve("Ballerina.toml"))) {
                    kubernetesOutputPath = projectRoot.resolve("target")
                            .resolve(KUBERNETES)
                            .resolve(extractJarName(executableJarFile));
                    dockerOutputPath = projectRoot.resolve("target")
                            .resolve(DOCKER)
                            .resolve(extractJarName(executableJarFile));
                    //Read and parse ballerina cloud
                    cloudToml.ifPresent(
                            kubernetesToml -> dataHolder.setBallerinaCloud(new Toml(kubernetesToml.tomlAstNode())));
                }
            }
            dataHolder.setK8sArtifactOutputPath(kubernetesOutputPath);
            dataHolder.setDockerArtifactOutputPath(dockerOutputPath);
            ArtifactManager artifactManager = new ArtifactManager();
            try {
                KubernetesUtils.deleteDirectory(kubernetesOutputPath);
                artifactManager.populateDeploymentModel();
                artifactManager.createArtifacts(buildType);
            } catch (KubernetesPluginException e) {
                String errorMessage = "module [" + packageId + "] " + e.getMessage();
                printError(errorMessage);
                if (!e.isSkipPrintTrace()) {
                    pluginLog.error(errorMessage, e);
                }
                try {
                    KubernetesUtils.deleteDirectory(kubernetesOutputPath);
                } catch (KubernetesPluginException ignored) {
                    //ignored
                }
            }
        } else {
            printError("error in resolving Docker generation location.");
            pluginLog.error("error in resolving Docker generation location.");
        }
    }

    private void addDependencyJars(PackageCompilation compilation, String executableFatJar) {
        JBallerinaBackend jBallerinaBackend = JBallerinaBackend.from(compilation,
                JvmTarget.JAVA_11);
        io.ballerina.projects.JarResolver jarResolver = jBallerinaBackend.jarResolver();

        // Add dependency jar files to docker model.
        dataHolder.getDockerModel().addDependencyJarPaths(
                jarResolver.getJarFilePathsRequiredForExecution().stream()
                        .map(JarLibrary::path)
                        .collect(Collectors.toSet()));
        jarResolver.getJarFilePathsRequiredForExecution()
                .stream()
                .filter(jarLibrary -> jarLibrary.path().getFileName().toString().endsWith(executableFatJar))
                .findFirst()
                .ifPresent(jarLibrary -> dataHolder.setJarPath(jarLibrary.path()));
    }
}
