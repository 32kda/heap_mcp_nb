package com.onpositive.analyzer.mcp;

import com.onpositive.analyzer.HeapDumpService;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import org.netbeans.lib.profiler.heap.HeapSummary;
import org.netbeans.lib.profiler.heap.GCRoot;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * MCP Tool Adapter.
 * Translates MCP requests into Service calls.
 */
public class HeapDumpTools {

    private final HeapDumpService heapDumpService;

    // Constructor Injection ensures no magic instantiation
    public HeapDumpTools(HeapDumpService heapDumpService) {
        this.heapDumpService = heapDumpService;
    }

    public SyncToolSpecification loadHeapTool() {
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("file_path", String.class),
                List.of("file_path"),
                false, null, null
        );

        McpSchema.Tool tool = new McpSchema.Tool(
                "load_heap",
                "Load Heap Dump",
                "Loads a .hprof heap dump file and returns its summary.",
                inputSchema,
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String filePath = (String) args.get("file_path");
                HeapSummary summary = heapDumpService.loadHeap(filePath);
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(formatSummary(summary))))
                        .isError(false)
                        .build();
            } catch (Exception e) {
                return errorResult("Failed to load heap: " + e.getMessage());
            }
        });
    }

    public SyncToolSpecification getAllClassesTool() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "get_all_classes",
                "Get All Classes",
                "Returns a list of all classes in the loaded heap.",
                new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null),
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                List<JavaClass> classes = heapDumpService.getAllClasses();
                String result = classes.stream()
                        .map(JavaClass::getName)
                        .collect(Collectors.joining("\n"));
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(result)))
                        .isError(false)
                        .build();
            } catch (Exception e) {
                return errorResult(e.getMessage());
            }
        });
    }

    public SyncToolSpecification getBiggestObjectsTool() {
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("limit", Integer.class),
                List.of("limit"),
                false, null, null
        );

        McpSchema.Tool tool = new McpSchema.Tool(
                "get_biggest_objects",
                "Get Biggest Objects",
                "Returns the biggest objects by retained size.",
                inputSchema,
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                int limit = ((Number) args.get("limit")).intValue();
                List<Instance> instances = heapDumpService.getBiggestObjectsByRetainedSize(limit);
                StringBuilder sb = new StringBuilder();
                for (Instance inst : instances) {
                    sb.append(String.format("ID: %d, Class: %s, Retained Size: %d\n",
                            inst.getInstanceId(), inst.getJavaClass().getName(), inst.getRetainedSize()));
                }
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(sb.toString())))
                        .isError(false)
                        .build();
            } catch (Exception e) {
                return errorResult(e.getMessage());
            }
        });
    }

    public SyncToolSpecification getGCRootsTool() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "get_gc_roots",
                "Get GC Roots",
                "Returns the GC roots of the loaded heap.",
                new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null),
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                Collection<GCRoot> roots = heapDumpService.getGCRoots();
                String result = roots.stream()
                        .filter(root -> root.getInstance() != null)
                        .map(root -> "ID: " + root.getInstance().getInstanceId() + ", Class: " + root.getInstance().getJavaClass().getName())
                        .collect(Collectors.joining("\n"));
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(result)))
                        .isError(false)
                        .build();
            } catch (Exception e) {
                return errorResult(e.getMessage());
            }
        });
    }

    public SyncToolSpecification getJavaClassByNameTool() {
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("name", String.class),
                List.of("name"),
                false, null, null
        );

        McpSchema.Tool tool = new McpSchema.Tool(
                "get_class_by_name",
                "Get Class By Name",
                "Returns class details by its full name.",
                inputSchema,
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String name = (String) args.get("name");
                JavaClass cls = heapDumpService.getJavaClassByName(name);
                if (cls == null) return errorResult("Class not found: " + name);
                String info = String.format("Name: %s\nInstances: %d\nTotal Size: %d",
                        cls.getName(), cls.getInstancesCount(), cls.getAllInstancesSize());
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(info)))
                        .isError(false)
                        .build();
            } catch (Exception e) {
                return errorResult(e.getMessage());
            }
        });
    }

    public SyncToolSpecification getJavaClassByIdTool() {
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("id", long.class),
                List.of("id"),
                false, null, null
        );

        McpSchema.Tool tool = new McpSchema.Tool(
                "get_class_by_id",
                "Get Class By ID",
                "Returns class details by its internal ID.",
                inputSchema,
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                long id = (long) args.get("id");
                JavaClass cls = heapDumpService.getJavaClassById(id);
                if (cls == null) return errorResult("Class not found: " + id);
                String info = String.format("Name: %s\nInstances: %d\nTotal Size: %d",
                        cls.getName(), cls.getInstancesCount(), cls.getAllInstancesSize());
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(info)))
                        .isError(false)
                        .build();
            } catch (Exception e) {
                return errorResult(e.getMessage());
            }
        });
    }

    public SyncToolSpecification getJavaClassesByRegExpTool() {
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("regexp", String.class),
                List.of("regexp"),
                false, null, null
        );

        McpSchema.Tool tool = new McpSchema.Tool(
                "get_classes_by_regexp",
                "Get Classes By RegExp",
                "Returns classes matching the regular expression.",
                inputSchema,
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String regexp = (String) args.get("regexp");
                Collection<JavaClass> classes = heapDumpService.getJavaClassesByRegExp(regexp);
                String result = classes.stream()
                        .map(cls -> cls.getName() + " (Instances: " + cls.getInstancesCount() + ")")
                        .collect(Collectors.joining("\n"));
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(result)))
                        .isError(false)
                        .build();
            } catch (Exception e) {
                return errorResult(e.getMessage());
            }
        });
    }

    public SyncToolSpecification getSummaryTool() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "get_summary",
                "Get Heap Summary",
                "Returns the summary of the loaded heap.",
                new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null),
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                HeapSummary summary = heapDumpService.getSummary();
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(formatSummary(summary))))
                        .isError(false)
                        .build();
            } catch (Exception e) {
                return errorResult(e.getMessage());
            }
        });
    }

    public SyncToolSpecification getSystemPropertiesTool() {
        McpSchema.Tool tool = new McpSchema.Tool(
                "get_system_properties",
                "Get System Properties",
                "Returns system properties from the heap dump.",
                new McpSchema.JsonSchema("object", Map.of(), List.of(), false, null, null),
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                Properties props = heapDumpService.getSystemProperties();
                StringBuilder sb = new StringBuilder();
                props.forEach((k, v) -> sb.append(k).append("=").append(v).append("\n"));
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(sb.toString())))
                        .isError(false)
                        .build();
            } catch (Exception e) {
                return errorResult(e.getMessage());
            }
        });
    }

    public SyncToolSpecification executeOqlTool() {
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "query", String.class,
                        "max_results", Integer.class
                ),
                List.of("query"),
                false, null, null
        );

        McpSchema.Tool tool = new McpSchema.Tool(
                "execute_oql",
                "Execute OQL Query",
                "Executes an OQL query on the heap dump.",
                inputSchema,
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String query = (String) args.get("query");
                Integer maxResultsObj = (Integer) args.get("max_results");
                int maxResults = (maxResultsObj != null) ? maxResultsObj : 100;

                String result = heapDumpService.executeOql(query, maxResults);
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(result)))
                        .isError(false)
                        .build();
            } catch (Exception e) {
                return errorResult(e.getMessage());
            }
        });
    }

    private String formatSummary(HeapSummary summary) {
        return String.format("Total Instances: %d\nTotal Size: %d bytes\nTime: %d",
                summary.getTotalLiveInstances(), summary.getTotalLiveBytes(), summary.getTime());
    }

    private McpSchema.CallToolResult errorResult(String message) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(message)))
                .isError(true)
                .build();
    }

    public McpServerFeatures.SyncToolSpecification analyzeHeapTool() {
        // 1. Define Input Schema
        // Arguments: file_path (string, required), limit (integer, optional, default 10)
        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                    "file_path", String.class,
                    "limit", Integer.class
                ),
                List.of("file_path"), // required fields
                false,
                null,
                null
        );

        // 2. Define Tool Metadata
        McpSchema.Tool tool = new McpSchema.Tool(
                "analyze_heap_dump",
                "Analyze Heap Dump",
                "Parses a .hprof heap dump file and returns the top classes by instance count.",
                inputSchema,
                null, null, null
        );

        // 3. Define Execution Logic (Handler)
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> {
            try {
                String filePath = (String) args.get("file_path");
                
                // Handle optional 'limit' argument
                Integer limitObj = (Integer) args.get("limit");
                int limit = (limitObj != null) ? limitObj : 10;

                // Call Domain Layer
                List<HeapDumpService.ClassStats> stats = heapDumpService.getTopClasses(filePath, limit);

                // Format Result for MCP
                StringBuilder sb = new StringBuilder();
                sb.append("Top ").append(stats.size()).append(" Classes in Heap Dump:\n");
                sb.append(String.format("%-50s | %-10s | %-10s%n", "Class Name", "Count", "Size"));
                sb.append("-".repeat(75)).append("\n");

                for (HeapDumpService.ClassStats stat : stats) {
                    sb.append(String.format("%-50s | %-10d | %-10d%n", 
                            truncate(stat.className, 50), stat.instanceCount, stat.size));
                }

                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(sb.toString())))
                        .isError(false)
                        .build();

            } catch (IOException e) {
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent("Failed to parse heap dump: " + e.getMessage())))
                        .isError(true)
                        .build();
            } catch (Exception e) {
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent("Unexpected error: " + e.getMessage())))
                        .isError(true)
                        .build();
            }
        });
    }
    
    private String truncate(String str, int len) {
        if (str.length() <= len) return str;
        return str.substring(0, len - 3) + "...";
    }
}