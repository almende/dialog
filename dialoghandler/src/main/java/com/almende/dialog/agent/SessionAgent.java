package com.almende.dialog.agent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import com.almende.dialog.model.Session;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.eve.agent.Agent;
import com.almende.eve.agent.annotation.ThreadSafe;
import com.almende.eve.rpc.annotation.Access;
import com.almende.eve.rpc.annotation.AccessType;
import com.almende.eve.rpc.annotation.Name;
import com.almende.eve.rpc.jsonrpc.JSONRequest;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.almende.util.TypeUtil;
import com.askfast.commons.entity.AdapterType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.QueueingConsumer.Delivery;

@ThreadSafe(true)
@Access(AccessType.PUBLIC)
public class SessionAgent extends Agent {

    //create a single static connection for publishing ddrs
    private static ConnectionFactory rabbitMQConnectionFactory;
    private static final String SESSION_QUEUE_NAME = "SESSION_POST_PROCESS_QUEUE";
    private static final int SESSION_SCHEDULER_INTERVAL = 60 * 1000; //60seconds
    private static final Integer SESSION_SCHEDULER_MAX_COUNT = 2;
    private static final String SESSION_SCHEDULER_KEY = "SESSION_SCHEDULER";
    private static final Logger log = Logger.getLogger("DialogHandler");
    private int sessionSchedulerCountForUnitTests = 0;
    @Override
    protected void onCreate() {

        onInit();
    }

    @Override
    protected void onInit() {

        Thread sessionPostProcessorThread = new Thread(
        //run the process to listen to incoming session records for post processings
                                                       new Runnable() {

                                                           @Override
                                                           public void run() {

                                                               try {
                                                                   consumeSessionInQueue();
                                                               }
                                                               catch (IOException e) {
                                                                   log.severe("Session post processing failed to initiate!. Error: " +
                                                                              e.getLocalizedMessage());
                                                               }
                                                           }
                                                       });
        sessionPostProcessorThread.start();
    }

    private void consumeSessionInQueue() throws IOException {

        try {
            rabbitMQConnectionFactory = rabbitMQConnectionFactory != null ? rabbitMQConnectionFactory
                                                                         : new ConnectionFactory();
            rabbitMQConnectionFactory.setHost("localhost");
            Connection connection = rabbitMQConnectionFactory.newConnection();
            Channel channel = connection.createChannel();
            //create a message
            channel.queueDeclare(SESSION_QUEUE_NAME, false, false, false, null);
            QueueingConsumer consumer = new QueueingConsumer(channel);
            channel.basicConsume(SESSION_QUEUE_NAME, true, consumer);
            log.info("Waiting to post process sessions...");
            while (true) {
                Delivery delivery = consumer.nextDelivery();
                try {
                    String sessionKey = new String(delivery.getBody());
                    log.info(String.format("---------Received a session: %s to post process!---------", sessionKey));
                    //initiate a scheduler to check if costs can be generated for the session
                    startPostProcessingSessionSceduler(sessionKey);
                }
                catch (Exception e) {
                    log.severe(String.format("Post processing failed for payload: %s. Error: %s",
                                             new String(delivery.getBody()), e.getLocalizedMessage()));
                }
            }
        }
        catch (Exception e) {
            log.severe("Error seen: " + e.getLocalizedMessage());
        }
    }
    
    /**
     * start scheduler for checking session. A new scheduler is created per session
     */
    public String startPostProcessingSessionSceduler(String sessionKey) {

        String id = getSessionData(sessionKey, new TypeReference<String>() {});
        if (id == null) {
            try {
                ObjectNode params = JOM.createObjectNode();
                params.put("sessionKey", sessionKey);
                JSONRequest req = new JSONRequest("postProcessSessions", params);
                String schedulerId = getScheduler().createTask(req, SESSION_SCHEDULER_INTERVAL, true, true);
                addSessionData(sessionKey, schedulerId);
            }
            catch (Exception e) {
                e.printStackTrace();
                log.warning("Exception in scheduler creation: " + e.getLocalizedMessage());
            }
        }
        else {
            log.warning("Task already running");
        }
        return id;
    }
    
