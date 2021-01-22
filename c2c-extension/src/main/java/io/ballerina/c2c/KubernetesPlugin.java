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

package io.ballerina.c2c;

import io.ballerina.c2c.diagnostics.TomlDiagnosticChecker;
import io.ballerina.c2c.exceptions.KubernetesPluginException;
import io.ballerina.c2c.models.KubernetesContext;
import io.ballerina.c2c.models.KubernetesDataHolder;
import io.ballerina.c2c.processors.NodeProcessorFactory;
import io.ballerina.c2c.processors.ServiceNodeProcessor;
import io.ballerina.c2c.utils.KubernetesUtils;
import io.ballerina.c2c.utils.TomlHelper;
import io.ballerina.projects.JBallerinaBackend;
import io.ballerina.projects.JvmTarget;
import io.ballerina.projects.KubernetesToml;
import io.ballerina.projects.PackageCompilation;
import io.ballerina.projects.Project;
import io.ballerina.projects.internal.model.Target;
import io.ballerina.toml.api.Toml;
import io.ballerina.toml.validator.TomlValidator;
import io.ballerina.toml.validator.schema.Schema;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import org.apache.commons.io.IOUtils;
import org.ballerinalang.compiler.CompilerOptionName;
import org.ballerinalang.compiler.plugins.AbstractCompilerPlugin;
import org.ballerinalang.compiler.plugins.SupportedAnnotationPackages;
import org.ballerinalang.model.TreeBuilder;
import org.ballerinalang.model.elements.Flag;
import org.ballerinalang.model.elements.PackageID;
import org.ballerinalang.model.tree.AnnotationAttachmentNode;
import org.ballerinalang.model.tree.FunctionNode;
import org.ballerinalang.model.tree.PackageNode;
import org.ballerinalang.model.tree.ServiceNode;
import org.ballerinalang.model.tree.SimpleVariableNode;
import org.ballerinalang.model.tree.TopLevelNode;
import org.ballerinalang.model.tree.expressions.ExpressionNode;
import org.ballerinalang.util.diagnostic.DiagnosticLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangRecordLiteral;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangSimpleVarRef;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangTypeInit;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.CompilerOptions;
import org.wso2.ballerinalang.compiler.util.TypeTags;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.ballerina.c2c.KubernetesConstants.DOCKER;
import static io.ballerina.c2c.KubernetesConstants.KUBERNETES;
import static io.ballerina.c2c.utils.KubernetesUtils.printError;
import static org.ballerinax.docker.generator.utils.DockerGenUtils.extractJarName;

/**
 * Compiler plugin to generate kubernetes artifacts.
 */
@SupportedAnnotationPackages(
        value = {"ballerina/cloud"}
)
public class KubernetesPlugin extends AbstractCompilerPlugin {

    private static final Logger pluginLog = LoggerFactory.getLogger(KubernetesPlugin.class);
    private DiagnosticLog dlog;
    private boolean enabled;

    @Override
    public void setCompilerContext(CompilerContext context) {
        String cloudProvider = CompilerOptions.getInstance(context).get(CompilerOptionName.CLOUD);
        if ("k8s".equals(cloudProvider)) {
            enabled = true;
        }
    }

    @Override
    public void init(DiagnosticLog diagnosticLog) {
        this.dlog = diagnosticLog;
    }

    @Override
    public List<Diagnostic> codeAnalyze(Project project) {
        Optional<KubernetesToml> kubernetesToml = project.currentPackage().kubernetesToml();
        if (kubernetesToml.isPresent()) {
            Toml toml = TomlHelper.createK8sTomlFromProject(kubernetesToml.get().tomlDocument());
            TomlValidator validator = new TomlValidator(Schema.from(getValidationSchema()));
            validator.validate(toml);
            List<Diagnostic> diagnostics = toml.diagnostics();
            TomlDiagnosticChecker tomlDiagnosticChecker = new TomlDiagnosticChecker();
            diagnostics.addAll(tomlDiagnosticChecker.validateTomlWithSource(toml, project));
            return diagnostics;
        }
        return Collections.emptyList();
    }

