package ufcdb.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Разобранный SQL-запрос. Каждая реализация описывает одну команду;
 * выполняет их класс Database.
 */
public interface Statement {

    /** INSERT INTO таблица [(столбцы)] VALUES (...), (...). */
    class Insert implements Statement {
        private final String table;
        private final List<String> columns;
        private final List<List<Object>> rows;

        /**
         * @param table   имя таблицы
         * @param columns список столбцов или null, если столбцы не указаны
         * @param rows    вставляемые строки значений
         */
        public Insert(String table, List<String> columns, List<List<Object>> rows) {
            this.table = table;
            this.columns = columns;
            this.rows = rows;
        }

        public String getTable() { return table; }
        /** @return список столбцов или null (значения идут в порядке столбцов таблицы) */
        public List<String> getColumns() { return columns; }
        public List<List<Object>> getRows() { return rows; }

        @Override
        public String toString() {
            return "Insert{table=" + table + ", columns=" + columns + ", rows=" + rows + "}";
        }
    }

    /** SELECT столбцы FROM таблица [WHERE условие]. */
    class Select implements Statement {
        private final String table;
        private final List<String> columns;
        private final Condition where;

        /**
         * @param table   имя таблицы
         * @param columns выбираемые столбцы или null для {@code *}
         * @param where   условие (Condition.ALL, если WHERE нет)
         */
        public Select(String table, List<String> columns, Condition where) {
            this.table = table;
            this.columns = columns;
            this.where = where;
        }

        public String getTable() { return table; }
        /** @return выбираемые столбцы или null, если запрошены все ({@code *}) */
        public List<String> getColumns() { return columns; }
        public Condition getWhere() { return where; }

        @Override
        public String toString() {
            return "Select{table=" + table + ", columns=" + columns
                    + ", hasWhere=" + (where != Condition.ALL) + "}";
        }
    }

    /** UPDATE таблица SET столбец = значение, ... [WHERE условие]. */
    class Update implements Statement {
        private final String table;
        private final Map<String, Object> assignments;
        private final Condition where;

        /**
         * @param table       имя таблицы
         * @param assignments новые значения: столбец -> значение
         * @param where       условие (Condition.ALL, если WHERE нет)
         */
        public Update(String table, Map<String, Object> assignments, Condition where) {
            this.table = table;
            this.assignments = new LinkedHashMap<>(assignments);
            this.where = where;
        }

        public String getTable() { return table; }
        public Map<String, Object> getAssignments() { return assignments; }
        public Condition getWhere() { return where; }

        @Override
        public String toString() {
            return "Update{table=" + table + ", assignments=" + assignments
                    + ", hasWhere=" + (where != Condition.ALL) + "}";
        }
    }

    /** DELETE FROM таблица [WHERE условие]. */
    class Delete implements Statement {
        private final String table;
        private final Condition where;

        /**
         * @param table имя таблицы
         * @param where условие (Condition.ALL, если WHERE нет)
         */
        public Delete(String table, Condition where) {
            this.table = table;
            this.where = where;
        }

        public String getTable() { return table; }
        public Condition getWhere() { return where; }

        @Override
        public String toString() {
            return "Delete{table=" + table + ", hasWhere=" + (where != Condition.ALL) + "}";
        }
    }

    /** CREATE TABLE таблица (столбец ТИП [PRIMARY KEY], ...). */
    class CreateTable implements Statement {
        private final String table;
        private final List<ufcdb.model.Column> columns;

        /**
         * @param table   имя новой таблицы
         * @param columns описание столбцов
         */
        public CreateTable(String table, List<ufcdb.model.Column> columns) {
            this.table = table;
            this.columns = new ArrayList<>(columns);
        }

        public String getTable() { return table; }
        public List<ufcdb.model.Column> getColumns() { return columns; }

        @Override
        public String toString() {
            return "CreateTable{table=" + table + ", columns=" + columns.size() + "}";
        }
    }

    /** DROP TABLE таблица. */
    class DropTable implements Statement {
        private final String table;

        /**
         * @param table имя удаляемой таблицы
         */
        public DropTable(String table) {
            this.table = table;
        }

        public String getTable() { return table; }

        @Override
        public String toString() {
            return "DropTable{table=" + table + "}";
        }
    }

    /** CREATE INDEX имя ON таблица (столбец). */
    class CreateIndex implements Statement {
        private final String name;
        private final String table;
        private final String column;

        /**
         * @param name   имя индекса
         * @param table  имя таблицы
         * @param column имя индексируемого столбца
         */
        public CreateIndex(String name, String table, String column) {
            this.name = name;
            this.table = table;
            this.column = column;
        }

        public String getName() { return name; }
        public String getTable() { return table; }
        public String getColumn() { return column; }

        @Override
        public String toString() {
            return "CreateIndex{name=" + name + ", table=" + table + ", column=" + column + "}";
        }
    }

    /** DROP INDEX имя. */
    class DropIndex implements Statement {
        private final String name;

        /**
         * @param name имя удаляемого индекса
         */
        public DropIndex(String name) {
            this.name = name;
        }

        public String getName() { return name; }

        @Override
        public String toString() {
            return "DropIndex{name=" + name + "}";
        }
    }
}
