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

package com.swirlds.config.processor.antlr;

import com.swirlds.config.processor.ConfigDataRecordDefinition;
import com.swirlds.config.processor.antlr.generated.JavaLexer;
import com.swirlds.config.processor.antlr.generated.JavaParser;
import com.swirlds.config.processor.antlr.generated.JavaParser.AnnotationContext;
import com.swirlds.config.processor.antlr.generated.JavaParser.ClassOrInterfaceModifierContext;
import com.swirlds.config.processor.antlr.generated.JavaParser.CompilationUnitContext;
import com.swirlds.config.processor.antlr.generated.JavaParser.ElementValueContext;
import com.swirlds.config.processor.antlr.generated.JavaParser.RecordComponentContext;
import com.swirlds.config.processor.antlr.generated.JavaParser.RecordDeclarationContext;
import com.swirlds.config.processor.antlr.generated.JavaParser.TypeDeclarationContext;
import com.swirlds.config.processor.antlr.generated.JavadocLexer;
import com.swirlds.config.processor.antlr.generated.JavadocParser;
import com.swirlds.config.processor.antlr.generated.JavadocParser.BlockTagContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.BlockTagTextContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.DocumentationContentContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.DocumentationContext;
import com.swirlds.config.processor.antlr.generated.JavadocParser.TagSectionContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Utils for antlr4 parsing of Java source code
 */
public class AntlrUtils {

    public static final String JAVADOC_PARAM = "param";

    private AntlrUtils() {}

    /**
     * Get all annotations for a given {@code record} declaration context
     *
     * @param ctx the antlr context of the {@code record}
     * @return all annotations as antlr context instances
     */
    @NonNull
    public static List<AnnotationContext> getAllAnnotations(@NonNull final RecordDeclarationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return ctx.getParent().children.stream()
                .filter(child -> child instanceof ClassOrInterfaceModifierContext)
                .map(child -> (ClassOrInterfaceModifierContext) child)
                .flatMap(modifierContext -> modifierContext.children.stream())
                .filter(child -> child instanceof AnnotationContext)
                .map(child -> (AnnotationContext) child)
                .collect(Collectors.toList());
    }

    /**
     * Get all annotations for a given {@code record} component context
     *
     * @param ctx the antlr context of the {@code record} component
     * @return all annotations as antlr context instances
     */
    @NonNull
    public static List<AnnotationContext> getAllAnnotations(@NonNull RecordComponentContext ctx) {
        return Collections.unmodifiableList(ctx.typeType().annotation());
    }

    /**
     * Search in the given annotation context for an optional annotation context for a given {@code Annotation}.
     *
     * @param <A>         the annotation type
     * @param annotation  the annotation class
     * @param annotations all possible annotations
     * @param packageName the package name of the context in that all contexts life in
     * @param imports     the imports of the context in that all contexts life in
     * @return the optional annotation
     */
    @NonNull
    public static <A extends Annotation> Optional<AnnotationContext> findAnnotationOfType(
            @NonNull final Class<A> annotation,
            @NonNull final List<AnnotationContext> annotations,
            @NonNull final String packageName,
            @NonNull final List<String> imports) {
        Objects.requireNonNull(annotation, "annotation must not be null");
        Objects.requireNonNull(annotations, "annotations must not be null");
        return annotations.stream()
                .filter(c -> c.qualifiedName().getText().endsWith(annotation.getSimpleName()))
                .filter(c -> isValid(c, annotation, packageName, imports))
                .findAny();
    }

    /**
     * Checks if the annotationContext is a valid usage of the annotation.
     *
     * @param annotationContext the annotation context
     * @param annotation        the annotation class
     * @param packageName       the package name of the context in that all contexts life in
     * @param imports           the imports of the context in that all contexts life in
     * @param <A>               the annotation type
     * @return true if the annotation is valid
     */
    private static <A extends Annotation> boolean isValid(
            @NonNull final AnnotationContext annotationContext,
            @NonNull final Class<A> annotation,
            @NonNull final String packageName,
            @NonNull final List<String> imports) {
        Objects.requireNonNull(annotationContext, "annotationContext must not be null");
        Objects.requireNonNull(annotation, "annotation must not be null");
        Objects.requireNonNull(packageName, "packageName must not be null");
        Objects.requireNonNull(imports, "imports must not be null");
        return imports.contains(annotation.getName())
                || annotationContext.qualifiedName().getText().equals(annotation.getName())
                || annotationContext.qualifiedName().getText().equals(packageName + "." + annotation.getSimpleName());
    }

    /**
     * Returns the compilation unit context for a given antlr context by doing a (recursive) search in the parent
     * contexts.
     *
     * @param ctx the antlr context
     * @return the compilation unit context
     */
    @NonNull
    public static CompilationUnitContext getCompilationUnit(@NonNull final ParserRuleContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (ctx instanceof CompilationUnitContext compilationUnitContext) {
            return compilationUnitContext;
        } else {
            return getCompilationUnit(ctx.getParent());
        }
    }

    /**
     * Returns all imports of a given declaration context (by going up to the compilation unit context)
     *
     * @param ctx the antlr contexts
     * @return all imports as strings
     */
    @NonNull
    public static List<String> getImports(@NonNull final ParserRuleContext ctx) {
        CompilationUnitContext compilationUnitContext = getCompilationUnit(ctx);
        return compilationUnitContext.importDeclaration().stream()
                .map(context -> context.qualifiedName())
                .map(name -> name.getText())
                .collect(Collectors.toList());
    }

