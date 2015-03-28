package com.almende.dialog;

import com.almende.util.uuid.UUID;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ScheduledTask{

    private String id = null;
    private String taskId = null;
    private String method = null;
    private ObjectNode params = null;
    private Integer interval = null;
    
    public ScheduledTask() {}
    
    public ScheduledTask(String method, ObjectNode params, Integer interval) {
        this.id = new UUID().toString();
        this.method = method;
        this.params = params;
        this.interval = interval;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId( String id ) {
        this.id = id;
    }
    
    public String getTaskId() {
        return taskId;
    }
    
    public void setTaskId( String taskId ) {
        this.taskId = taskId;
    }
    
    public String getMethod() {
        return method;
    }
    
    public void setMethod( String method ) {
        this.method = method;
    }
    
    public ObjectNode getParams() {
        return params;
    }
    
    public void setParams( ObjectNode params ) {
        this.params = params;
    }
    
    public Integer getInterval() {
        return interval;
    }
    
    public void setInterval( Integer interval ) {
        this.interval = interval;
    }
}
