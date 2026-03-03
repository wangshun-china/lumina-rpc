package com.lumina.rpc.core.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * 服务元数据提取器（手术刀型）
 *
 * 通过反射深度提取服务接口的方法信息，包括：
 * 1. 方法签名（名称、参数类型、返回类型）
 * 2. 参数类的字段结构（递归扫描，最多3层）
 * 3. 返回类的字段结构（递归扫描，最多3层）
 *
 * 用于支持前端精准的 Mock 规则配置
 *
 * @author Lumina-RPC Team
 * @since 1.0.0
 */
public class ServiceMetadataExtractor {

    private static final Logger logger = LoggerFactory.getLogger(ServiceMetadataExtractor.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Spring 参数名发现器 - 用于提取方法参数的真实名称（如 sector, shipId）
     * 而不是默认的 arg0, arg1 等
     */
    private static final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 需要排除的 Object 类方法名
     */
    private static final Set<String> OBJECT_METHODS = Set.of(
            "toString", "hashCode", "equals", "clone", "getClass",
            "notify", "notifyAll", "wait", "finalize"
    );

    /**
     * 简单类型（不需要递归扫描字段的类型）
     */
    private static final Set<String> SIMPLE_TYPES = Set.of(
            "java.lang.String", "String",
            "java.lang.Integer", "int", "Integer",
            "java.lang.Long", "long", "Long",
            "java.lang.Double", "double", "Double",
            "java.lang.Float", "float", "Float",
            "java.lang.Boolean", "boolean", "Boolean",
            "java.lang.Character", "char", "Character",
            "java.lang.Byte", "byte", "Byte",
            "java.lang.Short", "short", "Short",
            "java.math.BigDecimal", "java.math.BigInteger",
            "java.util.Date", "java.time.LocalDate", "java.time.LocalDateTime",
            "java.time.LocalTime", "java.time.ZonedDateTime"
    );

    /**
     * 最大递归深度
     */
    private static final int MAX_DEPTH = 3;

    /**
     * 提取服务接口的所有方法元数据（深度版）
     *
     * @param interfaceClass 服务接口类
     * @return JSON 格式的元数据
     */
    public static String extractMetadata(Class<?> interfaceClass) {
        if (interfaceClass == null) {
            logger.warn("⚠️ [Metadata] extractMetadata called with null interfaceClass");
            return null;
        }

        if (!interfaceClass.isInterface()) {
            logger.warn("⚠️ [Metadata] extractMetadata called with non-interface class: {}",
                    interfaceClass.getName());
        }

        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            List<Map<String, Object>> methods = new ArrayList<>();

            for (Method method : interfaceClass.getDeclaredMethods()) {
                if (OBJECT_METHODS.contains(method.getName())) {
                    continue;
                }

                Map<String, Object> methodInfo = extractMethodInfo(method);
                methods.add(methodInfo);

                logger.info("📋 [Metadata] Extracted method: {}({}) -> {}",
                        method.getName(),
                        methodInfo.get("parameterTypes"),
                        method.getReturnType().getSimpleName());
            }

            metadata.put("interfaceName", interfaceClass.getName());
            metadata.put("methods", methods);

            String json = objectMapper.writeValueAsString(metadata);
            logger.info("✅ [Metadata] Successfully extracted {} methods from interface: {}",
                    methods.size(), interfaceClass.getName());

            return json;

        } catch (Exception e) {
            logger.warn("Failed to extract metadata for interface: {}", interfaceClass.getName(), e);
            return null;
        }
    }

