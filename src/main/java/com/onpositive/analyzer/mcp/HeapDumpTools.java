package com.onpositive.analyzer.mcp;

import com.onpositive.analyzer.HeapDumpService;
import com.onpositive.analyzer.mcp.reflection.Default;
import com.onpositive.analyzer.mcp.reflection.Printer;
import com.onpositive.analyzer.mcp.reflection.Required;
import com.onpositive.analyzer.mcp.reflection.Tool;
import com.onpositive.analyzer.printing.Bm25ResultListPrinter;
import com.onpositive.analyzer.printing.ClassStatsListPrinter;
import com.onpositive.analyzer.printing.GCRootInfoListPrinter;
import com.onpositive.analyzer.printing.HeapSummaryPrinter;
import com.onpositive.analyzer.printing.InstanceInfoPrinter;
import com.onpositive.analyzer.printing.InstanceListPrinter;
import com.onpositive.analyzer.printing.InstancePagePrinter;
import com.onpositive.analyzer.printing.JavaClassPrinterWrapper;
import com.onpositive.analyzer.printing.PropertiesPrinter;
import com.onpositive.analyzer.printing.ReferenceInfoListPrinter;
import com.onpositive.analyzer.search.Bm25Result;
import org.netbeans.lib.profiler.heap.HeapSummary;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;

import java.util.List;

public class HeapDumpTools {

    private final HeapDumpService heapDumpService;

    public HeapDumpTools(HeapDumpService heapDumpService) {
        this.heapDumpService = heapDumpService;
    }

    @Tool(name = "load_heap", title = "Load Heap Dump", decription = "Loads a .hprof heap dump file and returns its summary.")
    @Printer(impl = HeapSummaryPrinter.class)
    public HeapSummary loadHeap(@Required("file_path") String filePath) throws Exception {
        return heapDumpService.loadHeap(filePath);
    }

    @Tool(name = "get_classes_by_max_instances_count", title = "Get Classes By Max Instances Count", decription = "Returns a sorted list of classes by instance count (descending) with pagination.")
    @Printer(impl = ClassStatsListPrinter.class)
    public List<HeapDumpService.ClassStats> getClassesByMaxInstancesCount(
            @Default(name = "from", value = "0") int from,
            @Default(name = "to", value = "50") int to) {
        return heapDumpService.getClassesByMaxInstancesCount(from, to);
    }

    @Tool(name = "get_classes_by_max_instances_size", title = "Get Classes By Max Instances Size", decription = "Returns a sorted list of classes by total instance size (descending) with pagination.")
    @Printer(impl = ClassStatsListPrinter.class)
    public List<HeapDumpService.ClassStats> getClassesByMaxInstancesSize(
            @Default(name = "from", value = "0") int from,
            @Default(name = "to", value = "50") int to) {
        return heapDumpService.getClassesByMaxInstancesSize(from, to);
    }

    @Tool(name = "get_biggest_objects", title = "Get Biggest Objects", decription = "Returns the biggest objects by retained size.")
    @Printer(impl = InstanceListPrinter.class)
    public List<Instance> getBiggestObjects(@Required("limit") int limit) {
        return heapDumpService.getBiggestObjectsByRetainedSize(limit);
    }

    @Tool(name = "get_gc_roots", title = "Get GC Roots", decription = "Returns the GC roots of the loaded heap.")
    @Printer(impl = GCRootInfoListPrinter.class)
    public List<HeapDumpService.GCRootInfo> getGCRoots(
            @Default(name = "from", value = "0") int from,
            @Default(name = "to", value = "50") int to) {
        return heapDumpService.getGCRootsPaginated(from, to);
    }

    @Tool(name = "get_gc_roots_paginated", title = "Get GC Roots Paginated", decription = "Returns GC roots with pagination, including kind and instance information.")
    @Printer(impl = GCRootInfoListPrinter.class)
    public List<HeapDumpService.GCRootInfo> getGCRootsPaginated(
            @Default(name = "from", value = "0") int from,
            @Default(name = "to", value = "50") int to) {
        return heapDumpService.getGCRootsPaginated(from, to);
    }

    @Tool(name = "get_class_by_name", title = "Get Class By Name", decription = "Returns class details by its full name.")
    @Printer(impl = JavaClassPrinterWrapper.class)
    public JavaClass getJavaClassByName(@Required("name") String name) {
        return heapDumpService.getJavaClassByName(name);
    }

