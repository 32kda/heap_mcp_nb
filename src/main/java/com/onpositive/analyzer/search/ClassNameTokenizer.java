package com.onpositive.analyzer.search;

import java.util.*;
import org.apache.commons.lang3.StringUtils;

public class ClassNameTokenizer {

    private final Set<String> stopwords;

    public ClassNameTokenizer() {
        this.stopwords = buildStopwordSet();
    }

    public ClassNameTokenizer(Set<String> additionalStopwords) {
        this.stopwords = buildStopwordSet();
        this.stopwords.addAll(additionalStopwords);
    }

    public List<String> tokenize(String name) {
        if (name == null || name.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String segment : splitByDelimiters(name)) {
            if (segment.isEmpty()) continue;
            for (String subSegment : splitCamelCase(segment)) {
                if (subSegment.isEmpty()) continue;
                for (String part : StringUtils.split(subSegment, '_')) {
                    if (part.isEmpty()) continue;
                    String cleaned = cleanIdentifier(part);
                    if (cleaned.isEmpty()) continue;
                    if (isStopword(cleaned)) continue;
                    if (cleaned.length() <= 1) continue;
                    result.add(cleaned);
                }
            }
        }
        return result;
    }

    public String simpleNameToken(String fullName) {
        if (fullName == null || fullName.isEmpty()) return null;
        int lastDot = fullName.lastIndexOf('.');
        String simple = lastDot >= 0 ? fullName.substring(lastDot + 1) : fullName;
        if (simple.isEmpty()) return null;
        String cleaned = cleanIdentifier(simple.toLowerCase());
        if (cleaned.isEmpty() || cleaned.length() <= 1) return null;
        if (isStopword(cleaned)) return null;
        return cleaned;
    }

    public boolean isStopword(String token) {
        return stopwords.contains(token);
    }

    static List<String> splitCamelCase(String input) {
        List<String> result = new ArrayList<>();
        if (input.isEmpty()) return result;
        StringBuilder current = new StringBuilder();
        current.append(input.charAt(0));
        for (int i = 1; i < input.length(); i++) {
            char prev = input.charAt(i - 1);
            char curr = input.charAt(i);

            if (Character.isLowerCase(prev) && Character.isUpperCase(curr)) {
                result.add(current.toString());
                current.setLength(0);
            } else if (i > 1 && Character.isUpperCase(prev) && Character.isUpperCase(input.charAt(i - 2))
                    && Character.isLowerCase(curr)) {
                if (current.length() > 1) {
                    char last = current.charAt(current.length() - 1);
                    current.setLength(current.length() - 1);
                    result.add(current.toString());
                    current.setLength(0);
                    current.append(last);
                }
            } else if (Character.isDigit(prev) && Character.isLetter(curr)) {
                result.add(current.toString());
                current.setLength(0);
            }
            current.append(curr);
        }
        if (current.length() > 0) {
            result.add(current.toString());
        }
        return result;
    }

    static String cleanIdentifier(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private static List<String> splitByDelimiters(String str) {
        List<String> result = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '.' || c == '$' || Character.isWhitespace(c)) {
                if (i > start) {
                    result.add(str.substring(start, i));
                }
                start = i + 1;
            }
        }
        if (start < str.length()) {
            result.add(str.substring(start));
        }
        return result;
    }

    private static Set<String> buildStopwordSet() {
        Set<String> s = new HashSet<>();
        Collections.addAll(s,
                "com", "org", "net", "io", "java", "javax", "jdk", "sun",
                "lang", "util", "utils", "impl", "internal", "api", "spi", "service",
                "module", "package", "src", "main", "test", "spec", "gen", "generated");
        Collections.addAll(s,
                "object", "string", "class", "enum", "record",
                "boolean", "byte", "char", "short", "int", "integer",
                "long", "float", "double", "void");
        Collections.addAll(s,
                "list", "set", "map", "array", "collection", "collections",
                "iterator", "iterable", "comparable", "comparator", "entry",
                "hashmap", "hashset", "hashtable", "arraylist", "linkedlist", "vector",
                "treemap", "treeset", "linkedhashmap", "linkedhashset",
                "priorityqueue", "deque", "queue", "stack",
                "sortedmap", "sortedset", "navigablemap", "navigableset",
                "enummap", "enumset", "properties", "dictionary");
        Collections.addAll(s,
                "get", "set", "put", "add", "remove",
                "delete", "create", "update", "save", "find",
                "fetch", "query", "insert", "select", "merge",
                "patch", "post", "list", "search", "count",
                "exists", "has", "is", "are", "was", "were",
                "do", "does", "did", "will", "would", "can", "could",
                "shall", "should", "may", "might", "must", "need", "check",
                "read", "write");
        Collections.addAll(s,
                "abstract", "base", "default", "simple",
                "generic", "common", "core",
                "factory", "builder", "helper", "util", "utils",
                "manager", "provider", "handler", "listener",
                "adapter", "processor", "mapper", "converter",
                "transformer", "generator", "renderer",
                "writer", "reader", "loader", "saver",
                "filter", "interceptor", "aspect", "advice",
                "proxy", "template", "delegate", "strategy",
                "registry", "cache", "pool", "context",
                "scope", "session", "transaction");
        return Collections.unmodifiableSet(s);
    }
}
