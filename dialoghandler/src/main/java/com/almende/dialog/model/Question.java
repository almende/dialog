package com.almende.dialog.model;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.model.MediaProperty.MediaPropertyKey;
import com.almende.dialog.model.MediaProperty.MediumType;
import com.almende.dialog.model.impl.Q_fields;
import com.almende.dialog.model.intf.QuestionIntf;
import com.almende.dialog.util.AFHttpClient;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.almende.util.uuid.UUID;
import com.askfast.commons.entity.Language;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.ClientHandlerException;

public class Question implements QuestionIntf {

    private static final long serialVersionUID = -9069211642074173182L;
    protected static final com.almende.dialog.Logger dialogLog = new com.almende.dialog.Logger();
    private static final Logger log = Logger.getLogger("DialogHandler");
    static final ObjectMapper om = ParallelInit.getObjectMapper();

    public static final int DEFAULT_MAX_QUESTION_LOAD = 5;
    public static final String MEDIA_PROPERTIES = "media_properties";
    private static HashMap<String, Integer> questionReloadCounter = new HashMap<String, Integer>();

    QuestionIntf question;
    private String preferred_language = null;

    private Collection<MediaProperty> media_properties;

    public Question() {

        this.question = new Q_fields(); // Default to simple in-memory class
    }

    // Factory functions:
    public static Question fromURL(String url, String adapterID, String ddrRecordId, String sessionKey) {

        return fromURL(url, adapterID, "", ddrRecordId, sessionKey);
    }

    public static Question fromURL(String url, String adapterID, String remoteID, String ddrRecordId, String sessionKey) {

        return fromURL(url, adapterID, remoteID, ddrRecordId, sessionKey, null);
    }
    
