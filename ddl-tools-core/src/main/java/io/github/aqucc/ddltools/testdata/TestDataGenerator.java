package io.github.aqucc.ddltools.testdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import io.github.aqucc.ddltools.model.ColumnMetadata;
import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.ForeignKeyMetadata;
import io.github.aqucc.ddltools.model.MaterializedViewMetadata;
import io.github.aqucc.ddltools.model.SchemaMetadata;
import io.github.aqucc.ddltools.model.TableMetadata;
import io.github.aqucc.ddltools.model.UniqueConstraintMetadata;
import io.github.aqucc.ddltools.model.ViewMetadata;

/**
 * メタ情報から全テーブルの型適合テストデータを生成する。
 *
 * <ul>
 *   <li>NOT NULLカラムには必ず値を入れ、NULL可カラムには一定割合でNULLを混ぜる</li>
 *   <li>PK/一意制約のカラムは連番ベースで重複しない値を生成する</li>
 *   <li>単純なCHECK制約(IN/比較/BETWEEN)の値域を尊重する</li>
 *   <li>FKカラムは親テーブルの生成値と同じ値を使う(依存順にINSERT可能)</li>
 *   <li>ビュー定義の等値結合条件を解析し、結合カラムに同じ値を与えて
 *       ビューが1〜n件の行を返すデータセットを作る(ベストエフォート)</li>
 *   <li>乱数シード固定により再現性がある</li>
 * </ul>
 */
public class TestDataGenerator {

    private static final String[] WORDS = {
            "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa",
    };

    private final DatabaseMetadata db;
    private final GenerationOptions options;

    public TestDataGenerator(DatabaseMetadata db, GenerationOptions options) {
        this.db = db;
        this.options = options;
    }

    public TestDataSet generate() {
        TestDataSet dataSet = new TestDataSet();
        Random random = new Random(options.getSeed());
        LocalDate baseDate = LocalDate.parse(options.getBaseDate());

        List<TableRef> tables = sortByDependency(collectTables(), dataSet.getWarnings());
        UnionFind groups = new UnionFind();
        Map<String, Object> fixedValues = new HashMap<String, Object>();
        buildForeignKeyGroups(tables, groups);
        if (options.isConsiderViews()) {
            buildViewGroups(groups, fixedValues, dataSet.getWarnings());
        }

        // グループ代表キー → 行indexごとの共有値
        Map<String, List<Object>> sharedValues = new HashMap<String, List<Object>>();

        for (TableRef ref : tables) {
            TableMetadata table = ref.table;
            Map<String, ColumnDomain> domains = CheckAnalyzer.analyze(table, dataSet.getWarnings());
            Set<String> uniqueColumns = uniqueColumnsOf(table);

            List<ColumnMetadata> insertColumns = new ArrayList<ColumnMetadata>();
            for (ColumnMetadata col : table.getColumns()) {
                if (!Boolean.TRUE.equals(col.getIdentity())) {
                    insertColumns.add(col);
                }
            }
            TableData tableData = new TableData(ref.schemaName, table.getName(), insertColumns);

            for (int rowIndex = 0; rowIndex < options.getRows(); rowIndex++) {
                List<Object> row = new ArrayList<Object>(insertColumns.size());
                for (ColumnMetadata col : insertColumns) {
                    String colKey = ViewAnalyzer.columnKey(ref.schemaName, table.getName(), col.getName());
                    String root = groups.find(colKey);
                    boolean grouped = groups.isGrouped(colKey);

                    Object value;
                    List<Object> shared = sharedValues.get(root);
                    if (grouped && shared != null && rowIndex < shared.size()) {
                        value = shared.get(rowIndex);
                    } else {
                        value = generateValue(col, rowIndex, random, baseDate,
                                domains.get(col.getName().toLowerCase(Locale.ROOT)),
                                uniqueColumns.contains(col.getName().toLowerCase(Locale.ROOT)),
                                grouped, fixedValues.get(colKey), dataSet.getWarnings(),
                                table.getName());
                        if (grouped) {
                            if (shared == null) {
                                shared = new ArrayList<Object>();
                                sharedValues.put(root, shared);
                            }
                            shared.add(value);
                        }
                    }
                    row.add(value);
                }
                tableData.getRows().add(row);
            }
            dataSet.getTables().add(tableData);
        }
        return dataSet;
    }

