package io.beans.util;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.text.DateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import sun.reflect.CallerSensitive;


/**
 * The swiss army knife for java hackers: Various introspection methods for internal object states.
 * 
 * Use these for testing and debugging, but not for production code.
 * 
 * @author kuli
 */
public class SwissArmyKnife {

    private enum Matcher {
        EXACT {
            @Override
            boolean matches(Class<?> expected, Class<?> fieldType) {
                return expected.equals(fieldType);
            }
        },
        ASSIGNABLE {
            @Override
            boolean matches(Class<?> expected, Class<?> fieldType) {
                return fieldType.isAssignableFrom(expected);
            }
        },
        ASSIGNABLE_WITH_PRIMITIVES {
            @Override
            boolean matches(Class<?> expected, Class<?> fieldType) {
                return SwissArmyKnife.isAssignableFrom(fieldType, expected);
            }
        };

        abstract boolean matches(Class<?> expected, Class<?> fieldType);
    }

    private SwissArmyKnife() {
        throw new AssertionError("Static class!");
    }

    /**
     * Injects a value of a given type into a target object. The injected field if the first found field that is of an
     * assignable type.
     * 
     * @param target The target that may contain the matching field
     * @param value The new value
     * @return <code>true</code> if there was one exact field match
     * @throws IllegalStateException if there are multiple fields of the same matching type
     */
    @SuppressWarnings("unchecked")
    public static boolean inject(Object target, Object value) {
        return inject(target, (Class<Object>) value.getClass(), value, Matcher.ASSIGNABLE_WITH_PRIMITIVES);
    }

    /**
     * Injects a value of a given type into a target object. The injected field if the first found field that is of an
     * assignable type.
     * 
     * @param target The target that may contain the matching field
     * @param fieldType The type of the field.
     * @param value The new value
     * @return <code>true</code> if there was one exact field match
     * @throws IllegalStateException if there are multiple fields of the same matching type
     */
    public static <T> boolean inject(Object target, Class<T> fieldType, T value) {
        return inject(target, fieldType, value, Matcher.ASSIGNABLE);
    }

    /**
     * Injects a value of a given type into a target object.
     * 
     * @param target The target that may contain the matching field
     * @param fieldType The type of the field.
     * @param value The new value
     * @param exactMatch If <code>true</code>, the field type must match the fieldType parameter exactly; if
     *            <code>false</code>, the field must be assignable.
     * @return <code>true</code> if there was one exact field match
     * @throws IllegalStateException if there are multiple fields of the same matching type
     */
    public static <T> boolean inject(Object target, Class<T> fieldType, T value, boolean exactMatch) {
        return inject(target, fieldType, value, exactMatch ? Matcher.EXACT : Matcher.ASSIGNABLE);
    }

    /**
     * Injects a value of a given type into a target object.
     * 
     * @param target The target that must contain exactly one matching field
     * @param fieldType The type of the field.
     * @param value The new value
     * @param exactMatch If <code>true</code>, the field type must match the fieldType parameter exactly; if
     *            <code>false</code>, the field must be assignable.
     * @throws IllegalStateException if there are multiple fields of the same matching type, or if there is no such
     *             field
     */
    public static <T> void injectRequired(Object target, Class<T> fieldType, T value, boolean exactMatch) {
        if (!inject(target, fieldType, value, exactMatch)) {
            throw new IllegalStateException("No field of type " + fieldType.getName() + " in target class "
                    + target.getClass().getName() + " found.");
        }
    }

    private static class FieldIterator implements Iterator<Field> {
        Class<?> currentClass;
        Field[] currentFields;
        int fieldIndex;

        FieldIterator(Class<?> clazz) {
            currentClass = clazz;
            currentFields = clazz.getDeclaredFields();
            prepareNextField();
        }

        private void prepareNextField() {
            while (fieldIndex >= currentFields.length) {
                currentClass = currentClass.getSuperclass();
                if (currentClass == null) return;
                fieldIndex = 0;
                currentFields = currentClass.getDeclaredFields();
            }
        }

        @Override
        public Field next() {
            if (currentClass == null) throw new NoSuchElementException();

            Field f = currentFields[fieldIndex++];
            prepareNextField();
            return f;
        }

