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
package io.ballerina.c2c.tooling.command;

import io.ballerina.c2c.tooling.toml.CommonUtil;
import io.ballerina.projects.CloudToml;
import io.ballerina.projects.Project;
import io.ballerina.toml.syntax.tree.DocumentMemberDeclarationNode;
import io.ballerina.toml.syntax.tree.SyntaxKind;
import io.ballerina.toml.validator.BoilerplateGenerator;
import io.ballerina.toml.validator.schema.Schema;
import org.apache.commons.io.IOUtils;
import org.ballerinalang.annotation.JavaSPIService;
import org.ballerinalang.langserver.commons.ExecuteCommandContext;
import org.ballerinalang.langserver.commons.command.CommandArgument;
import org.ballerinalang.langserver.commons.command.spi.LSCommandExecutor;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.CreateFile;
import org.eclipse.lsp4j.CreateFileOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;

/**
 * Command executor for creating the cloud toml file.
 *
 * @since 2.0.0
 */
@JavaSPIService("org.ballerinalang.langserver.commons.command.spi.LSCommandExecutor")
public class CreateCloudTomlExecutor implements LSCommandExecutor {

    public static final String COMMAND = "ballerina.create.cloud.exec";

    /**
     * {@inheritDoc}
     *
     * @param context
     */
    @Override
    public Object execute(ExecuteCommandContext context) {
        String content = generateContent();
        List<CommandArgument> arguments = context.getArguments();
        if (arguments.size() != 1) {
            return Collections.emptyList();
        }
        CommandArgument arg = arguments.get(0);
        if (!"uri".equals(arg.key())) {
            return Collections.emptyList();
        }
        String documentUri = arg.valueAs(String.class);
        Optional<Path> filePath = CommonUtil.getPathFromURI(documentUri);
        if (filePath.isEmpty()) {
            return Collections.emptyList();
        }
        Optional<Project> project = context.workspace().project(filePath.get());
        if (project.isEmpty()) {
            return Collections.emptyList();
        }
        Optional<CloudToml> cloudToml = project.get().currentPackage().cloudToml();
        if (cloudToml.isPresent()) {
            return Collections.emptyList();
        }

        List<Either<TextDocumentEdit, ResourceOperation>> actionsToTake = new ArrayList<>(2);

        String docURI = project.get().sourceRoot().resolve("Cloud.toml").toUri().toString();

        // 1. create an empty file
        actionsToTake.add(Either.forRight(new CreateFile(docURI, new CreateFileOptions(false, true))));

        // 2. update the created file with the given content
        VersionedTextDocumentIdentifier identifier = new VersionedTextDocumentIdentifier(docURI, 0);
        TextEdit te = new TextEdit(new Range(new Position(0, 0), new Position(0, 0)), content);
        actionsToTake.add(Either.forLeft(new TextDocumentEdit(identifier, Collections.singletonList(te))));

        LanguageClient languageClient = context.getLanguageClient();
        return applyWorkspaceEdit(actionsToTake, languageClient);
    }

    private String generateContent() {
        StringBuilder content = new StringBuilder(
                "# This file contains most used configurations supported by Ballerina Code to Cloud" +
                        CommonUtil.LINE_SEPARATOR +
                        "# All the fields are optional. If these fields are not specified, default value will be " +
                        "taken from the compiler." + CommonUtil.LINE_SEPARATOR +
                        "# Full Code to Cloud specification can be accessed from https://github" +
                        ".com/ballerina-platform/ballerina-spec/blob/master/c2c/code-to-cloud-spec.md" +
                        CommonUtil.LINE_SEPARATOR +
                        "# Uncomment Any field below if you want to override the default value." +
                        CommonUtil.LINE_SEPARATOR);

        BoilerplateGenerator generator = new BoilerplateGenerator(Schema.from(getValidationSchema()));
        Map<String, DocumentMemberDeclarationNode> nodes = generator.getNodes();
        
        // TODO Remove this from the Toml boilerplate side.
        // https://github.com/ballerina-platform/ballerina-lang/issues/34086
        nodes.remove("cloud.config.secrets");
        nodes.remove("cloud.config.files");
        
        for (DocumentMemberDeclarationNode node : nodes.values()) {
            if (node.kind() != SyntaxKind.KEY_VALUE) {
                content.append(CommonUtil.LINE_SEPARATOR);
            }
            content.append("# ").append(node.toSourceCode());
        }

        return content.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCommand() {
        return COMMAND;
    }

    /**
     * Apply a workspace edit for the current instance.
     *
     * @param documentChanges List of either document edits or set of resource changes for current session
     * @param client          Language Client
     * @return {@link Object}   workspace edit parameters
     */
    public static Object applyWorkspaceEdit(List<Either<TextDocumentEdit, ResourceOperation>> documentChanges,
                                            LanguageClient client) {
        WorkspaceEdit workspaceEdit = new WorkspaceEdit(documentChanges);
        ApplyWorkspaceEditParams applyWorkspaceEditParams = new ApplyWorkspaceEditParams(workspaceEdit);
        if (client != null) {
            client.applyEdit(applyWorkspaceEditParams);
        }
        return applyWorkspaceEditParams;
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

