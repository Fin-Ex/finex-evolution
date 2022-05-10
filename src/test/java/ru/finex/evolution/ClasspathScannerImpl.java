package ru.finex.evolution;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * @author m0nster.mind
 */
public class ClasspathScannerImpl implements ClasspathScanner {

    private final Reflections reflections;

    @Inject
    public ClasspathScannerImpl() {
        this.reflections = new Reflections(new ConfigurationBuilder()
            .setUrls(ClasspathHelper.forJavaClassPath())
            .addScanners(
                Scanners.SubTypes,
                Scanners.TypesAnnotated,
                Scanners.MethodsAnnotated,
                Scanners.ConstructorsAnnotated,
                Scanners.FieldsAnnotated,
                Scanners.Resources
            )
        );
    }

    @Override
    public Collection<Class<?>> getTypesAnnotatedWith(Class<? extends Annotation> annotation) {
        return reflections.getTypesAnnotatedWith(annotation);
    }

    @Override
    public Collection<String> getResources(Pattern pattern) {
        return reflections.getResources(pattern);
    }

}
