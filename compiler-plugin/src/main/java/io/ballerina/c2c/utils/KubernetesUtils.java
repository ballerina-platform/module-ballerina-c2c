/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 *
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

package io.ballerina.c2c.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.ballerina.c2c.DockerGenConstants;
import io.ballerina.c2c.diagnostics.NullLocation;
import io.ballerina.c2c.exceptions.DockerGenException;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.models.ConfigMapModel;
import io.ballerina.c2c.models.CopyFileModel;
import io.ballerina.c2c.models.DeploymentModel;
import io.ballerina.c2c.models.DockerModel;
import io.ballerina.c2c.models.JobModel;
import io.ballerina.c2c.models.KubernetesContext;
import io.ballerina.c2c.models.KubernetesDataHolder;
import io.ballerina.c2c.models.KubernetesModel;
import io.ballerina.c2c.models.SecretModel;
import io.ballerina.c2c.util.C2CDiagnosticCodes;
import io.ballerina.cli.utils.DebugUtils;
import io.ballerina.projects.Package;
import io.ballerina.toml.api.Toml;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import org.ballerinalang.model.elements.PackageID;
import org.wso2.ballerinalang.compiler.util.Name;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.ballerina.c2c.KubernetesConstants.DEPLOYMENT_POSTFIX;
import static io.ballerina.c2c.KubernetesConstants.EXECUTABLE_JAR;
import static io.ballerina.c2c.KubernetesConstants.JOB_POSTFIX;
import static io.ballerina.c2c.KubernetesConstants.YAML;
import static io.ballerina.c2c.utils.DockerGenUtils.extractJarName;

/**
 * Util methods used for artifact generation.
 */
public class KubernetesUtils {

