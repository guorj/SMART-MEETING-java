package com.smartmeeting.config.oabp;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 对单行 raw 值递归求值 filter 树（AND/OR/NOT）。
 */
public final class OabpFilterEvaluator {

    private static final int MAX_DEPTH = 4;
    private static final int MAX_NODES = 30;

    private OabpFilterEvaluator() {
    }

    public static boolean matches(OabpDisplayFilterNode root, List<String> headers, List<String> row) {
        if (root == null) {
            return true;
        }
        validateTreeDepth(root, 0, new int[]{0});
        return evalNode(root, headers, row);
    }

    private static void validateTreeDepth(OabpDisplayFilterNode node, int depth, int[] count) {
        if (node == null) {
            return;
        }
        if (++count[0] > MAX_NODES) {
            throw new IllegalArgumentException("filter 节点数超过上限 " + MAX_NODES);
        }
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("filter 嵌套深度超过上限 " + MAX_DEPTH);
        }
        if (node.getChildren() != null) {
            for (OabpDisplayFilterNode child : node.getChildren()) {
                validateTreeDepth(child, depth + 1, count);
            }
        }
    }

    private static boolean evalNode(OabpDisplayFilterNode node, List<String> headers, List<String> row) {
        if (node == null) {
            return true;
        }
        String type = node.getType() != null ? node.getType().trim().toLowerCase(Locale.ROOT) : "rule";
        if ("group".equals(type)) {
            return evalGroup(node, headers, row);
        }
        return evalRule(node, headers, row);
    }

    private static boolean evalGroup(OabpDisplayFilterNode node, List<String> headers, List<String> row) {
        String op = node.getOp() != null ? node.getOp().trim().toLowerCase(Locale.ROOT) : "and";
        List<OabpDisplayFilterNode> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            return true;
        }
        return switch (op) {
            case "or" -> children.stream().anyMatch(c -> evalNode(c, headers, row));
            case "not" -> !evalNode(children.get(0), headers, row);
            default -> children.stream().allMatch(c -> evalNode(c, headers, row));
        };
    }

    private static boolean evalRule(OabpDisplayFilterNode node, List<String> headers, List<String> row) {
        String field = node.getField();
        if (field == null || field.isBlank()) {
            return true;
        }
        String op = node.getOp() != null ? node.getOp().trim().toLowerCase(Locale.ROOT) : "==";
        String cell = cellValue(headers, row, field.trim());
        String value = node.getValue() != null ? node.getValue() : "";

        return switch (op) {
            case "==", "eq" -> cell.equals(value);
            case "!=", "ne" -> !cell.equals(value);
            case "contains" -> containsAny(cell, value);
            case "not_contains" -> notContainsAny(cell, value);
            case "starts_with" -> cell.startsWith(value);
            case "ends_with" -> cell.endsWith(value);
            case "is_empty" -> cell.isEmpty();
            case "is_not_empty" -> !cell.isEmpty();
            case "in" -> inSet(value, cell, false);
            case "not_in" -> inSet(value, cell, true);
            case "between" -> between(cell, value);
            case ">" -> compare(cell, value) > 0;
            case ">=" -> compare(cell, value) >= 0;
            case "<" -> compare(cell, value) < 0;
            case "<=" -> compare(cell, value) <= 0;
            default -> cell.equals(value);
        };
    }

    private static String cellValue(List<String> headers, List<String> row, String field) {
        if (headers == null || row == null) {
            return "";
        }
        for (int i = 0; i < headers.size(); i++) {
            if (field.equals(headers.get(i))) {
                return i < row.size() && row.get(i) != null ? row.get(i) : "";
            }
        }
        return "";
    }

    private static boolean inSet(String csv, String cell, boolean negate) {
        Set<String> set = new HashSet<>();
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                set.add(t);
            }
        }
        if (set.isEmpty()) {
            return true;
        }
        boolean hit = set.contains(cell);
        return negate ? !hit : hit;
    }

    /** 单元格包含任一比较值即命中（value 为英文逗号分隔）。 */
    private static boolean containsAny(String cell, String csv) {
        if (csv == null || csv.isBlank()) {
            return true;
        }
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty() && cell.contains(t)) {
                return true;
            }
        }
        return false;
    }

    /** 单元格不包含任一比较值才命中（value 为英文逗号分隔）。 */
    private static boolean notContainsAny(String cell, String csv) {
        if (csv == null || csv.isBlank()) {
            return true;
        }
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty() && cell.contains(t)) {
                return false;
            }
        }
        return true;
    }

    private static boolean between(String cell, String range) {
        String[] parts = range.split(",", 2);
        if (parts.length < 2) {
            return false;
        }
        return compare(cell, parts[0].trim()) >= 0 && compare(cell, parts[1].trim()) <= 0;
    }

    private static int compare(String left, String right) {
        Double ln = parseNumber(left);
        Double rn = parseNumber(right);
        if (ln != null && rn != null) {
            return Double.compare(ln, rn);
        }
        return left.compareTo(right);
    }

    private static Double parseNumber(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(s.replace("%", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
