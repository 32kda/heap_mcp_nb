package com.onpositive.analyzer;

import com.onpositive.analyzer.search.Bm25Index;
import com.onpositive.analyzer.search.Bm25Result;
import com.onpositive.analyzer.search.ClassNameTokenizer;
import com.onpositive.analyzer.search.DefaultClassSkippedPredicate;
import com.onpositive.analyzer.search.HeapDumpBm25Indexer;
import com.onpositive.analyzer.search.InMemoryBm25Index;
import com.onpositive.analyzer.util.LRUCache;
import org.netbeans.lib.profiler.heap.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static com.onpositive.analyzer.util.ClassUtil.getClassName;

public class HeapDumpService {

    private Heap heap;
    private OqlQueryExecutor oqlExecutor;
    private List<ClassStats> classesSortedByCount;
    private List<ClassStats> classesSortedBySize;
    private final LRUCache<String, List<JavaClass>> classesByRegexp = new LRUCache<>(10);

    private Bm25Index bm25Index;
    private final LRUCache<String, List<Bm25Result>> bm25SearchCache = new LRUCache<>(20);

    public static class InstancePage {
        public final List<Instance> instances;
        public final long totalCount;
        public final long remaining;

        public InstancePage(List<Instance> instances, long totalCount, long remaining) {
            this.instances = instances;
            this.totalCount = totalCount;
            this.remaining = remaining;
        }
    }

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
                        getClassName(inst)
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
        public List<FieldInfo> fields;

        public InstanceInfo(long instanceId, String className, long size, long retainedSize, List<FieldInfo> fields) {
            this.instanceId = instanceId;
            this.className = className;
            this.size = size;
            this.retainedSize = retainedSize;
            this.fields = fields;
        }
    }

    public static class FieldInfo {
        public String name;
        public String value;
        public Long objectInstanceId;

        public FieldInfo(String name, String value, Long objectInstanceId) {
            this.name = name;
            this.value = value;
            this.objectInstanceId = objectInstanceId;
        }
    }

    public InstanceInfo getInstanceById(long id) {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        Instance instance = heap.getInstanceByID(id);
        if (instance == null) return null;
        
        List<FieldInfo> fields = new ArrayList<>();
        for (Object fvObj : instance.getFieldValues()) {
            FieldValue fv = (FieldValue) fvObj;
            String fieldName = fv.getField().getName();
            String valueStr = String.valueOf(fv.getValue());
            Long objectInstanceId = null;
            
            if (fv instanceof ObjectFieldValue) {
                ObjectFieldValue ofv = (ObjectFieldValue) fv;
                Instance refInstance = ofv.getInstance();
                if (refInstance != null) {
                    objectInstanceId = refInstance.getInstanceId();
                }
            }
            
            fields.add(new FieldInfo(fieldName, valueStr, objectInstanceId));
        }
        
        return new InstanceInfo(
                instance.getInstanceId(),
                getClassName(instance),
                instance.getSize(),
                instance.getRetainedSize(),
                fields
        );
    }

    public static class ReferenceInfo {
        public long instanceId;
        public String className;
        public String fieldName;

        public ReferenceInfo(long instanceId, String className, String fieldName) {
            this.instanceId = instanceId;
            this.className = className;
            this.fieldName = fieldName;
        }
    }

    public List<ReferenceInfo> getAllReferences(long instanceId, int from, int to) {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        Instance instance = heap.getInstanceByID(instanceId);
        if (instance == null) return new ArrayList<>();
        
        Collection<?> references = instance.getReferences();
        List<Instance> refsList = new ArrayList<>();
        for (Object refObj : references) {
            if (refObj instanceof FieldValue) {
                FieldValue fv = (FieldValue) refObj;
                if (fv instanceof ObjectFieldValue) {
                    ObjectFieldValue ofv = (ObjectFieldValue) fv;
                    Instance refInstance = ofv.getInstance();
                    if (refInstance != null) {
                        refsList.add(refInstance);
                    }
                }
            }
        }
        int safeTo = Math.min(to, refsList.size());
        int safeFrom = Math.min(from, safeTo);
        
        List<ReferenceInfo> result = new ArrayList<>();
        for (Instance ref : refsList.subList(safeFrom, safeTo)) {
            result.add(new ReferenceInfo(
                    ref.getInstanceId(),
                    getClassName(ref),
                    null
            ));
        }
        return result;
    }

    public List<JavaClass> getJavaClassesByRegExpPaginated(String regexp, int from, int to) {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        List<JavaClass> classesList = classesByRegexp.get(regexp);
        if (classesList == null) {
            classesList = new ArrayList<>(heap.getJavaClassesByRegExp(regexp));
        }
        int safeTo = Math.min(to, classesList.size());
        int safeFrom = Math.min(from, safeTo);
        return classesList.subList(safeFrom, safeTo);
    }

    public InstancePage getInstancesByClassName(String className, int from, int to) {
        if (heap == null) throw new IllegalStateException("Heap not loaded");
        JavaClass javaClass = heap.getJavaClassByName(className);
        if (javaClass == null) return new InstancePage(new ArrayList<>(), 0, 0);

        Collection<Instance> allInstances = javaClass.getInstances();
        List<Instance> instancesList = new ArrayList<>(allInstances);
        long totalCount = instancesList.size();

        int safeTo = Math.min(to, instancesList.size());
        int safeFrom = Math.min(from, safeTo);
        long remaining = totalCount - safeTo;
        if (remaining < 0) remaining = 0;

        return new InstancePage(instancesList.subList(safeFrom, safeTo), totalCount, remaining);
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
        if (heap == null) {
            throw new IllegalStateException("Heap not loaded. Please load a heap dump first.");
        }
        if (oqlExecutor == null) {
            oqlExecutor = new OqlQueryExecutor(heap);
        }
        return oqlExecutor.executeOql(query, maxResults);
    }

    private void buildBm25Index() {
        if (heap == null) {
            throw new IllegalStateException("Heap not loaded");
        }
        InMemoryBm25Index index = new InMemoryBm25Index();
        HeapDumpBm25Indexer indexer = new HeapDumpBm25Indexer(
                index, new DefaultClassSkippedPredicate(), new ClassNameTokenizer());
        indexer.buildIndex(heap);
        this.bm25Index = index;
    }

    public List<Bm25Result> searchClassesBm25(String query, int topN, int from) {
        if (heap == null) {
            throw new IllegalStateException("Heap not loaded");
        }
        if (bm25Index == null) {
            buildBm25Index();
        }

        String cacheKey = query + "|" + topN;
        List<Bm25Result> fullResults = bm25SearchCache.get(cacheKey);
        if (fullResults == null) {
            fullResults = bm25Index.search(query, Math.max(topN + from, topN));
            for (Bm25Result result : fullResults) {
                try {
                    JavaClass jc = heap.getJavaClassByName(result.className());
                    if (jc != null) {
                        result.setInstanceCount(jc.getInstancesCount());
                        result.setTotalSize(jc.getAllInstancesSize());
                    }
                } catch (RuntimeException ignored) {
                }
            }
            bm25SearchCache.put(cacheKey, fullResults);
        }

        int safeFrom = Math.min(from, fullResults.size());
        int safeTo = Math.min(safeFrom + topN, fullResults.size());
        return fullResults.subList(safeFrom, safeTo);
    }
}
