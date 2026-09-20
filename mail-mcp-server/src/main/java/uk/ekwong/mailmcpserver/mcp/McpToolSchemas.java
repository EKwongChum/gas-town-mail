/*
 * Copyright 2026 ekwongchum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ekwong.mailmcpserver.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the JSON input schemas of the MCP tools and reads their arguments. A property declared
 * with {@code required = true} is added to the {@code required} list of the tool schema, so the
 * schema and the argument handling cannot drift apart.
 */
final class McpToolSchemas {

    private static final String TYPE = "type";
    private static final String DESCRIPTION = "description";

    private McpToolSchemas() {}

    /** Builds the object schema of a tool from its property declarations. */
    static McpSchema.JsonSchema jsonSchema(List<Map<String, Object>> properties) {
        Map<String, Object> propertyMap = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Map<String, Object> property : properties) {
            String name = String.valueOf(property.get("name"));
            Map<String, Object> schema = new LinkedHashMap<>(property);
            schema.remove("name");
            if (Boolean.TRUE.equals(schema.remove("required"))) {
                required.add(name);
            }
            propertyMap.put(name, schema);
        }
        return new McpSchema.JsonSchema("object", propertyMap, required, false, null, null);
    }

    /** Declares a string property. */
    static Map<String, Object> arg(String name, String description, boolean required) {
        return arg(name, "string", description, required);
    }

    /** Declares a property of a primitive JSON type ({@code string}, {@code integer}, ...). */
    static Map<String, Object> arg(String name, String type, String description, boolean required) {
        return property(name, Map.of(TYPE, type, DESCRIPTION, description), required);
    }

    /** Declares an array property whose items are of a primitive JSON type. */
    static Map<String, Object> arrayArg(
            String name, String itemType, String description, boolean required) {
        return arrayArg(name, Map.of(TYPE, itemType), description, required);
    }

    /** Declares an array property with a nested item schema, e.g. an array of objects. */
    static Map<String, Object> arrayArg(
            String name, Map<String, Object> items, String description, boolean required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(TYPE, "array");
        schema.put("items", items);
        schema.put(DESCRIPTION, description);
        return property(name, schema, required);
    }

    /** Builds a nested object schema for an array item. */
    static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put(TYPE, "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private static Map<String, Object> property(
            String name, Map<String, Object> schema, boolean required) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("name", name);
        property.put("required", required);
        property.putAll(schema);
        return property;
    }

    /** Returns a string argument; blank values are treated as not provided. */
    static String string(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return null;
        }
        Object value = arguments.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * Returns a free text argument exactly as it was sent: mail bodies keep their leading and
     * trailing white space, unlike the identifiers and addresses read with {@link #string}.
     */
    static String text(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return null;
        }
        Object value = arguments.get(key);
        return value == null ? null : String.valueOf(value);
    }

    /** Returns an integer argument, or {@code null} when it is absent or not a number. */
    static Integer integer(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return null;
        }
        Object value = arguments.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Returns an integer argument, or the given default when it is absent or not a number. */
    static int integer(Map<String, Object> arguments, String key, int defaultValue) {
        Integer value = integer(arguments, key);
        return value == null ? defaultValue : value;
    }

    /** Returns a boolean argument, or {@code null} when it is absent or not a boolean. */
    static Boolean bool(Map<String, Object> arguments, String key) {
        if (arguments == null) {
            return null;
        }
        Object value = arguments.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        return "false".equalsIgnoreCase(text) ? Boolean.FALSE : null;
    }

    /**
     * Returns an address list argument. Both a JSON array and a single (comma separated) string are
     * accepted, because MCP clients differ in how they pass repeated values.
     */
    static List<String> stringList(Map<String, Object> arguments, String key) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value == null) {
            return null;
        }
        List<?> values = value instanceof List<?> list ? list : List.of(value);
        List<String> strings = new ArrayList<>(values.size());
        for (Object element : values) {
            if (element != null) {
                strings.add(String.valueOf(element));
            }
        }
        return strings;
    }

    /** Returns a list of JSON objects, accepting a single object as well as an array of them. */
    static List<Map<String, Object>> objectList(Map<String, Object> arguments, String key) {
        Object value = arguments == null ? null : arguments.get(key);
        if (value == null) {
            return null;
        }
        List<?> values = value instanceof List<?> list ? list : List.of(value);
        List<Map<String, Object>> objects = new ArrayList<>(values.size());
        for (Object element : values) {
            if (element instanceof Map<?, ?> map) {
                Map<String, Object> object = new LinkedHashMap<>();
                map.forEach((name, item) -> object.put(String.valueOf(name), item));
                objects.add(object);
            } else {
                throw new IllegalArgumentException(key + " entries must be JSON objects");
            }
        }
        return objects;
    }

    /** Returns an ISO-8601 instant argument. */
    static Instant instant(Map<String, Object> arguments, String key) {
        String value = string(arguments, key);
        return value == null ? null : Instant.parse(value);
    }
}