    @Override
    public void process(PackageNode packageNode) {
        BLangPackage bPackage = (BLangPackage) packageNode;

        if (enabled) {
            KubernetesContext.getInstance().setCurrentPackage(bPackage.packageID);
            KubernetesDataHolder dataHolder = KubernetesContext.getInstance().getDataHolder();
            dataHolder.setPackageID(bPackage.packageID);

            // Get the units of the file which has kubernetes import as _
            List<TopLevelNode> topLevelNodes = bPackage.getCompilationUnits().stream()
                    .flatMap(cu -> cu.getTopLevelNodes().stream())
                    .collect(Collectors.toList());

            // Filter out the services
            List<ServiceNode> serviceNodes = topLevelNodes.stream()
                    .filter(tln -> tln instanceof ServiceNode)
                    .map(tln -> (ServiceNode) tln)
                    .collect(Collectors.toList());

            // Generate artifacts for services for all services
            serviceNodes.forEach(sn -> process(sn, Collections.singletonList(KubernetesUtils.createAnnotation(
                    "Deployment"))));

            serviceNodes.forEach(sn -> process(sn, Collections.singletonList(KubernetesUtils.createAnnotation(
                    "HPA"))));

            // Create Service annotation with NodePort service type
            AnnotationAttachmentNode serviceAnnotation = KubernetesUtils.createAnnotation("Service");
            BLangRecordLiteral svcRecordLiteral = (BLangRecordLiteral) TreeBuilder.createRecordLiteralNode();
            serviceAnnotation.setExpression(svcRecordLiteral);

            BLangLiteral serviceTypeKey = (BLangLiteral) TreeBuilder.createLiteralExpression();
            serviceTypeKey.value = ServiceNodeProcessor.ServiceConfiguration.serviceType.name();
            serviceTypeKey.type = new BType(TypeTags.STRING, null);

            BLangLiteral serviceTypeValue = new BLangLiteral();
            serviceTypeValue.value = KubernetesConstants.ServiceType.ClusterIP.name();
            serviceTypeValue.type = new BType(TypeTags.STRING, null);

            BLangRecordLiteral.BLangRecordKeyValueField serviceTypeRecordField =
                    new BLangRecordLiteral.BLangRecordKeyValueField();
            serviceTypeRecordField.key = new BLangRecordLiteral.BLangRecordKey(serviceTypeKey);
            serviceTypeRecordField.valueExpr = serviceTypeValue;

            svcRecordLiteral.fields.add(serviceTypeRecordField);

            // Filter services with 'new Listener()' and generate services
            for (ServiceNode serviceNode : serviceNodes) {
                Optional<? extends ExpressionNode> initListener = serviceNode.getAttachedExprs().stream()
                        .filter(aex -> aex instanceof BLangTypeInit)
                        .findAny();

                if (initListener.isPresent()) {
                    serviceNodes.forEach(sn -> process(sn, Collections.singletonList(serviceAnnotation)));
                }
            }

            // Get the variable names of the listeners attached to services
            List<String> listenerNamesToExpose = serviceNodes.stream()
                    .map(ServiceNode::getAttachedExprs)
                    .flatMap(Collection::stream)
                    .filter(aex -> aex instanceof BLangSimpleVarRef)
                    .map(aex -> (BLangSimpleVarRef) aex)
                    .map(BLangSimpleVarRef::toString)
                    .collect(Collectors.toList());

            // Generate artifacts for listeners attached to services
            topLevelNodes.stream()
                    .filter(tln -> tln instanceof SimpleVariableNode)
                    .map(tln -> (SimpleVariableNode) tln)
                    .filter(listener -> listenerNamesToExpose.contains(listener.getName().getValue()))
                    .forEach(listener -> process(listener, Collections.singletonList(serviceAnnotation)));

            // Generate artifacts for main functions
            topLevelNodes.stream()
                    .filter(tln -> tln instanceof FunctionNode)
                    .map(tln -> (FunctionNode) tln)
                    .filter(fn -> "main".equals(fn.getName().getValue()))
                    .forEach(fn -> process(fn, Collections.singletonList(KubernetesUtils.createAnnotation("Job"))));

        }
    }

    @Override
    public void process(ServiceNode serviceNode, List<AnnotationAttachmentNode> annotations) {
        for (AnnotationAttachmentNode attachmentNode : annotations) {
            String annotationKey = attachmentNode.getAnnotationName().getValue();
            try {
                NodeProcessorFactory.getNodeProcessorInstance(annotationKey).processNode(serviceNode);
            } catch (KubernetesPluginException e) {
                dlog.logDiagnostic(DiagnosticSeverity.ERROR, KubernetesContext.getInstance().getCurrentPackage()
                        , serviceNode.getPosition(), e.getMessage());
            }
        }
    }

