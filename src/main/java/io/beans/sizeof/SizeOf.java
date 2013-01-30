package io.beans.sizeof;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.util.Collections;

import io.beans.util.SwissArmyKnife;


/**
 * Utility class to calculate the used memory space of objects.
 * 
 * Calculates the total size of instancs, including a list of instantiated classes and their total size within the
 * elements block.
 * 
 * @author kuli
 */
public final class SizeOf {

    private static WeakReference<Environment> defaultEnvironment;

    @SuppressWarnings("unused")
    private static Environment hardLink;

    private SizeOf() {
        throw new AssertionError("Static class!");
    }

    /**
     * Creates a clean, empty environment without any global objects or types.
     */
    public static Environment createEnvironment() {
        return new Environment();
    }

    /**
     * Creates a new Environment with the default settings.
     * This means, Enum and Class are global types, and the static members of System and Collections are global instances.
     */
    public static Environment createDefaultEnvironment() {
        Environment e = createEnvironment();
        e.addGlobalType(Enum.class).addGlobalType(Class.class);
        e.addGlobalObjectsFrom(System.class, StaticFieldPolicy.DIRECT_REFERENCES).addGlobalObjectsFrom(Collections.class,
                StaticFieldPolicy.DIRECT_REFERENCES);
        return e;
    }

    /**
     * Gets the default environment that will be used in my convenient methods.
     *
     * This also makes the internal default environment become a hard link, meaning it's not
     * getting GC'ed. This is because otherwise user changes could get lost.
     */
    public static Environment getDefaultEnvironment() {
        return (hardLink = getWeakEnvironmentInstance());
    }

    private static Environment getWeakEnvironmentInstance() {
        Environment e = defaultEnvironment == null ? null : defaultEnvironment.get();
        if (e == null) {
            e = createDefaultEnvironment();
            defaultEnvironment = new WeakReference<Environment>(e);
        }

        return e;
    }

    /**
     * Creates a new collector that calculates the total size and the total size of each class.
     */
    public static Collector createCollector() {
        return getWeakEnvironmentInstance().createCollector();
    }

    /**
     * Creates a new collector that calculates the total size, but possibly without
     * the total size of each class.
     * 
     * This is much faster if the individual class mem size is not used.
     */
    public static Collector createCollector(boolean calcSizeForEachClass) {
        return getWeakEnvironmentInstance().createCollector(calcSizeForEachClass);
    }

    /**
     * Creates a new collector that calculates the total size and the total size of each class.
     * The given elements are already added for measurement.
     */
    public static Collector createCollectorWith(Object... elements) {
        return getWeakEnvironmentInstance().createCollectorWith(elements);
    }

    /**
     * Utility method to quickly calculate the used memory for a group of instances.
     * 
     * Instances that are referenced by multiple parents count only once.
     */
    public static long sizeOf(Object... instances) {
        return getWeakEnvironmentInstance().sizeOf(instances);
    }

    /**
     * Gets the flat size of the instance, without any references.
     * 
     * For arrays, this depends on the array's length, otherwise it only depends on the instance's type.
     */
    public static long flatSizeOf(Object instance) {
        if (instance == null) return 0;
        return getWeakEnvironmentInstance().getSchema(instance).flatSize(instance);
    }

    /**
     * Prints the statistics for an individual class into an output stream.
     */
    public static void printClassStats(ClassStats<?> stats, PrintStream out) {
        out.print(SwissArmyKnife.fullPrint(stats.type()));
        if (!stats.type().isArray()) {
            out.print(" {");
            out.print(stats.length());
            out.print("}");
        }
        out.print(": ");
        out.print(stats.referencedBy());
        out.print(stats.referencedBy() <= 1 ? " reference to " : " references to ");
        out.print(stats.instanceCount());
        out.print(stats.instanceCount() <= 1 ? " instance" : " instances");
        if (stats.totalSize() > 0) {
            out.print(" with ");
            out.print(stats.totalSize());
            out.print(" bytes");
        }
    }

    /**
     * Prints the statistics of a Collector into an output stream, e.g. into System.out.
     */
    public static void printStats(Stats stats, PrintStream out) {
        ClassStats<?>[] classStats = stats.stats();
        out.print(stats.memoryUsed());
        out.print(" bytes in ");
        out.print(classStats.length);
        out.print(" classes and ");
        out.print(stats.instanceCount());
        out.println(" instances:");

        for (ClassStats<?> cs : classStats) {
            out.print("* ");
            printClassStats(cs, out);
            out.println();
        }
    }

    /**
     * Gets a displayable representation of a Collector.
     */
    public static String printStats(Stats stats) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(256);
        printStats(stats, new PrintStream(buffer));

        return buffer.toString();
    }
}
