package com.almende.dialog.agent;

import java.util.HashMap;
import java.util.Map;

import com.almende.dialog.ScheduledTask;
import com.almende.eve.agent.Agent;
import com.almende.eve.protocol.jsonrpc.annotation.Access;
import com.almende.eve.protocol.jsonrpc.annotation.AccessType;
import com.almende.eve.protocol.jsonrpc.annotation.Name;
import com.almende.eve.protocol.jsonrpc.formats.JSONRequest;
import com.almende.util.TypeUtil;
import com.almende.util.jackson.JOM;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ScheduleAgent extends Agent {

    protected String schedule(String method, int due, boolean interval) {
        return schedule(method, null, due, interval);
    }
    
    protected String schedule(String method, ObjectNode params, int due, boolean interval) {
        
        if(interval) {
            ScheduledTask task = new ScheduledTask( method, params, due );
            setScheduledTask( task );
            
            String localMethod = "runScheduledTask";
            ObjectNode localParams = JOM.getInstance().createObjectNode();
            localParams.put("taskId", task.getId());
            
            String taskId = schedule( localMethod, localParams, due );
            task.setTaskId( taskId );
            setScheduledTask( task );
            
            return task.getId();
        } else {
            return schedule( method, params, due );
        }
    }
    
    protected String schedule(JSONRequest req, int due, boolean interval) {
        return schedule(req.getMethod(), req.getParams(), due, interval); 
    }
    
    protected void stopScheduledTask(String taskId) {
        ScheduledTask task = getScheduledTask( taskId );
        if(task!=null) {
            getScheduler().cancel( task.getTaskId() );
        }
        removeScheduledTask( taskId );
    }
    
    protected void stopAllScheludTasks() {
        Map<String, ScheduledTask> tasks = getScheduledTasks();
        for(String key : tasks.keySet()) {
            ScheduledTask task = tasks.get(key);
            stopScheduledTask( task.getId() );
        }
        
        getScheduler().clear();
    }
    
    @Access(AccessType.PUBLIC)
    public void runScheduledTask(@Name("taskId") String taskId) {
        
        String localMethod = "runScheduledTask";
        ObjectNode localParams = JOM.getInstance().createObjectNode();
        
        
        ScheduledTask task = getScheduledTask( taskId );
        if(task!=null) {
            
            // First schedule next task
            localParams.put("taskId", task.getId());
            String newTaskId = schedule(localMethod, localParams, task.getInterval());
            task.setTaskId( newTaskId );
            setScheduledTask( task );
            
            // execute the actual task
            schedule(task.getMethod(), task.getParams(), 0);
        }
    }
    
    private Map<String, ScheduledTask> getScheduledTasks() {
        Map<String, ScheduledTask> tasks = getState().get("scheduledTasks", new TypeUtil<Map<String, ScheduledTask>>(){});
        if(tasks==null) {
            tasks = new HashMap<String, ScheduledTask>();
        }
        return tasks;
    }
    
    private void setScheduledTask(ScheduledTask task) {
        Map<String, ScheduledTask> tasks = getScheduledTasks();
        tasks.put(task.getId(), task);
        setScheduledTasks( tasks );
    }
    
    private ScheduledTask getScheduledTask(String taskId) {
        Map<String, ScheduledTask> tasks = getScheduledTasks();
        return tasks.get( taskId );
    }
    
    private void removeScheduledTask(String taskId) {
        Map<String, ScheduledTask> tasks = getScheduledTasks();
        tasks.remove( taskId );
        setScheduledTasks( tasks );
    }
    
    private void setScheduledTasks(Map<String, ScheduledTask> scheduledTasks) {
        getState().put("scheduledTasks", scheduledTasks);
    }
}