    // ------------------------------------------------------------------
    // 値の生成
    // ------------------------------------------------------------------

    private Object generateValue(ColumnMetadata col, int rowIndex, Random random,
            LocalDate baseDate, ColumnDomain domain, boolean unique, boolean grouped,
            Object fixedValue, List<String> warnings, String tableName) {

        // ビューのWHERE条件による固定値 (一意性が必要なカラムには適用しない)
        if (fixedValue != null && !unique) {
            return fixedValue;
        }
        // NULL可カラムには一定割合でNULLを混ぜる (一意・グループ共有カラムは除外)
        if (col.isNullable() && !unique && !grouped
                && random.nextDouble() < options.getNullRatio()) {
            return null;
        }

        TypeCategory category = TypeCategory.of(col);
        List<Object> allowed = (domain == null) ? null
                : (domain.getAllowedValues().isEmpty() ? null : domain.getAllowedValues());
        if (allowed != null) {
            if (unique) {
                if (options.getRows() > allowed.size()) {
                    warnings.add("unique column " + tableName + "." + col.getName()
                            + " has only " + allowed.size() + " allowed values for "
                            + options.getRows() + " rows; duplicates will occur");
                }
                return allowed.get(rowIndex % allowed.size());
            }
            return allowed.get(random.nextInt(allowed.size()));
        }

        switch (category) {
            case INTEGER:
                return generateInteger(col, rowIndex, random, domain, unique, warnings, tableName);
            case DECIMAL:
                return generateDecimal(col, rowIndex, random, domain, unique);
            case STRING:
                return generateString(col, rowIndex, random, unique);
            case DATE:
                return unique ? baseDate.plusDays(rowIndex) : baseDate.plusDays(random.nextInt(365));
            case TIMESTAMP:
                return unique
                        ? LocalDateTime.of(baseDate, LocalTime.of(9, 0)).plusSeconds(rowIndex)
                        : LocalDateTime.of(baseDate.plusDays(random.nextInt(365)),
                                LocalTime.of(random.nextInt(24), random.nextInt(60), random.nextInt(60)));
            case TIME:
                return LocalTime.of(random.nextInt(24), random.nextInt(60), random.nextInt(60));
            case BOOLEAN:
                return random.nextBoolean();
            case CLOB:
                return "text-" + (rowIndex + 1);
            case BINARY:
                return new byte[] {(byte) (rowIndex + 1), (byte) random.nextInt(256)};
            case UUID:
                return new java.util.UUID(random.nextLong(), unique ? rowIndex : random.nextLong());
            case JSON:
                return "{\"seq\": " + (rowIndex + 1) + "}";
            case XML:
                return "<row seq=\"" + (rowIndex + 1) + "\"/>";
            case OTHER:
            default:
                warnings.add("unsupported type '" + col.getTypeName() + "' for "
                        + tableName + "." + col.getName() + "; generated as string");
                return "X";
        }
    }

    private Object generateInteger(ColumnMetadata col, int rowIndex, Random random,
            ColumnDomain domain, boolean unique, List<String> warnings, String tableName) {
        long min = (domain != null && domain.getMinValue() != null)
                ? domain.getMinValue().longValue() : 1L;
        long max = (domain != null && domain.getMaxValue() != null)
                ? domain.getMaxValue().longValue() : Long.MAX_VALUE;
        if (col.getPrecision() != null && col.getPrecision() < 18) {
            long precisionMax = pow10(col.getPrecision()) - 1;
            max = Math.min(max, precisionMax);
        }
        if (unique) {
            long value = min + rowIndex;
            if (value > max) {
                warnings.add("unique column " + tableName + "." + col.getName()
                        + " exceeded max value " + max + "; duplicates will occur");
                value = max;
            }
            return value;
        }
        long range = Math.min(max - min + 1, 100L);
        if (range <= 0) {
            range = 1;
        }
        return min + (long) random.nextInt((int) range);
    }

    private Object generateDecimal(ColumnMetadata col, int rowIndex, Random random,
            ColumnDomain domain, boolean unique) {
        int scale = col.getScale() == null ? 2 : col.getScale();
        BigDecimal min = (domain != null && domain.getMinValue() != null)
                ? domain.getMinValue() : BigDecimal.ZERO;
        BigDecimal value;
        if (unique) {
            value = min.add(BigDecimal.valueOf(rowIndex + 1));
        } else {
            value = min.add(BigDecimal.valueOf(random.nextInt(100000), scale));
        }
        if (domain != null && domain.getMaxValue() != null
                && value.compareTo(domain.getMaxValue()) > 0) {
            value = domain.getMaxValue();
        }
        return value.setScale(scale, BigDecimal.ROUND_HALF_UP);
    }

