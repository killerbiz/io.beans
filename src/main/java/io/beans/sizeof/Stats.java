package io.beans.sizeof;

/**
 * Returns statistics for either a class or an object.
 * 
 * @author kuli
 */
public interface Stats {

    /**
     * The full deep size of all objects.
     */
    long memoryUsed();

    /**
     * The number of all non-global (i.e. memory counting) instances that are referenced, including the added parent
     * objects.
     */
    int instanceCount();

    /**
     * Gets the statistics for a single class.
     * 
     * Returns <code>null</code> if the class is not used inside the referenced object tree.
     */
    <T> ClassStats<T> statsFor(Class<T> type);

    /**
     * Gets an array fo statistics for all referenced classes, sorted by the memory footprint per class, or the class's
     * instance count if memory is not measured.
     */
    ClassStats<?>[] stats();
}
