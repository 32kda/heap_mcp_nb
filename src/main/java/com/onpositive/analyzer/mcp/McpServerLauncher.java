package com.onpositive.analyzer.mcp;

import com.onpositive.analyzer.HeapDumpService;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.json.JsonMapper;

public class McpServerLauncher {


    public static McpSyncServer createServer() {
        HeapDumpService heapDumpService = new HeapDumpService();

        // 2. Initialize MCP Adapter Layer
        HeapDumpTools heapDumpTools = new HeapDumpTools(heapDumpService);

        JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(JsonMapper.builder().build());
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