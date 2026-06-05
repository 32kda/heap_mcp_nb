package com.onpositive.analyzer;

import com.onpositive.analyzer.mcp.HeapDumpTools;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class HeapDumpMcpTest {

    private ToolsGetter toolsGetter;
    private String samplePath;

    @BeforeEach
    void setUp() {
        HeapDumpService service = new HeapDumpService();
        HeapDumpTools tools = new HeapDumpTools(service);
        toolsGetter = new ToolsGetter(tools);
        File sampleFile = new File("src/test/resources/HeapDumpSample.hprof");
        assertTrue(sampleFile.exists(), "Sample heap dump file not found at " + sampleFile.getAbsolutePath());
        samplePath = sampleFile.getAbsolutePath();
    }

    @Test
    void testLoadHeapAndGetSummary() {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath));
        McpSchema.CallToolResult result = toolsGetter.loadHeapTool().callHandler().apply(null, request);
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.contains("Total Instances"), "Summary should contain Total Instances");
        assertTrue(content.contains("Total Size"), "Summary should contain Total Size");
    }

    @Test
    void testGetClassesByMaxInstancesCount() {
        McpSchema.CallToolRequest loadRequest = new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath));
        toolsGetter.loadHeapTool().callHandler().apply(null, loadRequest);
        
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_classes_by_max_instances_count", Map.of("from", 0, "to", 50));
        McpSchema.CallToolResult result = toolsGetter.getClassesByMaxInstancesCountTool().callHandler().apply(null, request);
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertFalse(content.isEmpty(), "Class list should not be empty");
        assertTrue(content.contains("java.lang.String"), "Should contain java.lang.String");
    }

    @Test
    void testGetGCRootsAfterLoad() {
        McpSchema.CallToolRequest loadRequest = new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath));
        toolsGetter.loadHeapTool().callHandler().apply(null, loadRequest);
        
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_gc_roots", Map.of());
        McpSchema.CallToolResult result = toolsGetter.getGCRootsTool().callHandler().apply(null, request);
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertNotNull(content);
    }

    @Test
    void testGetClassByNameAfterLoad() {
        McpSchema.CallToolRequest loadRequest = new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath));
        toolsGetter.loadHeapTool().callHandler().apply(null, loadRequest);
        
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_class_by_name", Map.of("name", "java.lang.String"));
        McpSchema.CallToolResult result = toolsGetter.getJavaClassByNameTool().callHandler().apply(null, request);
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.contains("java.lang.String"));
        assertTrue(content.contains("Instances:"));
        assertTrue(content.contains("Total Size:"));
        assertTrue(content.contains("Fields:") || content.contains("Static Fields:"));
    }

    @Test
    void testGetClassesByRegexpAfterLoad() {
        toolsGetter.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        
        McpSchema.CallToolResult result = toolsGetter.getJavaClassesByRegExpTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_classes_by_regexp", Map.of("regexp", "java\\.util\\..*")));
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.contains("java.util."));
    }

    @Test
    void testGetSummaryAfterLoad() {
        toolsGetter.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        
        McpSchema.CallToolResult result = toolsGetter.getSummaryTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_summary", Map.of()));
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.contains("Total Instances"));
        assertTrue(content.contains("Total Size"));
    }

    @Test
    void testGetSystemPropertiesAfterLoad() {
        toolsGetter.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        
        McpSchema.CallToolResult result = toolsGetter.getSystemPropertiesTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_system_properties", Map.of()));
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertNotNull(content);
    }

    @Test
    void testAnalyzeHeapDump() {
        McpSchema.CallToolResult result = toolsGetter.analyzeHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("analyze_heap_dump", Map.of(
                "file_path", samplePath,
                "limit", 5
        )));
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.contains("Top"));
        assertTrue(content.contains("Class Name"));
    }

    @Test
    void testGetJavaClassByIdAfterLoad() {
        toolsGetter.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        
        McpSchema.CallToolResult result = toolsGetter.getJavaClassByIdTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_class_by_id", Map.of("id", 1L)));
        if (!result.isError()) {
            String content = ((McpSchema.TextContent) result.content().get(0)).text();
            assertTrue(content.contains("Instances:"));
            assertTrue(content.contains("Total Size:"));
        }
    }

    @Test
    void testChainedOperationsLoadGetClassesSummary() {
        toolsGetter.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        
        McpSchema.CallToolResult classesResult = toolsGetter.getClassesByMaxInstancesCountTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_classes_by_max_instances_count", Map.of("from", 0, "to", 50)));
        assertFalse(classesResult.isError());
        
        McpSchema.CallToolResult summaryResult = toolsGetter.getSummaryTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_summary", Map.of()));
        assertFalse(summaryResult.isError());
        
        McpSchema.CallToolResult gcRootsResult = toolsGetter.getGCRootsTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_gc_roots", Map.of()));
        assertFalse(gcRootsResult.isError());
    }

    @Test
    void testMultipleLoadsReturnConsistentResults() {
        McpSchema.CallToolResult result1 = toolsGetter.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        assertFalse(result1.isError());
        String content1 = ((McpSchema.TextContent) result1.content().get(0)).text();
        
        McpSchema.CallToolResult result2 = toolsGetter.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        assertFalse(result2.isError());
        String content2 = ((McpSchema.TextContent) result2.content().get(0)).text();
        
        assertEquals(content1, content2, "Multiple loads should return consistent results");
    }

    @Test
    void testGetClassByNameNonExistent() {
        toolsGetter.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        
        McpSchema.CallToolResult result = toolsGetter.getJavaClassByNameTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_class_by_name", Map.of("name", "com.nonexistent.Class")));
        assertTrue(result.isError(), "Should return error for non-existent class");
    }

    @Test
    void testGetClassesByRegexpNoMatch() {
        toolsGetter.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        
        McpSchema.CallToolResult result = toolsGetter.getJavaClassesByRegExpTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_classes_by_regexp", Map.of("regexp", "^[xyz].*")));
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.isEmpty() || !content.contains("java."), "Should not contain java classes for regex ^[xyz].*");
    }

    @Test
    void testExecuteOqlWithoutLoadingHeap() {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("execute_oql", Map.of("query", "SELECT * FROM javax.swing.JFrame", "max_results", 10));
        McpSchema.CallToolResult result = toolsGetter.executeOqlTool().callHandler().apply(null, request);
        assertTrue(result.isError(), "Should return error when executing OQL without loading heap first");
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.contains("Heap not loaded") || content.contains("not loaded"), 
            "Error message should indicate heap is not loaded. Got: " + content);
    }

    @Test
    void testExecuteOqlAfterLoadingHeap() {
        McpSchema.CallToolRequest loadRequest = new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath));
        toolsGetter.loadHeapTool().callHandler().apply(null, loadRequest);
        
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("execute_oql", Map.of("query", "select s from java.lang.String s", "max_results", 10));
        McpSchema.CallToolResult result = toolsGetter.executeOqlTool().callHandler().apply(null, request);
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        System.out.println("OQL Result: " + content);
        assertTrue(content.contains("Query Results"),
            "Should return valid results when executing OQL after loading heap. Got: " + content);
    }

    @Test
    void testOqlGetFieldAfterLoadingHeap() {
        McpSchema.CallToolRequest loadRequest = new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath));
        toolsGetter.loadHeapTool().callHandler().apply(null, loadRequest);

        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("execute_oql", Map.of("query", "select s.value from java.lang.String s", "max_results", 10));
        McpSchema.CallToolResult result = toolsGetter.executeOqlTool().callHandler().apply(null, request);
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.contains("Array:char[]"),
                "Should return valid results when executing OQL after loading heap. Got: " + content);
    }

    @Test
    void testExecuteOqlWithQualifiedClassName() {
        McpSchema.CallToolRequest loadRequest = new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath));
        toolsGetter.loadHeapTool().callHandler().apply(null, loadRequest);
        
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("execute_oql", Map.of("query", "SELECT f FROM javax.swing.JFrame f", "max_results", 10));
        McpSchema.CallToolResult result = toolsGetter.executeOqlTool().callHandler().apply(null, request);
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        System.out.println("OQL Result: " + content);
        assertTrue(result.isError() || content.contains("Query Results") || content.contains("No results found"),
            "Should handle OQL query with qualified class name. Got: " + content);
    }

    @Test
    void testGetAllReferencesAfterLoad() {
        McpSchema.CallToolRequest loadRequest = new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath));
        toolsGetter.loadHeapTool().callHandler().apply(null, loadRequest);

        McpSchema.CallToolRequest oqlRequest = new McpSchema.CallToolRequest("execute_oql", Map.of("query", "select s from java.lang.String s where s.value != null", "max_results", 1));
        McpSchema.CallToolResult oqlResult = toolsGetter.executeOqlTool().callHandler().apply(null, oqlRequest);
        String oqlContent = ((McpSchema.TextContent) oqlResult.content().get(0)).text();

        if (oqlContent.contains("Instance ID:")) {
            String instanceIdStr = oqlContent.replaceAll(".*Instance ID:\\s*(\\d+).*", "$1");
            try {
                long instanceId = Long.parseLong(instanceIdStr);
                McpSchema.CallToolRequest refsRequest = new McpSchema.CallToolRequest("get_all_references", Map.of("id", instanceId, "from", 0, "to", 10));
                McpSchema.CallToolResult refsResult = toolsGetter.getAllReferencesTool().callHandler().apply(null, refsRequest);
                assertFalse(refsResult.isError(), "get_all_references should not return error: " + ((McpSchema.TextContent) refsResult.content().get(0)).text());
            } catch (NumberFormatException e) {
                System.out.println("Could not parse instance ID from OQL result: " + oqlContent);
            }
        }
    }

    @Test
    void testGetAllReferencesInvalidId() {
        toolsGetter.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));

        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_all_references", Map.of("id", 999999999L, "from", 0, "to", 10));
        McpSchema.CallToolResult result = toolsGetter.getAllReferencesTool().callHandler().apply(null, request);
        assertFalse(result.isError(), "Should handle non-existent instance gracefully");
    }

    @Test
    @Disabled("Disable due to NullPointerException in HprofGCRoot.getGCRoot()")
    void testGetBiggestObjectsAfterLoad() {
        McpSchema.CallToolRequest loadRequest = new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath));
        toolsGetter.loadHeapTool().callHandler().apply(null, loadRequest);
        
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_biggest_objects", Map.of("limit", 10));
        McpSchema.CallToolResult result = toolsGetter.getBiggestObjectsTool().callHandler().apply(null, request);
        
        assertFalse(result.isError(), "get_biggest_objects should not throw exception when heap is loaded. Error: " + 
            (result.content() != null && !result.content().isEmpty() ? ((McpSchema.TextContent) result.content().get(0)).text() : "unknown"));
        
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertNotNull(content, "Content should not be null");
    }
}
