package io.beans.sizeof;

import java.lang.reflect.Field;


/**
 * Used to filter or select specific fields in the {@link ClassSchema}.
 * 
 * @author kuli
 */
public interface FieldFilter {

    boolean accept(Field f);

    FieldFilter ACCEPT_ALL = new FieldFilter() {

        @Override
        public boolean accept(Field f) {
            return true;
        }
    };
}