    @Override
    public void process(SimpleVariableNode variableNode, List<AnnotationAttachmentNode> annotations) {
        if (!variableNode.getFlags().contains(Flag.LISTENER)) {
            dlog.logDiagnostic(DiagnosticSeverity.ERROR, KubernetesContext.getInstance().getCurrentPackage(),
                    variableNode.getPosition(), "@kubernetes annotations are only supported with listeners.");
            return;
        }
        for (AnnotationAttachmentNode attachmentNode : annotations) {
            String annotationKey = attachmentNode.getAnnotationName().getValue();
            try {
                NodeProcessorFactory.getNodeProcessorInstance(annotationKey).processNode(variableNode);
            } catch (KubernetesPluginException e) {
                dlog.logDiagnostic(DiagnosticSeverity.ERROR, KubernetesContext.getInstance().getCurrentPackage(),
                        variableNode.getPosition(), e.getMessage());
            }
        }

    }

    @Override
    public void process(FunctionNode functionNode, List<AnnotationAttachmentNode> annotations) {
        for (AnnotationAttachmentNode attachmentNode : annotations) {
            String annotationKey = attachmentNode.getAnnotationName().getValue();
            try {
                NodeProcessorFactory.getNodeProcessorInstance(annotationKey).processNode(functionNode, attachmentNode);
            } catch (KubernetesPluginException e) {
                dlog.logDiagnostic(DiagnosticSeverity.ERROR, KubernetesContext.getInstance().getCurrentPackage(),
                        functionNode.getPosition(), e.getMessage());
            }
        }
    }

    public void codeGeneratedInternal(PackageID moduleID, Path executableJarFile, Optional<KubernetesToml> k8sToml) {
        KubernetesContext.getInstance().setCurrentPackage(moduleID);
        KubernetesDataHolder dataHolder = KubernetesContext.getInstance().getDataHolder();
        dataHolder.setPackageID(moduleID);
        if (dataHolder.isCanProcess()) {
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
                        if (k8sToml.isPresent()) {
                            dataHolder.setBallerinaCloud(new Toml(k8sToml.get().tomlAstNode()));
                        }
                    }
                }
                dataHolder.setJarPath(executableJarFile);
                dataHolder.setK8sArtifactOutputPath(kubernetesOutputPath);
                dataHolder.setDockerArtifactOutputPath(dockerOutputPath);
                ArtifactManager artifactManager = new ArtifactManager();
                try {
                    KubernetesUtils.deleteDirectory(kubernetesOutputPath);
                    artifactManager.populateDeploymentModel();
                    artifactManager.createArtifacts();
                } catch (KubernetesPluginException e) {
                    String errorMessage = "module [" + moduleID + "] " + e.getMessage();
                    printError(errorMessage);
                    pluginLog.error(errorMessage, e);
                    try {
                        KubernetesUtils.deleteDirectory(kubernetesOutputPath);
                    } catch (KubernetesPluginException ignored) {
                        //ignored
                    }
                }
            } else {
                printError("error in resolving docker generation location.");
                pluginLog.error("error in resolving docker generation location.");
            }
        }
    }

    @Override
    public void codeGenerated(Project project, Target target) {
        PackageCompilation packageCompilation = project.currentPackage().getCompilation();
        JBallerinaBackend jBallerinaBackend = JBallerinaBackend.from(packageCompilation, JvmTarget.JAVA_11);
        io.ballerina.projects.JarResolver jarResolver = jBallerinaBackend.jarResolver();
        KubernetesContext.getInstance().getDataHolder().getDockerModel()
                .addDependencyJarPaths(new HashSet<>(jarResolver.getJarFilePathsRequiredForExecution()));
        try {
            final Path executablePath = target.getExecutablePath(project.currentPackage());
            KubernetesContext.getInstance().getDataHolder().setSourceRoot(executablePath.getParent()
                    .getParent().getParent());
            codeGeneratedInternal(KubernetesContext.getInstance().getCurrentPackage(),
                    executablePath, project.currentPackage().kubernetesToml());
        } catch (IOException e) {
            String errorMessage = "error while accessing executable path " + e.getMessage();
            printError(errorMessage);
            pluginLog.error(errorMessage, e);
        }
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


}
