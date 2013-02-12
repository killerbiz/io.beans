/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package io.beans.collections;

/**
 *
 * @author kuli
 */
public final class Comparisons {

    private Comparisons() {
        throw new AssertionError("Static class!");
    }

    private static class GoodHashEquality implements Comparison<Object> {
        @Override
        public boolean isEqual(Object givenValue, Object includedInCollection) {
            return givenValue.equals(includedInCollection);
        }

        @Override
        public int hashFor(Object object) {
            return object.hashCode();
        }
    }

    private static class DefaultEquality extends GoodHashEquality {
        @Override
        public int hashFor(Object object) {
            int h = super.hashFor(object);

            // The following code is copied from java.util.HashMap#hash(int)
            h ^= (h >>> 20) ^ (h >>> 12);
            return h ^ (h >>> 7) ^ (h >>> 4);
        }
    }

    public static final Comparison<Object> DEFAULT_EQUALITY = new DefaultEquality();

    public static final Comparison<Object> GOOD_HASH_EQUALITY = new GoodHashEquality();

    public static final Comparison<Object> IDENTITY = new Comparison<Object>() {

        @Override
        public boolean isEqual(Object givenValue, Object includedInCollection) {
            return givenValue == includedInCollection;
        }

        @Override
        public int hashFor(Object object) {
            int h = System.identityHashCode(object);
            // The following code is copied from java.util.IdentityHashMap#hash(Object,int)
            // Multiply by -127, and left-shift to use least bit as part of hash
            return (h << 1) - (h << 8);
        }
    };

    public static final Comparison<Object> SAME_CLASS_EQUALITY = new DefaultEquality() {

        @Override
        public boolean isEqual(Object givenValue, Object includedInCollection) {
            return givenValue.getClass().equals(includedInCollection.getClass())
                    && super.isEqual(givenValue, includedInCollection);
        }
    };

    public static final Comparison<String> CASE_INSENSITIVE_EQUALITY = new Comparison<String>() {

        @Override
        public boolean isEqual(String givenValue, String includedInCollection) {
            return givenValue.equalsIgnoreCase(includedInCollection);
        }

        @Override
        public int hashFor(String object) {
            return object.toUpperCase().hashCode();
        }
    };
}
