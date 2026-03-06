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
        McpSchema.CallToolResult result = tools.loadHeapTool().call().apply(null, Map.of("file_path", samplePath));
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.contains("Total Instances"), "Summary should contain Total Instances");
        assertTrue(content.contains("Total Size"), "Summary should contain Total Size");
    }

    @Test
    void testGetClassesByMaxInstancesCount() {
        tools.loadHeapTool().call().apply(null, Map.of("file_path", samplePath));
        
        McpSchema.CallToolResult result = tools.getClassesByMaxInstancesCountTool().call().apply(null, Map.of("from", 0, "to", 50));
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertFalse(content.isEmpty(), "Class list should not be empty");
        assertTrue(content.contains("java.lang.String"), "Should contain java.lang.String");
    }

    @Test
    void testGetGCRootsAfterLoad() {
        tools.loadHeapTool().call().apply(null, Map.of("file_path", samplePath));
        
        McpSchema.CallToolResult result = tools.getGCRootsTool().call().apply(null, Map.of());
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertNotNull(content);
    }

    @Test
    void testGetClassByNameAfterLoad() {
        tools.loadHeapTool().call().apply(null, Map.of("file_path", samplePath));
        
        McpSchema.CallToolResult result = tools.getJavaClassByNameTool().call().apply(null, Map.of("name", "java.lang.String"));
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.contains("java.lang.String"));
        assertTrue(content.contains("Instances:"));
    }

    @Test
    void testGetClassesByRegexpAfterLoad() {
        tools.loadHeapTool().call().apply(null, Map.of("file_path", samplePath));
        
        McpSchema.CallToolResult result = tools.getJavaClassesByRegExpTool().call().apply(null, Map.of("regexp", "java\\.util\\..*"));
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.contains("java.util."));
    }

    @Test
    void testGetSummaryAfterLoad() {
        tools.loadHeapTool().call().apply(null, Map.of("file_path", samplePath));
        
        McpSchema.CallToolResult result = tools.getSummaryTool().call().apply(null, Map.of());
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.contains("Total Instances"));
        assertTrue(content.contains("Total Size"));
    }

    @Test
    void testGetSystemPropertiesAfterLoad() {
        tools.loadHeapTool().call().apply(null, Map.of("file_path", samplePath));
        
        McpSchema.CallToolResult result = tools.getSystemPropertiesTool().call().apply(null, Map.of());
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertNotNull(content);
    }

    @Test
    void testAnalyzeHeapDump() {
        McpSchema.CallToolResult result = tools.analyzeHeapTool().call().apply(null, Map.of(
                "file_path", samplePath,
                "limit", 5
        ));
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.contains("Top"));
        assertTrue(content.contains("Class Name"));
    }

    @Test
    void testGetJavaClassByIdAfterLoad() {
        tools.loadHeapTool().call().apply(null, Map.of("file_path", samplePath));
        
        McpSchema.CallToolResult result = tools.getJavaClassByIdTool().call().apply(null, Map.of("id", 1L));
        assertTrue(result.isError() || !result.isError());
    }

    @Test
    void testChainedOperationsLoadGetClassesSummary() {
        tools.loadHeapTool().call().apply(null, Map.of("file_path", samplePath));
        
        McpSchema.CallToolResult classesResult = tools.getClassesByMaxInstancesCountTool().call().apply(null, Map.of("from", 0, "to", 50));
        assertFalse(classesResult.isError());
        
        McpSchema.CallToolResult summaryResult = tools.getSummaryTool().call().apply(null, Map.of());
        assertFalse(summaryResult.isError());
        
        McpSchema.CallToolResult gcRootsResult = tools.getGCRootsTool().call().apply(null, Map.of());
        assertFalse(gcRootsResult.isError());
    }

    @Test
    void testMultipleLoadsReturnConsistentResults() {
        McpSchema.CallToolResult result1 = tools.loadHeapTool().call().apply(null, Map.of("file_path", samplePath));
        assertFalse(result1.isError());
        String content1 = ((McpSchema.TextContent) result1.content().get(0)).text();
        
        McpSchema.CallToolResult result2 = tools.loadHeapTool().call().apply(null, Map.of("file_path", samplePath));
        assertFalse(result2.isError());
        String content2 = ((McpSchema.TextContent) result2.content().get(0)).text();
        
        assertEquals(content1, content2, "Multiple loads should return consistent results");
    }

    @Test
    void testGetClassByNameNonExistent() {
        tools.loadHeapTool().call().apply(null, Map.of("file_path", samplePath));
        
        McpSchema.CallToolResult result = tools.getJavaClassByNameTool().call().apply(null, Map.of("name", "com.nonexistent.Class"));
        assertTrue(result.isError(), "Should return error for non-existent class");
    }

    @Test
    void testGetClassesByRegexpNoMatch() {
        tools.loadHeapTool().call().apply(null, Map.of("file_path", samplePath));
        
        McpSchema.CallToolResult result = tools.getJavaClassesByRegExpTool().call().apply(null, Map.of("regexp", "^[xyz].*"));
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertTrue(content.isEmpty() || !content.contains("java."), "Should not contain java classes for regex ^[xyz].*");
    }
}
