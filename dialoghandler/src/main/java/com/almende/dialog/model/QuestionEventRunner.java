package com.almende.dialog.model;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Used to asyncronously fire question events.
 * @author shravan
 *
 */
public class QuestionEventRunner implements Runnable {

    private Session session;
    private String event;
    private String eventMessage;
    private Map<String, Object> timeMap;
    private String responder;
    private Question question;
    private volatile Question response = null;
    
    private final Object lock = new Object();
    private static Logger log = Logger.getLogger(QuestionEventRunner.class.getName());

    public QuestionEventRunner(Question question, String event, String eventMessage, String responder,
        Map<String, Object> timeMap, Session session) {

        this.question = question;
        this.event = event;
        this.eventMessage = eventMessage;
        this.responder = responder;
        this.timeMap = timeMap;
        this.session = session;
    }

    @Override
    public void run() {

        synchronized (lock) {
            try {
                if (question != null) {
                    response = question.event(event, eventMessage, timeMap, responder, session);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
                log.severe(String.format("Triggering question event: %s failed for sessionKey: %s with responder: %s",
                                         event, session != null ? session.getKey() : null, responder));
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