        @Override
        public boolean hasNext() {
            return currentClass != null;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Not supported yet.");
        }
    }

    private static void makeAccessible(Field f) {
        if (!Modifier.isPublic(f.getModifiers())) {
            f.setAccessible(true);
        }
    }

    private static <T> boolean inject(Object target, Class<T> fieldType, T value, Matcher matcher) {

        String[] injectedFields = new String[1];

        for (Iterator<Field> i = new FieldIterator(target.getClass()); i.hasNext(); ) {
            Field f = i.next();
            if (matcher.matches(fieldType, f.getType())) {
                if (injectedFields[0] != null) {
                    throw new IllegalStateException("Multiple fields of type " + fieldType.getName()
                            + " in target class " + target.getClass().getName() + " found: " + injectedFields[0]
                            + ", " + f.getName() + ", and maybe more.");
                }
                injectedFields[0] = f.getName();
                makeAccessible(f);
                try {
                    f.set(target, value);
                } catch (IllegalArgumentException ex) {
                    throw new AssertionError("Field type has been checked before; " + ex.getMessage());
                } catch (IllegalAccessException ex) {
                    throw new AssertionError("Field has been made accessible before; " + ex.getMessage());
                }
            }
        }

        return injectedFields[0] != null;
    }

    /**
     * Peek into a target instance and fetch the only instance variable containing a vlue of type T.
     * 
     * @param target The instance where the value is stored somewhere
     * @param fieldType The type of the searched value (may be a subtype of the instance variable definition)
     * @return The found value, or <code>null</code> if there is no such
     * @throws IllegalStateException if there are at least two field containing values of the requested type
     */
    public static <T> T peek(final Object target, final Class<T> fieldType) {
        final AtomicReference<T> ref = new AtomicReference<>();

        for (Iterator<Field> i = new FieldIterator(target.getClass()); i.hasNext(); ) {
            Field f = i.next();

            if (f.getType().isAssignableFrom(fieldType)) {
                makeAccessible(f);
                Object value;
                try {
                    value = f.get(target);
                } catch (IllegalArgumentException ex) {
                    throw new AssertionError("Field type has been checked before; " + ex.getMessage());
                } catch (IllegalAccessException ex) {
                    throw new AssertionError("Field has been made accessible before; " + ex.getMessage());
                }
                if (fieldType.isInstance(value)) {
                    if (ref.getAndSet(fieldType.cast(value)) != null) {
                        throw new IllegalStateException("Multiple values of type " + fieldType.getName()
                                + " in target class " + target.getClass().getName() + " found.");
                    }
                }
            }
        }

        return ref.get();
    }

    private static final Class<?>[] PRIMITIVES = { int.class, long.class, byte.class, short.class, boolean.class,
            float.class, double.class, char.class };

    private static final Class<?>[] WRAPPERS = { Integer.class, Long.class, Byte.class, Short.class, Boolean.class,
            Float.class, Double.class, Character.class };

    /**
     * Checks whether a given value is of a given type.
     * 
     * In contrast to normal instanceof checks, this does also check whether the value is of the appropriate wrapper type
     * if the expected class is a primitive.
     * 
     * @param type The checked class
     * @param value The tested value
     * @return <code>true</code> if 'value' can be assigned to a {@link Field} or method parameter of type 'type'
     *         without throwing an {@link IllegalArgumentException}
     */
    public static boolean isInstance(Class<?> type, Object value) {
        if (type.isInstance(value)) return true;
        if (!type.isPrimitive()) return false;

        for (int i = 0, n = PRIMITIVES.length; i < n; i++) {
            if (type == PRIMITIVES[i]) return WRAPPERS[i].isInstance(value);
        }

        return false;
    }

    /**
     * Checks whether a given class 'superClass' is a super class of another class 'subClass'.
     * 
     * In contrast to normal instanceof checks, this does also check whether B is of the appropriate wrapper type of the
     * possible primitive type B.
     * 
     * @param superType A
     * @param subType B
     * @return <code>true</code> if an instance of type 'subClass' can be fetched from a {@link Field} or method return
     *         value of type 'superClass' without throwing a {@link ClassCastException}
     */
    public static boolean isAssignableFrom(Class<?> superType, Class<?> subType) {
        if (superType.isAssignableFrom(subType)) return true;
        if (!superType.isPrimitive()) return false;

        for (int i = 0, n = PRIMITIVES.length; i < n; i++) {
            if (superType == PRIMITIVES[i]) return WRAPPERS[i].isAssignableFrom(subType);
        }

        return false;
    }

