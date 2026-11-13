package ufcdb.model;

/** Описание одного столбца таблицы. */
public class Column {
    private final String name;
    private final ColumnType type;
    private final boolean primaryKey;

    public Column(String name, ColumnType type, boolean primaryKey) {
        this.name = name;
        this.type = type;
        this.primaryKey = primaryKey;
    }

    public String getName() { return name; }
    public ColumnType getType() { return type; }
    public boolean isPrimaryKey() { return primaryKey; }
}
