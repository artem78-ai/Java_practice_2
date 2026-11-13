package ufcdb.query;

import ufcdb.model.Column;
import ufcdb.model.ColumnType;
import ufcdb.model.DbException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Рекурсивный парсер SQL-подобного языка. Поддерживает INSERT, SELECT, UPDATE
 * и DELETE; условие WHERE состоит из сравнений, соединённых через AND.
 */
public class SqlParser {
    private final List<Token> tokens;
    private int pos = 0;

    private SqlParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * Разбирает один SQL-запрос.
     *
     * @param sql текст запроса (завершающая точка с запятой необязательна)
     * @return объект-команда
     * @throws DbException при синтаксической ошибке
     */
    public static Statement parse(String sql) {
        List<Token> tokens = Tokenizer.tokenize(sql);
        if (tokens.isEmpty()) {
            throw new DbException("Пустой запрос");
        }
        SqlParser parser = new SqlParser(tokens);
        Statement st = parser.parseStatement();
        parser.acceptSymbol(";");
        if (parser.pos < tokens.size()) {
            throw new DbException("Лишний текст в конце запроса: " + tokens.get(parser.pos).getText());
        }
        return st;
    }

    private Statement parseStatement() {
        Token first = next();
        if (first.getType() == TokenType.WORD) {
            switch (first.getText().toUpperCase()) {
                case "INSERT": return parseInsert();
                case "SELECT": return parseSelect();
                case "UPDATE": return parseUpdate();
                case "DELETE": return parseDelete();
                case "CREATE": return parseCreate();
                case "DROP": return parseDrop();
                default: break;
            }
        }
        throw new DbException("Неизвестная команда: " + first.getText());
    }

    private Statement parseInsert() {
        expectKeyword("INTO");
        String table = expectIdent();
        List<String> columns = null;
        if (acceptSymbol("(")) {
            columns = parseIdentList();
            expectSymbol(")");
        }
        expectKeyword("VALUES");
        List<List<Object>> rows = new ArrayList<>();
        do {
            expectSymbol("(");
            List<Object> values = new ArrayList<>();
            do {
                values.add(parseLiteral());
            } while (acceptSymbol(","));
            expectSymbol(")");
            if (columns != null && columns.size() != values.size()) {
                throw new DbException("Число столбцов (" + columns.size()
                        + ") не равно числу значений (" + values.size() + ")");
            }
            rows.add(values);
        } while (acceptSymbol(","));
        return new Statement.Insert(table, columns, rows);
    }

    private Statement parseSelect() {
        List<String> columns = acceptSymbol("*") ? null : parseIdentList();
        expectKeyword("FROM");
        String table = expectIdent();
        return new Statement.Select(table, columns, parseOptionalWhere());
    }

    private Statement parseUpdate() {
        String table = expectIdent();
        expectKeyword("SET");
        Map<String, Object> assignments = new LinkedHashMap<>();
        do {
            String column = expectIdent();
            expectSymbol("=");
            assignments.put(column, parseLiteral());
        } while (acceptSymbol(","));
        return new Statement.Update(table, assignments, parseOptionalWhere());
    }

    private Statement parseDelete() {
        expectKeyword("FROM");
        String table = expectIdent();
        return new Statement.Delete(table, parseOptionalWhere());
    }

    private Statement parseCreate() {
        if (acceptKeyword("INDEX")) {
            return parseCreateIndex();
        }
        expectKeyword("TABLE");
        String table = expectIdent();
        expectSymbol("(");
        List<Column> columns = new ArrayList<>();
        do {
            String name = expectIdent();
            ColumnType type = parseType(expectIdent());
            boolean pk = false;
            if (acceptKeyword("PRIMARY")) {
                expectKeyword("KEY");
                pk = true;
            }
            String refTable = null;
            String refColumn = null;
            if (acceptKeyword("REFERENCES")) {
                refTable = expectIdent();
                expectSymbol("(");
                refColumn = expectIdent();
                expectSymbol(")");
            }
            columns.add(new Column(name, type, pk, refTable, refColumn));
        } while (acceptSymbol(","));
        expectSymbol(")");
        long pkCount = 0;
        for (Column c : columns) {
            if (c.isPrimaryKey()) pkCount++;
        }
        if (pkCount != 1) {
            throw new DbException("В таблице должен быть ровно один PRIMARY KEY");
        }
        return new Statement.CreateTable(table, columns);
    }