    private Object generateString(ColumnMetadata col, int rowIndex, Random random, boolean unique) {
        Integer length = col.getLength();
        if (unique) {
            String seq = Integer.toString(rowIndex + 1);
            String base = col.getName().toLowerCase(Locale.ROOT) + "_";
            if (length != null && base.length() + seq.length() > length) {
                int room = length - seq.length();
                base = room > 0 ? base.substring(0, Math.min(base.length(), room)) : "";
            }
            String value = base + seq;
            return (length != null && value.length() > length)
                    ? value.substring(value.length() - length)
                    : value;
        }
        String word = WORDS[random.nextInt(WORDS.length)];
        return (length != null && word.length() > length) ? word.substring(0, length) : word;
    }

    private static long pow10(int n) {
        long v = 1;
        for (int i = 0; i < n && i < 18; i++) {
            v *= 10;
        }
        return v;
    }

    // ------------------------------------------------------------------
    // テーブル収集・依存順ソート・グループ構築
    // ------------------------------------------------------------------

    private static class TableRef {
        final String schemaName;
        final TableMetadata table;

        TableRef(String schemaName, TableMetadata table) {
            this.schemaName = schemaName;
            this.table = table;
        }

        String key() {
            return (schemaName + "|" + table.getName()).toLowerCase(Locale.ROOT);
        }
    }

    private List<TableRef> collectTables() {
        List<TableRef> tables = new ArrayList<TableRef>();
        for (SchemaMetadata schema : db.getSchemas()) {
            for (TableMetadata table : schema.getTables()) {
                tables.add(new TableRef(schema.getName(), table));
            }
        }
        return tables;
    }

    /** FK依存の親→子の順になるようトポロジカルソートする。循環時は元の順序で続行。 */
    private List<TableRef> sortByDependency(List<TableRef> tables, List<String> warnings) {
        Map<String, TableRef> byKey = new LinkedHashMap<String, TableRef>();
        for (TableRef ref : tables) {
            byKey.put(ref.key(), ref);
        }
        List<TableRef> sorted = new ArrayList<TableRef>();
        Set<String> emitted = new HashSet<String>();
        boolean progress = true;
        while (sorted.size() < tables.size() && progress) {
            progress = false;
            for (TableRef ref : tables) {
                if (emitted.contains(ref.key())) {
                    continue;
                }
                boolean ready = true;
                for (ForeignKeyMetadata fk : ref.table.getForeignKeys()) {
                    String parentKey = parentKeyOf(ref, fk);
                    if (parentKey != null && byKey.containsKey(parentKey)
                            && !emitted.contains(parentKey) && !parentKey.equals(ref.key())) {
                        ready = false;
                        break;
                    }
                }
                if (ready) {
                    sorted.add(ref);
                    emitted.add(ref.key());
                    progress = true;
                }
            }
        }
        if (sorted.size() < tables.size()) {
            warnings.add("circular foreign key dependency detected; falling back to declaration order");
            for (TableRef ref : tables) {
                if (!emitted.contains(ref.key())) {
                    sorted.add(ref);
                }
            }
        }
        return sorted;
    }

    private String parentKeyOf(TableRef ref, ForeignKeyMetadata fk) {
        if (fk.getReferencedTable() == null) {
            return null;
        }
        String schema = fk.getReferencedSchema() != null ? fk.getReferencedSchema() : ref.schemaName;
        return (schema + "|" + fk.getReferencedTable()).toLowerCase(Locale.ROOT);
    }

