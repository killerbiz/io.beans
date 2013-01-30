package io.beans.sizeof;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.awt.Rectangle;
import java.lang.annotation.Retention;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import io.beans.util.SwissArmyKnife;
import java.util.logging.Level;
import java.util.logging.Logger;


public class SizeOfTest {

    private static final Logger LOGGER = SwissArmyKnife.getLogger();

    @Test
    public void simpleTest() {
        long size = SizeOf.sizeOf((Object) null);
        assertEquals(0, size);

        size = SizeOf.sizeOf(new Object());
        assertTrue(12 <= size && size <= 24);
    }

    @Test
    public void stringTest() {
        String val = "I am a String with üöäÜÖÄßéŝħŋ@";

        long size = SizeOf.sizeOf(val);
        // System.out.println("Direct size: " + size);
        assertTrue(size > val.length() * 2);

        Collector c = SizeOf.createCollectorWith(val);
        LOGGER.info(SizeOf.printStats(c));
        assertEquals(size, c.memoryUsed());
        assertNull(c.statsFor(Object.class));
        assertNotNull(c.statsFor(char[].class));
        assertTrue(c.statsFor(char[].class).totalSize() > val.length() * 2);

        c = SizeOf.createCollector(false);
        c.measure(val);
        assertEquals(c.statsFor(char[].class).totalSize(), 0);
    }

    @Test
    public void arrayTest() {
        Rectangle[] sarr = new Rectangle[10];
        long emptySize = SizeOf.sizeOf((Object) sarr);
        assertTrue(emptySize > 42);

        Rectangle val = new Rectangle(1, 2, 3, 4);
        for (int i = 0; i < 10; i++) {
            sarr[i] = val;
        }
        long sSize = SizeOf.sizeOf(val);
        Collector c = SizeOf.createCollectorWith((Object) sarr);
        LOGGER.info(SizeOf.printStats(c));
        assertEquals(emptySize + sSize, c.memoryUsed());
        ClassStats<Rectangle> cs = c.statsFor(Rectangle.class);
        assertEquals(sSize, cs.totalSize());
        assertEquals(1, cs.instanceCount());
        assertEquals(10, cs.referencedBy());

        for (int i = 0; i < 10; i++) {
            sarr[i] = new Rectangle(1 * i, 2 * i, 3 * i, 4 * i);
        }
        c = SizeOf.createCollectorWith((Object) sarr);
        LOGGER.info(SizeOf.printStats(c));
        assertEquals(emptySize + 10 * sSize, c.memoryUsed());
        cs = c.statsFor(Rectangle.class);
        assertEquals(10 * sSize, cs.totalSize());
        assertEquals(10, cs.instanceCount());
        assertEquals(10, cs.referencedBy());

        Set<Rectangle> rects = new HashSet<Rectangle>();
        for (Rectangle r : cs.instances()) {
            assertEquals(2 * r.x, r.y);
            assertEquals(3 * r.x, r.width);
            assertEquals(4 * r.x, r.height);

            rects.add(r);
        }
        assertEquals(10, rects.size());
        assertTrue(SizeOf.sizeOf(rects) > c.memoryUsed());

        long currentSize = c.memoryUsed();
        c.measure(new int[0]);
        long emptyIntArraySize = c.statsFor(int[].class).length();
        assertTrue(emptyIntArraySize > 0 && emptyIntArraySize < 100);
        assertEquals(currentSize + emptyIntArraySize, c.memoryUsed());

        long filledIntArrayLength = SizeOf.sizeOf(new int[] { 1, 2, 3, 4 });
        long extraSize = filledIntArrayLength - emptyIntArraySize;
        assertTrue(4 * 4 <= extraSize && extraSize <= (4 * 4 + 4));
    }

    static class A {
        B b;
    }

    static class B {
        A a;
    }

    @Test
    public void circularDependecyTest() {
        A a = new A();
        B b = new B();
        A a2 = new A();
        B b2 = new B();
        a.b = b;
        b.a = a2;
        a2.b = b2;
        b2.a = a;

        Collector c = SizeOf.createCollectorWith(a);
        LOGGER.info(SizeOf.printStats(c));
        long aSize = c.statsFor(A.class).totalSize();
        long bSize = c.statsFor(B.class).totalSize();
        assertEquals(aSize, bSize);
        long totalSize = c.memoryUsed();
        assertTrue(aSize + bSize > totalSize);

        c = SizeOf.createCollector();
        c.measure(a);
        c.measure(b); // shouldn't change anything
        LOGGER.info(SizeOf.printStats(c));
        assertEquals(aSize, c.statsFor(A.class).totalSize());
        assertEquals(bSize, c.statsFor(B.class).totalSize());
        assertEquals(totalSize, c.memoryUsed());
    }

    private static class Singleton {
        static Singleton INSTANCE = new Singleton();

        @Override
        public boolean equals(Object obj) {
            // Ha ha!
            return obj instanceof Singleton;
        }

        @Override
        public int hashCode() {
            return 123456;
        }
    }

