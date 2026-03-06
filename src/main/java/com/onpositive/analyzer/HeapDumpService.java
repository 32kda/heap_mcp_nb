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
    private List<ClassStats> classesSortedByCount;
    private List<ClassStats> classesSortedBySize;

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

    public List<ClassStats> getClassesByMaxInstancesCount(int from, int to) {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        if (classesSortedByCount == null) {
            classesSortedByCount = ((Collection<JavaClass>) heap.getAllClasses()).stream()
                    .map(cls -> new ClassStats(cls.getName(), cls.getInstancesCount(), cls.getAllInstancesSize()))
                    .sorted(Comparator.comparingLong((ClassStats cs) -> cs.instanceCount).reversed())
                    .collect(Collectors.toList());
        }
        int safeTo = Math.min(to, classesSortedByCount.size());
        int safeFrom = Math.min(from, safeTo);
        return classesSortedByCount.subList(safeFrom, safeTo);
    }

    public List<ClassStats> getClassesByMaxInstancesSize(int from, int to) {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        if (classesSortedBySize == null) {
            classesSortedBySize = ((Collection<JavaClass>) heap.getAllClasses()).stream()
                    .map(cls -> new ClassStats(cls.getName(), cls.getInstancesCount(), cls.getAllInstancesSize()))
                    .sorted(Comparator.comparingLong((ClassStats cs) -> cs.size).reversed())
                    .collect(Collectors.toList());
        }
        int safeTo = Math.min(to, classesSortedBySize.size());
        int safeFrom = Math.min(from, safeTo);
        return classesSortedBySize.subList(safeFrom, safeTo);
    }

    public List<Instance> getBiggestObjectsByRetainedSize(int limit) {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        return heap.getBiggestObjectsByRetainedSize(limit);
    }

    public Collection<GCRoot> getGCRoots() {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        return heap.getGCRoots();
    }

    public static class GCRootInfo {
        public String kind;
        public long instanceId;
        public String instanceClassName;

        public GCRootInfo(String kind, long instanceId, String instanceClassName) {
            this.kind = kind;
            this.instanceId = instanceId;
            this.instanceClassName = instanceClassName;
        }
    }

    public List<GCRootInfo> getGCRootsPaginated(int from, int to) {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        Collection<GCRoot> allRoots = heap.getGCRoots();
        List<GCRoot> rootsList = new ArrayList<>(allRoots);
        int safeTo = Math.min(to, rootsList.size());
        int safeFrom = Math.min(from, safeTo);
        List<GCRoot> page = rootsList.subList(safeFrom, safeTo);
        List<GCRootInfo> result = new ArrayList<>();
        for (GCRoot root : page) {
            Instance inst = root.getInstance();
            if (inst != null) {
                result.add(new GCRootInfo(
                        root.getKind(),
                        inst.getInstanceId(),
                        inst.getJavaClass().getName()
                ));
            }
        }
        return result;
    }

    public JavaClass getJavaClassByName(String name) {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        return heap.getJavaClassByName(name);
    }

    public JavaClass getJavaClassById(long id) {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        return heap.getJavaClassByID(id);
    }

    public static class InstanceInfo {
        public long instanceId;
        public String className;
        public long size;
        public long retainedSize;
        public List<String> fields;

        public InstanceInfo(long instanceId, String className, long size, long retainedSize, List<String> fields) {
            this.instanceId = instanceId;
            this.className = className;
            this.size = size;
            this.retainedSize = retainedSize;
            this.fields = fields;
        }
    }

    public InstanceInfo getInstanceById(long id) {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        Instance instance = heap.getInstanceByID(id);
        if (instance == null) return null;
        
        List<String> fields = new ArrayList<>();
        for (Object field : instance.getFieldValues()) {
            fields.add(String.valueOf(field));
        }
        
        return new InstanceInfo(
                instance.getInstanceId(),
                instance.getJavaClass().getName(),
                instance.getSize(),
                instance.getRetainedSize(),
                fields
        );
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
        return getClassesByMaxInstancesCount(0, limit);
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