    /**
     * Dumps some content into a (probably temporary) file. Useful for debugging larger content like HTML or XML pages.
     * 
     * @param filename Where to put the file. Directories will be created.
     * @param append If <code>true</code>, an existing file will be appended; the contents get separated by a marking
     *            line with time stamp.
     * @param values The values to write.
     */
    public static void dump(String filename, boolean append, String... values) {
        File f = new File(filename);
        if (f.isDirectory()) throw new IllegalArgumentException("File " + filename + " is a directory!");
        File dir = f.getParentFile();
        if (dir != null) {
            dir.mkdirs();
        }

        FileOutputStream out = null;
        PrintWriter writer = null;
        try {
            out = new FileOutputStream(f, append);
            writer = new PrintWriter(out);
            if (append) {
                writer.println("==== Start: " + DateFormat.getDateTimeInstance().format(new Date()) + " ====");
            }
            for (String v : values) {
                writer.println(v);
            }

            writer.println();
        } catch (FileNotFoundException ex) {
            throw new IllegalArgumentException("Cannot write to " + filename + ": " + ex.getMessage(), ex);
        } finally {
            if (writer != null) writer.close();
            try {
                if (out != null) out.close();
            } catch (IOException ex) {
                // Ignore
            }
        }

    }

    /**
     * Gets the {@link Logger} for the calling class.
     */
    @CallerSensitive
    public static Logger getLogger() {
        return getLogger0();
    }

    private static Logger getLogger0() {
        Class<?> callingClass = sun.reflect.Reflection.getCallerClass();
        if (callingClass == null) return null;

        return Logger.getLogger(callingClass.getName());
    }

    /**
     * Logs a message with the lowest enabled log level through the calling class's logger.
     * 
     * This is useful if you want to quickly log a message without explicitly defining a separate logger.
     * 
     * This method should only be used in cases where performance is not a matter of concern.
     * 
     * @param parameters The logged values. Unless they are Strings, the full internal representation gets logged.
     */
    @CallerSensitive
    public static void log(Object... parameters) {
        StringBuilder sb = new StringBuilder();
        for (Object p : parameters) {
            if (p instanceof String) {
                sb.append(p);
            } else {
                fullPrint(p, sb);
            }
        }
        String message = sb.toString();

        System.out.println(message);

        Logger logger = getLogger0();
        if (logger == null) {
            throw new AssertionError("Cannot get caller class");
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(message);
        } else if (logger.isLoggable(Level.INFO)) {
            logger.info(message);
        } else {
            logger.warning(message);
        }
    }

    /**
     * Gets a String representation of a time delay in nanoseconds.
     * 
     * Examples are MM:SS or HH:MM:SS:mmm.nnnnnn
     * 
     * The hours are added only if the duration is at least one hour.
     * 
     * The finest parameter specifies the fines displayed time unit.
     */
    public static String durationAsString(long durationInNanos, TimeUnit finest) {
        long nanos = durationInNanos % 1000000L;
        durationInNanos /= 1000000L;
        long millis = durationInNanos % 1000L;
        durationInNanos /= 1000L;
        long seconds = durationInNanos % 60L;
        durationInNanos /= 60L;
        long minutes = durationInNanos % 60L;
        durationInNanos /= 60L;
        long hours = durationInNanos % 24L;
        durationInNanos /= 24L;
        long days = durationInNanos;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(':');
            appendNumber(sb, hours, 2).append(':');
        } else if (hours > 0) {
            appendNumber(sb, hours, 2).append(':');
        }
        appendNumber(sb, minutes, 2);
        if (finest.compareTo(MINUTES) < 0) {
            appendNumber(sb.append(':'), seconds, 2);
            if (finest.compareTo(SECONDS) < 0) {
                appendNumber(sb.append(':'), millis, 3);
                if (nanos > 0 && finest == NANOSECONDS) {
                    appendNumber(sb.append('.'), nanos, 6);
                }
            }
        }

