package com.onpositive.analyzer.search;

import org.netbeans.lib.profiler.heap.Field;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.JavaClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class HeapDumpBm25Indexer {

    private final Bm25Index index;
    private final ClassSkippedPredicate skipPredicate;
    private final ClassNameTokenizer tokenizer;

    public HeapDumpBm25Indexer(Bm25Index index) {
        this(index, new DefaultClassSkippedPredicate(), new ClassNameTokenizer());
    }

    public HeapDumpBm25Indexer(Bm25Index index, ClassSkippedPredicate skipPredicate,
                               ClassNameTokenizer tokenizer) {
        this.index = index;
        this.skipPredicate = skipPredicate;
        this.tokenizer = tokenizer;
    }

    public Bm25Index buildIndex(Heap heap) {
        Collection<JavaClass> allClasses = (Collection<JavaClass>) heap.getAllClasses();
        List<JavaClass> orderedClasses = new ArrayList<>(allClasses);

        for (JavaClass javaClass : orderedClasses) {
            String className = javaClass.getName();
            if (skipPredicate.shouldSkip(className)) {
                continue;
            }

            int docId = index.getOrCreateDocId(className);

            List<String> nameTokens = tokenizer.tokenize(className);
            String simpleToken = tokenizer.simpleNameToken(className);
            if (simpleToken != null && simpleToken.length() > 1 && !nameTokens.contains(simpleToken)) {
                nameTokens = new ArrayList<>(nameTokens);
                nameTokens.add(simpleToken);
            }
            index.indexTokens(docId, Bm25Field.CLASS_NAME, nameTokens);

            JavaClass superClass = javaClass.getSuperClass();
            if (superClass != null) {
                String scName = superClass.getName();
                if (!"java.lang.Object".equals(scName)) {
                    List<String> scTokens = tokenizer.tokenize(scName);
                    String scSimple = tokenizer.simpleNameToken(scName);
                    if (scSimple != null && scSimple.length() > 1 && !scTokens.contains(scSimple)) {
                        scTokens = new ArrayList<>(scTokens);
                        scTokens.add(scSimple);
                    }
                    index.indexTokens(docId, Bm25Field.SUPER_CLASS, scTokens);
                }
            }

            Collection<Field> fields = javaClass.getFields();
            if (fields != null && !fields.isEmpty()) {
                List<String> fieldNameTokens = new ArrayList<>();
                List<String> fieldTypeTokens = new ArrayList<>();

                for (Field field : fields) {
                    String fieldName = field.getName();
                    if (fieldName != null) {
                        fieldNameTokens.addAll(tokenizer.tokenize(fieldName));
                    }

                    if (field.getType() != null) {
                        String typeName = field.getType().getName();
                        if (typeName != null) {
                            fieldTypeTokens.addAll(tokenizer.tokenize(typeName));
                            String ftSimple = tokenizer.simpleNameToken(typeName);
                            if (ftSimple != null && ftSimple.length() > 1
                                    && !fieldTypeTokens.contains(ftSimple)) {
                                fieldTypeTokens.add(ftSimple);
                            }
                        }
                    }
                }

                index.indexTokens(docId, Bm25Field.FIELD_NAMES, fieldNameTokens);
                index.indexTokens(docId, Bm25Field.FIELD_TYPES, fieldTypeTokens);
            }
        }

        if (index instanceof InMemoryBm25Index) {
            ((InMemoryBm25Index) index).finishBuild();
        }
        return index;
    }
}
