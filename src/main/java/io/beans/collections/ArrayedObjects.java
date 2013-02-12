/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package io.beans.collections;

/**
 *
 * @author kuli
 */
class ArrayedObjects {

    private final Object[] values;
    private final int bitMask, bitShift;

    ArrayedObjects(int length) {
        assert Integer.bitCount(length) == 1 : "length must be a power of two.";
        values = new Object[length];
        bitMask = length - 1;
        bitShift = Integer.numberOfTrailingZeros(length);
    }

    Object get(int index) {
        return values[index & bitMask];
    }
}
