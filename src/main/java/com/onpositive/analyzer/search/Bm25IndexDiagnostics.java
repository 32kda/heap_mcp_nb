package com.onpositive.analyzer.search;

import com.carrotsearch.hppc.IntIntHashMap;
import com.carrotsearch.hppc.ObjectIntMap;
import com.carrotsearch.hppc.cursors.IntIntCursor;
import com.carrotsearch.hppc.cursors.ObjectIntCursor;

import java.util.*;
import java.util.stream.Collectors;

public final class Bm25IndexDiagnostics {

    private Bm25IndexDiagnostics() {
    }

    public static IdFReport computeTopIdfTerms(InMemoryBm25Index index, int topN) {
        int N = index.getDocumentCount();
        if (N == 0) {
            return new IdFReport(Collections.emptyList());
        }

        List<IdFTerm> allTerms = new ArrayList<>();
        for (Bm25Field field : Bm25Field.values()) {
            ObjectIntMap<String> termDocCounts = index.getFieldTermDocCounts().get(field);
            for (ObjectIntCursor<String> cursor : termDocCounts) {
                String term = cursor.key;
                int df = cursor.value;
                if (df == 0) {
                    continue;
                }
                double idf = Math.log((double) N / df);
                allTerms.add(new IdFTerm(term, field.id(), idf, df));
            }
        }

        allTerms.sort((a, b) -> Double.compare(b.idf, a.idf));
        if (allTerms.size() > topN) {
            allTerms = allTerms.subList(0, topN);
        }
        return new IdFReport(allTerms);
    }

    public static IdFReport computeBottomIdfTerms(InMemoryBm25Index index, int topN) {
        int N = index.getDocumentCount();
        if (N == 0) {
            return new IdFReport(Collections.emptyList());
        }

        List<IdFTerm> allTerms = new ArrayList<>();
        for (Bm25Field field : Bm25Field.values()) {
            ObjectIntMap<String> termDocCounts = index.getFieldTermDocCounts().get(field);
            for (ObjectIntCursor<String> cursor : termDocCounts) {
                String term = cursor.key;
                int df = cursor.value;
                if (df == 0) {
                    continue;
                }
                double idf = Math.log((double) N / df);
                allTerms.add(new IdFTerm(term, field.id(), idf, df));
            }
        }

        allTerms.sort(Comparator.comparingDouble(a -> a.idf));
        if (allTerms.size() > topN) {
            allTerms = allTerms.subList(0, topN);
        }
        return new IdFReport(allTerms);
    }

    public static TfIdfReport computeTopTfIdfTerms(InMemoryBm25Index index, int topN) {
        int N = index.getDocumentCount();
        if (N == 0) {
            return new TfIdfReport(Collections.emptyList());
        }

        Map<String, Double> totalTfIdf = new HashMap<>();

        for (Bm25Field field : Bm25Field.values()) {
            Map<String, IntIntHashMap> fieldIndexes = index.getFieldIndexes().get(field);
            ObjectIntMap<String> termDocCounts = index.getFieldTermDocCounts().get(field);

            for (Map.Entry<String, IntIntHashMap> entry : fieldIndexes.entrySet()) {
                String term = entry.getKey();
                int df = termDocCounts.getOrDefault(term, 1);
                double idf = Math.log((double) N / df);
                double fieldWeight = field.weight();
                double termTotalTfIdf = 0;

                IntIntHashMap postings = entry.getValue();
                for (IntIntCursor c : postings) {
                    double tf = c.value;
                    termTotalTfIdf += tf * idf * fieldWeight;
                }
                totalTfIdf.merge(term + "@" + field.id(), termTotalTfIdf, Double::sum);
            }
        }

        List<TfIdfTerm> terms = totalTfIdf.entrySet().stream()
                .map(e -> new TfIdfTerm(e.getKey(), e.getValue()))
                .sorted((a, b) -> Double.compare(b.totalTfIdf, a.totalTfIdf))
                .limit(topN)
                .collect(Collectors.toList());
        return new TfIdfReport(terms);
    }

