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
package io.ballerina.c2c.test.codeactions.diagnostic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.ballerina.c2c.test.utils.FileUtils;
import io.ballerina.c2c.test.utils.TestUtil;
import org.ballerinalang.langserver.codeaction.CodeActionUtil;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.commons.LanguageServerContext;
import org.ballerinalang.langserver.commons.workspace.WorkspaceDocumentException;
import org.ballerinalang.langserver.commons.workspace.WorkspaceManager;
import org.ballerinalang.langserver.contexts.LanguageServerContextImpl;
import org.ballerinalang.langserver.workspace.BallerinaWorkspaceManager;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.jsonrpc.Endpoint;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Abstract base for implementing CodeActions.
 *
 * @since 2.0.0
 */
public abstract class CodeActionTest {

    private Endpoint serviceEndpoint;

    private final JsonParser parser = new JsonParser();

    private final Path sourcesPath = new File(getClass().getClassLoader().getResource("codeaction").getFile()).toPath();

    private static final WorkspaceManager workspaceManager =
            new BallerinaWorkspaceManager(new LanguageServerContextImpl());

    private static final LanguageServerContext serverContext = new LanguageServerContextImpl();

    @BeforeClass
    public void init() {
        this.serviceEndpoint = TestUtil.initializeLanguageSever();
    }

    @Test(dataProvider = "codeaction-data-provider")
    public void test(String config, String source) throws IOException, WorkspaceDocumentException {
        String configJsonPath =
                "codeaction" + File.separator + getResourceDir() + File.separator + "config" + File.separator + config;
        
        Path sourcePath = sourcesPath.resolve(getResourceDir()).resolve("source").resolve(source);
        TestUtil.generateCaches(sourcePath.getParent(), Paths.get(System.getProperty("ballerina.home")));
        JsonObject configJsonObject = FileUtils.fileContentAsObject(configJsonPath);
        TestUtil.openDocument(serviceEndpoint, sourcePath);

        // Filter diagnostics for the cursor position
        List<io.ballerina.tools.diagnostics.Diagnostic> diagnostics
                = TestUtil.compileAndGetDiagnostics(sourcePath, workspaceManager, serverContext);
        List<Diagnostic> diags = new ArrayList<>(CodeActionUtil.toDiagnostics(diagnostics));
        Position pos = new Position(configJsonObject.get("line").getAsInt(),
                configJsonObject.get("character").getAsInt());
        diags = diags.stream().
                filter(diag -> CommonUtil.isWithinRange(pos, diag.getRange()))
                .collect(Collectors.toList());
        CodeActionContext codeActionContext = new CodeActionContext(diags);

        Range range = new Range(pos, pos);
        String res = TestUtil.getCodeActionResponse(serviceEndpoint, sourcePath.toString(), range, codeActionContext);

        for (JsonElement element : configJsonObject.get("expected").getAsJsonArray()) {
            JsonObject expected = element.getAsJsonObject();
            String expTitle = expected.get("title").getAsString();

            boolean codeActionFound = false;
            JsonObject responseJson = this.getResponseJson(res);
            for (JsonElement jsonElement : responseJson.getAsJsonArray("result")) {
                JsonObject right = jsonElement.getAsJsonObject().get("right").getAsJsonObject();
                if (right != null) {
                    // Match title
                    String actualTitle = right.get("title").getAsString();
                    if (!expTitle.equals(actualTitle)) {
                        continue;
                    }
                    // Match edits
                    boolean invalid = false;
                    if (expected.get("edits") != null) {
                        JsonArray actualEditArray = right.get("edit").getAsJsonObject().get("documentChanges")
                                .getAsJsonArray();
                        for (JsonElement docChange : actualEditArray) {
                            JsonElement edits = docChange.getAsJsonObject().get("edits");
                            if (edits == null) {
                                continue;
                            }
                            JsonArray actualEdit = edits.getAsJsonArray();
                            JsonArray expEdit = expected.get("edits").getAsJsonArray();
                            if (!expEdit.equals(actualEdit)) {
                                invalid = true;
                            }
                        }
                    }
                    if (invalid) {
                        continue;
                    }
                    // Match args
                    if (expected.get("arguments") != null) {
                        JsonArray actualArgs = expected.get("arguments").getAsJsonArray();
                        JsonArray expArgs = expected.getAsJsonArray("arguments");
                        if (!TestUtil.isArgumentsSubArray(actualArgs, expArgs)) {
                            continue;
                        }
                    }
                    // Code-action matched
                    codeActionFound = true;
                    break;
                }
            }
            String cursorStr = range.getStart().getLine() + ":" + range.getEnd().getCharacter();
            Assert.assertTrue(codeActionFound,
                    "Cannot find expected Code Action for: " + expTitle + ", cursor at " + cursorStr);
        }
        TestUtil.closeDocument(this.serviceEndpoint, sourcePath);
    }

    @AfterClass
    public void cleanupLanguageServer() {
        TestUtil.shutdownLanguageServer(this.serviceEndpoint);
    }

    private JsonObject getResponseJson(String response) {
        JsonObject responseJson = parser.parse(response).getAsJsonObject();
        responseJson.remove("id");
        return responseJson;
    }

    @DataProvider(name = "codeaction-data-provider")
    public abstract Object[][] dataProvider();

    public abstract String getResourceDir();
}
