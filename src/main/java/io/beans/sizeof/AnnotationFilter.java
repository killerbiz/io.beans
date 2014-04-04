package io.beans.sizeof;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;


/**
 * A filter for annotations.
 * 
 * @author kuli
 * 
 * @param <A> If a field or type or annotation is annotated with A, then this field or type will not be measured if this
 *            filter instance return true.
 */
public interface AnnotationFilter<A extends Annotation> {

    /**
     * If this is <code>true</code>, then the annotated field or type will not be measured.
     * 
     * @param fieldOrClass The field or class that is annotated with either this marking annotation A itself, or with an
     *            annotation that itself is marked with A
     * @param annotation The marking annotation A
     */
    boolean isGlobal(AnnotatedElement fieldOrClass, A annotation);

    /**
     * A convenient instance that marks all elements as global that are annotated with A.
     */
    AnnotationFilter<Annotation> DONT_MEASURE = new AnnotationFilter<Annotation>() {
        @Override
        public boolean isGlobal(AnnotatedElement fieldOrClass, Annotation annotation) {
            return true;
        }
    };
}
