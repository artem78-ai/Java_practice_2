package ufcdb.model;

import java.time.LocalDate;

/**
 * Поддерживаемые типы данных столбцов.
 */
public enum ColumnType {
    INT, DOUBLE, STRING, DATE, BOOLEAN;

    /**
     * Преобразует строковый литерал в значение данного типа.
     *
     * @param literal текст значения без кавычек
     * @return объект Integer, Double, String, LocalDate или Boolean
     * @throws IllegalArgumentException если литерал не соответствует типу
     */
    public Object parse(String literal) {
        try {
            switch (this) {
                case INT:     return Integer.parseInt(literal.trim());
                case DOUBLE:  return Double.parseDouble(literal.trim());
                case STRING:  return literal;
                case DATE:    return LocalDate.parse(literal.trim());
                case BOOLEAN:
                    String s = literal.trim().toLowerCase();
                    if (!s.equals("true") && !s.equals("false")) {
                        throw new IllegalArgumentException("Не boolean: " + literal);
                    }
                    return Boolean.parseBoolean(s);
                default: throw new IllegalStateException();
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                "Значение '" + literal + "' не подходит для типа " + this, e);
        }
    }

    /**
     * Проверяет, что Java-объект соответствует типу столбца.
     *
     * @param value проверяемое значение (null допускается)
     * @return true, если значение подходит
     */
    public boolean accepts(Object value) {
        if (value == null) return true;
        switch (this) {
            case INT:     return value instanceof Integer;
            case DOUBLE:  return value instanceof Double;
            case STRING:  return value instanceof String;
            case DATE:    return value instanceof LocalDate;
            case BOOLEAN: return value instanceof Boolean;
            default:      return false;
        }
    }
}
