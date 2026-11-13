package ufcdb.storage;

import ufcdb.index.Index;
import ufcdb.model.ColumnType;
import ufcdb.model.DbException;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Репозиторий индексов: хранит каждый индекс в файле {@code <имя>.idx}.
 * Запись идёт через временный файл с атомарной заменой, как и у таблиц.
 */
public class IndexRepository {
    private static final int MAGIC = 0x55464349; // "UFCI"
    private static final String EXT = ".idx";
    private static final String TMP_EXT = ".idx.tmp";

    private final Path dir;

    /**
     * @param dir папка для файлов индексов (будет создана, если её нет)
     */
    public IndexRepository(Path dir) {
        this.dir = dir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new DbException("Не удалось создать папку данных: " + dir, e);
        }
    }

    /**
     * Сохраняет индекс на диск.
     *
     * @param index индекс
     * @param table таблица индекса (нужна для типов данных)
     */
    public void save(Index index, Table table) {
        Path target = dir.resolve(index.getName() + EXT);
        Path tmp = dir.resolve(index.getName() + TMP_EXT);
        try {
            FileOutputStream fos = new FileOutputStream(tmp.toFile());
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(fos))) {
                write(out, index, table.getSchema());
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
            throw new DbException("Не удалось сохранить индекс " + index.getName(), e);
        }
    }

    /**
     * Удаляет файл индекса.
     *
     * @param indexName имя индекса
     */
    public void delete(String indexName) {
        try {
            Files.deleteIfExists(dir.resolve(indexName + EXT));
        } catch (IOException e) {
            throw new DbException("Не удалось удалить файл индекса " + indexName, e);
        }
    }

    /**
     * Загружает все индексы. Недописанные временные файлы удаляются, так же как
     * и файлы индексов, которые нельзя прочитать или чья таблица не найдена.
     *
     * @param tables загруженные таблицы (по имени в нижнем регистре)
     * @return индексы по имени (в нижнем регистре)
     */
    public Map<String, Index> loadAll(Map<String, Table> tables) {
        Map<String, Index> result = new LinkedHashMap<>();
        try {
            try (DirectoryStream<Path> tmps = Files.newDirectoryStream(dir, "*" + TMP_EXT)) {
                for (Path p : tmps) {
                    Files.deleteIfExists(p);
                }
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*" + EXT)) {
                for (Path p : files) {
                    Index idx;
                    try {
                        idx = load(p, tables);
                    } catch (IOException | DbException | IllegalArgumentException e) {
                        idx = null;
                    }
                    if (idx == null) {
                        Files.deleteIfExists(p);
                    } else {
                        result.put(idx.getName().toLowerCase(), idx);
                    }
                }
            }
        } catch (IOException e) {
            throw new DbException("Ошибка чтения папки данных", e);
        }
        return result;
    }

    private Index load(Path file, Map<String, Table> tables) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (in.readInt() != MAGIC) {
                return null;
            }
            String name = in.readUTF();
            String tableName = in.readUTF();
            String columnName = in.readUTF();
            Table table = tables.get(tableName.toLowerCase());
            if (table == null) {
                return null;
            }
            Schema schema = table.getSchema();
            int col = schema.indexOf(columnName);
            ColumnType keyType = schema.getColumns().get(col).getType();
            ColumnType pkType = schema.getColumns().get(schema.primaryKeyIndex()).getType();
            Index index = new Index(name, schema.getTableName(), schema.getColumns().get(col).getName());
            int keyCount = in.readInt();
            for (int i = 0; i < keyCount; i++) {
                Object key = TableRepository.readValue(in, keyType);
                int n = in.readInt();
                for (int j = 0; j < n; j++) {
                    index.add(key, TableRepository.readValue(in, pkType));
                }
            }
            return index;
        }
    }

    private void write(DataOutputStream out, Index index, Schema schema) throws IOException {
        ColumnType keyType = schema.getColumns().get(schema.indexOf(index.getColumnName())).getType();
        ColumnType pkType = schema.getColumns().get(schema.primaryKeyIndex()).getType();
        out.writeInt(MAGIC);
        out.writeUTF(index.getName());
        out.writeUTF(index.getTableName());
        out.writeUTF(index.getColumnName());
        out.writeInt(index.entries().size());
        for (Map.Entry<Object, List<Object>> e : index.entries().entrySet()) {
            TableRepository.writeValue(out, keyType, e.getKey());
            out.writeInt(e.getValue().size());
            for (Object pk : e.getValue()) {
                TableRepository.writeValue(out, pkType, pk);
            }
        }
    }
}
