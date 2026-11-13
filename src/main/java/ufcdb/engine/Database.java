package ufcdb.engine;

import ufcdb.index.Index;
import ufcdb.model.Column;
import ufcdb.model.DbException;
import ufcdb.model.Row;
import ufcdb.model.Schema;
import ufcdb.model.Table;
import ufcdb.model.ValueCoercer;
import ufcdb.query.AndCondition;
import ufcdb.query.ComparisonCondition;
import ufcdb.query.Condition;
import ufcdb.query.Operator;
import ufcdb.query.SqlParser;
import ufcdb.query.Statement;
import ufcdb.storage.IndexRepository;
import ufcdb.storage.TableRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Ядро СУБД: хранит таблицы, выполняет разобранные команды и сохраняет
 * изменения на диск через {@link TableRepository}.
 */
public class Database {
    private final TableRepository repository;
    private final Map<String, Table> tables;
    private final IndexRepository indexRepository;
    private final Map<String, Index> indexes;
    private String lastUsedIndex;

    /**
     * Создаёт базу и загружает все таблицы с диска.
     *
     * @param repository      репозиторий файлов таблиц
     * @param indexRepository репозиторий файлов индексов
     */
    public Database(TableRepository repository, IndexRepository indexRepository) {
        this.repository = repository;
        this.indexRepository = indexRepository;
        this.tables = repository.loadAll();
        this.indexes = indexRepository.loadAll(tables);
        validateIndexes();
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
        } else if (st instanceof Statement.CreateTable) {
            return executeCreate((Statement.CreateTable) st);
        } else if (st instanceof Statement.DropTable) {
            return executeDrop((Statement.DropTable) st);
        } else if (st instanceof Statement.CreateIndex) {
            return executeCreateIndex((Statement.CreateIndex) st);
        } else if (st instanceof Statement.DropIndex) {
            return executeDropIndex((Statement.DropIndex) st);
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
        checkForeignKeys(table, newRows);
        table.insertAll(newRows);
        repository.save(table);
        refreshIndexes(table);
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
        for (Row row : selectRows(table, sel.getWhere(), filter)) {
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
        Predicate<Row> filter = upd.getWhere().bind(schema);
        checkUpdateForeignKeys(table, filter, changes);
        int count = table.update(filter, changes);
        if (count > 0) {
            repository.save(table);
            refreshIndexes(table);
        }
        return QueryResult.ofMessage("Обновлено записей: " + count);
    }

    private QueryResult executeDelete(Statement.Delete del) {
        Table table = getTable(del.getTable());
        Predicate<Row> filter = del.getWhere().bind(table.getSchema());
        checkNotReferenced(table, table.find(filter));
        int count = table.delete(filter);
        if (count > 0) {
            repository.save(table);
            refreshIndexes(table);
        }
        return QueryResult.ofMessage("Удалено записей: " + count);
    }

    private QueryResult executeCreate(Statement.CreateTable cr) {
        Schema schema;
        try {
            schema = new Schema(cr.getTable(), cr.getColumns());
        } catch (IllegalArgumentException e) {
            throw new DbException(e.getMessage());
        }
        checkReferences(schema);
        addTable(new Table(schema));
        return QueryResult.ofMessage("Таблица создана: " + cr.getTable());
    }

    private QueryResult executeDrop(Statement.DropTable dr) {
        Table table = getTable(dr.getTable());
        String name = table.getSchema().getTableName();
        for (Table other : tables.values()) {
            if (other == table) {
                continue;
            }
            for (Column c : other.getSchema().getColumns()) {
                if (c.isForeignKey() && c.getRefTable().equalsIgnoreCase(name)) {
                    throw new DbException("Нельзя удалить таблицу " + name
                            + ": на неё ссылается таблица " + other.getSchema().getTableName());
                }
            }
        }
        tables.remove(name.toLowerCase());
        repository.delete(name);
        dropIndexesOf(name);
        return QueryResult.ofMessage("Таблица удалена: " + name);
    }

    /** Проверяет, что внешние ключи новой таблицы указывают на существующие первичные ключи. */
    private void checkReferences(Schema schema) {
        for (Column c : schema.getColumns()) {
            if (!c.isForeignKey()) {
                continue;
            }
            if (c.getRefTable().equalsIgnoreCase(schema.getTableName())) {
                throw new DbException("Ссылка таблицы на саму себя не поддерживается");
            }
            Table parent = getTable(c.getRefTable());
            Schema ps = parent.getSchema();
            int refIdx = columnIndex(ps, c.getRefColumn());
            if (refIdx != ps.primaryKeyIndex()) {
                throw new DbException("Внешний ключ должен ссылаться на первичный ключ таблицы "
                        + ps.getTableName());
            }
            if (ps.getColumns().get(refIdx).getType() != c.getType()) {
                throw new DbException("Тип столбца '" + c.getName() + "' не совпадает с типом "
                        + ps.getTableName() + "." + c.getRefColumn());
            }
        }
    }

    /** Проверяет, что вставляемые записи ссылаются на существующие записи родительских таблиц. */
    private void checkForeignKeys(Table table, List<Row> rows) {
        List<Column> cols = table.getSchema().getColumns();
        for (int i = 0; i < cols.size(); i++) {
            Column c = cols.get(i);
            if (!c.isForeignKey()) {
                continue;
            }
            Table parent = getTable(c.getRefTable());
            for (Row row : rows) {
                checkParentExists(c, parent, row.get(i));
            }
        }
    }

    private void checkParentExists(Column c, Table parent, Object value) {
        if (value != null && !parent.containsKey(value)) {
            throw new DbException("Нарушение внешнего ключа: в таблице " + c.getRefTable()
                    + " нет записи с " + c.getRefColumn() + " = " + value);
        }
    }

    /** Проверяет ограничения перед UPDATE: новые значения внешних ключей и смена первичного ключа. */
    private void checkUpdateForeignKeys(Table table, Predicate<Row> filter, Map<Integer, Object> changes) {
        Schema schema = table.getSchema();
        for (Map.Entry<Integer, Object> e : changes.entrySet()) {
            Column c = schema.getColumns().get(e.getKey());
            if (c.isForeignKey()) {
                checkParentExists(c, getTable(c.getRefTable()), e.getValue());
            }
        }
        if (changes.containsKey(schema.primaryKeyIndex())) {
            checkNotReferenced(table, table.find(filter));
        }
    }

    /** Запрещает изменять или удалять записи, на которые ссылаются другие таблицы. */
    private void checkNotReferenced(Table table, List<Row> rows) {
        if (rows.isEmpty()) {
            return;
        }
        String name = table.getSchema().getTableName();
        int pk = table.getSchema().primaryKeyIndex();
        Set<Object> keys = new HashSet<>();
        for (Row r : rows) {
            keys.add(r.get(pk));
        }
        for (Table child : tables.values()) {
            if (child == table) {
                continue;
            }
            List<Column> cc = child.getSchema().getColumns();
            for (int i = 0; i < cc.size(); i++) {
                Column c = cc.get(i);
                if (!c.isForeignKey() || !c.getRefTable().equalsIgnoreCase(name)) {
                    continue;
                }
                for (Row r : child.getRows()) {
                    Object v = r.get(i);
                    if (v != null && keys.contains(v)) {
                        throw new DbException("Нельзя изменить или удалить запись " + v
                                + ": на неё ссылается таблица " + child.getSchema().getTableName());
                    }
                }
            }
        }
    }

    private QueryResult executeCreateIndex(Statement.CreateIndex ci) {
        if (indexes.containsKey(ci.getName().toLowerCase())) {
            throw new DbException("Индекс уже существует: " + ci.getName());
        }
        Table table = getTable(ci.getTable());
        Schema schema = table.getSchema();
        int col = columnIndex(schema, ci.getColumn());
        Index index = new Index(ci.getName(), schema.getTableName(), schema.getColumns().get(col).getName());
        index.rebuild(table);
        indexRepository.save(index, table);
        indexes.put(ci.getName().toLowerCase(), index);
        return QueryResult.ofMessage("Индекс создан: " + ci.getName());
    }

    private QueryResult executeDropIndex(Statement.DropIndex di) {
        Index index = indexes.remove(di.getName().toLowerCase());
        if (index == null) {
            throw new DbException("Нет индекса: " + di.getName());
        }
        indexRepository.delete(index.getName());
        return QueryResult.ofMessage("Индекс удалён: " + index.getName());
    }

    /** @return имя индекса, использованного в последнем SELECT, или null */
    public String getLastUsedIndex() {
        return lastUsedIndex;
    }

    /** Проверяет индексы после загрузки: при расхождении с таблицей перестраивает их. */
    private void validateIndexes() {
        for (Index idx : indexes.values()) {
            Table table = tables.get(idx.getTableName().toLowerCase());
            int col = table.getSchema().indexOf(idx.getColumnName());
            int expected = 0;
            for (Row r : table.getRows()) {
                if (r.get(col) != null) {
                    expected++;
                }
            }
            if (idx.size() != expected) {
                idx.rebuild(table);
                indexRepository.save(idx, table);
            }
        }
    }

    /** Перестраивает и сохраняет все индексы таблицы после её изменения. */
    private void refreshIndexes(Table table) {
        String name = table.getSchema().getTableName();
        for (Index idx : indexes.values()) {
            if (idx.getTableName().equalsIgnoreCase(name)) {
                idx.rebuild(table);
                indexRepository.save(idx, table);
            }
        }
    }

    /** Удаляет все индексы таблицы (при DROP TABLE). */
    private void dropIndexesOf(String tableName) {
        Iterator<Index> it = indexes.values().iterator();
        while (it.hasNext()) {
            Index idx = it.next();
            if (idx.getTableName().equalsIgnoreCase(tableName)) {
                it.remove();
                indexRepository.delete(idx.getName());
            }
        }
    }

    private Index findIndex(Table table, String column) {
        String tableName = table.getSchema().getTableName();
        for (Index idx : indexes.values()) {
            if (idx.getTableName().equalsIgnoreCase(tableName)
                    && idx.getColumnName().equalsIgnoreCase(column)) {
                return idx;
            }
        }
        return null;
    }

    private List<ComparisonCondition> comparisons(Condition where) {
        List<ComparisonCondition> list = new ArrayList<>();
        if (where instanceof ComparisonCondition) {
            list.add((ComparisonCondition) where);
        } else if (where instanceof AndCondition) {
            for (Condition c : ((AndCondition) where).getParts()) {
                if (c instanceof ComparisonCondition) {
                    list.add((ComparisonCondition) c);
                }
            }
        }
        return list;
    }

    /**
     * Выбирает записи для SELECT: через индекс, если в условии есть сравнение
     * по индексированному столбцу, иначе полным перебором таблицы.
     */
    private List<Row> selectRows(Table table, Condition where, Predicate<Row> filter) {
        Schema schema = table.getSchema();
        for (ComparisonCondition cc : comparisons(where)) {
            if (cc.getOperator() == Operator.NE) {
                continue;
            }
            Index idx = findIndex(table, cc.getColumn());
            if (idx == null) {
                continue;
            }
            int col = columnIndex(schema, cc.getColumn());
            Object value = ValueCoercer.coerce(schema.getColumns().get(col).getType(), cc.getValue());
            List<Row> result = new ArrayList<>();
            for (Object pk : idx.lookup(cc.getOperator(), value)) {
                Row row = table.findByKey(pk);
                if (row != null && filter.test(row)) {
                    result.add(row);
                }
            }
            lastUsedIndex = idx.getName();
            return result;
        }
        lastUsedIndex = null;
        return table.find(filter);
    }

    private int columnIndex(Schema schema, String name) {
        try {
            return schema.indexOf(name);
        } catch (IllegalArgumentException e) {
            throw new DbException(e.getMessage());
        }
    }
}
