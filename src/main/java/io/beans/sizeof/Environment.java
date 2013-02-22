package io.beans.sizeof;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import io.beans.sizeof.Collector.ClassCollector;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

/**
 * The environment specifies whether and which kind of global objects exist.
 *
 * @author kuli
 */
public class Environment {

    private final Map<Class<?>, ClassSchema<?>> definitions = new HashMap<Class<?>, ClassSchema<?>>();

    private final Collection<Class<?>> globalClasses = new HashSet<Class<?>>();

    private final Map<Class<? extends Annotation>, AnnotationFilter<? extends Annotation>> globalAnnotations = new HashMap<Class<? extends Annotation>, AnnotationFilter<? extends Annotation>>();

    private final Map<Object, Void> globalObjects = new IdentityHashMap<Object, Void>();

    private StaticFieldPolicy staticFieldPolicy;

    final FieldFilter allowOnlyNonGlobal = new FieldFilter() {

        @Override
        public boolean accept(Field f) {
            if (hasGlobalMarker(f)) return false;

            Class<?> fieldType = f.getType();
            for (Class<?> g : globalClasses) {
                if (g.isAssignableFrom(fieldType)) return false;
            }
            if (hasGlobalMarker(fieldType)) return false;

            return true;
        }
    };

    final FieldFilter collectStaticConstants = new FieldFilter() {

        @Override
        public boolean accept(Field f) {
            Class<?> fieldType = f.getType();
            // Don't add all those int[] arrays etc.
            if (fieldType.isArray() && fieldType.getComponentType().isPrimitive()) return false;

            for (Class<?> g : globalClasses) {
                if (g.isAssignableFrom(fieldType)) return false;
            }
            if (hasGlobalMarker(fieldType)) return false;

            return true;
        }
    };

    final FieldFilter onlyGloballyAnnotated = new FieldFilter() {

        @Override
        public boolean accept(Field f) {
            return hasGlobalMarker(f);
        }
    };

    final FieldFilter onlyGloballyAnnotatedAndConstants = new FieldFilter() {

        @Override
        public boolean accept(Field f) {
            return Modifier.isFinal(f.getModifiers()) || hasGlobalMarker(f);
        }
    };

    final FieldCallback directGlobalSetter = new FieldCallback() {

        @Override
        public void visit(String refName, Object value) {
            addGlobalInstance(value);
        }

        @Override
        public void finished() {
            // Nothing to finish;
        }
    };

    final FieldCallback deepGlobalSetter = new FieldCallback() {

        @Override
        public void visit(String refName, Object value) {
            iterateDeep(value, directGlobalSetter);
        }

        @Override
        public void finished() {
            // Nothing to finish;
        }
    };

    {
        addGlobalAnnotation(Global.class);
        staticFieldPolicy = StaticFieldPolicy.FULL_TREE;
    }

    boolean hasGlobalMarker(AnnotatedElement element) {
        for (Annotation a : element.getAnnotations()) {
            if (annotationMarksAsGlobal(element, a)) return true;

            Class<? extends Annotation> atype = a.annotationType();
            for (Annotation a2 : atype.getAnnotations()) {
                if (annotationMarksAsGlobal(element, a2)) return true;
            }
        }
        return false;
    }

    private <A extends Annotation> boolean annotationMarksAsGlobal(AnnotatedElement element, A annotation) {
        @SuppressWarnings("unchecked")
        AnnotationFilter<? super A> filter = (AnnotationFilter<? super A>) globalAnnotations.get(annotation
                .annotationType());
        return filter != null && filter.isGlobal(element, annotation);
    }

    boolean classHasGlobalMarker(Class<?> type) {
        if (hasGlobalMarker(type)) return true;
        for (Class<?> in : type.getInterfaces()) {
            if (classHasGlobalMarker(in)) return true;
        }

        for (Class<?> c = type.getSuperclass(); c != null; c = c.getSuperclass()) {
            if (hasGlobalMarker(c)) return true;
        }

        return false;
    }

