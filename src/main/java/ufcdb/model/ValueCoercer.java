package ufcdb.model;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Приводит литералы из SQL-запроса (Integer, Double, String, Boolean, null)
 * к типу конкретного столбца.
 */
public final class ValueCoercer {
    private ValueCoercer() {
    }

    /**
     * Приводит значение к типу столбца.
     *
     * @param type    тип столбца
     * @param literal значение из запроса (может быть null)
     * @return значение нужного типа
     * @throws DbException если значение нельзя привести к типу
     */
    public static Object coerce(ColumnType type, Object literal) {
        if (literal == null) {
            return null;
        }
        switch (type) {
            case INT:
                if (literal instanceof Integer) return literal;
                break;
            case DOUBLE:
                if (literal instanceof Double) return literal;
                if (literal instanceof Integer) return ((Integer) literal).doubleValue();
                break;
            case STRING:
                if (literal instanceof String) return literal;
                break;
            case DATE:
                if (literal instanceof LocalDate) return literal;
                if (literal instanceof String) {
                    try {
                        return LocalDate.parse((String) literal);
                    } catch (DateTimeParseException e) {
                        throw new DbException("Неверная дата '" + literal + "', нужен формат ГГГГ-ММ-ДД");
                    }
                }
                break;
            case BOOLEAN:
                if (literal instanceof Boolean) return literal;
                break;
            default:
                break;
        }
        throw new DbException("Значение '" + literal + "' не подходит для типа " + type);
    }
}
