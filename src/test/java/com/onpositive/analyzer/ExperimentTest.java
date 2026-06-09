package com.onpositive.analyzer;

import com.onpositive.analyzer.printing.InstancePagePrinter;
import org.junit.jupiter.api.Test;
import org.netbeans.lib.profiler.heap.JavaClass;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class ExperimentTest {
    @Test
    void testGetInstances() throws IOException {
        HeapDumpService service = new HeapDumpService();
        service.loadHeap("D:/work/heap_dump/heapdump-1780452136629.hprof");
        HeapDumpService.InstancePage instancesByClassName = service.getInstancesByClassName("com.intellij.openapi.diagnostic.RollingFileHandler", 0, 5);
        assertFalse(instancesByClassName.instances.isEmpty());
        InstancePagePrinter printer = new InstancePagePrinter();
        String printed = printer.print(instancesByClassName);
        assertNotEquals("No valid instances found", printed);
        JavaClass classByName = service.getJavaClassByName("com.intellij.openapi.diagnostic.RollingFileHandler$MeteredStream");
        assertNotNull(classByName);
    }
}