    /** FKの子カラム↔親カラムを同一グループにする。 */
    private void buildForeignKeyGroups(List<TableRef> tables, UnionFind groups) {
        for (TableRef ref : tables) {
            for (ForeignKeyMetadata fk : ref.table.getForeignKeys()) {
                if (fk.getReferencedTable() == null) {
                    continue;
                }
                String parentSchema = fk.getReferencedSchema() != null
                        ? fk.getReferencedSchema() : ref.schemaName;
                TableMetadata parent = findTable(parentSchema, fk.getReferencedTable());
                if (parent == null) {
                    continue;
                }
                List<String> parentColumns = fk.getReferencedColumns();
                if ((parentColumns == null || parentColumns.isEmpty())
                        && parent.getPrimaryKey() != null) {
                    parentColumns = parent.getPrimaryKey().getColumns();
                }
                if (parentColumns == null || parentColumns.size() != fk.getColumns().size()) {
                    continue;
                }
                for (int i = 0; i < fk.getColumns().size(); i++) {
                    groups.union(
                            ViewAnalyzer.columnKey(ref.schemaName, ref.table.getName(),
                                    fk.getColumns().get(i)),
                            ViewAnalyzer.columnKey(parentSchema, parent.getName(),
                                    parentColumns.get(i)));
                }
            }
        }
    }

    /** ビュー/マテビュー定義の等値結合・固定条件をグループと固定値に反映する。 */
    private void buildViewGroups(UnionFind groups, Map<String, Object> fixedValues,
            List<String> warnings) {
        ViewAnalyzer analyzer = new ViewAnalyzer(db);
        for (SchemaMetadata schema : db.getSchemas()) {
            for (ViewMetadata view : schema.getViews()) {
                applyViewAnalysis(analyzer, view.getDefinitionSql(),
                        schema.getName() + "." + view.getName(), groups, fixedValues, warnings);
            }
            for (MaterializedViewMetadata mv : schema.getMaterializedViews()) {
                applyViewAnalysis(analyzer, mv.getDefinitionSql(),
                        schema.getName() + "." + mv.getName(), groups, fixedValues, warnings);
            }
        }
    }

    private void applyViewAnalysis(ViewAnalyzer analyzer, String definitionSql, String viewName,
            UnionFind groups, Map<String, Object> fixedValues, List<String> warnings) {
        ViewAnalyzer.Analysis analysis = analyzer.analyze(definitionSql);
        if (analysis == null) {
            warnings.add("view definition could not be analyzed, skipped: " + viewName);
            return;
        }
        for (String[] pair : analysis.getEqualPairs()) {
            groups.union(pair[0], pair[1]);
        }
        for (Map.Entry<String, Object> e : analysis.getFixedValues().entrySet()) {
            if (!fixedValues.containsKey(e.getKey())) {
                fixedValues.put(e.getKey(), e.getValue());
            }
        }
    }

    private TableMetadata findTable(String schemaName, String tableName) {
        SchemaMetadata schema = db.findSchema(schemaName);
        if (schema != null) {
            TableMetadata t = schema.findTable(tableName);
            if (t != null) {
                return t;
            }
        }
        for (SchemaMetadata s : db.getSchemas()) {
            TableMetadata t = s.findTable(tableName);
            if (t != null) {
                return t;
            }
        }
        return null;
    }

    /** PK・一意制約の対象カラム名(小文字)。複合キーは先頭カラムを一意化して組を区別する。 */
    private Set<String> uniqueColumnsOf(TableMetadata table) {
        Set<String> unique = new HashSet<String>();
        if (table.getPrimaryKey() != null && !table.getPrimaryKey().getColumns().isEmpty()) {
            unique.add(table.getPrimaryKey().getColumns().get(0).toLowerCase(Locale.ROOT));
        }
        for (UniqueConstraintMetadata uq : table.getUniqueConstraints()) {
            if (!uq.getColumns().isEmpty()) {
                unique.add(uq.getColumns().get(0).toLowerCase(Locale.ROOT));
            }
        }
        return unique;
    }

    // ------------------------------------------------------------------
    // Union-Find
    // ------------------------------------------------------------------

    private static class UnionFind {
        private final Map<String, String> parent = new HashMap<String, String>();

        String find(String key) {
            String p = parent.get(key);
            if (p == null || p.equals(key)) {
                return key;
            }
            String root = find(p);
            parent.put(key, root);
            return root;
        }

        void union(String a, String b) {
            String ra = find(a);
            String rb = find(b);
            String root = ra.compareTo(rb) <= 0 ? ra : rb;
            parent.put(a, root);
            parent.put(b, root);
            parent.put(ra, root);
            parent.put(rb, root);
        }

        boolean isGrouped(String key) {
            if (!parent.containsKey(key)) {
                return false;
            }
            String root = find(key);
            int count = 0;
            for (String k : parent.keySet()) {
                if (find(k).equals(root)) {
                    count++;
                    if (count > 1) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
