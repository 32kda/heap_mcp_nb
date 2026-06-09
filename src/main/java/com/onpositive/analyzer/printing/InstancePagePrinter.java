package com.onpositive.analyzer.printing;

import com.onpositive.analyzer.HeapDumpService.InstancePage;
import org.netbeans.lib.profiler.heap.Instance;

import java.util.List;

public class InstancePagePrinter implements IValuePrinter {

    @Override
    public String print(Object object) {
        if (!(object instanceof InstancePage page)) {
            return object != null ? object.toString() : "";
        }

        List<Instance> instances = page.instances;
        if (instances.isEmpty()) {
            return "No instances found";
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Instance inst : instances) {
            sb.append(InstanceQuickPrinter.quickPrint(inst)).append("\n").append(InstanceQuickPrinter.formatFieldsShort(inst)).append("\n");
            count++;
        }

        if (count == 0) {
            return "No valid instances found";
        }

        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }

        if (page.remaining > 0) {
            sb.append(String.format("\n(%d more instances...)", page.remaining));
        }

        return sb.toString();
    }
}
