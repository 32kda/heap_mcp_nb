package com.onpositive.analyzer;

import com.onpositive.analyzer.mcp.HeapDumpTools;
import io.modelcontextprotocol.server.McpServerFeatures;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ToolsGetter {

    private final HeapDumpTools tools;
    private final Map<String, McpServerFeatures.SyncToolSpecification> specs;

    public ToolsGetter(HeapDumpTools tools) {
        this.tools = tools;
        this.specs = buildSpecs();
    }

    private Map<String, McpServerFeatures.SyncToolSpecification> buildSpecs() {
        List<McpServerFeatures.SyncToolSpecification> list = com.onpositive.analyzer.mcp.reflection.ToolsFactory.createToolSpecs(tools);
        return list.stream().collect(Collectors.toMap(s -> s.tool().name(), s -> s));
    }

    public Map<String, McpServerFeatures.SyncToolSpecification> getSpecifications() {
        return specs;
    }

    public McpServerFeatures.SyncToolSpecification loadHeapTool() {
        return get("load_heap");
    }

    public McpServerFeatures.SyncToolSpecification getClassesByMaxInstancesCountTool() {
        return get("get_classes_by_max_instances_count");
    }

    public McpServerFeatures.SyncToolSpecification getClassesByMaxInstancesSizeTool() {
        return get("get_classes_by_max_instances_size");
    }

    public McpServerFeatures.SyncToolSpecification getBiggestObjectsTool() {
        return get("get_biggest_objects");
    }

    public McpServerFeatures.SyncToolSpecification getGCRootsTool() {
        return get("get_gc_roots");
    }

    public McpServerFeatures.SyncToolSpecification getGCRootsPaginatedTool() {
        return get("get_gc_roots_paginated");
    }

    public McpServerFeatures.SyncToolSpecification getJavaClassByNameTool() {
        return get("get_class_by_name");
    }

    public McpServerFeatures.SyncToolSpecification getJavaClassByIdTool() {
        return get("get_class_by_id");
    }

    public McpServerFeatures.SyncToolSpecification getInstanceByIdTool() {
        return get("get_instance_by_id");
    }

    public McpServerFeatures.SyncToolSpecification getAllReferencesTool() {
        return get("get_all_references");
    }

    public McpServerFeatures.SyncToolSpecification getJavaClassesByRegExpTool() {
        return get("get_classes_by_regexp");
    }

    public McpServerFeatures.SyncToolSpecification getSummaryTool() {
        return get("get_summary");
    }

    public McpServerFeatures.SyncToolSpecification getSystemPropertiesTool() {
        return get("get_system_properties");
    }

    public McpServerFeatures.SyncToolSpecification executeOqlTool() {
        return get("execute_oql");
    }

    public McpServerFeatures.SyncToolSpecification analyzeHeapTool() {
        return get("analyze_heap_dump");
    }

    public McpServerFeatures.SyncToolSpecification searchClassesTool() {
        return get("search_classes");
    }

    private McpServerFeatures.SyncToolSpecification get(String name) {
        McpServerFeatures.SyncToolSpecification spec = specs.get(name);
        if (spec == null) {
            throw new IllegalStateException("Tool not found: " + name);
        }
        return spec;
    }

}
