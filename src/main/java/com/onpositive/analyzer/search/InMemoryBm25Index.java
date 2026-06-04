package com.onpositive.analyzer.search;

import com.carrotsearch.hppc.IntIntHashMap;
import com.carrotsearch.hppc.IntIntMap;
import com.carrotsearch.hppc.IntObjectHashMap;
import com.carrotsearch.hppc.IntObjectMap;
import com.carrotsearch.hppc.ObjectIntHashMap;
import com.carrotsearch.hppc.ObjectIntMap;
import com.carrotsearch.hppc.cursors.IntIntCursor;

import java.util.*;

public class InMemoryBm25Index implements Bm25Index {

    private static final double K1 = 1.5;
    private static final double B = 0.75;

    private final IntObjectMap<String> docIdToClassName;
    private final ObjectIntMap<String> classNameToDocId;
    private int nextDocId;

    private final Map<Bm25Field, Map<String, IntIntHashMap>> fieldIndexes;
    private final Map<Bm25Field, ObjectIntMap<String>> fieldTermDocCounts;
    private final Map<Bm25Field, IntIntHashMap> fieldDocLengths;
    private final Map<Bm25Field, Double> fieldAvgDl;

    private final ClassNameTokenizer tokenizer;
    private boolean built;

    public InMemoryBm25Index() {
        this.docIdToClassName = new IntObjectHashMap<>();
        this.classNameToDocId = new ObjectIntHashMap<>();
        this.nextDocId = 0;

        this.fieldIndexes = new EnumMap<>(Bm25Field.class);
        this.fieldTermDocCounts = new EnumMap<>(Bm25Field.class);
        this.fieldDocLengths = new EnumMap<>(Bm25Field.class);
        this.fieldAvgDl = new EnumMap<>(Bm25Field.class);

        for (Bm25Field field : Bm25Field.values()) {
            this.fieldIndexes.put(field, new HashMap<>());
            this.fieldTermDocCounts.put(field, new ObjectIntHashMap<>());
            this.fieldDocLengths.put(field, new IntIntHashMap());
            this.fieldAvgDl.put(field, 0.0);
        }

        this.tokenizer = new ClassNameTokenizer();
        this.built = false;
    }

    @Override
    public int getOrCreateDocId(String className) {
        if (classNameToDocId.containsKey(className)) {
            return classNameToDocId.get(className);
        }
        int id = nextDocId++;
        classNameToDocId.put(className, id);
        docIdToClassName.put(id, className);
        return id;
    }

    @Override
    public void indexTokens(int docId, Bm25Field field, List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        Map<String, IntIntHashMap> termMap = fieldIndexes.get(field);
        ObjectIntMap<String> termDocCountMap = fieldTermDocCounts.get(field);
        IntIntMap docLengths = fieldDocLengths.get(field);

        Map<String, Integer> termFreqInDoc = new HashMap<>();
        for (String token : tokens) {
            termFreqInDoc.merge(token, 1, Integer::sum);
        }

        for (Map.Entry<String, Integer> entry : termFreqInDoc.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();

            IntIntHashMap postings = termMap.computeIfAbsent(term, k -> new IntIntHashMap());
            postings.put(docId, tf);

            termDocCountMap.putOrAdd(term, 1, 1);
        }

        int currentLength = docLengths.getOrDefault(docId, 0);
        docLengths.put(docId, currentLength + tokens.size());
    }

    public void finishBuild() {
        int N = nextDocId;
        if (N == 0) {
            built = true;
            return;
        }
        for (Bm25Field field : Bm25Field.values()) {
            IntIntHashMap docLengths = fieldDocLengths.get(field);
            long total = 0;
            int nonZeroDocs = 0;
            for (IntIntCursor c : docLengths) {
                if (c.value > 0) {
                    total += c.value;
                    nonZeroDocs++;
                }
            }
            double avg = nonZeroDocs > 0 ? (double) total / nonZeroDocs : 1.0;
            fieldAvgDl.put(field, avg);
        }
        built = true;
    }

