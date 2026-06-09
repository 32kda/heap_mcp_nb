package com.onpositive.analyzer.printing;

import com.onpositive.analyzer.util.ClassUtil;
import com.onpositive.analyzer.util.ValueUtil;
import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;

import java.util.List;

public class InstancePrinter implements IValuePrinter {

    public static final int MAX_ARRAY_ITEMS = 100;

    @Override
    public String print(Object object) {
        if (!(object instanceof Instance instance)) {
            return object != null ? object.toString() : "";
        }

        String className = ClassUtil.getClassName(instance);
        if ("java.lang.String".equals(className)) {
             return ValueUtil.fastExtractStringValue(instance);
        }
        if (object instanceof PrimitiveArrayInstance arrayInstance) {
            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append("Array:").append(className).append(" values:[");
            boolean addEllipsis = arrayInstance.getLength() > MAX_ARRAY_ITEMS;
            int to = Math.min(100, arrayInstance.getLength());
            List values = arrayInstance.getValues();
            for (int i = 0; i < to; i++) {
                resultBuilder.append(values.get(i).toString());
                if (i < to - 1) {
                    resultBuilder.append(",");
                }
            }
            if (addEllipsis) {
                resultBuilder.append("...");
            }
            resultBuilder.append("]\n");
            return resultBuilder.toString();
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Instance ID: %d%n", instance.getInstanceId()));
        sb.append(String.format("Class: %s%n", className));
        sb.append(String.format("Size: %d%n", instance.getSize()));
        sb.append(String.format("Retained Size: %d%n", instance.getRetainedSize()));

        List<?> fieldValues = instance.getFieldValues();
        if (fieldValues != null && !fieldValues.isEmpty()) {
            sb.append("Fields:%n");
            for (Object fvObj : fieldValues) {
                FieldValue fv = (FieldValue) fvObj;
                String fieldName = fv.getField().getName();
                String fieldType = fv.getField().getType().getName();
                String valueStr = InstanceQuickPrinter.formatFieldValue(fv);

                sb.append(String.format("  %s %s = %s%n", fieldType, fieldName, valueStr));
            }
        }

        return sb.toString();
    }

}