    @Test
    public void globalObjectTest() {
        Integer global = 1;
        Integer local = new Integer(1);

        long integerSize = SizeOf.sizeOf(local);
        assertTrue(integerSize > 0);
        assertEquals(integerSize, SizeOf.sizeOf(global)); // Direct calls to global will compute its size

        AtomicReference<Integer> container = new AtomicReference<Integer>();
        long containerSize = SizeOf.sizeOf(container);
        container.set(global);
        assertEquals(containerSize, SizeOf.sizeOf(container));
        container.set(local);
        assertEquals(containerSize + integerSize, SizeOf.sizeOf(container));

        AtomicReference<Class<?>> classContainer = new AtomicReference<Class<?>>(ArrayList.class);
        assertEquals(containerSize, SizeOf.sizeOf(classContainer));
        assertTrue(SizeOf.sizeOf(ArrayList.class) > integerSize);

        AtomicReference<Enum<TimeUnit>> enumContainer = new AtomicReference<Enum<TimeUnit>>(TimeUnit.DAYS);
        assertEquals(containerSize, SizeOf.sizeOf(enumContainer));

        AtomicReference<Singleton> singletonContainer = new AtomicReference<Singleton>(Singleton.INSTANCE);
        assertEquals(containerSize, SizeOf.sizeOf(singletonContainer));
        singletonContainer.set(new Singleton());
        Collector c = SizeOf.createCollectorWith(singletonContainer);
        long singletonLength = c.statsFor(Singleton.class).length();
        assertTrue(containerSize + singletonLength >= SizeOf.sizeOf(singletonContainer));
        assertTrue(SizeOf.sizeOf(Singleton.INSTANCE) > 0);
    }

    @Test
    public void alignmentTest() {
        byte[] b = new byte[1];
        long size = SizeOf.sizeOf(b);
        assertEquals("Lowest two bits must be zero.", 0L, size & 0x3L);
    }

    @Retention(RUNTIME)
    private @interface TestAnno {
        boolean value();
    };

    @TestAnno(false)
    private static class Counting {};

    @TestAnno(true)
    private static class NonCounting {};

    private static class MemTester {
        @TestAnno(false)
        Date countingDate;

        @TestAnno(true)
        Date nonCountingDate;

        @SuppressWarnings("unused")
        Date countingAnyway;

        @SuppressWarnings("unused")
        Counting counting;

        @SuppressWarnings("unused")
        NonCounting nonCounting;
    }

    @Test
    public void annotatedTest() {
        Environment e = SizeOf.createEnvironment();
        e.addGlobalAnnotation(TestAnno.class, new AnnotationFilter<TestAnno>() {
            @Override
            public boolean isGlobal(AnnotatedElement fieldOrClass, TestAnno annotation) {
                return annotation.value();
            }
        });

        MemTester m = new MemTester();
        long size = e.sizeOf(m);

        m.countingDate = new Date();
        long newSize = e.sizeOf(m);
        assertTrue(newSize > size);

        size = newSize;
        m.nonCountingDate = new Date();
        newSize = e.sizeOf(m);
        assertEquals(size, newSize);

        m.countingAnyway = new Date();
        newSize = e.sizeOf(m);
        assertTrue(newSize > size);

        size = newSize;
        m.counting = new Counting();
        newSize = e.sizeOf(m);
        assertTrue(newSize > size);

        size = newSize;
        m.nonCounting = new NonCounting();
        newSize = e.sizeOf(m);
        assertEquals(size, newSize);

        Collector c = e.createCollector();
        c.measure(m);
        assertNull(c.statsFor(NonCounting.class));
        assertNotNull(c.statsFor(Counting.class));
    }

    // @Test // Long-running test - disabled
    public void largeArrayTestXX() {
        long emptyArraySize = SizeOf.flatSizeOf(new Object[0]);
        long arr256Size = SizeOf.flatSizeOf(new Object[256]);
        long refLength = (arr256Size - emptyArraySize) / 256;

        long dateSize = SizeOf.sizeOf(new Date());

        try {
            for (int l = 16; l > 0; l <<= 1) {
                try {
                    System.gc();
                    Thread.sleep(1000L);
                } catch (InterruptedException ex) {
                    LOGGER.warning("Interrupted!");
                    return;
                }
                long nanoStart = System.nanoTime();
                Date[] array = createArray(l);
                long nanoSecond = System.nanoTime();
                long size = SizeOf.sizeOf((Object) array);
                long nanoEnd = System.nanoTime();
                LOGGER.log(Level.FINE, "Array with length {0} has size {1}; creation took {2}, calculation took {3}",
                        new Object[]{l, size, SwissArmyKnife.printDuration(nanoSecond - nanoStart, TimeUnit.MILLISECONDS), SwissArmyKnife.printDuration(nanoEnd - nanoSecond, TimeUnit.MILLISECONDS)});
                assertEquals(emptyArraySize + l * dateSize + l * refLength, size);
            }
        } catch (OutOfMemoryError ex) {
            // Then finish!
        }
    }

    private Date[] createArray(int length) throws OutOfMemoryError {
        Date[] array = new Date[length];
        for (int i = 0; i < length; i++) {
            array[i] = new Date();
        }

        return array;
    }
}
