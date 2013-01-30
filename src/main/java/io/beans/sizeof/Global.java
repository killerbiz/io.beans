package io.beans.sizeof;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * You can mark classes, interfaces, anntotaions, and fields with this to declare
 * them as global.
 *
 * The rules are:
 * - If a class or interface is annotated with this, then this class will always
 *   be a global type. Instances of these will be ignored in calculation.
 * - If an annotation is annotated with this, then the annotation behaves like this.
 *   This means, classes and field annotated with such an annotation are handled
 *   the same as if annotated with @Global.
 * - If a static field is annotated with this, then the direct contents of this field
 *   gets marked as global, even if the current policy is different.
 * - If an instance field is annotated with this, then this field will be ignored
 *   in size measurement. The contents of such a field is not even read, and might
 *   still get counted if referenced by some other field.
 *
 * This annotation merely is a shortcut if you want to modify the measured classes
 * directly. Instead, you can make additional annotation behave likle this, when
 * you add them via Environment.addGlobalAnnotation(). This annotation simply is
 * added as the first global annotation when a new Environment initializes.
 */
@Target({ ANNOTATION_TYPE, TYPE, FIELD })
@Retention(RUNTIME)
public @interface Global {

}
