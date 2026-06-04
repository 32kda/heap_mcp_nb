package com.onpositive.analyzer.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Manual diagnostic test — requires a heap dump file on disk.
 * To enable: remove the @Disabled annotation and run individually.
 * Not intended for CI.
 */
@Disabled("Manual diagnostic test. Enable by removing @Disabled when inspecting index quality.")
public class Bm25IndexDiagnosticsTest {

    private Heap heap;

    @BeforeEach
    void setUp() throws IOException {
        File sampleFile = new File("D:/work/heapdump-1780452136629.hprof");
        if (!sampleFile.exists()) {
            throw new IOException("Heap dump not found: " + sampleFile.getAbsolutePath());
        }
        heap = HeapFactory.createHeap(sampleFile);
    }

    @Test
    void indexQualityDiagnostics() {
        ClassNameTokenizer tokenizer = new ClassNameTokenizer();
        DefaultClassSkippedPredicate skipPredicate = new DefaultClassSkippedPredicate();
        InMemoryBm25Index index = new InMemoryBm25Index();
        HeapDumpBm25Indexer indexer = new HeapDumpBm25Indexer(index, skipPredicate, tokenizer);

        indexer.buildIndex(heap);

        int totalDocs = index.getDocumentCount();
        int totalJavaClasses = ((List<?>) heap.getAllClasses()).size();
        int skipped = totalJavaClasses - totalDocs;

        System.out.println();
        System.out.println("══════════════════════════════════════════════════");
        System.out.println("  BM25 CLASS INDEX DIAGNOSTIC REPORT");
        System.out.println("══════════════════════════════════════════════════");
        System.out.println();
        System.out.printf("  Total classes in heap:        %,d%n", totalJavaClasses);
        System.out.printf("  Classes skipped (JDK/lib):    %,d%n", skipped);
        System.out.printf("  Classes indexed:              %,d%n", totalDocs);
        System.out.printf("  Indexing ratio:               %.1f%%%n",
                100.0 * totalDocs / Math.max(totalJavaClasses, 1));
        System.out.println();

        assertTrue(totalDocs > 0, "Expected at least one indexed class");

        // --- Top IDF terms (rarest / most discriminating) ---
        int topN = Math.min(30, totalDocs);
        System.out.println("──────────────────────────────────────────────────");
        System.out.printf("  TOP %d IDF TERMS (rarest, most distinguishing)%n", topN);
        System.out.println("  ────────────────────────────────────────────────");
        System.out.printf("  %-25s %-15s %10s %10s%n", "Term", "Field", "IDF", "DocFreq");
        System.out.println("  " + "-".repeat(65));

        Bm25IndexDiagnostics.IdFReport idfReport =
                Bm25IndexDiagnostics.computeTopIdfTerms(index, topN);
        for (Bm25IndexDiagnostics.IdFTerm t : idfReport.terms()) {
            System.out.printf("  %-25s %-15s %10.4f %10d%n",
                    truncate(t.term(), 25), t.field(), t.idf(), t.docFrequency());
        }
        System.out.println();

        // --- Bottom IDF terms (most common / least distinguishing) ---
        System.out.println("──────────────────────────────────────────────────");
        System.out.printf("  BOTTOM %d IDF TERMS (most common, least distinguishing)%n", topN);
        System.out.println("  ────────────────────────────────────────────────");
        System.out.printf("  %-25s %-15s %10s %10s%n", "Term", "Field", "IDF", "DocFreq");
        System.out.println("  " + "-".repeat(65));

        Bm25IndexDiagnostics.IdFReport bottomIdfReport =
                Bm25IndexDiagnostics.computeBottomIdfTerms(index, topN);
        for (Bm25IndexDiagnostics.IdFTerm t : bottomIdfReport.terms()) {
            System.out.printf("  %-25s %-15s %10.4f %10d%n",
                    truncate(t.term(), 25), t.field(), t.idf(), t.docFrequency());
        }
        System.out.println();

        // --- Top TF-IDF terms (highest impact on scoring) ---
        System.out.println("──────────────────────────────────────────────────");
        System.out.printf("  TOP %d TF‑IDF WEIGHTED TERMS%n", topN);
        System.out.println("  ────────────────────────────────────────────────");
        System.out.printf("  %-40s %15s%n", "Term@Field", "Total TF‑IDF");
        System.out.println("  " + "-".repeat(60));

        Bm25IndexDiagnostics.TfIdfReport tfidfReport =
                Bm25IndexDiagnostics.computeTopTfIdfTerms(index, topN);
        for (Bm25IndexDiagnostics.TfIdfTerm t : tfidfReport.terms()) {
            System.out.printf("  %-40s %15.4f%n",
                    truncate(t.displayName(), 40), t.totalTfIdf());
        }
        System.out.println();

        // --- Cosine similarity between documents ---
        System.out.println("──────────────────────────────────────────────────");
        System.out.println("  COSINE SIMILARITY BETWEEN DOCUMENTS");
        System.out.println("  ────────────────────────────────────────────────");

        Bm25IndexDiagnostics.CosineSimilarityReport cosReport =
                Bm25IndexDiagnostics.computeCosineSimilarity(index);

        System.out.printf("  Total documents:    %d%n", cosReport.totalDocuments());
        System.out.printf("  Sampled (capped):   %d%n", cosReport.sampledDocuments());
        System.out.printf("  Pairs compared:     %,d%n", cosReport.pairsCompared());
        System.out.printf("  Min similarity:     %.6f%n", cosReport.minSimilarity());
        System.out.printf("  Max similarity:     %.6f%n", cosReport.maxSimilarity());
        System.out.printf("  Mean similarity:    %.6f%n", cosReport.meanSimilarity());
        System.out.printf("  Median similarity:  %.6f%n", cosReport.medianSimilarity());
        System.out.printf("  Std deviation:      %.6f%n", cosReport.stdDevSimilarity());
        System.out.println();
        System.out.printf("  Max pair: %s%n", truncate(cosReport.maxPair(), 120));
        System.out.printf("  Min pair: %s%n", truncate(cosReport.minPair(), 120));
        System.out.println();

        System.out.println("  Histogram:");
        System.out.printf("  %-12s %s%n", "Range", "Count");
        System.out.println("  " + "-".repeat(40));
        int maxBucket = cosReport.histogram().stream()
                .mapToInt(Bm25IndexDiagnostics.SimilarityBucket::count).max().orElse(1);
        for (Bm25IndexDiagnostics.SimilarityBucket b : cosReport.histogram()) {
            String bar = "█".repeat(Math.max(1, 30 * b.count() / maxBucket));
            System.out.printf("  [%.1f-%.1f)    %5d  %s%n",
                    b.lower(), b.upper(), b.count(), bar);
        }
        System.out.println();

        System.out.printf("  Signal quality: %s%n", cosReport.signalQuality());
        System.out.println();
        System.out.println("══════════════════════════════════════════════════");
        System.out.println();

        assertTrue(cosReport.pairsCompared() > 0,
                "Expected at least one comparable document pair");
        assertNotEquals("TOO_HOMOGENEOUS", cosReport.signalQuality().substring(0,
                Math.min(15, cosReport.signalQuality().length())),
                "Index should not be completely homogeneous");
    }

    @Test
    void simpleIndexSearch() {
        InMemoryBm25Index index = new InMemoryBm25Index();
        HeapDumpBm25Indexer indexer = new HeapDumpBm25Indexer(index, new DefaultClassSkippedPredicate(), new ClassNameTokenizer());
        Bm25Index bm25Index = indexer.buildIndex(heap);
        List<Bm25Result> logFileResults = bm25Index.search("log file",10);
        System.out.println("Top for 'log file':");
        for (Bm25Result logFileResult : logFileResults) {
            System.out.println(logFileResult);
        }

    }

    private static String truncate(String s, int len) {
        if (s == null) return "(null)";
        if (s.length() <= len) return s;
        return s.substring(0, len - 3) + "...";
    }
}
