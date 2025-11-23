package com.link.comparar.model;

import java.util.Map;

public class FileRecord {
    private String id;
    private Map<String, String> data; // Almacena todos los campos del registro
    private String source; // "CSV" o "Excel"

    public FileRecord() {
    }

    public FileRecord(String id, Map<String, String> data, String source) {
        this.id = id;
        this.data = data;
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    /**
     * Obtiene un valor específico del registro
     */
    public String get(String key) {
        return data != null ? data.get(key) : null;
    }

    /**
     * Obtiene todos los campos como string formateado
     */
    public String getFormattedData() {
        if (data == null || data.isEmpty()) {
            return id;
        }
        StringBuilder sb = new StringBuilder();
        data.forEach((key, value) -> {
            if (!key.equalsIgnoreCase("id")) {
                if (sb.length() > 0)
                    sb.append(", ");
                sb.append(value);
            }
        });
        return sb.toString();
    }
}