    private Statement parseCreateIndex() {
        String name = expectIdent();
        expectKeyword("ON");
        String table = expectIdent();
        expectSymbol("(");
        String column = expectIdent();
        expectSymbol(")");
        return new Statement.CreateIndex(name, table, column);
    }

    private Statement parseDrop() {
        if (acceptKeyword("INDEX")) {
            return new Statement.DropIndex(expectIdent());
        }
        expectKeyword("TABLE");
        return new Statement.DropTable(expectIdent());
    }

    private ColumnType parseType(String name) {
        switch (name.toUpperCase()) {
            case "INT": case "INTEGER":
                return ColumnType.INT;
            case "DOUBLE": case "FLOAT":
                return ColumnType.DOUBLE;
            case "STRING": case "VARCHAR": case "TEXT":
                return ColumnType.STRING;
            case "DATE":
                return ColumnType.DATE;
            case "BOOLEAN": case "BOOL":
                return ColumnType.BOOLEAN;
            default:
                throw new DbException("Неизвестный тип данных: " + name);
        }
    }

    private Condition parseOptionalWhere() {
        if (!acceptKeyword("WHERE")) {
            return Condition.ALL;
        }
        List<Condition> parts = new ArrayList<>();
        do {
            parts.add(parseComparison());
        } while (acceptKeyword("AND"));
        return parts.size() == 1 ? parts.get(0) : new AndCondition(parts);
    }

    private Condition parseComparison() {
        String column = expectIdent();
        Operator op = Operator.fromSymbol(next().getText());
        Object value = parseLiteral();
        if (value == null) {
            throw new DbException("Сравнение с NULL не поддерживается");
        }
        return new ComparisonCondition(column, op, value);
    }

    private List<String> parseIdentList() {
        List<String> names = new ArrayList<>();
        do {
            names.add(expectIdent());
        } while (acceptSymbol(","));
        return names;
    }

    private Object parseLiteral() {
        Token t = next();
        switch (t.getType()) {
            case NUMBER:
                return parseNumber(t.getText());
            case STRING:
                return t.getText();
            case WORD:
                if (t.isKeyword("TRUE")) return Boolean.TRUE;
                if (t.isKeyword("FALSE")) return Boolean.FALSE;
                if (t.isKeyword("NULL")) return null;
                break;
            default:
                break;
        }
        throw new DbException("Ожидалось значение, найдено: " + t.getText());
    }

    private Object parseNumber(String text) {
        try {
            if (text.contains(".")) {
                return Double.parseDouble(text);
            }
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new DbException("Неверное число: " + text);
        }
    }

    private Token next() {
        if (pos >= tokens.size()) {
            throw new DbException("Неожиданный конец запроса");
        }
        return tokens.get(pos++);
    }

    private Token peek() {
        return pos < tokens.size() ? tokens.get(pos) : null;
    }

    private boolean acceptSymbol(String symbol) {
        Token t = peek();
        if (t != null && t.isSymbol(symbol)) {
            pos++;
            return true;
        }
        return false;
    }

    private boolean acceptKeyword(String keyword) {
        Token t = peek();
        if (t != null && t.isKeyword(keyword)) {
            pos++;
            return true;
        }
        return false;
    }

    private void expectSymbol(String symbol) {
        Token t = next();
        if (!t.isSymbol(symbol)) {
            throw new DbException("Ожидалось '" + symbol + "', найдено: " + t.getText());
        }
    }

    private void expectKeyword(String keyword) {
        Token t = next();
        if (!t.isKeyword(keyword)) {
            throw new DbException("Ожидалось " + keyword + ", найдено: " + t.getText());
        }
    }

    private String expectIdent() {
        Token t = next();
        if (t.getType() != TokenType.WORD) {
            throw new DbException("Ожидалось имя, найдено: " + t.getText());
        }
        return t.getText();
    }
}
