package io.beans.util;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import org.junit.Test;


public class SwissArmyKnifeTest {

    @Test
    public void testPrintNumber() {
        assertEquals("001", num(1, 3));
        assertEquals("01", num(1, 2));
        assertEquals("1", num(1, 1));
        assertEquals("-001", num(-1, 3));
        assertEquals("000", num(0, 3));
        assertEquals("1234", num(1234, 3));
        assertEquals("00000000000000000000000000000000000000000000000001", num(1, 50));
        assertEquals("-00000000000000000000000000000000000000000000000001", num(-1, 50));
        assertEquals("-00008459165634822169946", num(-8459165634822169946L, 23));
        assertEquals("00008459165634822169946", num(8459165634822169946L, 23));
        assertEquals("-8459165634822169946", num(-8459165634822169946L, 2));
        assertEquals("00000000845914822169946", num(845914822169946L, 23));
    }

    private String num(long number, int digits) {
        StringBuilder sb = new StringBuilder();
        SwissArmyKnife.appendNumber(sb, number, digits);
        return sb.toString();
    }

    @Test
    public void typeCheckTest() {
        assertTrue(SwissArmyKnife.isInstance(String.class, "Hallo!"));
        assertTrue(SwissArmyKnife.isInstance(Object.class, "Hallo!"));
        assertFalse(SwissArmyKnife.isInstance(Integer.class, "Hallo!"));
        assertTrue(SwissArmyKnife.isInstance(Integer.class, 15));
        assertTrue(SwissArmyKnife.isInstance(int.class, 15));
        assertFalse(SwissArmyKnife.isInstance(float.class, 15));
        assertTrue(SwissArmyKnife.isInstance(char.class, 'x'));
        assertTrue(SwissArmyKnife.isInstance(Object.class, 'x'));

        assertTrue(SwissArmyKnife.isAssignableFrom(String.class, String.class));
        assertTrue(SwissArmyKnife.isAssignableFrom(Object.class, String.class));
        assertFalse(SwissArmyKnife.isAssignableFrom(Integer.class, String.class));
        assertTrue(SwissArmyKnife.isAssignableFrom(int.class, Integer.class));
        assertFalse(SwissArmyKnife.isAssignableFrom(float.class, Integer.class));
        assertTrue(SwissArmyKnife.isAssignableFrom(char.class, Character.class));
        assertTrue(SwissArmyKnife.isAssignableFrom(Object.class, Character.class));
        assertFalse(SwissArmyKnife.isAssignableFrom(Float.class, float.class));
    }
}