    private static final PrintStream ERR = System.err;
    private static final PrintStream OUT = System.out;
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(
            new YAMLFactory().disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID)
    );

    /**
     * Write content to a File. Create the required directories if they don't not exists.
     *
     * @param context        context of the file
     * @param outputFileName target file path
     * @throws IOException If an error occurs when writing to a file
     */
    public static void writeToFile(String context, String outputFileName) throws IOException {
        KubernetesDataHolder dataHolder = KubernetesContext.getInstance().getDataHolder();
        writeToFile(dataHolder.getK8sArtifactOutputPath(), context, outputFileName);
    }

    /**
     * Write content to a File. Create the required directories if they don't not exists.
     *
     * @param outputDir  Artifact output path.
     * @param context    Context of the file
     * @param fileSuffix Suffix for artifact.
     * @throws IOException If an error occurs when writing to a file
     */
    public static void writeToFile(Path outputDir, String context, String fileSuffix) throws IOException {
        KubernetesDataHolder dataHolder = KubernetesContext.getInstance().getDataHolder();
        final String outputName = dataHolder.getOutputName();
        Path artifactFileName = outputDir.resolve(outputName + fileSuffix);
        DeploymentModel deploymentModel = dataHolder.getDeploymentModel();
        JobModel jobModel = dataHolder.getJobModel();
        // Priority given for job, then deployment.
        if (jobModel != null && dataHolder.isSingleYaml()) {
            artifactFileName = outputDir.resolve(outputName + YAML);
        } else if (jobModel == null && deploymentModel != null && dataHolder.isSingleYaml()) {
            artifactFileName = outputDir.resolve(outputName + YAML);
        }

        File newFile = artifactFileName.toFile();
        // append if file exists
        if (newFile.exists()) {
            Files.writeString(artifactFileName, context,
                    StandardOpenOption.APPEND);
            return;
        }
        //create required directories
        newFile.getParentFile().mkdirs();
        Files.writeString(artifactFileName, context);
    }

    /**
     * Read contents of a File.
     *
     * @param targetFilePath target file path
     * @throws KubernetesPluginException If an error occurs when reading file
     */
    public static byte[] readFileContent(Path targetFilePath) throws KubernetesPluginException {
        return readFileContent(targetFilePath, C2CDiagnosticCodes.PATH_CONTENT_READ_FAILED);
    }

    /**
     * Read contents of a File.
     *
     * @param targetFilePath target file path
     * @throws KubernetesPluginException If an error occurs when reading file
     */
    public static byte[] readFileContent(Path targetFilePath, C2CDiagnosticCodes code)
            throws KubernetesPluginException {
        File file = targetFilePath.toFile();
        // append if file exists
        if (file.exists() && !file.isDirectory()) {
            try {
                return Files.readAllBytes(targetFilePath);
            } catch (IOException e) {
                Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(code, new NullLocation(), targetFilePath);
                throw new KubernetesPluginException(diagnostic);
            }
        }
        Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(code, new NullLocation(), targetFilePath);
        throw new KubernetesPluginException(diagnostic);
    }

    /**
     * Prints an Error message.
     *
     * @param msg message to be printed
     */
    public static void printError(String msg) {
        ERR.println("error [k8s plugin]: " + msg);
    }

    /**
     * Prints an Instruction message.
     *
     * @param msg message to be printed
     */
    public static void printInstruction(String msg) {
        OUT.println(msg);
    }

    /**
     * Deletes a given directory.
     *
     * @param path path to directory
     * @throws KubernetesPluginException if an error occurs while deleting
     */
    public static void deleteDirectory(Path path) throws KubernetesPluginException {
        Path pathToBeDeleted = path.toAbsolutePath();
        if (!Files.exists(pathToBeDeleted)) {
            return;
        }
        try {
            Files.walk(pathToBeDeleted)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(C2CDiagnosticCodes.DIRECTORY_DELETE_FAILED,
                    new NullLocation(), path.toString());
            throw new KubernetesPluginException(diagnostic);
        }

    }

    /* Checks if a String is empty ("") or null.
     *
     * @param str the String to check, may be null
     * @return true if the String is empty or null
     */
    public static boolean isBlank(String str) {
        int strLen;
        if (str != null && (strLen = str.length()) != 0) {
            for (int i = 0; i < strLen; ++i) {
                if (!Character.isWhitespace(str.charAt(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns valid kubernetes name.
     *
     * @param name actual value
     * @return valid name
     */
    public static String getValidName(String name) {
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        name = name.toLowerCase(Locale.getDefault()).replaceAll("[_.]", "-")
                .replaceAll("[$]", "").replaceAll("/", "-")
                .replaceAll("--", "-");
        name = name.substring(0, Math.min(name.length(), 15));
        if (name.endsWith("-")) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }

    public static PackageID getProjectID(Package currentPackage) {
        return new PackageID(new Name(currentPackage.packageOrg().value()),
                new Name(currentPackage.packageName().value()),
                new Name(currentPackage.packageVersion().value().toString()));
    }

    public static Optional<String> getExtension(String filename) {
        return Optional.ofNullable(filename)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(filename.lastIndexOf(".") + 1));
    }

    public static String getFileNameOfSecret(SecretModel secretModel) {
        Map<String, String> data = secretModel.getData();
        return data.keySet().iterator().next();
    }

    public static String getFileNameOfConfigMap(ConfigMapModel configMapModel) {
        Map<String, String> data = configMapModel.getData();
        return data.keySet().iterator().next();
    }

    public static void resolveDockerToml(KubernetesModel model) throws KubernetesPluginException {
        KubernetesDataHolder dataHolder = KubernetesContext.getInstance().getDataHolder();
        final String containerImage = "container.image";
        Toml toml = dataHolder.getBallerinaCloud();
        DockerModel dockerModel = dataHolder.getDockerModel();
        dockerModel.setJarFileName(extractJarName(dataHolder.getJarPath()) + EXECUTABLE_JAR);

        String fatJarFileName = dockerModel.getFatJarPath().getFileName().toString();
        String executableName = fatJarFileName.replaceFirst(".jar", "");
        StringBuilder defaultBuilderCmd = new StringBuilder().append("native-image ");
        //TODO see if we need double quotes to name or jar
        if (!dockerModel.getGraalvmBuildArgs().equals("")) {
            defaultBuilderCmd.append(dockerModel.getGraalvmBuildArgs()).append(" ");
        }
        defaultBuilderCmd.append("-jar ").append(fatJarFileName).append(" -o ")
                .append(executableName).append(" --no-fallback");

        //Avoid adding mostly static flag if --static flag is given
        if (!dockerModel.getGraalvmBuildArgs().contains("--static")) {
            defaultBuilderCmd.append(" -H:+StaticExecutableWithDynamicLibC");
        }
        dockerModel.setBuilderCmd(defaultBuilderCmd.toString());
        String defaultBaseImage = DockerGenConstants.JRE_SLIM_BASE;
        if (dockerModel.isGraalVMBuild()) {
            defaultBaseImage = DockerGenConstants.NATIVE_RUNTIME_BASE_IMAGE;
        }
        dockerModel.setBaseImage(defaultBaseImage);
        if (toml != null) {
            dockerModel
                    .setRegistry(TomlHelper.getString(toml, containerImage + ".repository", null));
            dockerModel.setTag(TomlHelper.getString(toml, containerImage + ".tag", dockerModel.getTag()));
            dockerModel.setBaseImage(TomlHelper.getString(toml, containerImage + ".base", defaultBaseImage));
            dockerModel.setEntryPoint(TomlHelper.getString(toml, containerImage + ".entrypoint",
                    dockerModel.getEntryPoint()));
            if (model instanceof DeploymentModel) {

                dockerModel.setName(TomlHelper.getString(toml, containerImage + ".name",
                        model.getName().replace(DEPLOYMENT_POSTFIX, "")));
                String imageName = isBlank(dockerModel.getRegistry()) ?
                        dockerModel.getName() + ":" + dockerModel.getTag() :
                        dockerModel.getRegistry() + "/" + dockerModel.getName() + ":" + dockerModel.getTag();
                ((DeploymentModel) model).setImage(imageName);
            } else {
                dockerModel.setName(TomlHelper.getString(toml, containerImage + ".name",
                        model.getName().replace(JOB_POSTFIX, "")));
                String imageName = isBlank(dockerModel.getRegistry()) ?
                        dockerModel.getName() + ":" + dockerModel.getTag() :
                        dockerModel.getRegistry() + "/" + dockerModel.getName() + ":" + dockerModel.getTag();
                ((JobModel) model).setImage(imageName);
            }
            dockerModel.setBuildImage(TomlHelper.getBoolean(toml, "settings.buildImage", true));
            Set<CopyFileModel> copyFiles = new HashSet<>();
            for (Toml entry : toml.getTables("container.copy.files")) {
                CopyFileModel copyFileModel = new CopyFileModel();
                copyFileModel.setSource(TomlHelper.getString(entry, "sourceFile"));
                copyFileModel.setTarget(TomlHelper.getString(entry, "target"));
                copyFiles.add(copyFileModel);
            }
            dockerModel.setThinJar(isThinJar(toml, dockerModel));
            dockerModel.setBuilderBase(TomlHelper.getString(toml, "graalvm.builder.base",
                    DockerGenConstants.NATIVE_BUILDER_IMAGE));

            dockerModel.setBuilderCmd(TomlHelper.getString(toml, "graalvm.builder.buildCmd",
                    defaultBuilderCmd.toString()));
            try {
                dockerModel.setCopyFiles(copyFiles);
            } catch (DockerGenException e) {
                DiagnosticInfo diagnosticInfo = new DiagnosticInfo(C2CDiagnosticCodes.DOCKER_FAILED.getCode(),
                        e.getMessage(), DiagnosticSeverity.WARNING);
                Diagnostic diagnostic = DiagnosticFactory.createDiagnostic(diagnosticInfo, new NullLocation());
                throw new KubernetesPluginException(diagnostic);
            }
        }
    }

    /**
     * Creates docker model from Deployment Model object.
     *
     * @param deploymentModel Deployment model
     */
    public static DockerModel getDockerModel(DeploymentModel deploymentModel) {
        KubernetesDataHolder dataHolder = KubernetesContext.getInstance().getDataHolder();
        DockerModel dockerModel = dataHolder.getDockerModel();
        String dockerImage = deploymentModel.getImage();
        String imageTag = "latest";
        if (dockerImage.contains(":")) {
            imageTag = dockerImage.substring(dockerImage.lastIndexOf(":") + 1);
            dockerImage = dockerImage.substring(0, dockerImage.lastIndexOf(":"));
        }
        dockerModel.setJarFileName(extractJarName(dataHolder.getJarPath()) + EXECUTABLE_JAR);
        dockerModel.setPkgId(dataHolder.getPackageID());
        dockerModel.setRegistry(deploymentModel.getRegistry());
        dockerModel.setName(dockerImage);
        dockerModel.setTag(imageTag);

        if (!dockerModel.isTest()) {
            dockerModel.setService(true);
            dockerModel.setPorts(deploymentModel.getPorts().stream()
                    .map(ContainerPort::getContainerPort)
                    .collect(Collectors.toSet()));
        } else {
            dockerModel.setService(false);
            dockerModel.setPorts(Collections.emptySet());
        }

        dockerModel.addCommandArg(deploymentModel.getCommandArgs());
        return dockerModel;
    }

    public static boolean isValidBuildOption(String buildOption) {
        return switch (buildOption) {
            case "k8s", "docker", "openshift" -> true;
            default -> false;
        };
    }

    public static <T> String asYaml(T object) throws KubernetesPluginException {
        try {
            return YAML_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            DiagnosticInfo diagnosticInfo = new DiagnosticInfo(C2CDiagnosticCodes.ARTIFACT_GEN_FAILED.getCode(),
                    e.getMessage(), DiagnosticSeverity.WARNING);
            throw new KubernetesPluginException(DiagnosticFactory.createDiagnostic(diagnosticInfo, new NullLocation()));
        }
    }

    public static boolean isThinJar(Toml ballerinaCloud, DockerModel dockerModel) {
        if (!TomlHelper.getBoolean(ballerinaCloud, "settings.thinJar", true)) {
            return false;
        }

        return dockerModel.getDependencyJarPaths().size() + dockerModel.getCopyFiles().size() +
                dockerModel.getEnv().size() < DockerGenConstants.MAX_BALLERINA_LAYERS;
    }

    public static List<VolumeMount> generateConfigMapVolumeMounts(Collection<ConfigMapModel> configMapModels) {
        List<VolumeMount> volumeMounts = new ArrayList<>();
        for (ConfigMapModel configMapModel : configMapModels) {
            final String mountPath = configMapModel.getMountPath();
            VolumeMountBuilder volumeMountBuilder = new VolumeMountBuilder()
                    .withMountPath(mountPath)
                    .withName(configMapModel.getName() + "-volume")
                    .withReadOnly(configMapModel.isReadOnly());

            if ((!configMapModel.isDir()) && (!configMapModel.isBallerinaConf())) {
                volumeMountBuilder.withSubPath(KubernetesUtils.getFileNameOfConfigMap(configMapModel));
            }
            volumeMounts.add(volumeMountBuilder.build());
        }
        return volumeMounts;
    }

    public static List<VolumeMount> generateSecretVolumeMounts(Collection<SecretModel> secretModels) {
        List<VolumeMount> volumeMounts = new ArrayList<>();
        for (SecretModel secretModel : secretModels) {
            VolumeMountBuilder volumeMountBuilder = new VolumeMountBuilder()
                    .withMountPath(secretModel.getMountPath())
                    .withName(secretModel.getName() + "-volume")
                    .withReadOnly(secretModel.isReadOnly());
            if ((!secretModel.isDir()) && (!secretModel.isBallerinaConf())) {
                volumeMountBuilder.withSubPath(KubernetesUtils.getFileNameOfSecret(secretModel));
            }
            VolumeMount volumeMount = volumeMountBuilder.build();
            volumeMounts.add(volumeMount);
        }
        return volumeMounts;
    }

    public static void validateFileExistence(File file) throws KubernetesPluginException {
        if (!file.exists()) {
            Diagnostic diagnostic = C2CDiagnosticCodes.createDiagnostic(
                    C2CDiagnosticCodes.PATH_CONTENT_READ_FAILED, new NullLocation(), file.getAbsolutePath());
            throw new KubernetesPluginException(diagnostic);
        }
    }

    public static void runCommand(String dockerImage) {
        ProcessBuilder processBuilder;
        if (DebugUtils.isInDebugMode()) {
            String debugPort = System.getProperty("debug");
            processBuilder = new ProcessBuilder("docker", "run", "--rm", "-p", debugPort + ":" + debugPort,
                    dockerImage);
        } else {
            processBuilder = new ProcessBuilder("docker", "run", "--rm", dockerImage);
        }
        int exitCode = executeProcess(processBuilder);
        if (exitCode != 0) {
            printError("Error running the docker image: " + dockerImage);
        }

    }

    private static int executeProcess(ProcessBuilder processBuilder) {
        try {
            Process process = processBuilder.start();

            // Get the output stream of the process
            InputStream inputStream = process.getInputStream();
            InputStream errorStream = process.getErrorStream();

            // Handle output stream
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Untagged") || line.contains("Deleted")) {
                        continue;
                    }
                    OUT.println(line);
                }
            }

            // Handle error stream
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream))) {
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    ERR.println(errorLine);
                }
            }

            // Wait for the process to finish
            return process.waitFor();
        } catch (IOException | InterruptedException e) {
            printError("Error executing the process: " + processBuilder.command());
            return -1;
        }
    }

    public static void deleteDockerImage(String imageName) {
        ProcessBuilder processBuilder = new ProcessBuilder("docker", "rmi", imageName);
        int exitCode = executeProcess(processBuilder);
        if (exitCode != 0) {
            printError("Error deleting the docker image: " + imageName);
        }
    }

}
