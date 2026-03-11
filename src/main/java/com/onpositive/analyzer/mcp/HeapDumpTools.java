package com.onpositive.analyzer.mcp;

import com.onpositive.analyzer.HeapDumpService;
import com.onpositive.analyzer.JavaClassPrinter;
import com.onpositive.analyzer.JavaClassPrinter.ClassDetails;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import org.netbeans.lib.profiler.heap.HeapSummary;
import org.netbeans.lib.profiler.heap.GCRoot;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
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
        McpSchema.JsonSchema filePathSchema = new McpSchema.JsonSchema(
                "string", // The type must be a string literal matching JSON types
                null,     // properties (null for primitive types)
                null,     // required (null for primitive types)
                false, null, null
        );

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("file_path", filePathSchema),
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

        return new SyncToolSpecification(tool, (exchange, request) -> {
            try {
                Map<String, Object> args = request.arguments();
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

    public SyncToolSpecification getClassesByMaxInstancesCountTool() {
        McpSchema.JsonSchema fromSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema toSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("from", fromSchema, "to", toSchema),
                List.of(),
                false, null, null
        );

        McpSchema.Tool tool = new McpSchema.Tool(
                "get_classes_by_max_instances_count",
                "Get Classes By Max Instances Count",
                "Returns a sorted list of classes by instance count (descending) with pagination.",
                inputSchema,
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            try {
                Number fromObj = (Number) args.get("from");
                Number toObj = (Number) args.get("to");
                int from = (fromObj != null) ? fromObj.intValue() : 0;
                int to = (toObj != null) ? toObj.intValue() : 50;

                List<HeapDumpService.ClassStats> classes = heapDumpService.getClassesByMaxInstancesCount(from, to);
                String result = classes.stream()
                        .map(cs -> cs.className + " (Count: " + cs.instanceCount + ", Size: " + cs.size + ")")
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

    public SyncToolSpecification getClassesByMaxInstancesSizeTool() {
        McpSchema.JsonSchema fromSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema toSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("from", fromSchema, "to", toSchema),
                List.of(),
                false, null, null
        );

        McpSchema.Tool tool = new McpSchema.Tool(
                "get_classes_by_max_instances_size",
                "Get Classes By Max Instances Size",
                "Returns a sorted list of classes by total instance size (descending) with pagination.",
                inputSchema,
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            try {
                Number fromObj = (Number) args.get("from");
                Number toObj = (Number) args.get("to");
                int from = (fromObj != null) ? fromObj.intValue() : 0;
                int to = (toObj != null) ? toObj.intValue() : 50;

                List<HeapDumpService.ClassStats> classes = heapDumpService.getClassesByMaxInstancesSize(from, to);
                String result = classes.stream()
                        .map(cs -> cs.className + " (Count: " + cs.instanceCount + ", Size: " + cs.size + ")")
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
        McpSchema.JsonSchema limitSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("limit", limitSchema),
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

        return new SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            try {
                int limit = ((Number) args.get("limit")).intValue();
                List<Instance> instances = heapDumpService.getBiggestObjectsByRetainedSize(limit);
                StringBuilder sb = new StringBuilder();
                int count = 0;
                for (Instance inst : instances) {
                    try {
                        long instanceId = inst.getInstanceId();
                        String className = inst.getJavaClass().getName();
                        long retainedSize = inst.getRetainedSize();
                        sb.append(String.format("ID: %d, Class: %s, Retained Size: %d\n",
                                instanceId, className, retainedSize));
                        count++;
                    } catch (Exception e) {
                        // Skip objects with invalid instance references
                    }
                }
                if (count == 0) {
                    return errorResult("No valid instances found");
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
        McpSchema.JsonSchema fromSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema toSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("from", fromSchema, "to", toSchema),
                List.of(),
                false, null, null
        );

        McpSchema.Tool tool = new McpSchema.Tool(
                "get_gc_roots",
                "Get GC Roots",
                "Returns the GC roots of the loaded heap.",
                inputSchema,
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            if (args == null) {
                args = new HashMap<>();
            } else {
                args = new HashMap<>(args);
            }
            args.putIfAbsent("from", 0);
            args.putIfAbsent("to", 50);
            McpSchema.CallToolRequest delegateRequest = new McpSchema.CallToolRequest("get_gc_roots_paginated", args);
            return getGCRootsPaginatedTool().callHandler().apply(exchange, delegateRequest);
        });
    }

    public SyncToolSpecification getGCRootsPaginatedTool() {
        McpSchema.JsonSchema fromSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema toSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("from", fromSchema, "to", toSchema),
                List.of(),
                false, null, null
        );

        McpSchema.Tool tool = new McpSchema.Tool(
                "get_gc_roots_paginated",
                "Get GC Roots Paginated",
                "Returns GC roots with pagination, including kind and instance information.",
                inputSchema,
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            try {
                Number fromObj = (Number) args.get("from");
                Number toObj = (Number) args.get("to");
                int from = (fromObj != null) ? fromObj.intValue() : 0;
                int to = (toObj != null) ? toObj.intValue() : 50;

                List<HeapDumpService.GCRootInfo> roots = heapDumpService.getGCRootsPaginated(from, to);
                String result = roots.stream()
                        .map(root -> "Kind: " + root.kind + ", Instance ID: " + root.instanceId + ", Class: " + root.instanceClassName)
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
        McpSchema.JsonSchema nameSchema = new McpSchema.JsonSchema(
                "string",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("name", nameSchema),
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

        return new SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            try {
                String name = (String) args.get("name");
                JavaClass cls = heapDumpService.getJavaClassByName(name);
                if (cls == null) return errorResult("Class not found: " + name);
                ClassDetails details = JavaClassPrinter.getClassDetails(cls);
                String info = JavaClassPrinter.printClassDetails(details);
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
        McpSchema.JsonSchema idSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("id", idSchema),
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

        return new SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            try {
                long id = ((Number) args.get("id")).longValue();
                JavaClass cls = heapDumpService.getJavaClassById(id);
                if (cls == null) return errorResult("Class not found: " + id);
                ClassDetails details = JavaClassPrinter.getClassDetails(cls);
                String info = JavaClassPrinter.printClassDetails(details);
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(info)))
                        .isError(false)
                        .build();
            } catch (Exception e) {
                return errorResult(e.getMessage());
            }
        });
    }

    public SyncToolSpecification getInstanceByIdTool() {
        McpSchema.JsonSchema idSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("id", idSchema),
                List.of("id"),
                false, null, null
        );

        McpSchema.Tool tool = new McpSchema.Tool(
                "get_instance_by_id",
                "Get Instance By ID",
                "Returns instance details by its internal ID, including class, size, retained size, and field values.",
                inputSchema,
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            try {
                long id = ((Number) args.get("id")).longValue();
                HeapDumpService.InstanceInfo instance = heapDumpService.getInstanceById(id);
                if (instance == null) return errorResult("Instance not found: " + id);
                
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Instance ID: %d%n", instance.instanceId));
                sb.append(String.format("Class: %s%n", instance.className));
                sb.append(String.format("Size: %d%n", instance.size));
                sb.append(String.format("Retained Size: %d%n", instance.retainedSize));
                sb.append("Field Values:\n");
                for (HeapDumpService.FieldInfo field : instance.fields) {
                    if (field.objectInstanceId != null) {
                        sb.append(String.format("  %s: %s (Instance ID: %d)%n", field.name, field.value, field.objectInstanceId));
                    } else {
                        sb.append(String.format("  %s: %s%n", field.name, field.value));
                    }
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

    public SyncToolSpecification getAllReferencesTool() {
        McpSchema.JsonSchema idSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema fromSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema toSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("id", idSchema, "from", fromSchema, "to", toSchema),
                List.of("id"),
                false, null, null
        );

        McpSchema.Tool tool = new McpSchema.Tool(
                "get_all_references",
                "Get All References",
                "Returns all references to an instance by its ID with pagination.",
                inputSchema,
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            try {
                long id = ((Number) args.get("id")).longValue();
                Number fromObj = (Number) args.get("from");
                Number toObj = (Number) args.get("to");
                int from = (fromObj != null) ? fromObj.intValue() : 0;
                int to = (toObj != null) ? toObj.intValue() : 50;

                List<HeapDumpService.ReferenceInfo> refs = heapDumpService.getAllReferences(id, from, to);
                String result = refs.stream()
                        .map(ref -> "Instance ID: " + ref.instanceId + ", Class: " + ref.className)
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

    public SyncToolSpecification getJavaClassesByRegExpTool() {
        McpSchema.JsonSchema regexpSchema = new McpSchema.JsonSchema(
                "string",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema fromSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema toSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of("regexp", regexpSchema, "from", fromSchema, "to", toSchema),
                List.of("regexp"),
                false, null, null
        );

        McpSchema.Tool tool = new McpSchema.Tool(
                "get_classes_by_regexp",
                "Get Classes By RegExp",
                "Returns classes matching the regular expression with pagination.",
                inputSchema,
                null, null, null
        );

        return new SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            try {
                String regexp = (String) args.get("regexp");
                Number fromObj = (Number) args.get("from");
                Number toObj = (Number) args.get("to");
                int from = (fromObj != null) ? fromObj.intValue() : 0;
                int to = (toObj != null) ? toObj.intValue() : 50;

                List<JavaClass> classes = heapDumpService.getJavaClassesByRegExpPaginated(regexp, from, to);
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

        return new SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
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

        return new SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
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
        McpSchema.JsonSchema querySchema = new McpSchema.JsonSchema(
                "string",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema maxResultsSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                        "query", querySchema,
                        "max_results", maxResultsSchema
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

        return new SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            try {
                String query = (String) args.get("query");
                Number maxResultsObj = (Number) args.get("max_results");
                int maxResults = (maxResultsObj != null) ? maxResultsObj.intValue() : 100;

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
        McpSchema.JsonSchema filePathSchema = new McpSchema.JsonSchema(
                "string",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema limitSchema = new McpSchema.JsonSchema(
                "integer",
                null,
                null,
                false, null, null
        );

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                Map.of(
                    "file_path", filePathSchema,
                    "limit", limitSchema
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
        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            Map<String, Object> args = request.arguments();
            try {
                String filePath = (String) args.get("file_path");
                
                // Handle optional 'limit' argument
                Number limitObj = (Number) args.get("limit");
                int limit = (limitObj != null) ? limitObj.intValue() : 10;

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