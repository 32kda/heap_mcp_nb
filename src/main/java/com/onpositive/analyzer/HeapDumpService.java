package com.onpositive.analyzer;

import com.onpositive.analyzer.search.Bm25Index;
import com.onpositive.analyzer.search.Bm25Result;
import com.onpositive.analyzer.search.ClassNameTokenizer;
import com.onpositive.analyzer.search.DefaultClassSkippedPredicate;
import com.onpositive.analyzer.search.HeapDumpBm25Indexer;
import com.onpositive.analyzer.search.InMemoryBm25Index;
import com.onpositive.analyzer.util.LRUCache;
import org.netbeans.lib.profiler.heap.*;
import org.netbeans.modules.profiler.oql.engine.api.OQLEngine;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private final LRUCache<String, List<JavaClass>> classesByRegexp = new LRUCache<>(10);

    private Bm25Index bm25Index;
    private final LRUCache<String, List<Bm25Result>> bm25SearchCache = new LRUCache<>(20);

    public static class InstancePage {
        public List<Instance> instances;
        public long totalCount;
        public long remaining;

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

    private static String getClassName(Instance inst) {
        if (inst.getJavaClass() == null || inst.getJavaClass().getName() == null) {
            return "";
        }
        String name = inst.getJavaClass().getName();
        if (name.equals("java.lang.Class")) {
            Object clzName = inst.getValueOfField("name");
            if (clzName != null) {
                return name + "<" + clzName + ">";
            }
        }
        return name;
    }

    private static String extractStringValue(Instance stringInstance) {
        Object valueField = stringInstance.getValueOfField("value");
        if (valueField == null) return "null";

        if (valueField instanceof PrimitiveArrayInstance array) {
            String typeName = array.getJavaClass().getName();
            List values = array.getValues();
            if (values == null) return "null";

            if ("char[]".equals(typeName)) {
                StringBuilder sb = new StringBuilder(values.size());
                for (Object v : values) {
                    if (v != null) {
                        if (v instanceof Character) {
                            sb.append((char) v);
                        } else {
                            sb.append(v.toString());
                        }
                    } else {
                        sb.append('?');
                    }
                }
                return sb.toString();
            } else if ("byte[]".equals(typeName)) {
                byte[] bytes = new byte[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    bytes[i] = ((Number) values.get(i)).byteValue();
                }
                Object coder = stringInstance.getValueOfField("coder");
                int coderValue = (coder instanceof Number) ? ((Number) coder).intValue() : 0;
                if (coderValue == 1) {
                    StringBuilder sb = new StringBuilder(bytes.length / 2);
                    for (int i = 0; i < bytes.length - 1; i += 2) {
                        char c = (char) (((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF));
                        sb.append(c);
                    }
                    return sb.toString();
                } else {
                    return new String(bytes, StandardCharsets.ISO_8859_1);
                }
            }
        }
        return String.valueOf(valueField);
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
        
        if (oqlEngine == null) {
            oqlEngine = new OQLEngine(heap);
        }

//        String convertedQuery = convertToNetBeansOql(query);
        
        StringBuilder resultBuilder = new StringBuilder();

        oqlEngine.executeQuery(query, new OQLEngine.ObjectVisitor() {
            int count = 0;

            @Override
            public boolean visit(Object o) {
                count++;
                if (count > maxResults) {
                    return false;
                }
                
                if (o instanceof PrimitiveArrayInstance arrayInstance) {
                    resultBuilder.append("Array:").append(getClassName(arrayInstance)).append(" values:[");
                    int to = Math.min(100, arrayInstance.getLength());
                    List values = arrayInstance.getValues();
                    for (int i = 0; i < to; i++) {
                        resultBuilder.append(values.get(i).toString());
                        if (i < to - 1) {
                            resultBuilder.append(",");
                        }
                    }
                    resultBuilder.append("]\n");
                } else if (o instanceof Instance) {
                    Instance inst = (Instance) o;
                    String className = inst.getJavaClass().getName();
                    if ("java.lang.String".equals(className)) {
                        resultBuilder.append(String.format("[%d] \"%s\"\n", count, extractStringValue(inst)));
                    } else {
                        resultBuilder.append(String.format("[%d] %s (ID: 0x%x, Size: %d)\n",
                                count,
                                className,
                                inst.getInstanceId(),
                                inst.getSize()));
                    }
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
    
    private String convertToNetBeansOql(String query) {
        query = query.trim();
        if (query.toLowerCase().startsWith("select")) {
            String lowerQuery = query.toLowerCase();
            int fromIndex = lowerQuery.indexOf("from");
            if (fromIndex > 0) {
                int classStart = fromIndex + 5;
                int whereIndex = lowerQuery.indexOf("where");
                int aliasEnd = whereIndex > 0 ? whereIndex : query.length();
                
                String className = query.substring(classStart, aliasEnd).trim();
                String alias = "o";
                String whereClause = "";
                
                String beforeFrom = query.substring(6, fromIndex).trim();
                if (beforeFrom.equals("*") || beforeFrom.isEmpty()) {
                    if (whereIndex > 0) {
                        whereClause = query.substring(whereIndex + 5).trim();
                        return "heap.forEachObject(function(" + alias + ") { if (" + whereClause + ") { print('" + alias + "'); } }, '" + className + "')";
                    }
                    return "heap.forEachObject(function(" + alias + ") { print('" + alias + "'); }, '" + className + "')";
                }
            }
        }
        return query;
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
