package com.onpositive.analyzer;

import org.netbeans.lib.profiler.heap.Field;
import org.netbeans.lib.profiler.heap.FieldValue;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;
import org.netbeans.lib.profiler.heap.ObjectFieldValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JavaClassPrinter {

    public static class ClassDetails {
        public String name;
        public long instancesCount;
        public long totalSize;
        public String superclassName;
        public List<FieldInfo> fields;
        public List<StaticFieldInfo> staticFields;

        public ClassDetails(String name, long instancesCount, long totalSize, String superclassName,
                          List<FieldInfo> fields, List<StaticFieldInfo> staticFields) {
            this.name = name;
            this.instancesCount = instancesCount;
            this.totalSize = totalSize;
            this.superclassName = superclassName;
            this.fields = fields;
            this.staticFields = staticFields;
        }
    }

    public static class FieldInfo {
        public String name;
        public String type;

        public FieldInfo(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    public static class StaticFieldInfo {
        public String name;
        public String type;
        public String value;
        public Long instanceId;

        public StaticFieldInfo(String name, String type, String value, Long instanceId) {
            this.name = name;
            this.type = type;
            this.value = value;
            this.instanceId = instanceId;
        }
    }

    public static ClassDetails getClassDetails(JavaClass javaClass) {
        String name = javaClass.getName();
        long instancesCount = javaClass.getInstancesCount();
        long totalSize = javaClass.getAllInstancesSize();

        String superclassName = null;
        JavaClass superClass = javaClass.getSuperClass();
        if (superClass != null && !superClass.getName().equals("java.lang.Object")) {
            superclassName = superClass.getName();
        }

        List<FieldInfo> fields = new ArrayList<>();
        List<StaticFieldInfo> staticFields = new ArrayList<>();

        Collection<Field> allFields = javaClass.getFields();
        Collection<FieldValue> staticFieldValues = javaClass.getStaticFieldValues();

        for (Field field : allFields) {
            String fieldName = field.getName();
            String fieldType = field.getType().getName();

            if (isStatic(field)) {
                String staticValue = null;
                Long instanceId = null;

                for (FieldValue fv : staticFieldValues) {
                    if (fieldName.equals(fv.getField().getName())) {
                        staticValue = String.valueOf(fv.getValue());
                        if (fv instanceof ObjectFieldValue) {
                            ObjectFieldValue ofv = (ObjectFieldValue) fv;
                            Instance refInstance = ofv.getInstance();
                            if (refInstance != null) {
                                instanceId = refInstance.getInstanceId();
                            }
                        }
                        break;
                    }
                }

                staticFields.add(new StaticFieldInfo(fieldName, fieldType, staticValue, instanceId));
            } else {
                fields.add(new FieldInfo(fieldName, fieldType));
            }
        }

        return new ClassDetails(name, instancesCount, totalSize, superclassName, fields, staticFields);
    }

    private static boolean isStatic(Field field) {
        try {
            return field.isStatic();
        } catch (Exception e) {
            return false;
        }
    }

    public static String printClassDetails(ClassDetails details) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Name: %s%n", details.name));
        sb.append(String.format("Instances: %d%n", details.instancesCount));
        sb.append(String.format("Total Size: %d%n", details.totalSize));

        if (details.superclassName != null) {
            sb.append(String.format("Superclass: %s%n", details.superclassName));
        }

        if (!details.staticFields.isEmpty()) {
            sb.append("Static Fields:\n");
            for (StaticFieldInfo sf : details.staticFields) {
                if (sf.instanceId != null) {
                    sb.append(String.format("  %s %s = %s (Instance ID: %d)%n",
                            sf.type, sf.name, sf.value, sf.instanceId));
                } else {
                    sb.append(String.format("  %s %s = %s%n", sf.type, sf.name, sf.value));
                }
            }
        }

        if (!details.fields.isEmpty()) {
            sb.append("Fields:\n");
            for (FieldInfo f : details.fields) {
                sb.append(String.format("  %s %s%n", f.type, f.name));
            }
        }

        return sb.toString();
    }
}
