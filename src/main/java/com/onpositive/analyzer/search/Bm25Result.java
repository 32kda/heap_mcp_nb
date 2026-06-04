package com.onpositive.analyzer.search;

public class Bm25Result {

    private final String className;
    private final double score;
    private final String matchedTerms;
    private final String topField;
    private long instanceCount;
    private long totalSize;

    public Bm25Result(String className, double score, String matchedTerms,
                      String topField, long instanceCount, long totalSize) {
        this.className = className;
        this.score = score;
        this.matchedTerms = matchedTerms;
        this.topField = topField;
        this.instanceCount = instanceCount;
        this.totalSize = totalSize;
    }

    public String className() {
        return className;
    }

    public double score() {
        return score;
    }

    public String matchedTerms() {
        return matchedTerms;
    }

    public String topField() {
        return topField;
    }

    public long instanceCount() {
        return instanceCount;
    }

    public long totalSize() {
        return totalSize;
    }

    public void setInstanceCount(long instanceCount) {
        this.instanceCount = instanceCount;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    @Override
    public String toString() {
        return "Bm25Result{" +
                "className='" + className + '\'' +
                ", score=" + score +
                ", matchedTerms='" + matchedTerms + '\'' +
                ", topField='" + topField + '\'' +
                ", instanceCount=" + instanceCount +
                ", totalSize=" + totalSize +
                '}';
    }
}
