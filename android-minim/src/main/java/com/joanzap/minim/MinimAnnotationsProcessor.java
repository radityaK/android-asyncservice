package com.joanzap.minim;

import com.joanzap.minim.api.annotation.MinimService;
import com.joanzap.minim.utils.Logger;
import com.squareup.javawriter.JavaWriter;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import static com.joanzap.minim.utils.Utils.*;
import static java.util.EnumSet.of;
import static javax.lang.model.element.Modifier.*;

@SupportedAnnotationTypes({"com.joanzap.minim.api.annotation.MinimService"})
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class MinimAnnotationsProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> typeElements, RoundEnvironment roundEnvironment) {
        try {

            // Initialize a logger
            Logger logger = new Logger(processingEnv.getMessager());

            // Retrieve @MinimService annotated elements
            Set<? extends Element> minimServices = roundEnvironment.getElementsAnnotatedWith(MinimService.class);

            // Loop through elements
            for (Element minimServiceElement : minimServices) {
                logger.note("Processing @MinimService on " + minimServiceElement);

                // Get name and package
                String elementName = minimServiceElement.getSimpleName().toString();
                String elementPackage = getElementPackageName(minimServiceElement);

                // Create the output file
                String newElementName = elementName + "Api";
                JavaFileObject classFile = processingEnv.getFiler().createSourceFile(newElementName, minimServiceElement);
                logger.note(classFile.toUri().toString());
                Writer out = classFile.openWriter();
                JavaWriter writer = new JavaWriter(out);
                JavaWriter classWriter = writer.emitPackage(elementPackage)
                        .emitImports(
                                minimServiceElement.toString(),
                                "android.content.Context")
                        .emitEmptyLine()
                        .beginType(newElementName, "class", of(PUBLIC, FINAL));

                // Create a static holder for the instance
                classWriter.emitEmptyLine()
                        .emitField(newElementName, "instance", of(PRIVATE, FINAL, STATIC),
                                "new " + newElementName + "()");

                // Create the internal field
                classWriter
                        .emitEmptyLine()
                        .emitField(elementName, "internal", of(PRIVATE, FINAL),
                                "new " + elementName + "()");

                // Generate a private constructor
                classWriter
                        .emitEmptyLine()
                        .beginConstructor(of(PRIVATE))
                        .endConstructor();

                // Generate a static getter
                classWriter
                        .emitEmptyLine()
                        .beginMethod(newElementName, "get", of(PUBLIC, STATIC), "Context", "context")
                        .emitStatement("return instance")
                        .endMethod();

                // Manage each method
                for (Element element : minimServiceElement.getEnclosedElements())
                    if (isPublicMethod(element))
                        createDelegateMethod(classWriter, (ExecutableElement) element);

                classWriter.endType();

                out.flush();
                out.close();
            }
            return true;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void createDelegateMethod(JavaWriter classWriter, ExecutableElement method) throws IOException {

        // Start the mimic method
        classWriter.beginMethod(
                "void",
                method.getSimpleName().toString(),
                method.getModifiers(),
                formatParameters(method), null)

                // Delegate the call to the user method
                .emitStatement("internal.%s(%s)",
                        method.getSimpleName(),
                        formatParametersForCall(method))

                .endMethod();

    }
}