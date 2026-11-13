package ufcdb.model;

import java.util.Arrays;

/** Одна запись таблицы. */
public class Row {
    private final Object[] values;

    public Row(Object[] values) { this.values = values; }

    public Object get(int i) { return values[i]; }
    public void set(int i, Object v) { values[i] = v; }
    public int size() { return values.length; }
    public Object[] toArray() { return Arrays.copyOf(values, values.length); }
}
