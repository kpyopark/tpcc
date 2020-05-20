package com.codefutures.tpcc.load;

import java.util.Arrays;

/**
 * Simple object to represent a single row of data being loaded to the database (or written to a CSV file).
 */
public class Record {

    /**
     * Column values.
     */
    private final Object field[];

    /**
     * Index of next column to write value to.
     */
    private int index;

    public Record(int columnCount) {
        this.field = new Object[columnCount];
    }

    public void reset() {
        index = 0;
    }

    public void add(Object value) {
        field[index++] = value;
    }

    public Object getField(int i) {
        return field[i];
    }

    public Object[] getField() {
        return field;
    }

    public int getColumnCount() {
        return field.length;
    }

    @Override
    public String toString() {
        return Arrays.toString(field);
    }
}
