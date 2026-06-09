package com.onpositive.analyzer.printing;

import org.netbeans.lib.profiler.heap.Instance;

import java.util.List;

public class InstanceListPrinter implements IValuePrinter {

    @Override
    public String print(Object object) {
        if (object instanceof List<?> list) {
            if (list.isEmpty()) {
                return "No instances found";
            }

            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (Object item : list) {
                if (item instanceof Instance inst) {
                    try {
                        sb.append(InstanceQuickPrinter.quickPrint(inst));
                        String fields = InstanceQuickPrinter.formatFieldsShort(inst);
                        if (!fields.isEmpty()) {
                            sb.append(", Fields: ").append(fields);
                        }
                        sb.append("\n");
                        count++;
                    } catch (Exception e) {
                        // Skip objects with invalid instance references
                    }
                }
            }
            if (count == 0) {
                return "No valid instances found";
            }
            if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '\n') {
                sb.setLength(sb.length() - 1);
            }
            return sb.toString();
        }

        if (object instanceof Instance instance) {
            try {
                StringBuilder sb = new StringBuilder(InstanceQuickPrinter.quickPrint(instance));
                String fields = InstanceQuickPrinter.formatFieldsShort(instance);
                if (!fields.isEmpty()) {
                    sb.append(", Fields: ").append(fields);
                }
                return sb.toString();
            } catch (Exception e) {
                return "Invalid instance";
            }
        }

        return object != null ? object.toString() : "";
    }
}
