package io.beans.sizeof;

public interface ClassStats<T> {

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
    int referencedBy();

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
