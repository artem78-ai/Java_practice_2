package ufcdb.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Таблица в памяти: схема, список записей и справочник первичных ключей
 * для быстрой проверки уникальности.
 */
public class Table {
    private final Schema schema;
    private final List<Row> rows = new ArrayList<>();
    private final Map<Object, Row> pkMap = new HashMap<>();

    /**
     * @param schema схема создаваемой таблицы
     */
    public Table(Schema schema) {
        this.schema = schema;
    }

    public Schema getSchema() {
        return schema;
    }

    /** @return записи таблицы (только для чтения) */
    public List<Row> getRows() {
        return Collections.unmodifiableList(rows);
    }

    public int size() {
        return rows.size();
    }

    /**
     * Проверяет, есть ли запись с таким значением первичного ключа.
     *
     * @param key значение ключа
     * @return true, если запись существует
     */
    public boolean containsKey(Object key) {
        return pkMap.containsKey(key);
    }

    /**
     * Вставляет одну запись.
     *
     * @param row новая запись
     * @throws DbException если типы не совпадают или первичный ключ не уникален
     */
    public void insert(Row row) {
        insertAll(Collections.singletonList(row));
    }

    /**
     * Вставляет несколько записей. Операция атомарна: если хотя бы одна запись
     * некорректна, не будет вставлена ни одна.
     *
     * @param newRows записи для вставки
     * @throws DbException при неверных типах, пустом или повторяющемся ключе
     */
    public void insertAll(List<Row> newRows) {
        int pk = schema.primaryKeyIndex();
        Set<Object> batchKeys = new HashSet<>();
        for (Row row : newRows) {
            validate(row);
            Object key = row.get(pk);
            if (key == null) {
                throw new DbException("Первичный ключ не может быть пустым");
            }
            if (pkMap.containsKey(key) || !batchKeys.add(key)) {
                throw new DbException("Нарушение уникальности первичного ключа: " + key);
            }
        }
        for (Row row : newRows) {
            rows.add(row);
            pkMap.put(row.get(pk), row);
        }
    }

    private void validate(Row row) {
        List<Column> columns = schema.getColumns();
        if (row.size() != columns.size()) {
            throw new DbException("Ожидалось значений: " + columns.size() + ", получено: " + row.size());
        }
        for (int i = 0; i < columns.size(); i++) {
            Column c = columns.get(i);
            if (!c.getType().accepts(row.get(i))) {
                throw new DbException("Столбец '" + c.getName() + "' имеет тип " + c.getType()
                        + ", а значение: " + row.get(i));
            }
        }
    }
}
