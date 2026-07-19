package io.github.aqucc.ddltools.testdata;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.Between;
import net.sf.jsqlparser.expression.operators.relational.ComparisonOperator;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.expression.operators.relational.GreaterThanEquals;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.expression.operators.relational.MinorThan;
import net.sf.jsqlparser.expression.operators.relational.MinorThanEquals;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;

import io.github.aqucc.ddltools.model.CheckConstraintMetadata;
import io.github.aqucc.ddltools.model.TableMetadata;

/**
 * CHECK制約の式を解析し、カラムごとの値域({@link ColumnDomain})を導く。
 *
 * <p>対応パターン: {@code col IN (...)}、{@code col >= n} 等の比較、{@code col BETWEEN a AND b}、
 * およびそれらのAND結合。解析できない制約は警告として報告する。
 */
public final class CheckAnalyzer {

    private CheckAnalyzer() {
    }

    /**
     * テーブルの全CHECK制約を解析する。キーはカラム名(小文字)。
     */
    public static Map<String, ColumnDomain> analyze(TableMetadata table, List<String> warnings) {
        Map<String, ColumnDomain> domains = new HashMap<String, ColumnDomain>();
        for (CheckConstraintMetadata check : table.getCheckConstraints()) {
            String expr = check.getExpression();
            if (expr == null || expr.trim().isEmpty()) {
                continue;
            }
            try {
                Expression parsed = CCJSqlParserUtil.parseCondExpression(expr);
                if (!apply(parsed, domains)) {
                    warnings.add("unsupported CHECK constraint ignored: " + table.getName()
                            + " " + summarize(expr));
                }
            } catch (Exception e) {
                warnings.add("could not analyze CHECK constraint: " + table.getName()
                        + " " + summarize(expr));
            }
        }
        return domains;
    }

    private static boolean apply(Expression expr, Map<String, ColumnDomain> domains) {
        if (expr instanceof AndExpression) {
            AndExpression and = (AndExpression) expr;
            boolean left = apply(and.getLeftExpression(), domains);
            boolean right = apply(and.getRightExpression(), domains);
            return left || right;
        }
        if (expr instanceof InExpression) {
            return applyIn((InExpression) expr, domains);
        }
        if (expr instanceof Between) {
            return applyBetween((Between) expr, domains);
        }
        if (expr instanceof ComparisonOperator) {
            return applyComparison((ComparisonOperator) expr, domains);
        }
        return false;
    }

    private static boolean applyIn(InExpression in, Map<String, ColumnDomain> domains) {
        if (!(in.getLeftExpression() instanceof Column) || in.isNot()) {
            return false;
        }
        if (!(in.getRightExpression() instanceof ExpressionList)) {
            return false;
        }
        ColumnDomain domain = domainOf(domains, (Column) in.getLeftExpression());
        for (Expression item : ((ExpressionList<?>) in.getRightExpression())) {
            Object value = literalValue(item);
            if (value == null) {
                return false;
            }
            domain.getAllowedValues().add(value);
        }
        return true;
    }

    private static boolean applyBetween(Between between, Map<String, ColumnDomain> domains) {
        if (!(between.getLeftExpression() instanceof Column) || between.isNot()) {
            return false;
        }
        BigDecimal start = numericValue(between.getBetweenExpressionStart());
        BigDecimal end = numericValue(between.getBetweenExpressionEnd());
        if (start == null || end == null) {
            return false;
        }
        ColumnDomain domain = domainOf(domains, (Column) between.getLeftExpression());
        domain.setMinValue(start);
        domain.setMaxValue(end);
        return true;
    }

    private static boolean applyComparison(ComparisonOperator cmp, Map<String, ColumnDomain> domains) {
        Expression left = cmp.getLeftExpression();
        Expression right = cmp.getRightExpression();
        if (left instanceof Column && numericValue(right) != null) {
            return applyBound(domains, (Column) left, cmp, numericValue(right), true);
        }
        if (right instanceof Column && numericValue(left) != null) {
            return applyBound(domains, (Column) right, cmp, numericValue(left), false);
        }
        return false;
    }

    private static boolean applyBound(Map<String, ColumnDomain> domains, Column col,
            ComparisonOperator cmp, BigDecimal value, boolean columnOnLeft) {
        ColumnDomain domain = domainOf(domains, col);
        boolean greater = (cmp instanceof GreaterThan) || (cmp instanceof GreaterThanEquals);
        boolean less = (cmp instanceof MinorThan) || (cmp instanceof MinorThanEquals);
        boolean exclusive = (cmp instanceof GreaterThan) || (cmp instanceof MinorThan);
        if (!greater && !less) {
            return false;
        }
        // "n < col" のように定数が左辺の場合は向きが反転する
        boolean lowerBound = columnOnLeft == greater;
        BigDecimal adjusted = exclusive
                ? (lowerBound ? value.add(BigDecimal.ONE) : value.subtract(BigDecimal.ONE))
                : value;
        if (lowerBound) {
            domain.setMinValue(adjusted);
        } else {
            domain.setMaxValue(adjusted);
        }
        return true;
    }

    private static ColumnDomain domainOf(Map<String, ColumnDomain> domains, Column col) {
        String key = col.getColumnName().replace("\"", "").toLowerCase(Locale.ROOT);
        ColumnDomain domain = domains.get(key);
        if (domain == null) {
            domain = new ColumnDomain();
            domains.put(key, domain);
        }
        return domain;
    }

    private static Object literalValue(Expression expr) {
        if (expr instanceof StringValue) {
            return ((StringValue) expr).getValue();
        }
        if (expr instanceof LongValue) {
            return ((LongValue) expr).getValue();
        }
        if (expr instanceof DoubleValue) {
            return BigDecimal.valueOf(((DoubleValue) expr).getValue());
        }
        return null;
    }

    private static BigDecimal numericValue(Expression expr) {
        if (expr instanceof LongValue) {
            return BigDecimal.valueOf(((LongValue) expr).getValue());
        }
        if (expr instanceof DoubleValue) {
            return BigDecimal.valueOf(((DoubleValue) expr).getValue());
        }
        return null;
    }

    private static String summarize(String expr) {
        String s = expr.replaceAll("\\s+", " ").trim();
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }
}