    public static CosineSimilarityReport computeCosineSimilarity(InMemoryBm25Index index) {
        int N = index.getDocumentCount();
        if (N < 2) {
            return new CosineSimilarityReport(0, 0, 0, 0, 0, 0, Collections.emptyList());
        }

        int sampleSize = Math.min(N, 1000);
        int[] docIds = new int[N];
        for (int i = 0; i < N; i++) {
            docIds[i] = i;
        }

        List<Map<String, Double>> docVectors = new ArrayList<>(sampleSize);
        for (int di = 0; di < sampleSize; di++) {
            docVectors.add(buildDocVector(index, di));
        }

        List<Double> allSimilarities = new ArrayList<>();
        double minSim = 1.0;
        double maxSim = 0.0;
        String minPair = "";
        String maxPair = "";
        int pairCount = 0;

        for (int i = 0; i < sampleSize; i++) {
            Map<String, Double> vecA = docVectors.get(i);
            double normA = computeNorm(vecA);
            if (normA == 0) {
                continue;
            }
            for (int j = i + 1; j < sampleSize; j++) {
                Map<String, Double> vecB = docVectors.get(j);
                double normB = computeNorm(vecB);
                if (normB == 0) {
                    continue;
                }
                double dot = dotProduct(vecA, vecB);
                double cos = dot / (normA * normB);
                allSimilarities.add(cos);
                pairCount++;

                if (cos < minSim) {
                    minSim = cos;
                    minPair = index.getClassName(i) + " <-> " + index.getClassName(j);
                }
                if (cos > maxSim) {
                    maxSim = cos;
                    maxPair = index.getClassName(i) + " <-> " + index.getClassName(j);
                }
            }
        }

        if (allSimilarities.isEmpty()) {
            return new CosineSimilarityReport(N, sampleSize, pairCount, 0, 0, 0,
                    Collections.emptyList());
        }

        Collections.sort(allSimilarities);
        double mean = allSimilarities.stream().mapToDouble(d -> d).average().orElse(0);
        double median = allSimilarities.get(allSimilarities.size() / 2);
        double variance = allSimilarities.stream()
                .mapToDouble(d -> (d - mean) * (d - mean))
                .average().orElse(0);
        double stddev = Math.sqrt(variance);

        int[] histogram = new int[10];
        for (double sim : allSimilarities) {
            int bucket = Math.min((int) (sim * 10), 9);
            histogram[bucket]++;
        }

        List<SimilarityBucket> buckets = new ArrayList<>();
        for (int b = 0; b < 10; b++) {
            double lower = b * 0.1;
            double upper = (b + 1) * 0.1;
            buckets.add(new SimilarityBucket(lower, upper, histogram[b]));
        }

        return new CosineSimilarityReport(N, sampleSize, pairCount, minSim, maxSim, mean,
                buckets, median, stddev, minPair, maxPair);
    }

    private static Map<String, Double> buildDocVector(InMemoryBm25Index index, int docId) {
        int N = index.getDocumentCount();
        Map<String, Double> vector = new HashMap<>();

        for (Bm25Field field : Bm25Field.values()) {
            Map<String, IntIntHashMap> fieldIndexes = index.getFieldIndexes().get(field);
            ObjectIntMap<String> termDocCounts = index.getFieldTermDocCounts().get(field);

            for (Map.Entry<String, IntIntHashMap> entry : fieldIndexes.entrySet()) {
                String term = entry.getKey();
                IntIntHashMap postings = entry.getValue();
                int tf = postings.getOrDefault(docId, 0);
                if (tf == 0) {
                    continue;
                }
                int df = termDocCounts.getOrDefault(term, 1);
                double idf = Math.log((double) N / df);
                double tfidf = tf * idf * field.weight();
                String key = term + "@" + field.id();
                vector.merge(key, tfidf, Double::sum);
            }
        }
        return vector;
    }

    private static double computeNorm(Map<String, Double> vec) {
        double sumSq = 0;
        for (double v : vec.values()) {
            sumSq += v * v;
        }
        return Math.sqrt(sumSq);
    }

    private static double dotProduct(Map<String, Double> a, Map<String, Double> b) {
        Map<String, Double> smaller = a.size() < b.size() ? a : b;
        Map<String, Double> larger = a.size() < b.size() ? b : a;
        double dot = 0;
        for (Map.Entry<String, Double> e : smaller.entrySet()) {
            Double v = larger.get(e.getKey());
            if (v != null) {
                dot += e.getValue() * v;
            }
        }
        return dot;
    }

    // -- report types --

    public record IdFTerm(String term, String field, double idf, int docFrequency) {
    }

    public record IdFReport(List<IdFTerm> terms) {
    }

    public record TfIdfTerm(String termAndField, double totalTfIdf) {
        public String displayName() {
            return termAndField;
        }
    }

    public record TfIdfReport(List<TfIdfTerm> terms) {
    }

    public record CosineSimilarityReport(
            int totalDocuments,
            int sampledDocuments,
            int pairsCompared,
            double minSimilarity,
            double maxSimilarity,
            double meanSimilarity,
            List<SimilarityBucket> histogram,
            double medianSimilarity,
            double stdDevSimilarity,
            String minPair,
            String maxPair) {

        public CosineSimilarityReport(int totalDocuments, int sampledDocuments,
                                       int pairsCompared, double minSimilarity,
                                       double maxSimilarity, double meanSimilarity,
                                       List<SimilarityBucket> histogram) {
            this(totalDocuments, sampledDocuments, pairsCompared,
                    minSimilarity, maxSimilarity, meanSimilarity,
                    histogram, 0, 0, "", "");
        }

        public String signalQuality() {
            if (pairsCompared == 0) return "INSUFFICIENT_DATA";
            if (meanSimilarity > 0.7) return "TOO_HOMOGENEOUS — too many shared tokens, index may contain garbage";
            if (meanSimilarity < 0.05 && maxSimilarity < 0.1)
                return "TOO_SPARSE — documents share almost no tokens, index may lack signal";
            if (maxSimilarity > 0.4)
                return "GOOD — documents are generally distinct but related classes share tokens";
            return "ACCEPTABLE — moderate token overlap";
        }
    }

    public record SimilarityBucket(double lower, double upper, int count) {
    }
}
