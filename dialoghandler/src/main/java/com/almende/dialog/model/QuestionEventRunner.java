package com.almende.dialog.model;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Used to asyncronously fire question events.
 * @author shravan
 *
 */
public class QuestionEventRunner implements Runnable {

    private String sessionKey;
    private String event;
    private String eventMessage;
    private Map<String, Object> timeMap;
    private String responder;
    private Question question;
    private volatile Question response = null;
    
    private final Object lock = new Object();
    private static Logger log = Logger.getLogger(QuestionEventRunner.class.getName());

    public QuestionEventRunner(Question question, String event, String eventMessage, String responder,
        Map<String, Object> timeMap, String sessionKey) {

        this.question = question;
        this.event = event;
        this.eventMessage = eventMessage;
        this.responder = responder;
        this.timeMap = timeMap;
        this.sessionKey = sessionKey;
    }

    @Override
    public void run() {

        synchronized (lock) {
            try {
                if (question != null) {
                    response = question.event(event, eventMessage, timeMap, responder, sessionKey);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                log.severe(String.format("Triggering question event: %s failed for sessionKey: %s with responder: %s",
                                         event, sessionKey, responder));
            }
            finally {
                lock.notifyAll();
            }
        }
    }
    
    public Question getResponse() throws InterruptedException {

        synchronized (lock) {
            while (response == null) {
                lock.wait();
            }
            return response;
        }
    }
}
