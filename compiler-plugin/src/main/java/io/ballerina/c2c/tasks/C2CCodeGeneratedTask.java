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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.ballerina.c2c.ArtifactManager;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.models.KubernetesContext;
import io.ballerina.c2c.models.KubernetesDataHolder;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.ballerina.cli.launcher.LauncherUtils;
import io.ballerina.cli.utils.TestUtils;
import io.ballerina.projects.ArtifactType;
import io.ballerina.projects.BuildOptions;
import io.ballerina.projects.CloudToml;
import io.ballerina.projects.JBallerinaBackend;
import io.ballerina.projects.JarLibrary;
import io.ballerina.projects.JvmTarget;
import io.ballerina.projects.Module;
import io.ballerina.projects.ModuleDescriptor;
import io.ballerina.projects.Package;
import io.ballerina.projects.PackageCompilation;
import io.ballerina.projects.PackageDescriptor;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectKind;
import io.ballerina.projects.internal.model.Target;
import io.ballerina.projects.plugins.CompilerLifecycleEventContext;
import io.ballerina.projects.plugins.CompilerLifecycleTask;
import io.ballerina.projects.util.ProjectConstants;
import io.ballerina.toml.api.Toml;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinalang.test.runtime.entity.TestSuite;
import org.ballerinalang.test.runtime.util.TesterinaConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;