    /**
     * 批量提取多个服务接口的元数据（深度版）
     */
    public static String extractMetadataBatch(List<Class<?>> interfaceClasses) {
        if (interfaceClasses == null || interfaceClasses.isEmpty()) {
            logger.warn("⚠️ [Metadata] extractMetadataBatch called with empty interface list");
            return null;
        }

        logger.info("📋 [Metadata] Starting batch extraction for {} interfaces", interfaceClasses.size());

        try {
            Map<String, Object> root = new LinkedHashMap<>();
            List<Map<String, Object>> services = new ArrayList<>();

            for (Class<?> interfaceClass : interfaceClasses) {
                if (!interfaceClass.isInterface()) {
                    logger.warn("⚠️ [Metadata] Skipping non-interface class: {}", interfaceClass.getName());
                    continue;
                }

                Map<String, Object> serviceInfo = new LinkedHashMap<>();
                serviceInfo.put("interfaceName", interfaceClass.getName());

                List<Map<String, Object>> methods = new ArrayList<>();
                for (Method method : interfaceClass.getDeclaredMethods()) {
                    if (OBJECT_METHODS.contains(method.getName())) {
                        continue;
                    }

                    Map<String, Object> methodInfo = extractMethodInfo(method);
                    methods.add(methodInfo);
                }

                serviceInfo.put("methods", methods);
                services.add(serviceInfo);

                logger.info("✅ [Metadata] Extracted {} methods from: {}",
                        methods.size(), interfaceClass.getSimpleName());
            }

            root.put("services", services);

            String json = objectMapper.writeValueAsString(root);
            logger.info("✅ [Metadata] Batch extraction complete, total services: {}", services.size());

            return json;

        } catch (Exception e) {
            logger.warn("Failed to extract batch metadata", e);
            return null;
        }
    }

    /**
     * 提取单个方法的元数据（包含参数和返回值的字段信息）
     */
    private static Map<String, Object> extractMethodInfo(Method method) {
        Map<String, Object> methodInfo = new LinkedHashMap<>();
        methodInfo.put("name", method.getName());

        // 提取参数信息（包含字段结构）
        List<Map<String, Object>> params = new ArrayList<>();
        List<String> paramTypeNames = new ArrayList<>();
        List<String> paramNames = new ArrayList<>();

        Class<?>[] paramTypes = method.getParameterTypes();
        Type[] genericParamTypes = method.getGenericParameterTypes();

        // 使用 Spring 的 ParameterNameDiscoverer 获取参数名称
        String[] discoveredParamNames = parameterNameDiscoverer.getParameterNames(method);

        for (int i = 0; i < paramTypes.length; i++) {
            Map<String, Object> paramInfo = new LinkedHashMap<>();
            paramInfo.put("index", i);
            paramInfo.put("type", paramTypes[i].getName());

            // 优先使用 Spring 发现的真实参数名，否则使用默认名称
            String paramName = (discoveredParamNames != null && i < discoveredParamNames.length)
                    ? discoveredParamNames[i]
                    : "arg" + i;
            paramInfo.put("name", paramName);
            paramNames.add(paramName);

            paramTypeNames.add(paramTypes[i].getName());

            // 提取参数类的字段结构
            Map<String, Object> fields = extractClassFields(paramTypes[i], genericParamTypes[i], 0);
            if (!fields.isEmpty()) {
                paramInfo.put("fields", fields);
            }

            params.add(paramInfo);
        }

        methodInfo.put("parameterTypes", paramTypeNames);
        methodInfo.put("parameterNames", paramNames); // 新增：参数名称列表
        methodInfo.put("parameters", params);

        // 提取返回值信息（包含字段结构）
        Class<?> returnType = method.getReturnType();
        Type genericReturnType = method.getGenericReturnType();

        Map<String, Object> returnInfo = new LinkedHashMap<>();
        returnInfo.put("type", returnType.getName());

        if (!isSimpleType(returnType.getName()) && !returnType.equals(void.class)) {
            Map<String, Object> returnFields = extractClassFields(returnType, genericReturnType, 0);
            if (!returnFields.isEmpty()) {
                returnInfo.put("fields", returnFields);
            }
        }

        methodInfo.put("returnType", returnInfo);

        return methodInfo;
    }

