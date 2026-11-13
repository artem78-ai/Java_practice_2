package ufcdb.engine;

import ufcdb.model.Column;
import ufcdb.model.DbException;
import ufcdb.model.Row;
import ufcdb.model.Schema;
import ufcdb.model.Table;
import ufcdb.model.ValueCoercer;
import ufcdb.query.SqlParser;
import ufcdb.query.Statement;
import ufcdb.storage.TableRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Ядро СУБД: хранит таблицы, выполняет разобранные команды и сохраняет
 * изменения на диск через {@link TableRepository}.
 */
public class Database {
    private final TableRepository repository;
    private final Map<String, Table> tables;

    /**
     * Создаёт базу и загружает все таблицы с диска.
     *
     * @param repository репозиторий файлов данных
     */
    public Database(TableRepository repository) {
        this.repository = repository;
        this.tables = repository.loadAll();
    }

    /** @return true, если в базе нет ни одной таблицы */
    public boolean isEmpty() {
        return tables.isEmpty();
    }

    /** @return имена всех таблиц */
    public List<String> tableNames() {
        List<String> names = new ArrayList<>();
        for (Table t : tables.values()) {
            names.add(t.getSchema().getTableName());
        }
        return names;
    }

    /**
     * Находит таблицу по имени (без учёта регистра).
     *
     * @param name имя таблицы
     * @return таблица
     * @throws DbException если таблицы нет
     */
    public Table getTable(String name) {
        Table t = tables.get(name.toLowerCase());
        if (t == null) {
            throw new DbException("Нет таблицы: " + name);
        }
        return t;
    }

    /**
     * Добавляет новую таблицу в базу и сохраняет её на диск.
     *
     * @param table новая таблица
     * @throws DbException если таблица с таким именем уже есть
     */
    public void addTable(Table table) {
        String key = table.getSchema().getTableName().toLowerCase();
        if (tables.containsKey(key)) {
            throw new DbException("Таблица уже существует: " + table.getSchema().getTableName());
        }
        tables.put(key, table);
        repository.save(table);
    }

    /**
     * Разбирает и выполняет SQL-запрос.
     *
     * @param sql текст запроса
     * @return результат выполнения
     * @throws DbException при синтаксической или логической ошибке
     */
    public QueryResult execute(String sql) {
        Statement st = SqlParser.parse(sql);
        if (st instanceof Statement.Insert) {
            return executeInsert((Statement.Insert) st);
        } else if (st instanceof Statement.Select) {
            return executeSelect((Statement.Select) st);
        } else if (st instanceof Statement.Update) {
            return executeUpdate((Statement.Update) st);
        } else if (st instanceof Statement.Delete) {
            return executeDelete((Statement.Delete) st);
        }
        throw new DbException("Команда не поддерживается");
    }

    private QueryResult executeInsert(Statement.Insert ins) {
        Table table = getTable(ins.getTable());
        Schema schema = table.getSchema();
        List<Column> cols = schema.getColumns();
        List<Row> newRows = new ArrayList<>();
        for (List<Object> values : ins.getRows()) {
            Object[] arr = new Object[cols.size()];
            if (ins.getColumns() == null) {
                if (values.size() != cols.size()) {
                    throw new DbException("Ожидалось значений: " + cols.size()
                            + ", получено: " + values.size());
                }
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = ValueCoercer.coerce(cols.get(i).getType(), values.get(i));
                }
            } else {
                for (int j = 0; j < values.size(); j++) {
                    int idx = columnIndex(schema, ins.getColumns().get(j));
                    arr[idx] = ValueCoercer.coerce(cols.get(idx).getType(), values.get(j));
                }
            }
            newRows.add(new Row(arr));
        }
        table.insertAll(newRows);
        repository.save(table);
        return QueryResult.ofMessage("Вставлено записей: " + newRows.size());
    }

    private QueryResult executeSelect(Statement.Select sel) {
        Table table = getTable(sel.getTable());
        Schema schema = table.getSchema();
        List<Column> cols = schema.getColumns();
        List<Integer> indexes = new ArrayList<>();
        if (sel.getColumns() == null) {
            for (int i = 0; i < cols.size(); i++) {
                indexes.add(i);
            }
        } else {
            for (String name : sel.getColumns()) {
                indexes.add(columnIndex(schema, name));
            }
        }
        List<String> header = new ArrayList<>();
        for (int idx : indexes) {
            header.add(cols.get(idx).getName());
        }
        Predicate<Row> filter = sel.getWhere().bind(schema);
        List<Object[]> result = new ArrayList<>();
        for (Row row : table.find(filter)) {
            Object[] out = new Object[indexes.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = row.get(indexes.get(i));
            }
            result.add(out);
        }
        return QueryResult.ofRows(header, result);
    }

    private QueryResult executeUpdate(Statement.Update upd) {
        Table table = getTable(upd.getTable());
        Schema schema = table.getSchema();
        Map<Integer, Object> changes = new HashMap<>();
        for (Map.Entry<String, Object> e : upd.getAssignments().entrySet()) {
            int idx = columnIndex(schema, e.getKey());
            changes.put(idx, ValueCoercer.coerce(schema.getColumns().get(idx).getType(), e.getValue()));
        }
        int count = table.update(upd.getWhere().bind(schema), changes);
        if (count > 0) {
            repository.save(table);
        }
        return QueryResult.ofMessage("Обновлено записей: " + count);
    }

    private QueryResult executeDelete(Statement.Delete del) {
        Table table = getTable(del.getTable());
        int count = table.delete(del.getWhere().bind(table.getSchema()));
        if (count > 0) {
            repository.save(table);
        }
        return QueryResult.ofMessage("Удалено записей: " + count);
    }

    private int columnIndex(Schema schema, String name) {
        try {
            return schema.indexOf(name);
        } catch (IllegalArgumentException e) {
            throw new DbException(e.getMessage());
        }
    }
}
