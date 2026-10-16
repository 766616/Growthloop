package com.growthloop.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class JsonUtil {
    private final ObjectMapper mapper;

    public JsonUtil(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String write(Object o) {
        try {
            return o == null ? null : mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialise JSON", e);
        }
    }

    public Object read(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return mapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }

    public List<Object> readList(String json) {
        Object o = read(json);
        if (o instanceof List<?> l) return new ArrayList<>(l);
        return new ArrayList<>();
    }

    public Map<String, Object> toMap(JsonNode node) {
        return mapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }

    public Object toObject(JsonNode node) {
        return node == null || node.isNull() ? null : mapper.convertValue(node, Object.class);
    }

    public static List<String> splitCsv(String s) {
        if (s == null || s.isBlank()) return new ArrayList<>();
        return new ArrayList<>(Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList());
    }

    public static String joinCsv(List<String> list) {
        if (list == null) return null;
        return String.join(", ", list.stream().map(String::trim).filter(x -> !x.isEmpty()).distinct().toList());
    }
}