    @Override
    public List<Bm25Result> search(String query, int topN) {
        if (!built || nextDocId == 0) {
            return Collections.emptyList();
        }

        List<String> queryTokens = tokenizer.tokenize(query);
        if (queryTokens.isEmpty()) {
            return Collections.emptyList();
        }

        String simpleToken = tokenizer.simpleNameToken(query);
        if (simpleToken != null && simpleToken.length() > 1 && !queryTokens.contains(simpleToken)) {
            queryTokens = new ArrayList<>(queryTokens);
            queryTokens.add(simpleToken);
        }

        Set<String> uniqueQueryTokens = new LinkedHashSet<>(queryTokens);
        int N = nextDocId;
        Bm25Field[] fields = Bm25Field.values();

        double[][] fieldScores = new double[fields.length][N];
        double[] totalScores = new double[N];

        for (String qTerm : uniqueQueryTokens) {
            for (int fi = 0; fi < fields.length; fi++) {
                Bm25Field field = fields[fi];
                Map<String, IntIntHashMap> termMap = fieldIndexes.get(field);
                IntIntHashMap postings = termMap.get(qTerm);
                if (postings == null || postings.isEmpty()) {
                    continue;
                }

                ObjectIntMap<String> termDocCounts = fieldTermDocCounts.get(field);
                int n = termDocCounts.getOrDefault(qTerm, 0);
                if (n == 0) {
                    continue;
                }

                double idf = Math.log(1.0 + (N - n + 0.5) / (n + 0.5));
                double weight = field.weight();
                double avgDl = fieldAvgDl.get(field);
                if (avgDl == 0.0) {
                    avgDl = 1.0;
                }
                IntIntMap docLengths = fieldDocLengths.get(field);

                for (IntIntCursor cursor : postings) {
                    int docId = cursor.key;
                    int tf = cursor.value;
                    double dl = docLengths.getOrDefault(docId, 1);
                    double numerator = tf * (K1 + 1.0);
                    double denominator = tf + K1 * (1.0 - B + B * dl / avgDl);
                    double score = weight * idf * (numerator / denominator);
                    fieldScores[fi][docId] += score;
                    totalScores[docId] += score;
                }
            }
        }

        List<Bm25Result> results = new ArrayList<>();
        for (int docId = 0; docId < N; docId++) {
            if (totalScores[docId] > 0) {
                String className = docIdToClassName.get(docId);

                double bestFieldScore = 0;
                String bestField = "";
                for (int fi = 0; fi < fields.length; fi++) {
                    if (fieldScores[fi][docId] > bestFieldScore) {
                        bestFieldScore = fieldScores[fi][docId];
                        bestField = fields[fi].id();
                    }
                }

                results.add(new Bm25Result(className, totalScores[docId],
                        String.join(",", uniqueQueryTokens), bestField, 0, 0));
            }
        }

        results.sort((a, b) -> Double.compare(b.score(), a.score()));
        if (results.size() > topN) {
            return results.subList(0, topN);
        }
        return results;
    }

    @Override
    public int getDocumentCount() {
        return nextDocId;
    }

    @Override
    public String getClassName(int docId) {
        return docIdToClassName.get(docId);
    }

    @Override
    public boolean isBuilt() {
        return built;
    }

    public ClassNameTokenizer getTokenizer() {
        return tokenizer;
    }

    Map<Bm25Field, Map<String, IntIntHashMap>> getFieldIndexes() {
        return fieldIndexes;
    }

    Map<Bm25Field, ObjectIntMap<String>> getFieldTermDocCounts() {
        return fieldTermDocCounts;
    }

    Map<Bm25Field, IntIntHashMap> getFieldDocLengths() {
        return fieldDocLengths;
    }

    Map<Bm25Field, Double> getFieldAvgDl() {
        return fieldAvgDl;
    }

    int getNextDocId() {
        return nextDocId;
    }
}
