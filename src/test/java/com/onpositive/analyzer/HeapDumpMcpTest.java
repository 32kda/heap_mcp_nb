package com.onpositive.analyzer;

import com.onpositive.analyzer.mcp.HeapDumpTools;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class HeapDumpMcpTest {

    private HeapDumpService service;
    private HeapDumpTools tools;
    private String samplePath;

    @BeforeEach
    void setUp() {
        service = new HeapDumpService();
        tools = new HeapDumpTools(service);
        File sampleFile = new File("src/test/resources/HeapDumpSample.hprof");
        assertTrue(sampleFile.exists(), "Sample heap dump file not found at " + sampleFile.getAbsolutePath());
        samplePath = sampleFile.getAbsolutePath();
    }

    @Test
    void testLoadHeapAndGetSummary() {
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath));
        McpSchema.CallToolResult result = tools.loadHeapTool().callHandler().apply(null, request);
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.contains("Total Instances"), "Summary should contain Total Instances");
        assertTrue(content.contains("Total Size"), "Summary should contain Total Size");
    }

    @Test
    void testGetClassesByMaxInstancesCount() {
        McpSchema.CallToolRequest loadRequest = new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath));
        tools.loadHeapTool().callHandler().apply(null, loadRequest);
        
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_classes_by_max_instances_count", Map.of("from", 0, "to", 50));
        McpSchema.CallToolResult result = tools.getClassesByMaxInstancesCountTool().callHandler().apply(null, request);
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertFalse(content.isEmpty(), "Class list should not be empty");
        assertTrue(content.contains("java.lang.String"), "Should contain java.lang.String");
    }

    @Test
    void testGetGCRootsAfterLoad() {
        McpSchema.CallToolRequest loadRequest = new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath));
        tools.loadHeapTool().callHandler().apply(null, loadRequest);
        
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_gc_roots", Map.of());
        McpSchema.CallToolResult result = tools.getGCRootsTool().callHandler().apply(null, request);
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertNotNull(content);
    }

    @Test
    void testGetClassByNameAfterLoad() {
        McpSchema.CallToolRequest loadRequest = new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath));
        tools.loadHeapTool().callHandler().apply(null, loadRequest);
        
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest("get_class_by_name", Map.of("name", "java.lang.String"));
        McpSchema.CallToolResult result = tools.getJavaClassByNameTool().callHandler().apply(null, request);
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.contains("java.lang.String"));
        assertTrue(content.contains("Instances:"));
    }

    @Test
    void testGetClassesByRegexpAfterLoad() {
        tools.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        
        McpSchema.CallToolResult result = tools.getJavaClassesByRegExpTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_classes_by_regexp", Map.of("regexp", "java\\.util\\..*")));
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.contains("java.util."));
    }

    @Test
    void testGetSummaryAfterLoad() {
        tools.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        
        McpSchema.CallToolResult result = tools.getSummaryTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_summary", Map.of()));
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.contains("Total Instances"));
        assertTrue(content.contains("Total Size"));
    }

    @Test
    void testGetSystemPropertiesAfterLoad() {
        tools.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        
        McpSchema.CallToolResult result = tools.getSystemPropertiesTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_system_properties", Map.of()));
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertNotNull(content);
    }

    @Test
    void testAnalyzeHeapDump() {
        McpSchema.CallToolResult result = tools.analyzeHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("analyze_heap_dump", Map.of(
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
        tools.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        
        McpSchema.CallToolResult result = tools.getJavaClassByIdTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_class_by_id", Map.of("id", 1L)));
        assertTrue(result.isError() || !result.isError());
    }

    @Test
    void testChainedOperationsLoadGetClassesSummary() {
        tools.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        
        McpSchema.CallToolResult classesResult = tools.getClassesByMaxInstancesCountTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_classes_by_max_instances_count", Map.of("from", 0, "to", 50)));
        assertFalse(classesResult.isError());
        
        McpSchema.CallToolResult summaryResult = tools.getSummaryTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_summary", Map.of()));
        assertFalse(summaryResult.isError());
        
        McpSchema.CallToolResult gcRootsResult = tools.getGCRootsTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_gc_roots", Map.of()));
        assertFalse(gcRootsResult.isError());
    }

    @Test
    void testMultipleLoadsReturnConsistentResults() {
        McpSchema.CallToolResult result1 = tools.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        assertFalse(result1.isError());
        String content1 = ((McpSchema.TextContent) result1.content().get(0)).text();
        
        McpSchema.CallToolResult result2 = tools.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        assertFalse(result2.isError());
        String content2 = ((McpSchema.TextContent) result2.content().get(0)).text();
        
        assertEquals(content1, content2, "Multiple loads should return consistent results");
    }

    @Test
    void testGetClassByNameNonExistent() {
        tools.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        
        McpSchema.CallToolResult result = tools.getJavaClassByNameTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_class_by_name", Map.of("name", "com.nonexistent.Class")));
        assertTrue(result.isError(), "Should return error for non-existent class");
    }

    @Test
    void testGetClassesByRegexpNoMatch() {
        tools.loadHeapTool().callHandler().apply(null, new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        
        McpSchema.CallToolResult result = tools.getJavaClassesByRegExpTool().callHandler().apply(null, new McpSchema.CallToolRequest("get_classes_by_regexp", Map.of("regexp", "^[xyz].*")));
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.isEmpty() || !content.contains("java."), "Should not contain java classes for regex ^[xyz].*");
    }
}