    /**
     * Sets the static field policy for newly declared classes.
     */
    public synchronized Environment setStaticFieldPolicy(StaticFieldPolicy staticFieldPolicy) {
        this.staticFieldPolicy = staticFieldPolicy;
        return this;
    }

    /**
     * Gets the static field policy for newly declared classes.
     */
    public StaticFieldPolicy getStaticFieldPolicy() {
        return staticFieldPolicy;
    }

    /**
     * Marks this object as being global.
     */
    public synchronized Environment addGlobalInstance(Object g) {
        if (g != null) {
            globalObjects.put(g, null);
            register(g.getClass());
        }
        return this;
    }

    /**
     * Marks the given instance and all its referenced values as being global.
     */
    public synchronized Environment addFullGlobalTree(Object g) {
        iterateDeep(g, deepGlobalSetter);
        return addGlobalInstance(g);
    }

    /**
     * Marks the given type as a global one.
     */
    public synchronized Environment addGlobalType(Class<?> g) {
        globalClasses.add(g);
        return this;
    }

    /**
     * Declares the given annotation as a global marker.
     */
    public Environment addGlobalAnnotation(Class<? extends Annotation> g) {
        return addGlobalAnnotation(g, AnnotationFilter.DONT_MEASURE);
    }

    /**
     * Declares the given annotation as a global marker.
     */
    public synchronized <A extends Annotation> Environment addGlobalAnnotation(
            Class<A> g, AnnotationFilter<? super A> filter) {
        globalAnnotations.put(g, filter);
        return this;
    }

    Set<Object> getGlobalObjects() {
        return globalObjects.keySet();
    }

    /**
     * Checks whether a given value is global.
     * This is the case if either the instance itself was marked as global,
     * or if it is of a global type.
     */
    public boolean isGlobal(Object value) {
        if (globalObjects.containsKey(value)) return true;
        for (Class<?> g : globalClasses) {
            if (g.isInstance(value)) return true;
        }

        return classHasGlobalMarker(value.getClass());
    }

    /**
     * Checks whether the given type is marked as global,
     * either explicit via addGlobalType(), or implicit by having a specific annotation.
     */
    public boolean isGlobalClass(Class<?> type) {
        for (Class<?> g : globalClasses) {
            if (g.isAssignableFrom(type)) return true;
        }

        return classHasGlobalMarker(type);
    }

    <T> ClassSchema<T> getSchema(Class<T> type) {
        return getSchema(type, staticFieldPolicy);
    }

    <T> ClassSchema<T> getSchema(Class<T> type, StaticFieldPolicy policy) {
        @SuppressWarnings("unchecked")
        ClassSchema<T> cs = (ClassSchema<T>) definitions.get(type);
        if (cs != null) return cs;

        cs = ClassSchema.createSchemaFor(type, allowOnlyNonGlobal);
        definitions.put(type, cs);
        // Eventually register static values as constants
        addGlobalObjectsFrom(type, policy);

        return cs;
    }

    /**
     * Registers the given type as known.
     * Eventually declare all static variable values as global, if not registered yet.
     */
    public Environment register(Class<?> type) {
        getSchema(type);
        return this;
    }

    /**
     * Registers the given type as known.
     * Eventually declare all static variable values as global, if not registered yet.
     * This depends on the given argument.
     */
    public Environment register(Class<?> type, StaticFieldPolicy policy) {
        getSchema(type, policy);
        return this;
    }

    <T> ClassSchema<T> getSchema(T instance) {
        @SuppressWarnings("unchecked")
        Class<T> c = (Class<T>) instance.getClass();
        return getSchema(c);
    }

    private Environment addGlobalObjectsFrom(Class<?> type, StaticFieldPolicy policy) {

        ClassSchema.iterateStatic(type, policy.filterStatics(this), policy.addToGlobalsCallback(this));
        // Also add onstants from inner classes
        Class<?> c = type;
        do {
            for (Class<?> inner : c.getDeclaredClasses()) {
                ClassSchema.iterateStatic(inner, policy.filterStatics(this), policy.addToGlobalsCallback(this));
            }
        } while ((c = c.getSuperclass()) != null);

        return this;
    }

