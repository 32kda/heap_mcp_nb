package com.onpositive.analyzer.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onpositive.analyzer.HeapDumpService;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;

public class McpServerLauncher {


    public static McpSyncServer createServer() {
        HeapDumpService heapDumpService = new HeapDumpService();

        // 2. Initialize MCP Adapter Layer
        HeapDumpTools heapDumpTools = new HeapDumpTools(heapDumpService);

        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(new ObjectMapper());
        StdioServerTransportProvider transportProvider = new StdioServerTransportProvider(
                jsonMapper);

        return McpServer.sync(transportProvider)
                .serverInfo("java-heap-analyzer", "0.0.1")
                .capabilities(McpSchema.ServerCapabilities.builder()
                        .tools(true)
                        .logging()
                        .build())
                .tools(
                        heapDumpTools.loadHeapTool(),
                        heapDumpTools.getClassesByMaxInstancesCountTool(),
                        heapDumpTools.getClassesByMaxInstancesSizeTool(),
                        heapDumpTools.getGCRootsPaginatedTool(),
                        heapDumpTools.getBiggestObjectsTool(),
                        heapDumpTools.getGCRootsTool(),
                        heapDumpTools.getInstanceByIdTool(),
                        heapDumpTools.getJavaClassByNameTool(),
                        heapDumpTools.getJavaClassesByRegExpTool(),
                        heapDumpTools.getJavaClassByIdTool(),
                        heapDumpTools.getSummaryTool(),
                        heapDumpTools.getSystemPropertiesTool(),
                        heapDumpTools.executeOqlTool()
                )
                .build();
    }

    public static void main(String[] args) {
        createServer();
    }
}