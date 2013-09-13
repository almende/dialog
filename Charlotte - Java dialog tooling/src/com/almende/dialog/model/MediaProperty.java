package com.almende.dialog.model;


import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;


public class MediaProperty
{
    public enum MediaPropertyKey
    {
        TIMEOUT, ANSWER_INPUT, LENGTH, TYPE;
        
        @JsonCreator
        public static MediaPropertyKey fromJson(String name) {
            return valueOf(name.toUpperCase());
        }
    }

    public enum MediumType
    {
        BROADSOFT, GTALK, SKYPE, SMS;
        
        @JsonCreator
        public static MediumType fromJson(String name) {
            return valueOf(name.toUpperCase());
        }
    }
    
    private MediumType medium;
    private Map<MediaPropertyKey, String> properties;
    
    public MediaProperty() {
        properties = new HashMap<MediaPropertyKey, String>();
    }
    

    public MediumType getMedium()
    {
        return medium;
    }

    public void setMedium( MediumType medium )
    {
        this.medium = medium;
    }

    public Map<MediaPropertyKey, String> getProperties()
    {
        return properties;
    }

    public void addProperty( MediaPropertyKey key, String value )
    {
        properties.put( key, value );
    }
}