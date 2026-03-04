# Heap Dump MCP Server

A Model Context Protocol (MCP) server for analyzing Java heap dump files (.hprof). This project provides a set of tools that allow AI assistants to analyze Java heap dumps through a standardized MCP interface.

## Features

- **Load Heap Dumps** - Parse and load .hprof heap dump files
- **Query Classes** - Get all classes, search by name, or use regex patterns
- **Analyze Instances** - Find biggest objects by retained size
- **GC Root Analysis** - View garbage collection roots
- **Heap Summary** - Get overview statistics of the heap
- **System Properties** - Access JVM system properties from the heap dump
- **OQL Support** - Execute Object Query Language queries on the heap (format-dependent)

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      MCP Client (AI)                        │
└─────────────────────────┬───────────────────────────────────┘
                          │ MCP Protocol (JSON-RPC)
┌─────────────────────────▼───────────────────────────────────┐
│                    MCP Server                               │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              HeapDumpTools (MCP Adapter)           │    │
│  └─────────────────────────┬─────────────────────────┘    │
└────────────────────────────┼────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────┐
│                 HeapDumpService (Core)                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │         NetBeans Profiler Heap Analysis API          │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## Available Tools

| Tool | Description |
|------|-------------|
| `load_heap` | Load a .hprof heap dump file |
| `get_all_classes` | List all classes in the heap |
| `get_class_by_name` | Get details of a specific class |
| `get_class_by_id` | Get class details by internal ID |
| `get_classes_by_regexp` | Search classes using regex |
| `get_biggest_objects` | Find largest objects by retained size |
| `get_gc_roots` | View GC root references |
| `get_summary` | Get heap summary statistics |
| `get_system_properties` | Access JVM system properties |
| `execute_oql` | Execute OQL queries |
| `analyze_heap_dump` | Analyze and return top classes by instance count |

## Requirements

- Java 17 or higher
- Maven 3.6+

## Building

```bash
mvn clean package
```

## Running

### As MCP Server (STDIO)

```bash
java -jar target/heap_mcp_nb-1.0-SNAPSHOT.jar
```

The server communicates via STDIO, making it compatible with MCP clients like Claude Desktop.

### Running Tests

```bash
mvn test
```

To run specific tests:

```bash
mvn test -Dtest=HeapDumpMcpTest
```

## Usage Example

### Using with Claude Desktop

Add this to your Claude Desktop config:

```json
{
  "mcpServers": {
    "heap-analyzer": {
      "command": "java",
      "args": ["-jar", "path/to/heap_mcp_nb-1.0-SNAPSHOT.jar"],
      "env": {}
    }
  }
}
```

### Direct API Usage

```java
// Create service and tools
HeapDumpService service = new HeapDumpService();
HeapDumpTools tools = new HeapDumpTools(service);

// Load heap dump
tools.loadHeapTool().call().apply(null, Map.of(
    "file_path", "/path/to/heapdump.hprof"
));

// Get all classes
McpSchema.CallToolResult result = tools.getAllClassesTool().call()
    .apply(null, Map.of());
```

## Dependencies

- **io.modelcontextprotocol.sdk:mcp** - MCP Java SDK
- **org.netbeans.modules:org-netbeans-lib-profiler** - Heap analysis
- **org.netbeans.modules:org-netbeans-modules-profiler-oql** - OQL engine
- **JUnit 5** - Testing framework

## License

MIT License
