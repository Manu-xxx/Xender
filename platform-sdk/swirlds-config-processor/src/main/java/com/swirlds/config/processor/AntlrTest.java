/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.config.processor;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.processor.generated.JavaLexer;
import com.swirlds.config.processor.generated.JavaParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;

@ConfigData
public class AntlrTest {

    public static void main(String[] args) throws Exception {

        final String path =
                "/Users/hendrikebbers/git/hedera-services/platform-sdk/swirlds-common/src/main/java/com/swirlds/common/config/BasicConfig.java";

        String javaClassContent =
                "import com.swirlds.config.api.ConfigData; @ConfigData public record SampleClass() { void DoSomething(){System.out.printf(\"Hello World 123\");} }";
        JavaLexer lexer = new JavaLexer(CharStreams.fromString(Files.readString(Path.of(path))));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);
        ParseTree tree = parser.compilationUnit();
        ParseTreeWalker walker = new ParseTreeWalker();
        AntlrListener listener = new AntlrListener();
        walker.walk(listener, tree);
        final ConfigDataRecordDefinition recordDefinition = listener.getDefinition();
        DocumentationFactory.doWork(recordDefinition, Path.of(System.getProperty("user.dir"), "test-config.md"));
    }
}