        return sb.toString();
    }

    private static final long[] DIGI;

    static {
        int n = String.valueOf(Long.MAX_VALUE).length();
        DIGI = new long[n];
        long exp = 1L;

        for (int i = 1; i < n; i++) {
            DIGI[i] = (exp *= 10L);
        }
    }

    /**
     * Prints a fixed number with a given length of digits into a StringBuilder, filled with leading zeros.
     */
    public static StringBuilder appendNumber(StringBuilder sb, long num, int digits) {
        if (num < 0) {
            // Minus sign does not count into digits param
            sb.append('-');
            num *= -1;
        }
        while (digits-- > DIGI.length)
            sb.append('0');
        while (digits > 0) {
            if (num >= DIGI[digits--]) break;
            sb.append('0');
        }
        return sb.append(num);
    }

    /**
     * Execute some code, and print out the needed time.
     */
    public static void runAndPrintTime(Runnable r) {
        long nanos = System.nanoTime();
        r.run();
        long duration = System.nanoTime() - nanos;

        log("Execution took " + durationAsString(duration, TimeUnit.MILLISECONDS));
    }

    /**
     * Returns an indented representation of the given object, containing all internal fields.
     * 
     * @param o The inspected object
     * @return a (possibly multilined) String with the introspection of the object
     */
    public static String fullPrint(Object o) {
        return fullPrint(o, new StringBuilder()).toString();
    }

    /**
     * Returns an indented representation of the given object, containing all internal fields.
     * 
     * @param o The inspected object
     * @param depth The introspection depth
     * @return a (possibly multilined) String with the introspection of the object
     */
    public static String fullPrint(Object o, int depth) {
        return fullPrint(o, new StringBuilder(), depth).toString();
    }

    /**
     * Returns an indented representation of the given object, containing all internal fields.
     * 
     * @param o The inspected object
     * @param depth The introspection depth
     * @param maxLength How many elements of arrays, collections, and maps are shown at max
     * @return a (possibly multilined) String with the introspection of the object
     */
    public static String fullPrint(Object o, int depth, int maxLength) {
        return fullPrint(o, new StringBuilder(), depth, maxLength).toString();
    }

    /**
     * Adds an indented representation of the given object, containing all internal fields, into a {@link StringBuilder}
     * 
     * @param o The inspected object
     * @param sb Where to add
     * @return The given StringBuilder instance itself
     */
    public static StringBuilder fullPrint(Object o, StringBuilder sb) {
        return fullPrint(o, sb, Integer.MAX_VALUE);
    }

    /**
     * Adds an indented representation of the given object, containing all internal fields, into a {@link StringBuilder}
     * 
     * @param o The inspected object
     * @param sb Where to add
     * @param depth The introspection depth
     * @return The given StringBuilder instance itself
     */
    public static StringBuilder fullPrint(Object o, StringBuilder sb, int depth) {
        return fullPrint(o, sb, depth, 20);
    }

    /**
     * Adds an indented representation of the given object, containing all internal fields, into a {@link StringBuilder}
     * 
     * @param o The inspected object
     * @param sb Where to add
     * @param depth The introspection depth
     * @param maxLength How many elements of arrays, collections, and maps are shown at max
     * @return The given StringBuilder instance itself
     */
    public static StringBuilder fullPrint(Object o, StringBuilder sb, int depth, int maxLength) {
        return append(sb, o, new HashSet<>(), depth, 0, maxLength);
    }

    private static final int INVISIBLE_MODIFIERS = Modifier.STATIC | Modifier.TRANSIENT;

    private static StringBuilder append(StringBuilder sb, Object x, Set<Object> alreadyVisited, int depth, int indent,
        int maxLength) {
        if (x == null) {
            return sb.append("<null>");
        }
        try {
            if (x instanceof Date) {
                return sb.append(DateFormat.getDateTimeInstance().format((Date) x));
            }
            if (x instanceof Collection) {
                int n = ((Collection<?>) x).size();
                sb.append('<').append(n).append(">[");
                int count = 0;
                boolean needsComma = false;
                for (Object x2 : (Collection<?>) x) {
                    if (++count > maxLength) {
                        sb.append("... (").append(n - maxLength).append(" more)");
                        break;
                    }
                    if (needsComma) sb.append(',');
                    else
                        needsComma = true;
                    append(sb, x2, alreadyVisited, depth, indent + 1, maxLength);
                }
                return sb.append(']');
            }
            if (x instanceof Map) {
                int n = ((Map<?, ?>) x).size();
                sb.append('<').append(n).append(">{");
                boolean needsComma = false;
                int count = 0;
                for (Map.Entry<?, ?> e : ((Map<?, ?>) x).entrySet()) {
                    if (++count > maxLength) {
                        sb.append("... (").append(n - maxLength).append(" more)");
                        break;
                    }
                    if (needsComma) sb.append(',');
                    else
                        needsComma = true;
                    append(sb.append(e.getKey()).append('='), e.getValue(), alreadyVisited, depth, indent + 1,
                        maxLength);
                }
                return sb.append('}');
            }
            if (x.getClass().isArray()) {
                int n = Array.getLength(x);
                sb.append('<').append(n).append(">[");
                boolean needsComma = false;

                for (int i = 0; i < n; i++) {
                    if (i > maxLength) {
                        sb.append("... (").append(n - maxLength).append(" more)");
                        break;
                    }
                    if (needsComma) sb.append(',');
                    else
                        needsComma = true;
                    if (x instanceof Object[]) {
                        append(sb, Array.get(x, i), alreadyVisited, depth, indent + 1, maxLength);
                    } else {
                        sb.append(Array.get(x, i));
                    }
                }
                return sb.append(']');
            } else if (x instanceof String) {
                return sb.append('"').append(x).append('"');
            } else if (x instanceof Class) {
                Class<?> c = (Class<?>) x;
                if (c.isArray()) {
                    return append(sb, c.getComponentType(), alreadyVisited, depth, indent, maxLength).append("[]");
                }
                return sb.append(c.getName());
            } else if (depth > 0) {
                // Normal java objects
                if (x instanceof Enum) {
                    return sb.append(((Enum<?>) x).getDeclaringClass().getSimpleName()).append('.')
                            .append(x.toString());
                }
                if (x.getClass().getPackage().getName().startsWith("java")) {
                    return sb.append('(').append(x.getClass().getSimpleName()).append(')').append(x.toString());
                }
                if (!alreadyVisited.add(x)) {
                    return sb.append("<link to ").append(x.getClass().getName()).append('>');
                }
                Class<?> c = x.getClass();
                sb.append(c.getName()).append(": ");
                do {
                    for (Field f : c.getDeclaredFields()) {
                        Debugged debugState = f.getAnnotation(Debugged.class);
                        int m = f.getModifiers();
                        if (debugState == null) {
                            if ((m & INVISIBLE_MODIFIERS) != 0) continue;
                        } else if (!debugState.value()) {
                            continue;
                        }
                        try {
                            f.setAccessible(true);
                            Object x2 = f.get(x);
                            if (x2 != null || debugState != null && debugState.printNull()) {
                                indent(sb, indent + 1);
                                if ((m & Modifier.TRANSIENT) != 0) {
                                    sb.append('~');
                                }
                                if ((m & Modifier.VOLATILE) != 0) {
                                    sb.append('^');
                                }
                                if ((m & Modifier.FINAL) != 0) {
                                    sb.append('!');
                                }
                                sb.append(f.getName()).append('=');
                                if (f.getType().isPrimitive()) {
                                    sb.append(x2).append(';');
                                } else {
                                    append(sb, x2, alreadyVisited, depth - 1, indent + 1, maxLength).append(';');
                                }
                            }
                        } catch (IllegalAccessException ex) {
                            ex.printStackTrace();
                        }
                    }
                    c = c.getSuperclass();
                } while (c != null);
                return indent(sb, indent);
            } else {
                return sb.append('(').append(x.getClass().getSimpleName()).append(')').append(x.toString());
            }
        } catch (Throwable ex) {
            return sb.append("<<").append(ex.getMessage()).append(">>");
        }
    }

    private static StringBuilder indent(StringBuilder sb, int tabs) {
        sb.append('\n');
        for (int i = tabs; i > 0; i--) {
            sb.append("  ");
        }
        return sb;
    }
}