    /**
     * Returns the package name of a given declaration context (by going up to the compilation unit context)
     *
     * @param ctx the antlr context
     * @return the package name
     */
    @NonNull
    public static String getPackage(@NonNull final ParserRuleContext ctx) {
        CompilationUnitContext compilationUnitContext = getCompilationUnit(ctx);
        return compilationUnitContext.packageDeclaration().qualifiedName().getText();
    }

    /**
     * Returns the value of an annotation attribute
     *
     * @param annotationContext the annotation context
     * @param identifier        the identifier of the attribute
     * @return the value of the attribute
     */
    @NonNull
    public static Optional<String> getAnnotationValue(
            @NonNull final AnnotationContext annotationContext, @NonNull final String identifier) {
        return annotationContext.elementValuePairs().elementValuePair().stream()
                .filter(p -> Objects.equals(p.identifier().getText(), identifier))
                .map(p -> p.elementValue().getText())
                .findAny();
    }

    public static boolean isJavaDocNode(@NonNull final ParseTree node) {
        if (node instanceof TerminalNode terminalNode) {
            if (terminalNode.getSymbol().getType() == JavaParser.JAVADOC_COMMENT) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the value of an annotation {@code value} attribute
     *
     * @param annotationContext the annotation context
     * @return the value of the {@code value} attribute
     */
    @NonNull
    public static Optional<String> getAnnotationValue(@NonNull final AnnotationContext annotationContext) {
        final ElementValueContext elementValueContext = annotationContext.elementValue();
        if (elementValueContext != null) {
            return Optional.of(elementValueContext.getText());
        }
        return getAnnotationValue(annotationContext, "value");
    }

    public static List<RecordDeclarationContext> getRecordDeclarationContext(
            CompilationUnitContext compilationUnitContext) {
        return compilationUnitContext.children.stream()
                .filter(child -> child instanceof TypeDeclarationContext)
                .map(child -> (TypeDeclarationContext) child)
                .flatMap(typeDeclarationContext -> typeDeclarationContext.children.stream())
                .filter(child -> child instanceof RecordDeclarationContext)
                .map(child -> (RecordDeclarationContext) child)
                .collect(Collectors.toList());
    }

    /**
     * Returns all {@code @param} tags of a given java doc. The key of the map is the name of the parameter and the
     * value is the description of the parameter.
     *
     * @param rawDocContent the javadoc
     * @return the params
     */
    @NonNull
    public static Map<String, String> getJavaDocParams(@NonNull String rawDocContent) {
        Objects.requireNonNull(rawDocContent, "rawDocContent must not be null");
        final Map<String, String> params = new HashMap<>();
        Lexer lexer = new JavadocLexer(CharStreams.fromString(rawDocContent));
        TokenStream tokens = new CommonTokenStream(lexer);
        JavadocParser parser = new JavadocParser(tokens);
        DocumentationContext documentationContext = parser.documentation();
        Optional.ofNullable(documentationContext.exception).ifPresent(e -> {
            throw new IllegalStateException("Error in ANTLR parsing", e);
        });
        documentationContext.children.stream()
                .filter(c -> c instanceof DocumentationContentContext)
                .map(c -> (DocumentationContentContext) c)
                .flatMap(context -> context.children.stream())
                .filter(c -> c instanceof TagSectionContext)
                .map(c -> (TagSectionContext) c)
                .flatMap(context -> context.children.stream())
                .filter(c -> c instanceof BlockTagContext)
                .map(c -> (BlockTagContext) c)
                .filter(c -> Objects.equals(c.blockTagName().NAME().getText(), JAVADOC_PARAM))
                .forEach(c -> {
                    final BlockTagTextContext paramContext =
                            c.blockTagContent().get(0).blockTagText();

                    Optional.ofNullable(paramContext).map(co -> co.getText()).ifPresent(firstLine -> {
                        final String paramName = firstLine.split(" ")[0].trim();
                        final String description =
                                firstLine.substring(paramName.length()).trim() + " " + IntStream.range(
                                        1, c.blockTagContent().size())
                                .mapToObj(i -> c.blockTagContent().get(i).blockTagText())
                                .filter(Objects::nonNull)
                                .map(co -> co.getText().trim())
                                .filter(t -> !t.isBlank())
                                        .reduce((a, b) -> a.trim() + " " + b.trim())
                                .orElse("");
                        params.put(paramName, description);
                    });
                });
        return params;
    }

    /**
     * Parse the given file content and return a {@link ConfigDataRecordDefinition} object. The file must be a valid
     * Java file.
     *
     * @param fileContent the file content to parse
     * @return the {@link ConfigDataRecordDefinition} object
     * @throws IOException if an I/O error occurs
     */
    public static CompilationUnitContext parse(@NonNull final String fileContent) throws IOException {
        Lexer lexer = new JavaLexer(CharStreams.fromString(fileContent));
        TokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(
                tokens);
        CompilationUnitContext context = parser.compilationUnit();
        Optional.ofNullable(context.exception).ifPresent(e -> {
            throw new IllegalStateException("Error in ANTLR parsing", e);
        });
        return context;
    }
}
