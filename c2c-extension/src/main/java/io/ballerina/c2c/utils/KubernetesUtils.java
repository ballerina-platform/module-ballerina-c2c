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

import io.ballerina.c2c.diagnostics.NullLocation;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.models.DeploymentModel;
import io.ballerina.c2c.models.JobModel;
import io.ballerina.c2c.models.KubernetesContext;
import io.ballerina.c2c.models.KubernetesDataHolder;
import io.ballerina.c2c.models.KubernetesModel;
import io.ballerina.c2c.util.C2CDiagnosticCodes;
import io.ballerina.projects.Package;
import io.ballerina.toml.api.Toml;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import io.fabric8.kubernetes.api.model.ContainerPort;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinax.docker.generator.exceptions.DockerGenException;
import org.ballerinax.docker.generator.models.CopyFileModel;
import org.ballerinax.docker.generator.models.DockerModel;
import org.wso2.ballerinalang.compiler.util.Name;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static io.ballerina.c2c.KubernetesConstants.DEPLOYMENT_POSTFIX;
import static io.ballerina.c2c.KubernetesConstants.EXECUTABLE_JAR;
import static io.ballerina.c2c.KubernetesConstants.JOB_POSTFIX;
import static io.ballerina.c2c.KubernetesConstants.YAML;
import static org.ballerinax.docker.generator.utils.DockerGenUtils.extractJarName;

/**
 * Util methods used for artifact generation.
 */
public class KubernetesUtils {

    private static final PrintStream ERR = System.err;
    private static final PrintStream OUT = System.out;

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
            Files.write(artifactFileName, context.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.APPEND);
            return;
        }
        //create required directories
        if (newFile.getParentFile().mkdirs()) {
            Files.write(artifactFileName, context.getBytes(StandardCharsets.UTF_8));
            return;
        }
        Files.write(artifactFileName, context.getBytes(StandardCharsets.UTF_8));
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

    public static void resolveDockerToml(KubernetesModel model) throws KubernetesPluginException {
        KubernetesDataHolder dataHolder = KubernetesContext.getInstance().getDataHolder();
        final String containerImage = "container.image";
        Toml toml = dataHolder.getBallerinaCloud();
        if (toml != null) {
            DockerModel dockerModel = dataHolder.getDockerModel();
            dockerModel
                    .setRegistry(TomlHelper.getString(toml, containerImage + ".repository", null));
            dockerModel.setTag(TomlHelper.getString(toml, containerImage + ".tag", dockerModel.getTag()));
            dockerModel.setBaseImage(TomlHelper.getString(toml, containerImage + ".base", dockerModel.getBaseImage()));
            dockerModel.setJarFileName(extractJarName(dataHolder.getJarPath()) + EXECUTABLE_JAR);
            dockerModel.setCmd(TomlHelper.getString(toml, containerImage + ".cmd", dockerModel.getCmd()));
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
        dockerModel.setDockerConfig(deploymentModel.getDockerConfigPath());
        dockerModel.setPorts(deploymentModel.getPorts().stream()
                .map(ContainerPort::getContainerPort)
                .collect(Collectors.toSet()));
        dockerModel.setService(true);
        dockerModel.setDockerHost(deploymentModel.getDockerHost());
        dockerModel.setDockerCertPath(deploymentModel.getDockerCertPath());
        dockerModel.addCommandArg(deploymentModel.getCommandArgs());
        return dockerModel;
    }

    public static boolean isBuildOptionDockerOrK8s(String buildOption) {
        switch (buildOption) {
            case "k8s":
            case "docker":
                return true;
        }
        return false;
    }
}
