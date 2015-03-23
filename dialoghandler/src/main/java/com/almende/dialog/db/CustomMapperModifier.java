package com.almende.dialog.db;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jongo.marshall.jackson.configuration.MapperModifier;

import java.util.HashMap;
import java.util.Map;

public class CustomMapperModifier implements MapperModifier {
    Map<String, String> mapping = new HashMap<String, String>();
 
    public CustomMapperModifier(){
        mapping.put("id", "_id");
    }
 
    @Override
    public void modify(ObjectMapper objectMapper) {
        objectMapper.setPropertyNamingStrategy(new MapTransformNamingStrategy(mapping));
    }
}