    /**
     * 递归提取类的字段结构
     *
     * @param clazz      要提取的类
     * @param genericType 泛型类型信息
     * @param depth      当前递归深度
     * @return 字段映射 (fieldName -> fieldInfo)
     */
    private static Map<String, Object> extractClassFields(Class<?> clazz, Type genericType, int depth) {
        Map<String, Object> fields = new LinkedHashMap<>();

        // 检查是否超过最大深度
        if (depth >= MAX_DEPTH) {
            return fields;
        }

        // 简单类型不提取字段
        if (clazz == null || isSimpleType(clazz.getName())) {
            return fields;
        }

        // 集合类型：提取元素类型
        if (Collection.class.isAssignableFrom(clazz) || Map.class.isAssignableFrom(clazz)) {
            return extractCollectionElementType(genericType, depth);
        }

        // 数组类型
        if (clazz.isArray()) {
            Class<?> componentType = clazz.getComponentType();
            if (!isSimpleType(componentType.getName())) {
                Map<String, Object> elementFields = extractClassFields(componentType, componentType, depth + 1);
                if (!elementFields.isEmpty()) {
                    fields.put("[element]", Map.of(
                            "type", componentType.getName(),
                            "fields", elementFields
                    ));
                }
            }
            return fields;
        }

        // 递归提取所有字段
        try {
            for (Field field : getAllFields(clazz)) {
                String fieldName = field.getName();
                Class<?> fieldType = field.getType();
                Type fieldGenericType = field.getGenericType();

                Map<String, Object> fieldInfo = new LinkedHashMap<>();
                fieldInfo.put("type", fieldType.getName());

                // 对于非简单类型，递归提取字段
                if (!isSimpleType(fieldType.getName()) && depth + 1 < MAX_DEPTH) {
                    Map<String, Object> nestedFields = extractClassFields(fieldType, fieldGenericType, depth + 1);
                    if (!nestedFields.isEmpty()) {
                        fieldInfo.put("fields", nestedFields);
                    }
                }

                fields.put(fieldName, fieldInfo);
            }
        } catch (Exception e) {
            logger.debug("Failed to extract fields for class: {}", clazz.getName(), e);
        }

        return fields;
    }

    /**
     * 提取集合/Map 的元素类型信息
     */
    private static Map<String, Object> extractCollectionElementType(Type genericType, int depth) {
        Map<String, Object> fields = new LinkedHashMap<>();

        if (genericType instanceof ParameterizedType) {
            ParameterizedType pt = (ParameterizedType) genericType;
            Type[] typeArgs = pt.getActualTypeArguments();

            for (int i = 0; i < typeArgs.length; i++) {
                Type typeArg = typeArgs[i];
                Class<?> elementClass = getRawClass(typeArg);

                if (elementClass != null && !isSimpleType(elementClass.getName())) {
                    Map<String, Object> elementFields = extractClassFields(elementClass, typeArg, depth + 1);
                    if (!elementFields.isEmpty()) {
                        fields.put("[element" + i + "]", Map.of(
                                "type", elementClass.getName(),
                                "fields", elementFields
                        ));
                    }
                }
            }
        }

        return fields;
    }

    /**
     * 获取类型的原始类
     */
    private static Class<?> getRawClass(Type type) {
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        }
        return null;
    }

    /**
     * 获取类的所有字段（包括继承的）
     */
    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;

        while (current != null && current != Object.class) {
            try {
                Field[] declaredFields = current.getDeclaredFields();
                for (Field field : declaredFields) {
                    // 排除静态字段和内部类引用
                    if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) &&
                        !field.getName().startsWith("this$")) {
                        fields.add(field);
                    }
                }
            } catch (Exception e) {
                // 忽略无法访问的类
            }
            current = current.getSuperclass();
        }

        return fields;
    }

    /**
     * 判断是否为简单类型
     */
    private static boolean isSimpleType(String typeName) {
        if (typeName == null) {
            return true;
        }

        // 基本类型和包装类
        if (SIMPLE_TYPES.contains(typeName)) {
            return true;
        }

        // 原始类型
        if (typeName.equals("int") || typeName.equals("long") || typeName.equals("double") ||
            typeName.equals("float") || typeName.equals("boolean") || typeName.equals("char") ||
            typeName.equals("byte") || typeName.equals("short")) {
            return true;
        }

        // 数组的元素是简单类型
        if (typeName.endsWith("[]")) {
            String elementTypeName = typeName.substring(0, typeName.length() - 2);
            return isSimpleType(elementTypeName);
        }

        return false;
    }

    /**
     * 解析元数据 JSON
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.readValue(metadataJson, Map.class);
        } catch (Exception e) {
            logger.warn("Failed to parse metadata JSON", e);
            return null;
        }
    }

    /**
     * 从元数据中获取方法列表
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> getMethods(Map<String, Object> metadata) {
        if (metadata == null) {
            return Collections.emptyList();
        }

        Object methods = metadata.get("methods");
        if (methods instanceof List) {
            return (List<Map<String, Object>>) methods;
        }

        return Collections.emptyList();
    }
}