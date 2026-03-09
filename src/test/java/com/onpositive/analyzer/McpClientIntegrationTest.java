package com.onpositive.analyzer;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class McpClientIntegrationTest {

    private McpSyncClient client;
    private String samplePath;

    @BeforeEach
    void setUp() {
        File sampleFile = new File("src/test/resources/HeapDumpSample.hprof");
        assertTrue(sampleFile.exists(), "Sample heap dump file not found at " + sampleFile.getAbsolutePath());
        samplePath = sampleFile.getAbsolutePath();

        // Configure transport to launch the server via java command
        // We use the current classpath and the main class
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");

        ServerParameters params = ServerParameters.builder(javaBin)
                .args("-cp", classpath, "com.onpositive.analyzer.mcp.McpServerLauncher")
                .build();

        tools.jackson.databind.json.JsonMapper jsonMapper = tools.jackson.databind.json.JsonMapper.builder().build();
        JacksonMcpJsonMapper mapper = new JacksonMcpJsonMapper(jsonMapper);
        StdioClientTransport transport = new StdioClientTransport(params, mapper);

        client = McpClient.sync(transport).build();
        client.initialize();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testListTools() {
        McpSchema.ListToolsResult tools = client.listTools(null);
        assertNotNull(tools);
        assertFalse(tools.tools().isEmpty());
        
        boolean hasLoadHeap = tools.tools().stream()
                .anyMatch(tool -> "load_heap".equals(tool.name()));
        assertTrue(hasLoadHeap, "Server should expose load_heap tool");
    }

    @Test
    void testLoadHeapAndGetSummaryViaClient() {
        // 1. Load heap
        McpSchema.CallToolResult loadResult = client.callTool(
                new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath))
        );
        assertFalse(loadResult.isError());

        // 2. Get summary
        McpSchema.CallToolResult summaryResult = client.callTool(
                new McpSchema.CallToolRequest("get_summary", Map.of())
        );
        assertFalse(summaryResult.isError());
        
        String content = ((McpSchema.TextContent) summaryResult.content().get(0)).text();
        assertTrue(content.contains("Total Instances"), "Summary should contain Total Instances");
    }

    @Test
    void testGetClassesByMaxInstancesCount() {
        client.callTool(new McpSchema.CallToolRequest("load_heap", Map.of("file_path", samplePath)));
        
        McpSchema.CallToolResult result = client.callTool(
                new McpSchema.CallToolRequest("get_classes_by_max_instances_count", Map.of("from", 0, "to", 10))
        );
        
        assertFalse(result.isError());
        String content = ((McpSchema.TextContent) result.content().get(0)).text();
        assertFalse(content.isEmpty());
        assertTrue(content.contains("java.lang.String") || content.contains("char[]") || content.contains("byte[]"));
    }
}
