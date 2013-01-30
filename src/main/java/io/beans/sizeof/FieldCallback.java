package io.beans.sizeof;

/**
 * Callback for {@link ClassSchema}.
 * 
 * Used to iterate over all Object members of an instance.
 * 
 * @author kuli
 */
public interface FieldCallback {

    void visit(Object value);

    void finished();
}