    @Tool(name = "get_class_by_id", title = "Get Class By ID", decription = "Returns class details by its internal ID.")
    @Printer(impl = JavaClassPrinterWrapper.class)
    public JavaClass getJavaClassById(@Required("id") long id) {
        return heapDumpService.getJavaClassById(id);
    }

    @Tool(name = "get_instance_by_id", title = "Get Instance By ID", decription = "Returns instance details by its internal ID, including class, size, retained size, and field values.")
    @Printer(impl = InstanceInfoPrinter.class)
    public HeapDumpService.InstanceInfo getInstanceById(@Required("id") long id) {
        return heapDumpService.getInstanceById(id);
    }

    @Tool(name = "get_all_references", title = "Get All References", decription = "Returns all references to an instance by its ID with pagination.")
    @Printer(impl = ReferenceInfoListPrinter.class)
    public List<HeapDumpService.ReferenceInfo> getAllReferences(
            @Required("id") long id,
            @Default(name = "from", value = "0") int from,
            @Default(name = "to", value = "50") int to) {
        return heapDumpService.getAllReferences(id, from, to);
    }

    @Tool(name = "get_classes_by_regexp", title = "Get Classes By RegExp", decription = "Returns classes matching the regular expression with pagination.")
    @Printer(impl = com.onpositive.analyzer.printing.JavaClassListPrinter.class)
    public List<JavaClass> getJavaClassesByRegExp(
            @Required("regexp") String regexp,
            @Default(name = "from", value = "0") int from,
            @Default(name = "to", value = "50") int to) {
        return heapDumpService.getJavaClassesByRegExpPaginated(regexp, from, to);
    }

    @Tool(name = "get_instances", title = "Get Instances", decription = "Returns instances of a class by class name with pagination, 10 per page.")
    @Printer(impl = InstancePagePrinter.class)
    public HeapDumpService.InstancePage getInstances(
            @Required("class_name") String className,
            @Default(name = "from", value = "0") int from,
            @Default(name = "to", value = "10") int to) {
        return heapDumpService.getInstancesByClassName(className, from, to);
    }

    @Tool(name = "get_summary", title = "Get Heap Summary", decription = "Returns the summary of the loaded heap.")
    @Printer(impl = HeapSummaryPrinter.class)
    public HeapSummary getSummary() {
        return heapDumpService.getSummary();
    }

    @Tool(name = "get_system_properties", title = "Get System Properties", decription = "Returns system properties from the heap dump.")
    @Printer(impl = PropertiesPrinter.class)
    public java.util.Properties getSystemProperties() {
        return heapDumpService.getSystemProperties();
    }

    @Tool(name = "execute_oql", title = "Execute OQL Query", decription = "Executes an OQL query on the heap dump. Query syntax example: 'select s.value from java.lang.String s'")
    public String executeOql(
            @Required("query") String query,
            @Default(name = "max_results", value = "100") int maxResults) throws Exception {
        return heapDumpService.executeOql(query, maxResults);
    }

    @Tool(name = "search_classes", title = "Search Classes (BM25)", decription = "Searches heap dump classes using BM25 full-text ranking on class names, superclass, field names and field types. Splits CamelCase and snake_case, filters stopwords.")
    @Printer(impl = Bm25ResultListPrinter.class)
    public List<Bm25Result> searchClasses(
            @Required("query") String query,
            @Default(name = "top_n", value = "25") int topN,
            @Default(name = "from", value = "0") int from) {
        return heapDumpService.searchClassesBm25(query, topN, from);
    }

    @Tool(name = "analyze_heap_dump", title = "Analyze Heap Dump", decription = "Parses a .hprof heap dump file and returns the top classes by instance count.")
    public String analyzeHeap(
            @Required("file_path") String filePath,
            @Default(name = "limit", value = "10") int limit) throws Exception {
        List<HeapDumpService.ClassStats> stats = heapDumpService.getTopClasses(filePath, limit);
        StringBuilder sb = new StringBuilder();
        sb.append("Top ").append(stats.size()).append(" Classes in Heap Dump:\n");
        sb.append(String.format("%-50s | %-10s | %-10s%n", "Class Name", "Count", "Size"));
        sb.append("-".repeat(75)).append("\n");
        for (HeapDumpService.ClassStats stat : stats) {
            sb.append(String.format("%-50s | %-10d | %-10d%n",
                    truncate(stat.className, 50), stat.instanceCount, stat.size));
        }
        return sb.toString();
    }

    private String truncate(String str, int len) {
        if (str.length() <= len) return str;
        return str.substring(0, len - 3) + "...";
    }


}