    /**
     * Creates a new collector that calculates the total size and the total size of each class.
     */
    public Collector createCollector() {
        return createCollector(true);
    }

    /**
     * Creates a new collector that calculates the total size, but maybe wihout the total size of each class.
     * 
     * This will work faster if the individual class mem size is not used.
     */
    public Collector createCollector(boolean calcSizeForEachClass) {

        return new Collector(this, calcSizeForEachClass);
    }

    /**
     * Creates a new collector that calculates the total size and the total size of each class.
     * 
     * The given elements are already added for measurement.
     */
    public Collector createCollectorWith(Object... elements) {
        Collector c = createCollector(true);
        for (Object e : elements) {
            c.measure(e);
        }
        return c;
    }

    /**
     * Utility method to quickly calculate the used memory for a group of instances.
     * 
     * Instances that are referenced by multiple parents count only once.
     */
    public long sizeOf(Object... instances) {
        Collector c = createCollector(false);
        for (Object o : instances) {
            c.measure(o);
        }
        return c.memoryUsed();
    }

    private static class MyClassStats<T> implements ClassStats<T>, Comparable<MyClassStats<?>> {
        final ClassSchema<T> schema;

        final long totalSize;

        final Map<String, Reference> referencedBy;

        final T[] instances;

        final int[] referenceCounts;

        MyClassStats(ClassCollector<T> cc) {

            this.schema = cc.schema;
            this.totalSize = cc.totalSize();
            this.referencedBy = cc.referencedBy;

            Class<T> type = schema.getType();
            int n = cc.instanceCount;
            @SuppressWarnings("unchecked")
            T[] instanceArray = (T[]) Array.newInstance(type, n);
            int[] refCounts = new int[n];
            int i = 0;
            for (Map.Entry<Object, Integer> e : cc.instancesToRefCounts.entrySet()) {
                Integer count = e.getValue();
                // Is null when global instance
                if (count == null) continue;

                instanceArray[i] = type.cast(e.getKey());
                refCounts[i++] = count;
            }

            this.instances = instanceArray;
            this.referenceCounts = refCounts;
        }

        @Override
        public Class<T> type() {
            return schema.getType();
        }

        @Override
        public long length() {
            return schema.shallowSize(null);
        }

        @Override
        public int instanceCount() {
            return instances.length;
        }

        @Override
        public long totalSize() {
            return totalSize;
        }

        @Override
        public Reference[] referencedBy() {
            Reference[] ref = referencedBy.values().toArray(new Reference[referencedBy.size()]);
            Arrays.sort(ref, new Comparator<Reference>() {

                @Override
                public int compare(Reference o1, Reference o2) {
                    return o2.getCount() - o1.getCount();
                }

            });

            return ref;
        }

        @Override
        public T[] instances() {
            return instances;
        }

        @Override
        public int[] referenceCounts() {
            return referenceCounts;
        }

        @Override
        public int compareTo(MyClassStats<?> o) {
            long r = o.totalSize - totalSize; // more mem consuming comes first
            return r == 0 ? o.instanceCount() - instanceCount() : (r < 0L ? -1 : 1);
        }
    }

    <T> ClassStats<T> classStatsFor(ClassCollector<T> cc) {
        if (cc == null) return null;
        return new MyClassStats<T>(cc);
    }

    void iterateDeep(Object instance, final FieldCallback fc) {
        final Map<Object, Boolean> visited = new IdentityHashMap<Object, Boolean>();
        visited.put(null, Boolean.TRUE); // Null values are always skipped

        final FieldCallback deeplyIterating = new FieldCallback() {

            @Override
            public void visit(String refName, Object value) {
                iterateDeep(value, fc, this, visited);
            }

            @Override
            public void finished() {}
        };

        iterateDeep(instance, fc, deeplyIterating, visited);
    }

    private void iterateDeep(Object instance, FieldCallback fc, FieldCallback deepIterator,
                Map<Object, Boolean> visited) {
        if (visited.put(instance, Boolean.TRUE) != null) return;

        fc.visit("#", instance);
        ClassSchema<?> cs = getSchema(instance.getClass());
        cs.safeIterate(instance, deepIterator);
    }
}
