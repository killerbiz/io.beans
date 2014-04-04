package io.beans.sizeof;

import io.beans.util.SwissArmyKnife;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;


/**
 * Calculates statistics about classes, and allows iteration over all referenced instances.
 * 
 * Makes use of undocumented Unsafe instance.
 * 
 * @see http://www.docjar.com/docs/api/sun/misc/Unsafe.html
 * 
 * @Todo Be aware of upcoming @Contended annotation which might get introduced in JDK8.
 * 
 * @author kuli
 */
@SuppressWarnings("restriction")
public abstract class ClassSchema<T> {

    private static final sun.misc.Unsafe unsafe;

    private static final class X {
        @SuppressWarnings("unused")
        int i;

        @SuppressWarnings("unused")
        Object a, b, c;
    }

    private static final long emptyObjectSize;

    private static final long alignmentMask, alignmentSummand;

    private static final long objectRefSize;

    static {
        try {
            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        } catch (Exception ex) {
            throw new AssertionError("sun.misc.Unsafe is not accessible.");
        }

        int bytesPerAddress = unsafe.addressSize();
        int bitShift = Integer.numberOfTrailingZeros(bytesPerAddress);
        alignmentMask = (~0L) << bitShift;
        alignmentSummand = bytesPerAddress;

        long minRef = Long.MAX_VALUE;
        long[] refs = new long[X.class.getDeclaredFields().length];
        int i = 0;
        for (Field f : X.class.getDeclaredFields()) {
            long fAddr = unsafe.objectFieldOffset(f);
            if (fAddr < minRef) minRef = fAddr;
            refs[i++] = fAddr;
        }
        emptyObjectSize = minRef;
        Arrays.sort(refs); // Just in case...
        long oSize = 0;
        for (i = 1; i < 4; i++) {
            long thisSize = refs[i] - refs[i - 1];
            oSize = Math.max(oSize, thisSize);
        }
        objectRefSize = oSize;
    }

    private static long alignAddress(long address) {
        long aligned = address & alignmentMask;
        return aligned == address ? aligned : aligned + alignmentSummand;
    }

    final Class<T> type;

    private ClassSchema(Class<T> type) {
        this.type = type;
    }

    public final void iterate(T container, FieldCallback callback) {
        if (!type.isInstance(container)) {
            if (container == null) throw new NullPointerException();
            throw new ClassCastException("Instance " + container + " should be of type " + type);
        }
        safeIterate(container, callback);
        callback.finished();
    }

    abstract void safeIterate(Object container, FieldCallback callback);

    public Class<T> getType() {
        return type;
    }

    public abstract long shallowSize(T instance);

    private static class FieldRef {
        final String name;
        final long ref;

        FieldRef(String name, long ref) {
            this.name = name;
            this.ref = ref;
        }
    }

    private final static FieldRef[] refsStart = new FieldRef[0];

    @Override
    public String toString() {
        return getClass().getSimpleName() + " for " + type.getName() + " {" + shallowSize(null) + "}";
    }

    private static class ObjectClassSchema<T> extends ClassSchema<T> {
        final long size;

        final FieldRef[] refs;

        private ObjectClassSchema(Class<T> type, FieldFilter filter) {
            super(type);

            long s = emptyObjectSize;
            FieldRef[] r = refsStart;

            int i = 0;
            Class<?> lastFieldType = null;
            Class<?> c = type;
            do {
                for (Field f : c.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;

                    Class<?> t = f.getType();
                    long offset = unsafe.objectFieldOffset(f);
                    if (offset > s) {
                        s = offset;
                        lastFieldType = t;
                    }
                    if (t.isPrimitive()) {
                        continue;
                    }

                    if (filter.accept(f)) {
                        if (i >= r.length) r = Arrays.copyOf(r, i + 16);
                        r[i++] = new FieldRef(type.getName() + "#" + f.getName(), offset);
                    }
                }
            } while ((c = c.getSuperclass()) != null);

            this.refs = i == r.length ? r : Arrays.copyOf(r, i);

            if (lastFieldType != null) {
                if (lastFieldType.isPrimitive()) {
                    s += (lastFieldType == long.class || lastFieldType == double.class) ? 8 : 4;
                } else {
                    s += objectRefSize;
                }
            }
            this.size = alignAddress(s);
        }

        @Override
        void safeIterate(Object container, FieldCallback callback) {
            for (FieldRef r : refs) {
                Object o = unsafe.getObject(container, r.ref);
                callback.visit(r.name, o);
            }
        }

        @Override
        public long shallowSize(Object instance) {
            return size;
        }

        @Override
        public String toString() {
            return super.toString() + " <" + refs.length + " obj-ref>";
        }
    }

    private static abstract class ArraySchema<T> extends ClassSchema<T> {
        final long baseOffset;

        final long indexScale;

        ArraySchema(Class<T> type) {
            super(type);
            assert type.isArray();
            baseOffset = unsafe.arrayBaseOffset(type);
            indexScale = unsafe.arrayIndexScale(type);
        }

        @Override
        public long shallowSize(T array) {
            if (array == null) return baseOffset;

            return alignAddress(stopAddress(array));
        }

        private long stopAddress(Object array) {
            long l = Array.getLength(array);
            return baseOffset + l * indexScale;
        }
    }

    private static class PrimitiveArraySchema<T> extends ArraySchema<T> {
        private PrimitiveArraySchema(Class<T> type) {
            super(type);
        }

        @Override
        void safeIterate(Object array, FieldCallback callback) {
            // Do nothing here
        }
    }

    private static class ObjectArraySchema<T> extends ArraySchema<T> {
        final String refName;

        private ObjectArraySchema(Class<T> type) {
            super(type);
            refName = SwissArmyKnife.fullPrint(type);
        }

        @Override
        void safeIterate(Object array, FieldCallback callback) {
            for (Object element : (Object[]) array) {
                callback.visit(refName, element);
            }
        }
    }

    public static <T> ClassSchema<T> createSchemaFor(Class<T> type) {
        return createSchemaFor(type, FieldFilter.ACCEPT_ALL);
    }

    public static <T> ClassSchema<T> createSchemaFor(Class<T> type, FieldFilter filter) {
        if (type.isArray()) {
            if (type.getComponentType().isPrimitive()) {
                return new PrimitiveArraySchema<>(type);
            } else {
                return new ObjectArraySchema<>(type);
            }
        } else {
            return new ObjectClassSchema<>(type, filter);
        }
    }

    public static void iterateStatic(Class<?> type, FieldFilter filter, FieldCallback fc) {
        Class<?> c = type;
        do {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;

                if (filter.accept(f)) {
                    Object cookie = unsafe.staticFieldBase(f);
                    long addr = unsafe.staticFieldOffset(f);
                    Object val = unsafe.getObject(cookie, addr);

                    fc.visit(f.getName(), val);
                }
            }
        } while ((c = c.getSuperclass()) != null);

        fc.finished();
    }
}
