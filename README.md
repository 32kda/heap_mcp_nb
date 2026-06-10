# Java Heap Dump MCP Server

A Model Context Protocol (MCP) server for analyzing Java heap dump files (.hprof). Provides tools that allow AI assistants to analyze Java heap dumps through a standardized MCP interface, backed by the NetBeans Profiler library.

## Features

- **Load Heap Dumps** - Parse and load .hprof heap dump files
- **Class Analysis** - Browse classes sorted by instance count/size, search by exact name, regex, or BM25 full-text ranking
- **Instance Analysis** - Get instance details with field values, paginate through instances by class, compute retained size (separate tool to isolate costly computation)
- **References** - Get all references to an instance with pagination
- **GC Root Analysis** - View garbage collection roots with pagination
- **Heap Summary & Properties** - Get overview statistics and JVM system properties from the heap dump
- **OQL Support** - Execute Object Query Language queries on the heap
- **Reflection-based Tool Registration** - Tools are automatically generated from `@Tool`-annotated methods

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
| `load_heap` | Load a .hprof heap dump file and return its summary |
| `get_summary` | Get heap summary statistics |
| `get_classes_by_max_instances_count` | List classes sorted by instance count descending (paginated, defaults 0-50) |
| `get_classes_by_max_instances_size` | List classes sorted by total instance size descending (paginated, defaults 0-50) |
| `get_class_by_name` | Get class details by fully qualified name (fields, static fields, superclass) |
| `get_class_by_id` | Get class details by internal class ID |
| `get_classes_by_regexp` | Search classes matching a regular expression (paginated) |
| `search_classes` | Full-text search classes using BM25 ranking on names, fields, superclass; splits CamelCase/snake_case, filters stopwords |
| `get_instances` | Paginate through instances of a class by class name (defaults 10 per page) |
| `get_instance_by_id` | Get instance details including field values and object references |
| `get_instance_retained_size` | Compute retained size of an instance by ID (separate from `get_instance_by_id` since retained size computation is costly and may fail) |
| `get_biggest_objects` | Find largest objects by retained size |
| `get_all_references` | Get all references to an instance with pagination |
| `get_gc_roots` | View GC root references with pagination (defaults 0-50) |
| `get_gc_roots_paginated` | View GC roots with pagination, including kind and instance info |
| `get_system_properties` | Access JVM system properties captured in the heap dump |
| `execute_oql` | Execute OQL queries (e.g., `select s.value from java.lang.String s`) |
| `analyze_heap_dump` | One-shot: load a heap dump and return top classes by instance count |

## Requirements

- Java 17 or higher
- Maven 3.6+

## Installing

Download the JAR from the [releases page](https://github.com/anomalyco/heap_mcp_nb/releases).

## Building

```bash
mvn clean package
```

This creates a shaded JAR at `target/heap_mcp_nb-0.0.3.jar` with all dependencies included.

## Running

### As MCP Server (STDIO)

```bash
java -jar target/heap_mcp_nb-0.0.3.jar
```

The server communicates via STDIO, compatible with any MCP client.

### Configuration

#### For opencode

Add to your `opencode.json`:

```json
{
  "mcpServers": {
    "heap-analyzer": {
      "command": "java",
      "args": ["-jar", "${workspace}/target/heap_mcp_nb-0.0.3.jar"],
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
      "args": ["-jar", "path/to/heap_mcp_nb-0.0.3.jar"],
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

In tools like Trae, opencode, or Qwen CLI you can point to a .hprof file and ask e.g. "Find possible problems in this heap dump".

### Typical Workflow

```
1. load_heap(file_path="dump.hprof")       → Load the dump
2. get_summary()                            → Overview statistics
3. get_classes_by_max_instances_count()     → Top classes by count
4. get_class_by_name(name="com.example.MyLeakyClass")  → Inspect a suspicious class
5. get_instances(class_name="com.example.MyLeakyClass", from=0, to=5)  → Browse instances
6. get_instance_by_id(id=12345)             → Full details of a specific instance
7. get_instance_retained_size(id=12345)     → Compute retained size (costly, separate call)
8. get_all_references(id=12345)             → Find what holds this instance
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

Tools are defined via annotations on methods in `HeapDumpTools` and automatically registered by `ToolsFactory`:

```java
@Tool(name = "my_tool", title = "My Tool", decription = "Does something")
@Printer(impl = MyPrinter.class)
public String myToolMethod(
    @Required("param1") String param1,
    @Default(name = "param2", value = "50") int param2) {
    // implementation
}
```

See `src/main/java/com/onpositive/analyzer/mcp/reflection/` for annotation definitions.

## Dependencies

- **io.modelcontextprotocol.sdk:mcp** (1.1.2) - MCP Java SDK
- **org.netbeans.modules:org-netbeans-lib-profiler** (RELEASE200) - Heap analysis
- **org.netbeans.modules:org-netbeans-modules-profiler-oql** (RELEASE200) - OQL engine
- **org.openjdk.nashorn:nashorn-core** (15.4) - JavaScript engine for OQL
- **JUnit 5** - Testing framework

## License

MIT License
