package ufcdb.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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

    /**
     * Находит записи, удовлетворяющие условию.
     *
     * @param filter условие отбора
     * @return список подходящих записей
     */
    public List<Row> find(Predicate<Row> filter) {
        List<Row> result = new ArrayList<>();
        for (Row row : rows) {
            if (filter.test(row)) {
                result.add(row);
            }
        }
        return result;
    }

    /**
     * Обновляет записи, удовлетворяющие условию. Операция атомарна: сначала
     * проверяются все изменения, и только потом применяются.
     *
     * @param filter  условие отбора записей
     * @param changes новые значения: номер столбца -> значение
     * @return количество обновлённых записей
     * @throws DbException при неверном типе значения или нарушении уникальности ключа
     */
    public int update(Predicate<Row> filter, Map<Integer, Object> changes) {
        int pk = schema.primaryKeyIndex();
        List<Column> columns = schema.getColumns();
        for (Map.Entry<Integer, Object> e : changes.entrySet()) {
            Column c = columns.get(e.getKey());
            if (!c.getType().accepts(e.getValue())) {
                throw new DbException("Столбец '" + c.getName() + "' имеет тип " + c.getType()
                        + ", а значение: " + e.getValue());
            }
        }
        List<Row> matched = find(filter);
        if (matched.isEmpty()) {
            return 0;
        }
        boolean pkChanges = changes.containsKey(pk);
        Object newKey = null;
        if (pkChanges) {
            newKey = changes.get(pk);
            if (newKey == null) {
                throw new DbException("Первичный ключ не может быть пустым");
            }
            if (matched.size() > 1) {
                throw new DbException("Нельзя присвоить один первичный ключ нескольким записям");
            }
            Row owner = pkMap.get(newKey);
            if (owner != null && owner != matched.get(0)) {
                throw new DbException("Нарушение уникальности первичного ключа: " + newKey);
            }
        }
        for (Row row : matched) {
            if (pkChanges) {
                pkMap.remove(row.get(pk));
            }
            for (Map.Entry<Integer, Object> e : changes.entrySet()) {
                row.set(e.getKey(), e.getValue());
            }
            if (pkChanges) {
                pkMap.put(newKey, row);
            }
        }
        return matched.size();
    }

    /**
     * Удаляет записи, удовлетворяющие условию.
     *
     * @param filter условие отбора записей
     * @return количество удалённых записей
     */
    public int delete(Predicate<Row> filter) {
        int pk = schema.primaryKeyIndex();
        int count = 0;
        Iterator<Row> it = rows.iterator();
        while (it.hasNext()) {
            Row row = it.next();
            if (filter.test(row)) {
                pkMap.remove(row.get(pk));
                it.remove();
                count++;
            }
        }
        return count;
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