import static io.ballerina.c2c.KubernetesConstants.DOCKER;
import static io.ballerina.c2c.KubernetesConstants.KUBERNETES;
import static io.ballerina.c2c.utils.DockerGenUtils.extractJarName;
import static io.ballerina.c2c.utils.DockerGenUtils.getTestSuiteJsonCopiedDir;
import static io.ballerina.c2c.utils.DockerGenUtils.getWorkDir;
import static io.ballerina.c2c.utils.KubernetesUtils.printError;
import static io.ballerina.cli.launcher.LauncherUtils.createLauncherException;

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

        ArtifactType artifactType = compilerLifecycleEventContext.artifactType();
        Optional<Path> executablePath = compilerLifecycleEventContext.getGeneratedArtifactPath();
        final Package currentPackage = compilerLifecycleEventContext.currentPackage();

        if (artifactType == ArtifactType.BUILD) {
            PackageDescriptor descriptor = currentPackage.descriptor();
            executablePath.ifPresent(path -> {
                String executableJarName = "$anon".equals(descriptor.org().value()) ? path.getFileName().toString() :
                        descriptor.org().value() + "-" + descriptor.name().value() +
                                "-" + descriptor.version().value() + ".jar";
                String outputName = "$anon".equals(descriptor.org().value()) ? extractJarName(path.getFileName()) :
                        descriptor.name().value().toLowerCase(Locale.ROOT);
                dataHolder.setOutputName(outputName);
                addDependencyJars(compilerLifecycleEventContext.compilation(), executableJarName);
                dataHolder.setSourceRoot(executablePath.get().getParent()
                        .getParent().getParent());
                codeGeneratedInternal(KubernetesUtils.getProjectID(currentPackage),
                        path, compilerLifecycleEventContext.currentPackage(), artifactType);
            });
        } else {
            JBallerinaBackend jBallerinaBackend = JBallerinaBackend.from(compilerLifecycleEventContext.compilation(),
                    JvmTarget.JAVA_17);
            dataHolder.getDockerModel().setTest(true);

            //executablePath is the standalone testable jar
            executablePath.ifPresent(path -> {
                //get the test suit json
                Target target;
                Path testsCachePath;
                Path cachesRoot;

                try {
                    if (project.kind() == ProjectKind.BUILD_PROJECT) {
                        cachesRoot = project.sourceRoot();
                        target = new Target(project.targetDir());
                    } else {
                        cachesRoot = Files.createTempDirectory("ballerina-test-cache" + System.nanoTime());
                        target = new Target(cachesRoot);
                    }

                    testsCachePath = target.getTestsCachePath();
                } catch (IOException e) {
                    throw createLauncherException("error while creating target directory: ", e);
                }

                Path jsonFilePath = testsCachePath.resolve(TesterinaConstants.TESTERINA_TEST_SUITE);

                if (!Files.exists(jsonFilePath)) {
                    throw LauncherUtils.createLauncherException("error while finding the test suit json");
                }

                Map<String, TestSuite> testSuiteMap;
                List<Path> classPaths = new ArrayList<>();
                List<Path> moduleJarPaths = TestUtils.getModuleJarPaths(jBallerinaBackend, currentPackage);
                moduleJarPaths = moduleJarPaths.stream().map(Path::getFileName).toList();

                try (BufferedReader br = Files.newBufferedReader(jsonFilePath, StandardCharsets.UTF_8)) {
                    Gson gson = new Gson();

                    testSuiteMap = gson.fromJson(br,
                            new TypeToken<Map<String, TestSuite>> () { }.getType());

                    for (ModuleDescriptor moduleDescriptor :
                            currentPackage.moduleDependencyGraph().toTopologicallySortedList()) {
                        //add the test execution dependencies
                        addTestDependencyJars(project, compilerLifecycleEventContext.compilation(),
                                moduleDescriptor, testSuiteMap, classPaths, moduleJarPaths);
                    }
                } catch (IOException e) {
                    throw LauncherUtils.createLauncherException("error while reading the test suit json");
                }

                //now rewrite the json
                TestUtils.writeToTestSuiteJson(testSuiteMap, testsCachePath);

                dataHolder.getDockerModel().setTestSuiteJsonPath(jsonFilePath);
                StringJoiner classPath = TestUtils.joinClassPaths(classPaths);
                this.dataHolder.getDockerModel().setClassPath(classPath.toString());

                //once rewritten, set the command line args for the BTestMain
                List<String> cmd;
                String cmdStr;

                //read the mainArgs.txt and get the cmd args
                Path mainArgsPath = path.getParent().resolve(ProjectConstants.TEST_RUNTIME_MAIN_ARGS_FILE);
                try {
                    cmd = Files.readAllLines(mainArgsPath);
                } catch (IOException e) {
                    throw LauncherUtils.createLauncherException("error while reading the mainArgs.txt");
                }

                //replace test suite json path [1], target [2], jacoco agent jar path [3]
                cmd.set(1, Paths.get(getTestSuiteJsonCopiedDir())
                        .resolve(TesterinaConstants.TESTERINA_TEST_SUITE).toString());
                //target directory is -> to load the test suite json (work directory is the target)
                cmd.set(2, getWorkDir());

                Path jacocoAgentJarPath = Path.of(TestUtils.getJacocoAgentJarPath());
                cmd.set(3, getCopiedJarPath(jacocoAgentJarPath.getFileName()).toString());
                //TODO:: FIX JACOCO AGENT PATH
                this.dataHolder.getDockerModel().setJacocoAgentJarPath(jacocoAgentJarPath);

                this.dataHolder.getDockerModel().setTestRunTimeCmdArgs(cmd);

                //set the output name
                this.dataHolder.setOutputName(project.currentPackage()
                        .packageName().toString().toLowerCase(Locale.ROOT)  + "-" + TesterinaConstants.TESTABLE);
                //set the source root
                this.dataHolder.setSourceRoot(project.sourceRoot());
                //finally create the internal code for the test executable
                codeGeneratedInternal(KubernetesUtils.getProjectID(currentPackage), path, currentPackage, artifactType);
            });
        }


    }

    public void codeGeneratedInternal(PackageID packageId, Path executableJarFile, Package currentPackage,
                                      ArtifactType artifactType) {
        Optional<CloudToml> cloudToml = currentPackage.cloudToml();
        BuildOptions buildOptions = currentPackage.project().buildOptions();
        String buildType = buildOptions.cloud();
        dataHolder.getDockerModel().setFatJarPath(executableJarFile);
        String graalvmBuildArgs = buildOptions.graalVMBuildOptions();
        dataHolder.getDockerModel().setGraalvmBuildArgs(graalvmBuildArgs);
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
                Path projectRoot = currentPackage.project().sourceRoot();
                if (Files.exists(projectRoot.resolve("Ballerina.toml"))) {
                    if (artifactType == ArtifactType.TEST) {
                        kubernetesOutputPath = projectRoot.resolve("target")
                                .resolve(KUBERNETES)
                                .resolve("test")
                                .resolve(extractJarName(executableJarFile));
                        dockerOutputPath = projectRoot.resolve("target")
                                .resolve(DOCKER)
                                .resolve("test")
                                .resolve(extractJarName(executableJarFile));

                    } else {
                        kubernetesOutputPath = projectRoot.resolve("target")
                                .resolve(KUBERNETES)
                                .resolve(extractJarName(executableJarFile));
                        dockerOutputPath = projectRoot.resolve("target")
                                .resolve(DOCKER)
                                .resolve(extractJarName(executableJarFile));
                    }
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
                KubernetesUtils.deleteDirectory(dockerOutputPath);
                artifactManager.populateDeploymentModel();
                artifactManager.createArtifacts(buildType, buildOptions.nativeImage());
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

    private void addTestDependencyJars(Project project, PackageCompilation compilation,
                                       ModuleDescriptor moduleDescriptor,
                                       Map<String, TestSuite> testSuiteMap,
                                       List<Path> classPaths, List<Path> moduleJarPaths) {
        JBallerinaBackend jBallerinaBackend = JBallerinaBackend.from(compilation,
                JvmTarget.JAVA_17);
        io.ballerina.projects.JarResolver jarResolver = jBallerinaBackend.jarResolver();

        Collection<JarLibrary> dependencies = jarResolver.getJarFilePathsRequiredForTestExecution(
                moduleDescriptor.name()
        );

        // Add dependency jar files to docker model.
        dataHolder.getDockerModel().addDependencyJarPaths(
                dependencies.stream()
                        .map(JarLibrary::path)
                        .collect(Collectors.toSet()));

        //add the dependencies to the testSuiteMap
        Stream<Path> jarPaths = dependencies.stream().map(JarLibrary::path);

        Stream<Path> pathStream = jarPaths.map(jarPath -> {
            //get the file name
            Path jarFileName = jarPath.getFileName();

            //create a new path to reflect the following directory -> "/home/jars/{fileName}"
            jarPath = getCopiedJarPath(jarFileName);

            if (!moduleJarPaths.contains(jarFileName)) { //if the jar file is not a module jar
                classPaths.add(jarPath);
            }

            return jarPath;
        });

        Module module = project.currentPackage().module(moduleDescriptor.name());

        testSuiteMap.get(TestUtils.getResolvedModuleName(module, moduleDescriptor.name())).addTestExecutionDependencies(
                pathStream.collect(Collectors.toCollection(ArrayList::new))
        );
    }

    private static Path getCopiedJarPath(Path jarFileName) {
        Path jarPath;
        StringBuilder newPath = new StringBuilder();
        jarPath = Path.of(newPath.append(getWorkDir()).append("/jars/").append(jarFileName.toString()).toString());
        return jarPath;
    }

    private void addDependencyJars(PackageCompilation compilation, String executableFatJar) {
        JBallerinaBackend jBallerinaBackend = JBallerinaBackend.from(compilation,
                JvmTarget.JAVA_17);
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
