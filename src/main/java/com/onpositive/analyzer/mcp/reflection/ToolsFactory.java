package com.onpositive.analyzer.mcp.reflection;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ToolsFactory {

    public static List<SyncToolSpecification> createToolSpecs(Object toolsService) {
        List<SyncToolSpecification> specs = new ArrayList<>();
        Class<?> toolsClass = toolsService.getClass();

        for (Method method : toolsClass.getDeclaredMethods()) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation != null) {
                SyncToolSpecification spec = createToolSpec(method, toolAnnotation, toolsService);
                specs.add(spec);
            }
        }

        return specs;
    }

    private static SyncToolSpecification createToolSpec(Method method, Tool toolAnnotation, Object toolsService) {
        Map<String, Object> properties = new HashMap<>();
        List<String> requiredParams = new ArrayList<>();

        Annotation[][] paramAnnotations = method.getParameterAnnotations();
        Class<?>[] paramTypes = method.getParameterTypes();

        for (int i = 0; i < paramAnnotations.length; i++) {
            ParamInfo paramInfo = getParamInfo(paramAnnotations[i], i);
            if (paramInfo == null) {
                continue;
            }

            String jsonType = getJsonType(paramTypes[i]);
            properties.put(paramInfo.name, new McpSchema.JsonSchema(jsonType, null, null, false, null, null));

            if (paramInfo.isRequired) {
                requiredParams.add(paramInfo.name);
            }
        }

        McpSchema.JsonSchema inputSchema = new McpSchema.JsonSchema(
                "object",
                properties,
                requiredParams.isEmpty() ? List.of() : requiredParams,
                false,
                null,
                null
        );

        McpSchema.Tool tool = new McpSchema.Tool(
                toolAnnotation.name(),
                toolAnnotation.title(),
                toolAnnotation.decription(),
                inputSchema,
                null,
                null,
                null
        );

        return new SyncToolSpecification(tool, (exchange, request) -> {
            try {
                Map<String, Object> args = request.arguments();
                Object[] methodArgs = prepareMethodArgs(method, paramAnnotations, paramTypes, args);
                Object result = method.invoke(toolsService, methodArgs);

                String resultStr = result != null ? result.toString() : "null";
                return McpSchema.CallToolResult.builder()
                        .content(List.of(new McpSchema.TextContent(resultStr)))
                        .isError(false)
                        .build();
            } catch (IllegalArgumentException e) {
                return errorResult("Invalid arguments: " + e.getMessage());
            } catch (Exception e) {
                return errorResult("Error executing tool: " + e.getMessage());
            }
        });
    }

    private static class ParamInfo {
        String name;
        boolean isRequired;
        String defaultValue;

        ParamInfo(String name, boolean isRequired, String defaultValue) {
            this.name = name;
            this.isRequired = isRequired;
            this.defaultValue = defaultValue;
        }
    }

    private static ParamInfo getParamInfo(Annotation[] annotations, int paramIndex) {
        String name = null;
        boolean isRequired = false;
        String defaultValue = null;

        for (Annotation annotation : annotations) {
            if (annotation instanceof Required) {
                isRequired = true;
                name = ((Required) annotation).value();
            } else if (annotation instanceof Default) {
                Default def = (Default) annotation;
                name = def.name();
                defaultValue = def.value();
            }
        }

        if (name == null) {
            return null;
        }

        return new ParamInfo(name, isRequired, defaultValue);
    }

    private static String getJsonType(Class<?> paramType) {
        if (paramType == String.class) {
            return "string";
        } else if (paramType == int.class || paramType == long.class || 
                   paramType == Integer.class || paramType == Long.class ||
                   paramType == short.class || paramType == Short.class ||
                   paramType == byte.class || paramType == Byte.class) {
            return "integer";
        } else if (paramType == float.class || paramType == double.class ||
                   paramType == Float.class || paramType == Double.class) {
            return "number";
        } else if (paramType == boolean.class || paramType == Boolean.class) {
            return "boolean";
        } else {
            return "string";
        }
    }

    private static Object[] prepareMethodArgs(Method method, Annotation[][] paramAnnotations, 
                                               Class<?>[] paramTypes, Map<String, Object> args) throws IllegalArgumentException {
        Object[] methodArgs = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            ParamInfo paramInfo = getParamInfo(paramAnnotations[i], i);
            if (paramInfo == null) {
                continue;
            }

            Object argValue = args.get(paramInfo.name);
            Class<?> paramType = paramTypes[i];

            if (argValue == null) {
                if (paramInfo.isRequired) {
                    throw new IllegalArgumentException("Required parameter '" + paramInfo.name + "' is missing");
                }
                if (paramInfo.defaultValue != null) {
                    try {
                        argValue = convertDefaultValue(paramInfo.defaultValue, paramType);
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Failed to convert value for '" + paramInfo.name + "' to type " + paramType);
                    }
                } else {
                    argValue = getDefaultForType(paramType);
                }
            } else {
                argValue = convertArgument(argValue, paramType);
            }

            methodArgs[i] = argValue;
        }

        return methodArgs;
    }

    private static Object convertArgument(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (targetType == String.class) {
            return String.valueOf(value);
        } else if (targetType == int.class || targetType == Integer.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString());
        } else if (targetType == long.class || targetType == Long.class) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(value.toString());
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof Boolean) {
                return value;
            }
            return Boolean.parseBoolean(value.toString());
        } else if (targetType == double.class || targetType == Double.class) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(value.toString());
        } else if (targetType == float.class || targetType == Float.class) {
            if (value instanceof Number) {
                return ((Number) value).floatValue();
            }
            return Float.parseFloat(value.toString());
        }

        return value.toString();
    }

    private static Object convertDefaultValue(String defaultValue, Class<?> targetType) {
        if (defaultValue == null || defaultValue.isEmpty()) {
            return getDefaultForType(targetType);
        }

        if (targetType == String.class) {
            return defaultValue;
        } else if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(defaultValue);
        } else if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(defaultValue);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(defaultValue);
        } else if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(defaultValue);
        } else if (targetType == float.class || targetType == Float.class) {
            return Float.parseFloat(defaultValue);
        }

        return defaultValue;
    }

    private static Object getDefaultForType(Class<?> type) {
        if (type == String.class) {
            return "";
        } else if (type == int.class || type == Integer.class) {
            return 0;
        } else if (type == long.class || type == Long.class) {
            return 0L;
        } else if (type == boolean.class || type == Boolean.class) {
            return false;
        } else if (type == double.class || type == Double.class) {
            return 0.0;
        } else if (type == float.class || type == Float.class) {
            return 0.0f;
        }
        return null;
    }

    private static McpSchema.CallToolResult errorResult(String message) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(message)))
                .isError(true)
                .build();
    }
}
