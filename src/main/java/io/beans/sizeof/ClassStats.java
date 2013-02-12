package io.beans.sizeof;

public interface ClassStats<T> {

    /**
     * Container used for reference counting.
     */
    final class Reference {
        private final String name;
        private int count;

        public Reference(String name) {
            this(name, 0);
        }

        public Reference(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }

        void increment() {
            count++;
        }

        @Override
        public boolean equals(Object obj) {
            return obj != null && obj.getClass() == Reference.class
                    && name.equals(((Reference)obj).getName()) && count == ((Reference)obj).getCount();
        }

        @Override
        public int hashCode() {
            return 1103 + name.hashCode() + count;
        }

        @Override
        public String toString() {
            return name + ": " + count;
        }
    }

    /**
     * The class that was measured.
     */
    Class<T> type();

    /**
     * The static size for each flat instance.
     * 
     * If the type is an array, then the length of an empty array is returned.
     */
    long length();

    /**
     * How many other instances reference to this class or instance.
     */
    Reference[] referencedBy();

    /**
     * The number of direct instances this class has within the object tree.
     */
    int instanceCount();

    /**
     * The total size of all instances within the object tree.
     * 
     * If the collector was created with the "calcSizeForEachClass" flag set to false, then this value is always 0.
     */
    long totalSize();

    /**
     * Array with all referenced and counted instances.
     */
    T[] instances();

    /**
     * Array with the total count of references to each instance.
     */
    int[] referenceCounts();
}
