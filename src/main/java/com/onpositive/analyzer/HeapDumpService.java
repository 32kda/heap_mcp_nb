package com.onpositive.analyzer;

import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.HeapFactory;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.HeapSummary;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.GCRoot;
import org.netbeans.modules.profiler.oql.engine.api.OQLEngine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

public class HeapDumpService {

    private Heap heap;
    private OQLEngine oqlEngine;

    public static class ClassStats {
        public String className;
        public long instanceCount;
        public long size;

        public ClassStats(String className, long instanceCount, long size) {
            this.className = className;
            this.instanceCount = instanceCount;
            this.size = size;
        }
    }

    public HeapSummary loadHeap(String filePath) throws IOException {
        File heapFile = new File(filePath);
        if (!heapFile.exists()) {
            throw new IOException("Heap dump file not found: " + filePath);
        }
        heap = HeapFactory.createHeap(heapFile);
        return heap.getSummary();
    }

    public List<JavaClass> getAllClasses() {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        return new ArrayList<>(heap.getAllClasses());
    }

    public List<Instance> getBiggestObjectsByRetainedSize(int limit) {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        return heap.getBiggestObjectsByRetainedSize(limit);
    }

    public Collection<GCRoot> getGCRoots() {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        return heap.getGCRoots();
    }

    public JavaClass getJavaClassByName(String name) {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        return heap.getJavaClassByName(name);
    }

    public JavaClass getJavaClassById(long id) {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        return heap.getJavaClassByID(id);
    }

    public Collection<JavaClass> getJavaClassesByRegExp(String regexp) {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        return heap.getJavaClassesByRegExp(regexp);
    }

    public HeapSummary getSummary() {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        return heap.getSummary();
    }

    public Properties getSystemProperties() {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        return heap.getSystemProperties();
    }

    public List<ClassStats> getTopClasses(String filePath, int limit) throws IOException {
        loadHeap(filePath);
        
        List<JavaClass> classes = getAllClasses();
        return classes.stream()
                .sorted(Comparator.comparingLong(JavaClass::getInstancesCount).reversed())
                .limit(limit)
                .map(cls -> new ClassStats(
                        cls.getName(),
                        cls.getInstancesCount(),
                        cls.getAllInstancesSize()
                ))
                .collect(Collectors.toList());
    }

    public String executeOql(String query, int maxResults) throws Exception {
        if (oqlEngine == null) {
            oqlEngine = new OQLEngine(heap);
        }

        StringBuilder resultBuilder = new StringBuilder();

        oqlEngine.executeQuery(query, new OQLEngine.ObjectVisitor() {
            int count = 0;

            @Override
            public boolean visit(Object o) {
                count++;
                if (count > maxResults) {
                    return false;
                }

                if (o instanceof Instance) {
                    Instance inst = (Instance) o;
                    resultBuilder.append(String.format("[%d] %s (ID: 0x%x, Size: %d)\n",
                            count,
                            inst.getJavaClass().getName(),
                            inst.getInstanceId(),
                            inst.getSize()));
                } else if (o != null) {
                    resultBuilder.append(String.format("[%d] %s\n", count, o.toString()));
                }
                return true;
            }
        });

        if (resultBuilder.length() == 0) {
            return "No results found or empty result set.";
        }

        return "Query Results:\n" + resultBuilder.toString();
    }
}
