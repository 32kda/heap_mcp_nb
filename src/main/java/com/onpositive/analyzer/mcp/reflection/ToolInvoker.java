package com.onpositive.analyzer.mcp.reflection;

import com.onpositive.analyzer.mcp.FileLogger;
import com.onpositive.analyzer.printing.IValuePrinter;
import com.onpositive.analyzer.printing.ValuePrintersRegistry;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public final class ToolInvoker {

    private ToolInvoker() {
    }

    public static Method findToolMethod(Object toolsService, String toolName) {
        for (Method method : toolsService.getClass().getDeclaredMethods()) {
            Tool annotation = method.getAnnotation(Tool.class);
            if (annotation != null && annotation.name().equals(toolName)) {
                return method;
            }
        }
        return null;
    }

    public static List<ToolInfo> listTools(Object toolsService) {
        List<ToolInfo> result = new ArrayList<>();
        for (Method method : toolsService.getClass().getDeclaredMethods()) {
            Tool annotation = method.getAnnotation(Tool.class);
            if (annotation != null) {
                result.add(new ToolInfo(method, annotation));
            }
        }
        return result;
    }

    public static Object[] prepareArgs(Method method, Map<String, String> stringArgs) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            ParamMeta meta = parseParamMeta(paramAnnotations[i], i);
            if (meta == null) {
                continue;
            }
            String rawValue = stringArgs.get(meta.name);
            args[i] = resolveArg(rawValue, meta, paramTypes[i]);
        }
        return args;
    }

    public static String invokeAndFormat(Method method, Object target, Object[] args)
            throws InvocationTargetException, IllegalAccessException {
        Object result = method.invoke(target, args);
        if (result == null) {
            return "(null)";
        }

        IValuePrinter printer = IValuePrinter.DEFAULT;
        Printer printerAnnot = method.getAnnotation(Printer.class);
        if (printerAnnot != null) {
            try {
                printer = printerAnnot.impl().getDeclaredConstructor().newInstance();
            } catch (Exception exc) {
                FileLogger.getInstance().logError(ToolInvoker.class.getName(), exc);
            }
        }

        if (printer == IValuePrinter.DEFAULT) {
            IValuePrinter registered = ValuePrintersRegistry.getInstance().getPrinter(result.getClass());
            if (registered != null) {
                printer = registered;
            }
        }

        return printer.print(result);
    }

    // -- internal helpers --

    private static ParamMeta parseParamMeta(Annotation[] annotations, int index) {
        for (Annotation a : annotations) {
            if (a instanceof Required) {
                return new ParamMeta(((Required) a).value(), true, null);
            }
            if (a instanceof Default) {
                Default d = (Default) a;
                return new ParamMeta(d.name(), false, d.value());
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Object resolveArg(String rawValue, ParamMeta meta, Class<?> targetType) {
        if (rawValue == null || rawValue.isEmpty()) {
            if (meta.required) {
                throw new IllegalArgumentException("Missing required parameter: " + meta.name);
            }
            if (meta.defaultValue != null) {
                return parseValue(meta.defaultValue, targetType);
            }
            return defaultValue(targetType);
        }
        return parseValue(rawValue, targetType);
    }

    private static Object parseValue(String s, Class<?> type) {
        if (type == String.class) return s;
        if (type == int.class || type == Integer.class) return Integer.parseInt(s);
        if (type == long.class || type == Long.class) return Long.parseLong(s);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(s);
        if (type == double.class || type == Double.class) return Double.parseDouble(s);
        if (type == float.class || type == Float.class) return Float.parseFloat(s);
        return s;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == String.class) return "";
        if (type == int.class || type == Integer.class) return 0;
        if (type == long.class || type == Long.class) return 0L;
        if (type == boolean.class || type == Boolean.class) return false;
        if (type == double.class || type == Double.class) return 0.0;
        if (type == float.class || type == Float.class) return 0.0f;
        return null;
    }

    private static class ParamMeta {
        final String name;
        final boolean required;
        final String defaultValue;

        ParamMeta(String name, boolean required, String defaultValue) {
            this.name = name;
            this.required = required;
            this.defaultValue = defaultValue;
        }
    }

    public static class ToolInfo {
        public final Method method;
        public final String name;
        public final String title;
        public final String description;

        ToolInfo(Method method, Tool annotation) {
            this.method = method;
            this.name = annotation.name();
            this.title = annotation.title();
            this.description = annotation.decription();
        }
    }
}
