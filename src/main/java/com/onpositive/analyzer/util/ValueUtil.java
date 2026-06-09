package com.onpositive.analyzer.util;

import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.PrimitiveArrayInstance;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class ValueUtil {

    public static String fastExtractStringValue(Instance stringInstance) {
        if (stringInstance == null) return null;
        Object valueField = stringInstance.getValueOfField("value");
        if (valueField == null) return "null";

        if (valueField instanceof PrimitiveArrayInstance array) {
            String typeName = array.getJavaClass().getName();
            List values = array.getValues();
            if (values == null) return "null";

            if ("char[]".equals(typeName)) {
                StringBuilder sb = new StringBuilder(values.size());
                for (Object v : values) {
                    if (v != null) {
                        if (v instanceof Character) {
                            sb.append((char) v);
                        } else {
                            sb.append(v.toString());
                        }
                    } else {
                        sb.append('?');
                    }
                }
                return sb.toString();
            } else if ("byte[]".equals(typeName)) {
                byte[] bytes = new byte[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    Object val = values.get(i);
                    if (val instanceof Number) {
                        bytes[i] = ((Number) val).byteValue();
                    } else if (val != null) {
                        bytes[i] = Byte.parseByte(val.toString());
                    }
                }
                Object coder = stringInstance.getValueOfField("coder");
                int coderValue = (coder instanceof Number) ? ((Number) coder).intValue() : 0;
                if (coderValue == 1) {
                    StringBuilder sb = new StringBuilder(bytes.length / 2);
                    for (int i = 0; i < bytes.length - 1; i += 2) {
                        char c = (char) (((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF));
                        sb.append(c);
                    }
                    return sb.toString();
                } else {
                    return new String(bytes, StandardCharsets.ISO_8859_1);
                }
            }
        }
        return String.valueOf(valueField);
    }
}
