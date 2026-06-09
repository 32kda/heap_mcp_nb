package com.onpositive.analyzer.printing;

import com.onpositive.analyzer.util.ClassUtil;
import com.onpositive.analyzer.util.ValueUtil;
import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.ObjectFieldValue;
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;

import java.util.List;

public class InstanceQuickPrinter {

    public static final int MAX_ARRAY_ITEMS = 100;
    public static final int MAX_STRING_LENGTH = 200;

    private InstanceQuickPrinter() {
    }

    public static String quickPrint(Instance instance) {
        return String.format("ID: %d, Class: %s",
                instance.getInstanceId(), ClassUtil.getClassName(instance));
    }

    public static String formatFieldsShort(Instance instance) {
        List<?> fieldValues = instance.getFieldValues();
        if (fieldValues == null || fieldValues.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("{\n");
        boolean first = true;
        for (Object fvObj : fieldValues) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            FieldValue fv = (FieldValue) fvObj;
            sb.append(fv.getField().getName()).append("=");
            sb.append(formatFieldValue(fv));
            sb.append("\n");
        }
        sb.append("}");
        return sb.toString();
    }

    public static String formatFieldValue(FieldValue fv) {
        if (fv instanceof ObjectFieldValue ofv) {
            Instance refInstance = ofv.getInstance();
            if (refInstance != null) {
                String className = ClassUtil.getClassName(refInstance);
                if ("java.lang.String".equals(className)) {
                    return formatStringValue(refInstance);
                }
                if (refInstance instanceof PrimitiveArrayInstance arrayInstance) {
                    return formatArrayValue(arrayInstance);
                }
                return String.format("Instance[id=%d, class=%s]",
                        refInstance.getInstanceId(), className);
            }
        }
        return fv.getValue();
    }

    private static String formatStringValue(Instance stringInstance) {
        long id = stringInstance.getInstanceId();
        String value = ValueUtil.fastExtractStringValue(stringInstance);
        if (value == null) {
            return String.format("id=%d, value=null", id);
        }
        if (value.length() <= MAX_STRING_LENGTH) {
            return String.format("id=%d, value=%s", id, value);
        }
        String truncated = value.substring(0, MAX_STRING_LENGTH);
        return String.format("id=%d, value=%s...,length=%d", id, truncated, value.length());
    }

    private static String formatArrayValue(PrimitiveArrayInstance arrayInstance) {
        long id = arrayInstance.getInstanceId();
        int length = arrayInstance.getLength();
        StringBuilder sb = new StringBuilder();
        sb.append("id=").append(id).append(", value=[");
        int to = Math.min(MAX_ARRAY_ITEMS, length);
        List values = arrayInstance.getValues();
        for (int i = 0; i < to; i++) {
            sb.append(values.get(i));
            if (i < to - 1) {
                sb.append(",");
            }
        }
        if (length > MAX_ARRAY_ITEMS) {
            sb.append("...,length=").append(length);
        }
        sb.append("]");
        return sb.toString();
    }
}
