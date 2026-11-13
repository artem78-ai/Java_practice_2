package ufcdb.storage;

import ufcdb.model.Column;
import ufcdb.model.ColumnType;
import ufcdb.model.DbException;
import ufcdb.model.Row;
import ufcdb.model.Schema;
import ufcdb.model.Table;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Репозиторий таблиц: сохраняет и загружает таблицы из бинарных файлов на диске.
 * Запись выполняется через временный файл с атомарной заменой, что защищает
 * данные при аварийном завершении программы.
 */
public class TableRepository {
    private static final int MAGIC = 0x55464345; // "UFCE", версия формата с внешними ключами
    private static final String EXT = ".tbl";
    private static final String TMP_EXT = ".tbl.tmp";

    private final Path dir;

    /**
     * @param dir папка для файлов данных (будет создана, если её нет)
     */
    public TableRepository(Path dir) {
        this.dir = dir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new DbException("Не удалось создать папку данных: " + dir, e);
        }
    }

    /**
     * Сохраняет таблицу на диск.
     *
     * @param table таблица для сохранения
     */
    public void save(Table table) {
        String name = table.getSchema().getTableName();
        Path target = dir.resolve(name + EXT);
        Path tmp = dir.resolve(name + TMP_EXT);
        try {
            FileOutputStream fos = new FileOutputStream(tmp.toFile());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(fos))) {
                write(out, table);
                out.flush();
                fos.getFD().sync();
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new DbException("Не удалось сохранить таблицу " + name, e);
        }
    }

    /**
     * Загружает все таблицы из папки данных. Недописанные временные файлы
     * (остатки аварийного завершения) удаляются.
     *
     * @return таблицы по имени (в нижнем регистре)
     */
    public Map<String, Table> loadAll() {
        Map<String, Table> result = new LinkedHashMap<>();
        try {
            try (DirectoryStream<Path> tmps = Files.newDirectoryStream(dir, "*" + TMP_EXT)) {
                for (Path p : tmps) {
                    Files.deleteIfExists(p);
                }
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*" + EXT)) {
                for (Path p : files) {
                    Table t = load(p);
                    result.put(t.getSchema().getTableName().toLowerCase(), t);
                }
            }
        } catch (IOException e) {
            throw new DbException("Ошибка чтения папки данных", e);
        }
        return result;
    }

    /**
     * Удаляет файл таблицы с диска.
     *
     * @param tableName имя таблицы
     */
    public void delete(String tableName) {
        try {
            Files.deleteIfExists(dir.resolve(tableName + EXT));
        } catch (IOException e) {
            throw new DbException("Не удалось удалить файл таблицы " + tableName, e);
        }
    }

    private Table load(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC) {
                throw new DbException("Файл повреждён или имеет неверный формат: " + file.getFileName());
            }
            String tableName = in.readUTF();
            int colCount = in.readInt();
            List<Column> columns = new ArrayList<>();
            for (int i = 0; i < colCount; i++) {
                String colName = in.readUTF();
                ColumnType type = ColumnType.valueOf(in.readUTF());
                boolean pk = in.readBoolean();
                String refTable = null;
                String refColumn = null;
                if (in.readBoolean()) {
                    refTable = in.readUTF();
                    refColumn = in.readUTF();
                }
                columns.add(new Column(colName, type, pk, refTable, refColumn));
            }
            Table table = new Table(new Schema(tableName, columns));
            int rowCount = in.readInt();
            List<Row> rows = new ArrayList<>(rowCount);
            for (int r = 0; r < rowCount; r++) {
                Object[] values = new Object[colCount];
                for (int c = 0; c < colCount; c++) {
                    values[c] = readValue(in, columns.get(c).getType());
                }
                rows.add(new Row(values));
            }
            table.insertAll(rows);
            return table;
        }
    }

    private void write(DataOutputStream out, Table table) throws IOException {
        Schema schema = table.getSchema();
        out.writeInt(MAGIC);
        out.writeUTF(schema.getTableName());
        out.writeInt(schema.getColumns().size());
        for (Column c : schema.getColumns()) {
            out.writeUTF(c.getName());
            out.writeUTF(c.getType().name());
            out.writeBoolean(c.isPrimaryKey());
            out.writeBoolean(c.isForeignKey());
            if (c.isForeignKey()) {
                out.writeUTF(c.getRefTable());
                out.writeUTF(c.getRefColumn());
            }
        }
        out.writeInt(table.size());
        for (Row row : table.getRows()) {
            for (int i = 0; i < schema.getColumns().size(); i++) {
                writeValue(out, schema.getColumns().get(i).getType(), row.get(i));
            }
        }
    }

    static void writeValue(DataOutputStream out, ColumnType type, Object v) throws IOException {
        out.writeBoolean(v == null);
        if (v == null) return;
        switch (type) {
            case INT:     out.writeInt((Integer) v); break;
            case DOUBLE:  out.writeDouble((Double) v); break;
            case STRING:  out.writeUTF((String) v); break;
            case DATE:    out.writeLong(((LocalDate) v).toEpochDay()); break;
            case BOOLEAN: out.writeBoolean((Boolean) v); break;
            default: throw new IllegalStateException();
        }
    }

    static Object readValue(DataInputStream in, ColumnType type) throws IOException {
        if (in.readBoolean()) return null;
        switch (type) {
            case INT:     return in.readInt();
            case DOUBLE:  return in.readDouble();
            case STRING:  return in.readUTF();
            case DATE:    return LocalDate.ofEpochDay(in.readLong());
            case BOOLEAN: return in.readBoolean();
            default: throw new IllegalStateException();
        }
    }
}
