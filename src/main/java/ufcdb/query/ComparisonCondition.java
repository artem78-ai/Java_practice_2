package ufcdb.query;

import ufcdb.model.ColumnType;
import ufcdb.model.DbException;
import ufcdb.model.Row;
import ufcdb.model.Schema;

import java.util.function.Predicate;

/** Условие вида {@code столбец оператор значение}, например {@code weight > 70}. */
public class ComparisonCondition implements Condition {
    private final String column;
    private final Operator operator;
    private final Object value;

    /**
     * @param column   имя столбца
     * @param operator оператор сравнения
     * @param value    значение, с которым сравнивается столбец (не null)
     */
    public ComparisonCondition(String column, Operator operator, Object value) {
        this.column = column;
        this.operator = operator;
        this.value = value;
    }

    public String getColumn() {
        return column;
    }

    public Operator getOperator() {
        return operator;
    }

    public Object getValue() {
        return value;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Predicate<Row> bind(Schema schema) {
        final int idx;
        try {
            idx = schema.indexOf(column);
        } catch (IllegalArgumentException e) {
            throw new DbException(e.getMessage());
        }
        ColumnType type = schema.getColumns().get(idx).getType();
        if (value == null || !type.accepts(value)) {
            throw new DbException("Значение '" + value + "' не подходит для столбца '"
                    + column + "' типа " + type);
        }
        return row -> {
            Object actual = row.get(idx);
            if (actual == null) return false;
            return operator.matches(((Comparable) actual).compareTo(value));
        };
    }
}
