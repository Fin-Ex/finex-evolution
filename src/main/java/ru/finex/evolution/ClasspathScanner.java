package ru.finex.evolution;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Classpath scanner interface, provide access to available classes.
 * @author m0nster.mind
 */
public interface ClasspathScanner {

    /**
     * Get types annotated with a given annotation, both classes and annotations.
     * @param annotation given annotation
     * @return types annotated with a given annotation
     */
    Collection<Class<?>> getTypesAnnotatedWith(Class<? extends Annotation> annotation);

    /**
     * Get classpath resources matching with regular expression.
     * @param pattern regular expression
     * @return matched resources
     */
    default Collection<String> getResources(String pattern) {
        return getResources(Pattern.compile(pattern));
    }

    /**
     * Get classpath resources matching with regular expression.
     * @param pattern regular expression
     * @return matched resources
     */
    Collection<String> getResources(Pattern pattern);

}
