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
package io.ballerina.c2c;

import io.ballerina.c2c.tasks.C2CAnalysisTask;
import io.ballerina.c2c.tasks.ChoreoAnalysisTask;
import io.ballerina.c2c.tasks.CloudTomlAnalysisTask;
import io.ballerina.projects.plugins.CodeAnalysisContext;
import io.ballerina.projects.plugins.CodeAnalyzer;

/**
 * A {@code CodeAnalyzer} implementation that registers a Syntax node analyzer to detect 'checkpanic' usages.
 *
 * @since 1.0.0
 */
public class C2CCodeAnalyzer extends CodeAnalyzer {
    @Override
    public void init(CodeAnalysisContext analysisContext) {
        analysisContext.addCompilationAnalysisTask(new CloudTomlAnalysisTask());
        analysisContext.addCompilationAnalysisTask(new C2CAnalysisTask());
        analysisContext.addCompilationAnalysisTask(new ChoreoAnalysisTask());
    }
}
