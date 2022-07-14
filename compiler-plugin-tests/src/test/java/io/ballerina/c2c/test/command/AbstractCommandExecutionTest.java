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
package io.ballerina.c2c.test.command;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.ballerina.c2c.test.utils.FileUtils;
import io.ballerina.c2c.test.utils.TestUtil;
import org.ballerinalang.langserver.commons.command.CommandArgument;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.jsonrpc.Endpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Command Execution Test Cases.
 */
public abstract class AbstractCommandExecutionTest {

    private Endpoint serviceEndpoint;

    private final Gson gson = new Gson();

    private final JsonParser parser = new JsonParser();

    private final Path resourcesPath = new File(getClass().getClassLoader().getResource("command").getFile()).toPath();

    private static final Logger log = LoggerFactory.getLogger(AbstractCommandExecutionTest.class);

    @BeforeClass
    public void init() {
        this.serviceEndpoint = TestUtil.initializeLanguageSever();
    }

    public void performTest(String config, String source, String command) throws IOException {
        String configJsonPath = Paths.get("command", getSourceRoot(), "config", config).toString();
        Path sourcePath = resourcesPath.resolve(getSourceRoot()).resolve("source").resolve(source);
        TestUtil.openDocument(serviceEndpoint, sourcePath);
        JsonObject configJsonObject = FileUtils.fileContentAsObject(configJsonPath);
        JsonObject expected = configJsonObject.get("expected").getAsJsonObject();

        List<Object> args = getArgs(sourcePath);

        JsonObject responseJson = getCommandResponse(args, command);
        JsonArray documentChanges = responseJson.get("result").getAsJsonObject().get("edit").getAsJsonObject()
                .getAsJsonArray("documentChanges");
        documentChanges.forEach(element -> element.getAsJsonObject().remove("textDocument"));
        documentChanges.forEach(element -> element.getAsJsonObject().remove("uri"));

        log.debug("Actual response: {}", responseJson.get("result").getAsJsonObject().get("edit"));
        log.debug("Expected response: {}", expected.get("result").getAsJsonObject().get("edit"));

        TestUtil.closeDocument(serviceEndpoint, sourcePath);
        Assert.assertEquals(responseJson, expected, "Test Failed for: " + config);
    }

    // TODO: #23371
    // TODO: #23371

    @DataProvider(name = "testgen-fail-data-provider")
    public Object[][] testGenerationNegativeDataProvider() {
        log.info("Test, test generation command failed cases");
        return new Object[][]{
                { "testGenerationForServicesNegative.json", Paths.get("testgen", "module2", "services.bal") },
        };
    }

    @DataProvider(name = "testgen-append-data-provider")
    public Object[][] testGenerationAppendDataProvider() {
        return new Object[][]{
                { "testGenerationForServicesNegative.json", Paths.get("testgen", "module2", "services.bal") },
        };
    }

    /**
     * Get args to be sent to LS.
     *
     * @param sourcePath Source file path
     * @return List of args
     */
    protected List<Object> getArgs(Path sourcePath) {
        List<Object> args = new ArrayList<>();
        args.add(CommandArgument.from("uri", sourcePath.toUri().toString()));
        return args;
    }

    /**
     * Get the root directory name where test sources and test config are located.
     *
     * @return directory name
     */
    protected abstract String getSourceRoot();

    @AfterClass
    public void cleanupLanguageServer() {
        TestUtil.shutdownLanguageServer(this.serviceEndpoint);
    }

    private List<Object> argsToJson(List<Object> args) {
        List<Object> jsonArgs = new ArrayList<>();
        for (Object arg : args) {
            jsonArgs.add(gson.toJsonTree(arg));
        }
        return jsonArgs;
    }

    private JsonObject getCommandResponse(List<Object> args, String command) {
        List<Object> argsList = argsToJson(args);
        ExecuteCommandParams params = new ExecuteCommandParams(command, argsList);
        String response = TestUtil.getExecuteCommandResponse(params, this.serviceEndpoint).replace("\\r\\n", "\\n");
        JsonObject responseJson = parser.parse(response).getAsJsonObject();
        responseJson.remove("id");
        return responseJson;
    }
}
