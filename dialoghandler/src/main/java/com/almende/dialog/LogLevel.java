package com.almende.dialog;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum LogLevel {
    SEVERE, WARNING,
    @Deprecated
    DDR,
    @Deprecated
    INFO,
    
    @Deprecated
    DEBUG;

    @JsonCreator
    public static LogLevel fromJson(String name) {

        return name != null && !name.isEmpty() ? valueOf(name.toUpperCase()) : null;
    }
}
