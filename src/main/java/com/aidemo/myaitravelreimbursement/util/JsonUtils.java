package com.aidemo.myaitravelreimbursement.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON 工具类
 */
public class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.registerModule(new JavaTimeModule());
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON序列化失败", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }

    public static ObjectMapper getMapper() {
        return MAPPER;
    }
}
