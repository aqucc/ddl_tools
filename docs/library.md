# ライブラリとしての使用例

[← README](../README.md)

CLIを経由せず、`ddl-tools-core` をJavaコードから直接呼び出すこともできます。
以下は「DDLファイルを解析 → テストデータを生成 → INSERT文として書き出す」例です。

```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.github.aqucc.ddltools.model.DatabaseMetadata;
import io.github.aqucc.ddltools.model.Dialect;
import io.github.aqucc.ddltools.parse.DdlParser;
import io.github.aqucc.ddltools.parse.ParseOptions;
import io.github.aqucc.ddltools.parse.ParseResult;
import io.github.aqucc.ddltools.testdata.GenerationOptions;
import io.github.aqucc.ddltools.testdata.InsertWriter;
import io.github.aqucc.ddltools.testdata.TestDataGenerator;
import io.github.aqucc.ddltools.testdata.TestDataSet;

public class GenerateSampleData {
    public static void main(String[] args) throws Exception {
        String ddl = new String(Files.readAllBytes(Paths.get("schema.sql")), "UTF-8");

        // DDLを解析してDB非依存のメタ情報を得る
        ParseResult parseResult = new DdlParser(new ParseOptions(Dialect.POSTGRESQL)).parse(ddl);
        DatabaseMetadata metadata = parseResult.getMetadata();

        // メタ情報からPK/FK/CHECK/NOT NULLに適合したテストデータを生成する
        GenerationOptions options = new GenerationOptions().setRows(20).setSeed(1L);
        TestDataSet dataSet = new TestDataGenerator(metadata, options).generate();

        // INSERT文として書き出す
        String sql = new InsertWriter(Dialect.POSTGRESQL).writeAll(dataSet);
        Files.write(Paths.get("insert_data.sql"), sql.getBytes("UTF-8"));
    }
}
```

生成されるテストデータの詳しい仕様は [テストデータ生成の仕様](test-data.md) を参照してください。
