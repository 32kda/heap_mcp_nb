# Java Heap Dump MCP Server

A Model Context Protocol (MCP) server for analyzing Java heap dump files (.hprof). This project provides a set of tools that allow AI assistants to analyze Java heap dumps through a standardized MCP interface.
Based on NetBeans Profiler library as a backend.

## Features

- **Load Heap Dumps** - Parse and load .hprof heap dump files
- **Query Classes** - Get classes sorted by instance count/size, search by name, or use regex patterns with pagination
- **Analyze Instances** - Find biggest objects by retained size, get instance details with field values
- **References** - Get all references to an instance
- **GC Root Analysis** - View garbage collection roots with pagination
- **Heap Summary** - Get overview statistics of the heap
- **System Properties** - Access JVM system properties from the heap dump
- **OQL Support** - Execute Object Query Language queries on the heap
- **Reflection-based Tools** - Tools are automatically generated from annotated methods

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
│                 HeapDumpService (Core)                     │
│  ┌──────────────────────────────────────────────────────┐   │
│  │         NetBeans Profiler Heap Analysis API          │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## Available Tools

| Tool | Description |
|------|-------------|
| `load_heap` | Load a .hprof heap dump file |
| `get_classes_by_max_instances_count` | List classes sorted by instance count (descending) with pagination |
| `get_classes_by_max_instances_size` | List classes sorted by total instance size (descending) with pagination |
| `get_classes_by_regexp` | Search classes using regex with pagination |
| `get_class_by_name` | Get details of a specific class including fields, static fields, and superclass |
| `get_class_by_id` | Get class details by internal ID including fields, static fields, and superclass |
| `get_instance_by_id` | Get instance details including field values and object references |
| `get_all_references` | Get all references to an instance with pagination |
| `get_biggest_objects` | Find largest objects by retained size |
| `get_gc_roots` | View GC root references with pagination (from, to args, defaults 0-50) |
| `get_gc_roots_paginated` | View GC roots with pagination, including kind and instance info |
| `get_summary` | Get heap summary statistics |
| `get_system_properties` | Access JVM system properties |
| `execute_oql` | Execute OQL queries |
| `analyze_heap_dump` | Analyze and return top classes by instance count |

## Requirements

- Java 17 or higher
- Maven 3.6+

## Installing
* Just download jar from the releases section
  
## Building

```bash
mvn clean package
```

This creates a shaded JAR at `target/heap_mcp_nb-1.0-SNAPSHOT-shaded.jar` with all dependencies included.

## Running

### As MCP Server (STDIO)

```bash
java -jar target/heap_mcp_nb-0.0.1.jar
```

The server communicates via STDIO, making it compatible with MCP clients like Claude Desktop or opencode.

### Configuration

#### For opencode

Add to your `opencode.json`:

```json
{
  "mcpServers": {
    "heap-analyzer": {
      "command": "java",
      "args": ["-jar", "${workspace}/target/heap_mcp_nb-0.0.1.jar"],
      "env": {}
    }
  }
}
```

#### For Claude Desktop

```json
{
  "mcpServers": {
    "heap-analyzer": {
      "command": "java",
      "args": ["-jar", "path/to/heap_mcp_nb-0.0.1.jar"],
      "env": {}
    }
  }
}
```

### Running Tests

```bash
mvn test
```

To run specific tests:

```bash
mvn test -Dtest=HeapDumpMcpTest
mvn test -Dtest=McpClientIntegrationTest
```

## Usage Examples

In tools like Trae, OpenCode or Qwen CLI you can just point to .hrpof file with your heap dump and ask smth like `Find possible problems in this heap dump`

### Using with MCP Client

```java
// Create service and tools
HeapDumpService service = new HeapDumpService();
HeapDumpTools tools = new HeapDumpTools(service);

// Load heap dump
CallToolRequest loadRequest = new CallToolRequest("load_heap", Map.of("file_path", "/path/to/heapdump.hprof"));
tools.loadHeapTool().callHandler().apply(null, loadRequest);

// Get top classes by instance count
CallToolRequest classesRequest = new CallToolRequest("get_classes_by_max_instances_count", Map.of("from", 0, "to", 50));
CallToolResult result = tools.getClassesByMaxInstancesCountTool().callHandler().apply(null, classesRequest);
```

### Tool Response Format

Most tools return results as newline-separated text. For example:

```
get_class_by_name:
Name: java.util.HashMap
Instances: 152
Total Size: 24320
Superclass: java.util.AbstractMap
Static Fields:
  int DEFAULT_INITIAL_CAPACITY = 16
  float loadFactor = 0.75 (Instance ID: 123456789)
Fields:
  java.util.HashMap$Node[] table
  int size
  int threshold
  float loadFactor
```

## Reflection-based Tool Factory

Tools can also be created dynamically using the `ToolsFactory` class with annotations:

```java
@Tool(name = "my_tool", title = "My Tool", description = "Does something")
public String myToolMethod(
    @Required("param1") String param1,
    @Default(name = "param2", value = "50") int param2) {
    // implementation
}
```

See `src/main/java/com/onpositive/analyzer/mcp/reflection/` for the annotation definitions.

## Dependencies

- **io.modelcontextprotocol.sdk:mcp** (1.0.0) - MCP Java SDK
- **org.netbeans.modules:org-netbeans-lib-profiler** (RELEASE200) - Heap analysis
- **org.netbeans.modules:org-netbeans-modules-profiler-oql** (RELEASE200) - OQL engine
- **JUnit 5** - Testing framework

## License

MIT License
