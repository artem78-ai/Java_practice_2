package ufcdb.index;

import ufcdb.model.DbException;
import ufcdb.model.Row;
import ufcdb.model.Table;
import ufcdb.query.Operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Упорядоченный индекс по одному столбцу таблицы: значение столбца ->
 * список первичных ключей записей с этим значением. Значения NULL не индексируются.
 */
public class Index {
    private final String name;
    private final String tableName;
    private final String columnName;
    private final TreeMap<Object, List<Object>> entries = new TreeMap<>();
    private int size;

    /**
     * @param name       имя индекса
     * @param tableName  имя таблицы
     * @param columnName имя индексируемого столбца
     */
    public Index(String name, String tableName, String columnName) {
        this.name = name;
        this.tableName = tableName;
        this.columnName = columnName;
    }

    public String getName() { return name; }
    public String getTableName() { return tableName; }
    public String getColumnName() { return columnName; }

    /** @return общее число записей в индексе */
    public int size() { return size; }

    /** @return содержимое индекса (для сохранения на диск) */
    public NavigableMap<Object, List<Object>> entries() {
        return entries;
    }

    /**
     * Добавляет запись в индекс.
     *
     * @param key значение индексируемого столбца
     * @param pk  первичный ключ записи
     */
    public void add(Object key, Object pk) {
        List<Object> list = entries.get(key);
        if (list == null) {
            list = new ArrayList<>(1);
            entries.put(key, list);
        }
        list.add(pk);
        size++;
    }

    /** Очищает индекс. */
    public void clear() {
        entries.clear();
        size = 0;
    }

    /**
     * Строит индекс заново по всем записям таблицы.
     *
     * @param table таблица, к которой относится индекс
     */
    public void rebuild(Table table) {
        clear();
        int col = table.getSchema().indexOf(columnName);
        int pk = table.getSchema().primaryKeyIndex();
        for (Row row : table.getRows()) {
            Object key = row.get(col);
            if (key != null) {
                add(key, row.get(pk));
            }
        }
    }

    /**
     * Ищет первичные ключи записей, у которых значение столбца
     * удовлетворяет отношению "столбец оператор value".
     *
     * @param op    оператор сравнения (кроме !=)
     * @param value значение, уже приведённое к типу столбца
     * @return первичные ключи подходящих записей в порядке возрастания значения
     * @throws DbException если оператор индексом не поддерживается
     */
    public List<Object> lookup(Operator op, Object value) {
        List<Object> result = new ArrayList<>();
        switch (op) {
            case EQ:
                List<Object> exact = entries.get(value);
                if (exact != null) result.addAll(exact);
                return result;
            case LT:
                collect(entries.headMap(value, false), result);
                return result;
            case LE:
                collect(entries.headMap(value, true), result);
                return result;
            case GT:
                collect(entries.tailMap(value, false), result);
                return result;
            case GE:
                collect(entries.tailMap(value, true), result);
                return result;
            default:
                throw new DbException("Индекс не поддерживает оператор " + op.getSymbol());
        }
    }

    private static void collect(Map<Object, List<Object>> part, List<Object> out) {
        for (List<Object> list : part.values()) {
            out.addAll(list);
        }
    }
}