    public static Question fromURL(String url, String adapterID, String remoteID, String ddrRecordId,
        String sessionKey, Map<String, String> extraParams) {

        AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapterID);
        String fromID = adapterConfig != null ? adapterConfig.getMyAddress() : null;
        return fromURL(url, adapterID, remoteID, fromID, ddrRecordId, sessionKey, extraParams);
    }

    public static Question fromURL(String url, String adapterID, String remoteID, String fromID, String ddrRecordId,
        String sessionKey, Map<String, String> extraParams) {

        log.info(String.format("Trying to parse Question from URL: %s with remoteId: %s and fromId: %s", url, remoteID,
                               fromID));
        if (remoteID == null) {
            remoteID = "";
        }
        if (fromID == null) {
            fromID = "";
        }
        if (extraParams == null) {
            extraParams = new HashMap<String, String>();
        }
        String json = "";
        if (url != null && !url.trim().isEmpty()) {

            AFHttpClient client = ParallelInit.getAFHttpClient();
            try {
                url = ServerUtils.getURLWithQueryParams(url, "responder", remoteID);
                url = ServerUtils.getURLWithQueryParams(url, "requester", fromID);
                
                url = appendParentSessionKeyToURL(sessionKey, url);
                for (String key : extraParams.keySet()) {
                    url = ServerUtils.getURLWithQueryParams(url, key, extraParams.get(key));
                }
                dialogLog.info(adapterID, "Loading new question from: " + url, ddrRecordId, sessionKey);
                String credentialsFromSession = Dialog.getCredentialsFromSession(sessionKey);
                if (credentialsFromSession != null) {
                    client.addBasicAuthorizationHeader(credentialsFromSession);
                }
                json = client.get(url.replace(" ", URLEncoder.encode(" ", "UTF-8")));
                dialogLog.info(adapterID, "Received new question: " + json, ddrRecordId, sessionKey);
            }
            catch (Exception e) {
                log.severe(e.toString());
                dialogLog.severe(adapterID,
                                 String.format("ERROR loading question from: %s. Error: %s", url, e.toString()),
                                 ddrRecordId, sessionKey);
                if (questionReloadCounter.get(url) == null) {
                    questionReloadCounter.put(url, 0);
                }
                while (questionReloadCounter.get(url) != null &&
                       questionReloadCounter.get(url) < DEFAULT_MAX_QUESTION_LOAD) {
                    try {
                        dialogLog.info(adapterID,
                                       String.format("Fetch question from URL: %s failed. Trying again (Count: %s) ",
                                                     url, questionReloadCounter.get(url)), ddrRecordId, sessionKey);
                        json = client.get(url);
                        dialogLog.info(adapterID, "Received new question: " + json, ddrRecordId, sessionKey);
                        break;
                    }
                    catch (Exception ex) {
                        Integer retryCount = questionReloadCounter.get(url);
                        questionReloadCounter.put(url, ++retryCount);
                    }
                }
            }
            questionReloadCounter.remove(url);
            return fromJSON(json, adapterID, ddrRecordId, sessionKey);
        }
        else {
            return null;
        }
    }
    
    public static Question fromJSON(String json, String adapterID, String ddrRecordId, String sessionKey) {

        Question question = null;
        if (json != null && !json.isEmpty()) {
            try {
                question = om.readValue(json, Question.class);
            }
            catch (Exception e) {
                log.severe(e.toString());
                dialogLog.severe(adapterID, "ERROR parsing question: " + e.getLocalizedMessage(), ddrRecordId, sessionKey);
            }
        }
        return question;
    }
    
    public static Question getError(String language) {

        Language lang = Language.getByValue(language);

        String message = "";
        switch (lang) {
            case DUTCH:
                message = "Er is iets mis gegaan met het ophalen van uw dialoog";
                break;
            default: // Default is en-US
                language = Language.ENGLISH_UNITEDSTATES.getCode();
                message = "Something went wrong retrieving your dialog";
                break;
        }

        Question question = new Question();
        question.setPreferred_language(language);
        question.setType("comment");
        question.setQuestion_text("text://" + message);
        question.generateIds();
        return question;
    }

    @JsonIgnore
    public String toJSON() {

        return toJSON(false);
    }

    @JsonIgnore
    public String toJSON(boolean expanded_texts) {

        try {
            return ServerUtils.serialize(this);
        }
        catch (Exception e) {
            e.printStackTrace();
            log.severe("Question serialization failed. Message: " + e.getLocalizedMessage());
            return null;
        }
    }

    @JsonIgnore
    public void generateIds() {

        if (this.getQuestion_id() == null || this.getQuestion_id().equals("")) {
            this.setQuestion_id(new UUID().toString());
        }
        if (this.getAnswers() != null) {
            for (Answer ans : this.getAnswers()) {
                if (ans.getAnswer_id() == null || ans.getAnswer_id().equals("")) {
                    ans.setAnswer_id(new UUID().toString());
                }
            }
        }
    }

    public Question answer(String responder, String adapterID, String answer_id, String answer_input, String sessionKey) {

        log.info("answerTest: " + answer_input);
        boolean answered = false;
        Answer answer = null;
        if (this.getType().equals("open")) {
            // updated as part of bug#16 at
            // https://github.com/almende/dialog/issues/16
            // Infinitely prepares the same question when an invalid format of
            // Open question is received.
            ArrayList<Answer> answers = getAnswers();
            answer = answers != null ? answers.get(0) : null;
        }
        else if (this.getType().equals("openaudio")) {
            answer = this.getAnswers().get(0);
            try {
                answer_input = URLDecoder.decode(answer_input, "UTF-8");
                log.info("Received answer: " + answer_input);
            }
            catch (Exception e) {
                log.severe(e.getLocalizedMessage());
            }
        }
        //just get the next question if its a comment, referral or an exit question
        else if (Arrays.asList("comment", "referral").contains(getType())) {
            if (this.getAnswers() == null || this.getAnswers().size() == 0) {
                return null;
            }
            answer = this.getAnswers().get(0);
        }
        else if (answer_id != null) {
            answered = true;
            Collection<Answer> answers = question.getAnswers();
            for (Answer ans : answers) {
                if (ans.getAnswer_id().equals(answer_id)) {
                    answer = ans;
                    break;
                }
            }
        }
        else if (answer_input != null) {
            answered = true;
            // check all answers of question to see if they match, possibly
            // retrieving the answer_text for each
            answer_input = answer_input.trim();
            ArrayList<Answer> answers = question.getAnswers();
            for (Answer ans : answers) {
                if (ans.getAnswer_text() != null &&
                    ans.getAnswer_text().replace("dtmfKey://", "").equalsIgnoreCase(answer_input)) {
                    answer = ans;
                    break;
                }
            }
            if (answer == null) {
                for (Answer ans : answers) {
                    if (ans.getAnswer_expandedtext(this.preferred_language,
                                                   Dialog.getCredentialsFromSession(sessionKey))
                                                    .equalsIgnoreCase(answer_input)) {
                        answer = ans;
                        break;
                    }
                }
            }
            if (answer == null) {
                try {
                    int answer_nr = Integer.parseInt(answer_input);
                    if (answer_nr <= answers.size()) {
                        answer = answers.get(answer_nr - 1);
                    }
                }
                catch (NumberFormatException ex) {
                    log.severe(ex.getLocalizedMessage());
                    if (answer_input.equals("#") && answers.size() > 11) {

                    }
                    else if (answer_input.equals("*") && answers.size() > 10) {

                    }
                    else {
                        for (Answer ans : answers) {
                            if (ans.getAnswer_text() != null && ans.getAnswer_text().contains(answer_input)) {
                                answer = ans;
                                break;
                            }
                        }
                    }

                }
            }
        }
        else if (answer_input == null) {
            return retryLoadingQuestion(adapterID, answer_input, sessionKey);
        }

        Question newQ = null;
        if (!this.getType().equals("comment") && answer == null) {
            // Oeps, couldn't find/handle answer, just repeat last question:
            // TODO: somewhat smarter behavior? Should dialog standard provide
            // error handling?
            if (answered) {
                HashMap<String, Object> extras = null;
                if (adapterID != null) {
                    String localAddress = AdapterConfig.getAdapterConfig(adapterID).getMyAddress();
                    if (localAddress != null) {
                        extras = new HashMap<String, Object>();
                        extras.put("requester", localAddress);
                    }
                }
                newQ = this.event("exception", "Wrong answer received", extras, responder, sessionKey);
            }
            if (newQ != null) {
                return newQ;
            }
            return retryLoadingQuestion(adapterID, answer_input, sessionKey);
        }
        else {
            // flush any existing retry counters for this session
            flushRetryCount(sessionKey);
        }

        // Send answer to answer.callback.
        AnswerPost ans = new AnswerPost(this.getQuestion_id(), answer.getAnswer_id(), answer_input, responder);
        ans.getExtras().put("adapterId", adapterID);
        String requester = null;
        if (adapterID != null) {
            requester = AdapterConfig.getAdapterConfig(adapterID).getMyAddress();
        }
        if (requester != null) {
            ans.getExtras().put("requester", requester);
            ans.getExtras().put("sessionKey", sessionKey);
        }
        String url = answer.getCallback();
        // Check if answer.callback gives new question for this dialog
        if (url != null) {
            try {
                log.info(String.format("answerText: %s and answer: %s", answer_input, om.writeValueAsString(answer)));

                url = ServerUtils.getURLWithQueryParams(url, "responder", responder);
                url = appendParentSessionKeyToURL(sessionKey, url);
                String post = om.writeValueAsString(ans);
                log.info("Going to send: " + post);
                String newQuestionJSON = null;
                AFHttpClient client = ParallelInit.getAFHttpClient();
                String credentialsFromSession = Dialog.getCredentialsFromSession(sessionKey);
                if (credentialsFromSession != null) {
                    client.addBasicAuthorizationHeader(credentialsFromSession);
                }
                newQuestionJSON = client.post(post, url.replace(" ", URLEncoder.encode(" ", "UTF-8")));

                log.info("Received new question (answer): " + newQuestionJSON);
                dialogLog.info(adapterID, "Received new question (answer): " + newQuestionJSON, null, sessionKey);

                newQ = om.readValue(newQuestionJSON, Question.class);
                newQ.setPreferred_language(preferred_language);
                
                if(newQ.getType() == null) {
                    newQ.setType( "comment" );
                }
            }
            catch (ClientHandlerException ioe) {
                dialogLog.severe(adapterID, String.format("Unable to load question from: %s. \n Error: %s",
                                                          answer.getCallback(), ioe.getMessage()), null, sessionKey);
                log.severe(ioe.toString());
                ioe.printStackTrace();
                newQ = this.event("exception", "Unable to load question", null, responder, sessionKey);
            }
            catch (Exception e) {
                dialogLog.severe(adapterID, "Unable to parse question json: " + e.getMessage(), null, sessionKey);
                log.severe(e.toString());
                newQ = this.event("exception", "Unable to parse question json", null, responder, sessionKey);
            }
        }
        return newQ;
    }

    public Question event(String eventType, String message, Object extras, String responder, String sessionKey) {

        log.info(String.format("Received: %s Message: %s Responder: %s Extras: %s", eventType, message, responder,
                               ServerUtils.serializeWithoutException(extras)));
        ArrayList<EventCallback> events = this.getEvent_callbacks();
        EventCallback eventCallback = null;
        if (events != null) {
            for (EventCallback e : events) {
                if (e.getEvent().toLowerCase().equals(eventType)) {
                    eventCallback = e;
                    break;
                }
            }
        }

        if (eventCallback == null) {
            // Oeps, couldn't find/handle event, just repeat last question:
            // TODO: somewhat smarter behavior? Should dialog standard provide
            // error handling?
            if (eventType.equals("hangup") || eventType.equals("exception") ||
                this.question.getType().equals("referral") || eventType.equals("answered")) {
                return null;
            }
            else {
                return retryLoadingQuestion(null, null, sessionKey);
            }
        }

        Question newQ = null;
        String url = eventCallback.getCallback();
        if (url != null) {
            EventPost event = new EventPost(responder, this.getQuestion_id(), eventType, message, extras);
            AFHttpClient client = ParallelInit.getAFHttpClient();
            try {
                url = ServerUtils.getURLWithQueryParams(url, "responder", responder);
                url = appendParentSessionKeyToURL(sessionKey, url);
                String post = om.writeValueAsString(event);
                String credentialsFromSession = Dialog.getCredentialsFromSession(sessionKey);
                if (credentialsFromSession != null) {
                    client.addBasicAuthorizationHeader(credentialsFromSession);
                }
                String s = client.post(post, url.replace(" ", URLEncoder.encode(" ", "UTF-8")));
                log.info("Received new question (event: "+eventType+"): " + s);

                if (s != null && !s.equals("")) {
                    newQ = om.readValue(s, Question.class);
                    newQ.setPreferred_language(preferred_language);
                }
            }
            catch (Exception e) {
                log.severe(e.toString());
            }
        }
        return newQ;
    }

    // Getters/Setters:
    @Override
    public String getQuestion_id() {

        return question.getQuestion_id();
    }

    @Override
    public String getQuestion_text() {

        return question.getQuestion_text();
    }

    @Override
    public String getType() {

        return question.getType();
    }

    @Override
    public List<String> getUrl() {

        return question.getUrl();
    }

    @Override
    public String getRequester() {

        return question.getRequester();
    }

    @Override
    @JsonIgnore
    public HashMap<String, String> getExpandedRequester(String sessionKey) {

        return question.getExpandedRequester(this.preferred_language, sessionKey);
    }

    @Override
    @JsonIgnore
    public HashMap<String, String> getExpandedRequester(String language, String sessionKey) {

        this.preferred_language = language;
        return question.getExpandedRequester(language, sessionKey);
    }

    @Override
    public ArrayList<Answer> getAnswers() {

        return question.getAnswers();
    }

    @Override
    public ArrayList<EventCallback> getEvent_callbacks() {

        return question.getEvent_callbacks();
    }

    @Override
    @JsonIgnore
    public String getQuestion_expandedtext(String sessionKey) {

        return question.getQuestion_expandedtext(this.preferred_language, sessionKey);
    }

    @Override
    @JsonIgnore
    public String getQuestion_expandedtext(String language, String sessionKey) {

        this.preferred_language = language;
        return question.getQuestion_expandedtext(language, sessionKey);
    }

    @Override
    public void setQuestion_id(String question_id) {

        question.setQuestion_id(question_id);
    }

    @Override
    public void setQuestion_text(String question_text) {

        question.setQuestion_text(question_text);
    }

    @Override
    public void setType(String type) {

        question.setType(type);
    }

    @Override
    public void setUrl(Object url) {

        question.setUrl(url);
    }

    @Override
    public void setRequester(String requester) {

        question.setRequester(requester);
    }

    @Override
    public void setAnswers(ArrayList<Answer> answers) {

        question.setAnswers(answers);
    }

    @Override
    public void setEvent_callbacks(ArrayList<EventCallback> event_callbacks) {

        question.setEvent_callbacks(event_callbacks);
    }

    public String getPreferred_language() {

        return preferred_language;
    }

    public void setPreferred_language(String preferred_language) {

        this.preferred_language = preferred_language;
    }

    @Override
    public String getData() {

        return this.question.getData();
    }

    @Override
    public void setData(String data) {

        this.question.setData(data);

    }

    @Override
    public String getTrackingToken() {

        return this.question.getTrackingToken();
    }

    @Override
    public void setTrackingToken(String token) {

        this.question.setTrackingToken(token);
    }

    @JsonProperty("media_properties")
    public Collection<MediaProperty> getMedia_properties() {

        return media_properties;
    }

    @JsonProperty("media_properties")
    public void setMedia_Properties(Collection<MediaProperty> media_properties) {

        this.media_properties = media_properties;
    }

    @JsonIgnore
    public Map<MediaPropertyKey, String> getMediaPropertyByType(MediumType type) {

        return getMediaPropertyByType(this.media_properties, type);
    }

    public String getMediaPropertyValue(MediumType type, MediaPropertyKey key) {

        return getMediaPropertyValue(this.media_properties, type, key);
    }

    /**
     * fetches the first MediaProperty value from teh collection of
     * media_properties based on the type and key
     * 
     * @param media_properties
     * @param type
     * @param key
     * @return
     */
    public static String getMediaPropertyValue(Collection<MediaProperty> media_properties, MediumType type,
                                               MediaPropertyKey key) {

        Map<MediaPropertyKey, String> properties = getMediaPropertyByType(media_properties, type);
        if (properties != null) {
            if (properties.containsKey(key)) {
                return properties.get(key);
            }
        }
        return null;
    }

    @JsonIgnore
    public static Map<MediaPropertyKey, String> getMediaPropertyByType(Collection<MediaProperty> media_properties,
                                                                       MediumType type) {

        if (media_properties != null) {
            for (MediaProperty mediaProperties : media_properties) {
                if (mediaProperties.getMedium().equals(type)) {
                    return mediaProperties.getProperties();
                }
            }
        }
        return null;
    }

    public void addMedia_Properties(MediaProperty mediaProperty) {

        media_properties = media_properties == null ? new ArrayList<MediaProperty>() : media_properties;
        media_properties.add(mediaProperty);
    }

    /**
     * fetches the first event which matches the parameter
     * 
     * @param event
     * @return
     */
    public EventCallback getEventCallback(String event) {

        ArrayList<EventCallback> event_callbacks = getEvent_callbacks();
        if (event_callbacks != null) {
            for (EventCallback eventCallback : event_callbacks) {
                if (eventCallback.getEvent().equals(event)) {
                    return eventCallback;
                }
            }
        }
        return null;
    }

    public void addEventCallback(String eventId, String event, String callback) {

        if (question.getEvent_callbacks() == null) {
            question.setEvent_callbacks(new ArrayList<EventCallback>());
        }
        EventCallback eventCallback = new EventCallback();
        eventCallback.setEvent(event);
        eventCallback.setEvent_id(eventId);
        eventCallback.setCallback(callback);
        question.getEvent_callbacks().add(eventCallback);
    }

    /**
     * gets the retry Count of the number of times the Question has been loaded
     * corresponding to the current sessionKey. If the sessionKey is not found,
     * it will create one and set the value to 1.
     * 
     * @param sessionKey
     * @return
     */
    public static Integer getRetryCount(String sessionKey) {

        Session session = Session.getSession(sessionKey);
        Integer retryCount = null;
        if (session != null) {
            retryCount = session.getRetryCount();
            if (retryCount == null) {
                retryCount = 0;
                session.setRetryCount(retryCount);
                session.storeSession();
            }
        }
        return retryCount;
    }
    
    /**
     * flushes the retry count corresponding to hte session key in the
     * questionRetryCounter
     * 
     * @param sessionKey
     */
    public static void flushRetryCount(String sessionKey) {
        Session session = Session.getSession(sessionKey);
        if (session != null) {
            session.setRetryCount(0);
            session.storeSession();
        }
    }

    /**
     * updates The retryCount based on the sessionKey. If sessionKey is null and
     * questionRetryCounter has only one element, then increments it
     * 
     * @param sessionKey
     * @return
     */
    public static Integer updateRetryCount(String sessionKey) {

        Integer retryCount = getRetryCount(sessionKey);
        if (retryCount != null) {
            Session session = Session.getSession(sessionKey);
            session.setRetryCount(++retryCount);
            session.storeSession();
        }
        return retryCount;
    }
    
    /**
     * Returns the message body in the format: <br>
     * Question_Text <br>
     * [ AnswerText1 | AnswerText2 | ... AnswerText-n ]
     * @return
     */
    @JsonIgnore
    public String getTextWithAnswerTexts(String sessionKey) {

        String reply = getQuestion_expandedtext(sessionKey);
        if (question.getAnswers() != null) {
            reply += "\n[";
            for (Answer ans : question.getAnswers()) {
                reply += " " +
                         ans.getAnswer_expandedtext(getPreferred_language(),
                                                    Dialog.getCredentialsFromSession(sessionKey)) + " |";
            }
            reply = reply.substring(0, reply.length() - 1) + "]";
        }
        return reply;
    }

    /**
     * (intended for VOICE) simple retry mechanism to reload the question
     * finitely.
     * 
     * @param adapterID
     * @param answer_input
     * @param sessionKey
     * @return
     */
    protected Question retryLoadingQuestion(String adapterID, String answer_input, String sessionKey) {

        String retryLoadLimit = getMediaPropertyValue(MediumType.BROADSOFT, MediaPropertyKey.RETRY_LIMIT);
        retryLoadLimit = retryLoadLimit != null ? retryLoadLimit : String.valueOf(DEFAULT_MAX_QUESTION_LOAD);
        Integer retryCount = getRetryCount(sessionKey);
        if (retryLoadLimit != null && retryCount != null && retryCount < Integer.parseInt(retryLoadLimit)) {
            updateRetryCount(sessionKey);
            log.info(String.format("returning the same question as RetryCount: %s < RetryLoadLimit: %s", retryCount,
                                   retryLoadLimit));
            if (adapterID != null && answer_input != null) {
                dialogLog.warning(adapterID, String
                                                .format("Repeating question %s (count: %s) due to invalid answer: %s",
                                                        ServerUtils.serializeWithoutException(this), retryCount,
                                                        answer_input), null, sessionKey);
            }
            return this;
        } 
        else if (sessionKey != null) {
            flushRetryCount(sessionKey);
            log.warning(String.format("return null question as RetryCount: %s >= DEFAULT_MAX: %s or >= LOAD_LIMIT: %s",
                                      retryCount, DEFAULT_MAX_QUESTION_LOAD, retryLoadLimit));
            if (adapterID != null && answer_input != null) {
                dialogLog.warning(adapterID,
                                  String.format("Return empty/null question as retryCount %s equals DEFAULT_MAX: %s or LOAD_LIMIT: %s, due to invalid answer: %s",
                                                retryCount, DEFAULT_MAX_QUESTION_LOAD, retryLoadLimit, answer_input), null, sessionKey);
            }
            return null;
        }
        else {
            log.info("returning the same question as its TextServlet request??");
            dialogLog.warning(adapterID,
                              String.format("Repeating question %s due to invalid answer: %s in the TextServlet",
                                            ServerUtils.serializeWithoutException(this), answer_input), null,
                              sessionKey);
            return this;
        }
    }
    
    /** Adds the parent session key attached with the given sessionkey to the url
     * @param sessionKey
     * @param url
     * @return
     * @throws UnsupportedEncodingException
     */
    public static String appendParentSessionKeyToURL(String sessionKey, String url) throws UnsupportedEncodingException {

        // Add session key
        url = ServerUtils.getURLWithQueryParams(url, "sessionKey", sessionKey);
        
        // try to add the parent session key
        Session session = Session.getSession(sessionKey);
        if (session != null) {
            String parentSessionKey = session.getAllExtras().get(Session.PARENT_SESSION_KEY);
            if (parentSessionKey != null) {
                url = ServerUtils.getURLWithQueryParams(url, Session.PARENT_SESSION_KEY, parentSessionKey);
            }
        }
        return url;
    }
}