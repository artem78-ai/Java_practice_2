package ufcdb.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Схема таблицы: имя и упорядоченный список столбцов. */
public class Schema {
    private final String tableName;
    private final List<Column> columns;

    public Schema(String tableName, List<Column> columns) {
        this.tableName = tableName;
        this.columns = new ArrayList<>(columns);
        long pkCount = columns.stream().filter(Column::isPrimaryKey).count();
        if (pkCount != 1) {
            throw new IllegalArgumentException("В таблице должен быть ровно один первичный ключ");
        }
    }

    public String getTableName() { return tableName; }
    public List<Column> getColumns() { return Collections.unmodifiableList(columns); }

    /**
     * Ищет столбец по имени без учёта регистра.
     *
     * @param columnName имя столбца
     * @return позиция столбца в таблице
     */
    public int indexOf(String columnName) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).getName().equalsIgnoreCase(columnName)) return i;
        }
        throw new IllegalArgumentException("Нет столбца: " + columnName);
    }

    /** @return позиция столбца первичного ключа */
    public int primaryKeyIndex() {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).isPrimaryKey()) return i;
        }
        throw new IllegalStateException();
    }
}
