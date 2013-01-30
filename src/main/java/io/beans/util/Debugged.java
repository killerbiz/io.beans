package io.beans.util;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;


/**
 * Declares whether the attribute shall be printed in {@link DebugPrinter}. This overwrites the default behaviour, which
 * debugs all fields except transient ones.
 * 
 * @author kuli
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface Debugged {

    /**
     * If <code>true</code>, then this field gets printed in DebugPrinter.
     */
    boolean value();

    /**
     * If this is set, the field will be debugged even if it is <code>null</code>.
     */
    boolean printNull() default false;
}
