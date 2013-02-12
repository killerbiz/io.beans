/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package io.beans.collections;

/**
 * Specifies in which way two objects are equal.
 *
 * @author kuli
 */
public interface Comparison<T> {

    /**
     * Compares two values for equality.
     * Both parameters are already checked agains <code>null</null>, so only non-null values
     * are getting compared.
     * 
     * @param givenValue This is the given "foreign" value that gets checked against the contained elementts
     * @param includedInCollection This is one of the already added values from the collection
     * @return <code>true</code> if both values are equal
     */
    boolean isEqual(T givenValue, T includedInCollection);

    /**
     * Creates a hash code for the given object.
     * The contract defined in Object#hashCode() must be fulfilled, i.e. if the equals() method
     * of an implementation returns true for parameters A and B, then hashFor() with parameters
     * A and B must return the same value.
     *
     * @param object The object to calculate the hash code for. This will never be <code>null</code>
     * @return A hash code for the object. This value is used directly, so if implementors call
     * hash methods that might returns balues of poor quality, then they should shuffle their values
     * on their own, similar to java.util.HashMap#hash(int).
     */
    int hashFor(T object);
}
