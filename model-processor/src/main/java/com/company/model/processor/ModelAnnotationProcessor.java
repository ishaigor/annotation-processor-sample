package com.company.model.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import org.immutables.value.Value;

import com.company.model.Model;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ModelAnnotationProcessor extends AbstractProcessor {

    public static final String GENERATED_CLASS_NAME = "ImmutableObjectsConstants";

    @SuppressWarnings("unchecked")
    protected static final Set<Class<? extends Annotation>> SUPPORTED_ANNOTATIONS = ImmutableSet.of(
        Model.class,
        Model.WithBuilderOnly.class
    );

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return SUPPORTED_ANNOTATIONS.stream().map(Class::getCanonicalName).collect(Collectors.toSet());
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        Set<? extends Element> annotatedElements = getElementsAnnotatedWithAny(roundEnv, SUPPORTED_ANNOTATIONS);
        List<TypeElement> allTypedElement = new ArrayList<>();

        for (Element annotatedElement : annotatedElements) {
            if (annotatedElement.getKind() != ElementKind.CLASS &&
                            annotatedElement.getKind() != ElementKind.INTERFACE) {
                error(annotatedElement, "Only classes and interfaces can be annotated.");
                return true;
            }
            TypeElement typeElement = (TypeElement) annotatedElement;
            allTypedElement.add(typeElement);
        }

        if (allTypedElement.isEmpty()) {
            return false;
        }

        Map<PackageElement, List<TypeElement>> package2Type = groupByPackage(allTypedElement);

        for (PackageElement packageElement : package2Type.keySet()) {
            String qualifiedClassName = packageElement.getQualifiedName() + "." + GENERATED_CLASS_NAME;
            try {
                JavaFileObject classFile = this.processingEnv.getFiler().createSourceFile(
                                qualifiedClassName, packageElement);

                try (Writer writer = classFile.openWriter()) {

                    TypeSpec.Builder constants = TypeSpec.classBuilder(GENERATED_CLASS_NAME);
                    constants.addModifiers(Modifier.PUBLIC, Modifier.FINAL);

                    ParameterizedTypeName mapClass2Class =
                        ParameterizedTypeName.get(Map.class, Class.class, Class.class);
                    constants.addField(mapClass2Class, "IMMUTABLE_CLASSES",
                            Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);

                    CodeBlock.Builder staticBlock = CodeBlock.builder();

                    staticBlock.addStatement("$T immutableClasses = new $T<>()", mapClass2Class, HashMap.class);

                    List<TypeElement> typeElements = package2Type.get(packageElement);

                    for (TypeElement typeElement : typeElements) {
                        if (typeElement.getNestingKind().isNested()) {
                            addNestedClassMapping(staticBlock, typeElement);
                        } else {
                            addTopLevelClassMapping(staticBlock, typeElement);
                        }
                    }

                    staticBlock.addStatement("IMMUTABLE_CLASSES = $T.unmodifiableMap(immutableClasses)", Collections.class);
                    constants.addStaticBlock(staticBlock.build());

                    JavaFile.builder(String.valueOf(packageElement.getQualifiedName()), constants.build())
                                    .indent("    ").build()
                                    .writeTo(writer);

                }

            } catch (IOException e) {
                error(packageElement, "error generating " + qualifiedClassName, e);
            }

        }

        return false;
    }

    private void addTopLevelClassMapping(CodeBlock.Builder staticBlock, TypeElement typeElement) {
        staticBlock.addStatement("immutableClasses.put($L.class, Immutable$L.class)",
                        typeElement.getSimpleName(), typeElement.getSimpleName());
        if (typeElement.getAnnotation(Value.Enclosing.class) != null) {
            for (Element enclosedElement : typeElement.getEnclosedElements()) {
                if (enclosedElement.getKind().isClass() &&
                                enclosedElement.getModifiers().contains(Modifier.STATIC)) {
                    TypeElement innerClass = (TypeElement) enclosedElement;
                    if (innerClass.getAnnotation(Value.Immutable.class) != null) {
                        staticBlock.addStatement(
                                        "immutableClasses.put($L.$L.class, Immutable$L.$L.class)",
                                        typeElement.getSimpleName(),
                                        innerClass.getSimpleName(),
                                        typeElement.getSimpleName(),
                                        innerClass.getSimpleName());
                    }
                }
            }
        }
    }

    private void addNestedClassMapping(CodeBlock.Builder staticBlock, TypeElement typeElement) {
        Name enclosingClass = null;
        Stack<Name> nestingClasses = new Stack<>();
        Element enclosingElement = typeElement.getEnclosingElement();

        while (!ElementKind.PACKAGE.equals(enclosingElement.getKind())) {
            // Value.Enclosing can be set only at top-level types
            if (!((TypeElement)enclosingElement).getNestingKind().isNested() &&
                            enclosingElement.getAnnotation(Value.Enclosing.class) != null) {
                enclosingClass = enclosingElement.getSimpleName();
            }
            nestingClasses.push(enclosingElement.getSimpleName());
            enclosingElement = enclosingElement.getEnclosingElement();
        }
        StringBuilder className = new StringBuilder();
        while (!nestingClasses.empty()) {
            className.append(nestingClasses.pop())
                            .append('.');
        }

        staticBlock.addStatement("immutableClasses.put($L$L.class, Immutable$L.class)",
                        className.toString(), typeElement.getSimpleName(),
                        (enclosingClass == null ? "" : enclosingClass + ".") + typeElement.getSimpleName());
    }

    private Map<PackageElement, List<TypeElement>> groupByPackage(Iterable<TypeElement> typeElements) {
        Map<PackageElement, List<TypeElement>> package2Type = new HashMap<>();
        for (TypeElement typeElement : typeElements) {
            Element enclosingElement = typeElement.getEnclosingElement();
            while (!ElementKind.PACKAGE.equals(enclosingElement.getKind())) {
                enclosingElement = enclosingElement.getEnclosingElement();
            }

            PackageElement packageElement = (PackageElement) enclosingElement;

            List<TypeElement> elementsInPackage =
                            package2Type.computeIfAbsent(packageElement, (key) -> new ArrayList<>());
            elementsInPackage.add(typeElement);

        }
        return package2Type;
    }

    /**
     * Back-ported from JDK9: Returns the elements annotated with one or more of the given annotation types.
     */
    protected static Set<? extends Element> getElementsAnnotatedWithAny(
                    RoundEnvironment roundEnv, Set<Class<? extends Annotation>> annotations
    ) {
        // Use LinkedHashSet rather than HashSet for predictability
        Set<Element> result = new LinkedHashSet<>();
        for (Class<? extends Annotation> annotation : annotations) {
            result.addAll(roundEnv.getElementsAnnotatedWith(annotation));
        }
        return Collections.unmodifiableSet(result);
    }

    protected void error(Element e, String msg, Object... args) {
        processingEnv.getMessager().printMessage(
                        Diagnostic.Kind.ERROR,
                        String.format(msg, args),
                        e);
    }

}
