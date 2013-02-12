package io.beans.sizeof;

import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;


/**
 * You can feed the collector with several defferent instances, and then get the complete statistics from it.
 * 
 * Multiple references to the same instance count only once.
 * 
 * @author kuli
 */
public class Collector implements Stats {

    final Measurement measurement;

    static class ClassCollector<T> {
        final ClassSchema<T> schema;

        final Map<Object, Integer> instancesToRefCounts = new IdentityHashMap<Object, Integer>();

        int instanceCount;

        final Map<String, ClassStats.Reference> referencedBy = new HashMap<String, ClassStats.Reference>();

        ClassCollector(Environment env, Class<T> type) {
            assert !type.isInterface();

            env.addGlobalObjectsFrom(type);
            schema = env.getSchema(type);

            for (Object o : env.getGlobalObjects()) {
                if (o.getClass().equals(type)) instancesToRefCounts.put(o, null);
            }
        }

        /**
         * I was referenced by some other bean.
         */
        void ref(String refName, T instance, Measurement caller) {
            ClassStats.Reference r = referencedBy.get(refName);
            if (r == null) referencedBy.put(refName, new ClassStats.Reference(refName, 1));
            else r.increment();

            execute(instance, caller, false);
        }

        /**
         * Start measurement with a given bean to calczlate.
         */
        void startWith(T instance, Measurement caller) {
            execute(instance, caller, true);
        }

        private void execute(T instance, Measurement caller, boolean calcGlobalObjects) {
            if (instancesToRefCounts.containsKey(instance)) {
                Integer count = instancesToRefCounts.get(instance);
                if (count != null) {
                    instancesToRefCounts.put(instance, count + 1);
                } else if (calcGlobalObjects) {
                    calc(instance, caller);
                }
            } else {
                instancesToRefCounts.put(instance, 1);
                instanceCount++;
                calc(instance, caller);
            }
        }

        void calc(T instance, Measurement measurement) {
            measurement.measure(instance, schema);
        }

        long totalSize() {
            return 0;
        }

        @Override
        public String toString() {
            if (instancesToRefCounts.isEmpty()) return getClass().getSimpleName() + " <- (" + referencedBy + ")";
            else
                return getClass().getSimpleName() + " *" + instancesToRefCounts.keySet().iterator().next().getClass()
                        + "* <- (" + referencedBy + ")";
        }
    }

    static class GlobalClassCollector<T> extends ClassCollector<T> {
        GlobalClassCollector(Environment env, Class<T> type) {
            super(env, type);
        }

        @Override
        void ref(String refName, T instance, Measurement caller) {
            // Do nothing - don't iterate through fields!
        }
    }

    static class MemoryCountingClassCollector<T> extends ClassCollector<T> {
        final Measurement classSpecificMeasurement;

        MemoryCountingClassCollector(Environment env, Class<T> type) {
            super(env, type);
            classSpecificMeasurement = new Measurement(env);
        }

        @Override
        void calc(T instance, Measurement globalMeasurement) {
            classSpecificMeasurement.measure(instance, schema);
            super.calc(instance, globalMeasurement);
        }

        @Override
        long totalSize() {
            return classSpecificMeasurement.totalSize;
        }
    }

    private static class Measurement implements FieldCallback {
        final Environment env;

        final Map<Class<?>, ClassCollector<?>> classColl;

        long totalSize;

        int instanceCount;

        Measurement(Environment env) {
            this.env = env;
            classColl = new HashMap<Class<?>, ClassCollector<?>>();
        }

        <T> void measure(T instance, ClassSchema<T> schema) {
            totalSize += schema.flatSize(instance);
            instanceCount++;

            schema.safeIterate(instance, this);
        }

        final <T> ClassCollector<T> getClassCollector(T instance) {
            @SuppressWarnings("unchecked")
            ClassCollector<T> cc = (ClassCollector<T>) classColl.get(instance.getClass());
            if (cc == null) {
                @SuppressWarnings("unchecked")
                Class<T> type = (Class<T>) instance.getClass();
                cc = env.isGlobalClass(type) ? new GlobalClassCollector<T>(env, type) : createNewCollector(type);
                classColl.put(type, cc);
            }
            return cc;
        }

        <T> ClassCollector<T> createNewCollector(Class<T> type) {
            return new ClassCollector<T>(env, type);
        }

        @Override
        public void visit(String refName, Object value) {
            if (value != null) {
                refTo(refName, value);
            }
        }

        private <T> void refTo(String refName, T value) {
            ClassCollector<T> cc = getClassCollector(value);
            cc.ref(refName, value, this);
        }

        @Override
        public void finished() {
            // Nothing to do
        }

        private <T> ClassStats<T> statsFor(Class<T> type) {
            @SuppressWarnings("unchecked")
            ClassCollector<T> cc = (ClassCollector<T>) classColl.get(type);
            return cc == null ? null : env.classStatsFor(cc);
        }

        private ClassStats<?>[] stats() {
            ClassStats<?>[] stats = new ClassStats<?>[classColl.size()];
            int i = 0;
            for (ClassCollector<?> cc : classColl.values()) {
                stats[i++] = env.classStatsFor(cc);
            }

            Arrays.sort(stats);
            return stats;
        }
    }

    private static class IndividualClassMeasurement extends Measurement {
        public IndividualClassMeasurement(Environment env) {
            super(env);
        }

        @Override
        <T> ClassCollector<T> createNewCollector(Class<T> type) {
            return new MemoryCountingClassCollector<T>(env, type);
        }
    }

    @Override
    public synchronized long memoryUsed() {
        return measurement.totalSize;
    }

    @Override
    public synchronized int instanceCount() {
        return measurement.instanceCount;
    }

    @Override
    public synchronized <T> ClassStats<T> statsFor(Class<T> type) {
        return measurement.statsFor(type);
    }

    @Override
    public synchronized ClassStats<?>[] stats() {
        return measurement.stats();
    }

    Collector(Environment env, boolean measureSizeForEveryClass) {
        measurement = measureSizeForEveryClass ? new IndividualClassMeasurement(env) : new Measurement(env);
    }

    /**
     * Add a bean to the instance pool and measure its size.
     * 
     * If this instance already was measured by a previously added or referenced bean, then nothing changes.
     * 
     * This method measures explicitely, i.e. if this instance wouldn't normally be counted because it's marked as
     * global, then it will still be measured when added directly using this method.
     * 
     * The referenceBy-counter of this instance's class does not increment.
     * 
     * This method is synchronized to make sure that possible volatile instance variables are read correctly. As an
     * additional advantage, this class becomes thread safe, though you won't gain much in most cases.
     */
    public synchronized <T> void measure(T instance) {
        if (instance == null) return;

        ClassCollector<T> cc = measurement.getClassCollector(instance);
        cc.startWith(instance, measurement);
    }
}