    /**
     * tries to process the session and delete the scheduler if processed. tries to process it twice
     * @param sessionKey
     * @return
     */
    public String postProcessSessions(@Name("sessionKey") String sessionKey) {

        //if the ddr is processed succesfully then delete the scheduled task
        Session session = Session.getSession(sessionKey);
        String schedulerId = null;
        Integer updateSessionScedulerRunCount = 0;
        //maintain a counter for voicecalls. 
        if (session != null && session.getAdapterConfig() != null &&
            AdapterType.CALL.equals(AdapterType.getByValue(session.getAdapterConfig().getAdapterType()))) {

            updateSessionScedulerRunCount = updateSessionScedulerRunCount(sessionKey);
        }
        if (session == null || DDRUtils.stopDDRCosts(sessionKey, true) ||
            updateSessionScedulerRunCount > SESSION_SCHEDULER_MAX_COUNT) {
            if (!ServerUtils.isInUnitTestingEnvironment()) {
                schedulerId = stopProcessSessions(sessionKey);
                if (schedulerId == null) {

                    //fetch the tasks that are not canceled properly
                    Set<String> tasks = getScheduler().getTasks();
                    log.warning(String.format("Following taks not cancelled correctly: %s",
                                              ServerUtils.serializeWithoutException(tasks)));
                    if (tasks != null) {
                        for (String task : tasks) {
                            boolean canStopTask = true;
                            Map<String, Object> allSessionData = getAllSessionData();
                            if (allSessionData != null) {
                                for (Object sessionData : allSessionData.values()) {
                                    if (task.equals(sessionData.toString())) {
                                        canStopTask = false;
                                        break;
                                    }
                                }
                            }
                            if (canStopTask) {
                                getScheduler().cancelTask(task);
                            }
                        }
                    }
                }
            }
            //remove the session if its already processed
            log.info(String.format("Session %s processed. Deleting..", sessionKey));
            if (session != null) {
                session.drop();
            }
        }
        //add the count to the agentState too
        return schedulerId;
    }
    
    /**
     * process the session and delete the scheduler if processed
     * 
     * @param sessionKey
     * @return
     */
    public String stopProcessSessions(@Name("sessionKey") String sessionKey) {

        //if the ddr is processed succesfully then delete the scheduled task
        String schedulerId = getSessionData(sessionKey, new TypeReference<String>() {
        });
        if (schedulerId != null) {
            getScheduler().cancelTask(schedulerId);
        }
        Map<String, Object> allSessionData = getAllSessionData();
        if (allSessionData != null) {
            allSessionData.remove(sessionKey);
            allSessionData.remove(sessionKey + "_count");
            getState().put(SESSION_SCHEDULER_KEY, allSessionData);
        }
        //remove the session if its already processed
        log.info(String.format("Stopped session %s processing.", sessionKey));
        Session session = Session.getSession(sessionKey);
        if (session != null) {
            session.drop();
        }
        return schedulerId;
    }
    
    private <T> T getSessionData(String key, TypeReference<T> type) {

        if (!ServerUtils.isInUnitTestingEnvironment()) {
            Map<String, Object> sessionSchedulerMap = getState().get(SESSION_SCHEDULER_KEY,
                                                                     new TypeUtil<Map<String, Object>>() {
                                                                     });
            if (sessionSchedulerMap != null && sessionSchedulerMap.get(key) != null) {
                return JOM.getInstance().convertValue(sessionSchedulerMap.get(key), type);
            }
        }
        return null;
    }
    
    private Map<String, Object> getAllSessionData() {

        if (!ServerUtils.isInUnitTestingEnvironment()) {
            return getState().get(SESSION_SCHEDULER_KEY, new TypeUtil<Map<String, Object>>() {
            });
        }
        return null;
    }
    
    private <T> T addSessionData(String key, Object value) {

        if (!ServerUtils.isInUnitTestingEnvironment()) {
            Map<String, Object> allSessionData = getAllSessionData();
            allSessionData = allSessionData != null ? allSessionData : new HashMap<String, Object>();
            allSessionData.put(key, value);
            getState().put(SESSION_SCHEDULER_KEY, allSessionData);
        }
        return null;
    }
    
    private Integer updateSessionScedulerRunCount(String sessionKey) {

        Integer sessionScheduleRunCount = null;
        if (!ServerUtils.isInUnitTestingEnvironment()) {
            sessionScheduleRunCount = getSessionData(sessionKey + "_count", new TypeReference<Integer>() {});
            sessionScheduleRunCount = sessionScheduleRunCount != null ? sessionScheduleRunCount : 0;
            addSessionData(sessionKey + "_count", ++sessionScheduleRunCount);
        }
        else {
            sessionScheduleRunCount = this.sessionSchedulerCountForUnitTests++;
        }
        return sessionScheduleRunCount;
    }
}
