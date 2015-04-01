package com.almende.dialog.db;

import static com.fasterxml.jackson.databind.PropertyNamingStrategy.PropertyNamingStrategyBase;

import java.util.Map;
 
public class MapTransformNamingStrategy extends PropertyNamingStrategyBase{
 
    private static final long serialVersionUID = 1L;
 
    private Map<String, String> mapping;
 
    public MapTransformNamingStrategy(Map<String, String> mapping) {
        this.mapping = mapping;
    }
 
    @Override
    public String translate(String property) {
        if (mapping.containsKey(property)) {
            return mapping.get(property);
        }
 
        return property;
    }
}
