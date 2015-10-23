package com.almende.dialog.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.adapter.TextServlet;
import com.almende.dialog.adapter.VoiceXMLRESTProxy;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.jackson.JOM;
import com.almende.util.twigmongo.FilterOperator;
import com.almende.util.twigmongo.QueryResultIterator;
import com.almende.util.twigmongo.SortDirection;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore.RootFindCommand;
import com.almende.util.twigmongo.annotations.Id;
import com.askfast.commons.entity.AccountType;
import com.askfast.commons.utils.TimeUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Session{

    private static final Logger log = Logger.getLogger("DialogHandler");
//    private static ConnectionFactory rabbitMQConnectionFactory;
//    private static final String SESSION_QUEUE_NAME = "SESSION_POST_PROCESS_QUEUE";
    public static final String SESSION_KEY = "sessionKey";
    public static final String IS_TEST_SESSION_KEY = "isTest";
    public static final String PARENT_SESSION_KEY = "parentSessionKey";
    public static final String CHILD_SESSION_KEY = "childSessionKey";
    public static final String EXTERNAL_CONFERENCE_KEY = "conferenceKey";
    /**
     * Tag used by calling adapters to mark it in the session if a call is
     * picked up or not. If there is a preconnect on the callee's end, then its
     * marked as true only if a connection is setup.
     */
    private static final String IS_CALL_PICKED_UP = "isCallPickedUp";
    private static final String IS_CALL_CONNECTED = "isCallConnected";
    
    @Id
    public String key = "";

    String accountId;
    String startUrl;
    String remoteAddress;
    String localAddress;
    String localName;
    String direction;
    String type;
    /**
     * session id maintained by our providers
     */
    String externalSession;
    /**
     * Session id maintained by us as a reference. 
     * Used by Broadsoft to store an intermediary id of adapterType|localAddress|remoteAddress
     */
    String internalSession;
    
    String keyword;
    String adapterID;
    String creationTimestamp;
    String startTimestamp;
    String answerTimestamp;
    String releaseTimestamp;
    String ddrRecordId;
    public boolean killed = false;
    String language = null;
    Question question = null;
    Map<String, String> extras = null;
    Integer retryCount = null;
    AccountType accountType;
    @JsonIgnore
    boolean existingSession = false;
    
    @JsonIgnore
    AdapterConfig adapterConfig = null;
    @JsonIgnore
    DDRRecord ddrRecord = null;
    
    String callStatus = null;
	
    @JsonIgnore
    public void kill() {

        this.killed = true;
        this.storeSession();
        if (AdapterAgent.ADAPTER_TYPE_XMPP.equalsIgnoreCase(this.getType()) ||
            AdapterAgent.ADAPTER_TYPE_EMAIL.equalsIgnoreCase(this.getType()) ||
            AdapterAgent.ADAPTER_TYPE_SMS.equalsIgnoreCase(this.getType())) {
            TextServlet.killSession(this);
        }
        else {
            VoiceXMLRESTProxy.killSession(this);
        }
    }

    @JsonIgnore
    public String toJSON() {

        try {
            return JOM.getInstance().writeValueAsString(this);
        }
        catch (Exception e) {
            log.severe("Session toJSON: failed to serialize Session!");
        }
        return null;
    }

    /**
     * Session can be updated from many different sources. esp in
     * {@link VoiceXMLRESTProxy}. So every call of this method will ignore any
     * null updates. i.e if the actual Session in the db is without a null value
     * and the new entiry is with a null value, the null value is not
     * considered. This is done on purpose so as to avoid multiple fetch requests to update a Session entity
     */
    @JsonIgnore
    public void storeSession() {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        //update the values and not reset
        try {
            Session oldSession = getSession(key);
            if (oldSession != null) {
                String serializedSession = ServerUtils.serialize(this);
                JOM.getInstance().readerForUpdating(oldSession).readValue(serializedSession);
                if (question != null) {
                    oldSession.question = question;
                }
                datastore.storeOrUpdate(oldSession);
            }
            else {
                key = key.toLowerCase();
                datastore.storeOrUpdate(this);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @JsonIgnore
    public void drop() {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        Session session = datastore.load(Session.class, key.toLowerCase());
        if (session != null) {
            log.info("Deleting session with id: "+ session.getKey());
            datastore.delete(session);
        }
    }
    
    @JsonIgnore
    public void dropIfRemoteAddressMatches(String remoteAddress) {

        if (remoteAddress != null && remoteAddress.equals(this.remoteAddress)) {
            drop();
        }
    }
	
    public static void drop( String key )
    {
        Session session = getSession( key );
        if (session != null) {
            session.drop();
        }
    }

    public static Session getOrCreateSession(AdapterConfig config, String remoteAddress) {

        Session session = getSessionByInternalKey(config.getAdapterType(), config.getMyAddress(), remoteAddress);
        if (session == null) {
            session = createSession(config, remoteAddress);
            session.existingSession = false;
        }
        else {
            session.existingSession = true;
        }
        if (config.getPreferred_language() != null) {
            session.setLanguage(config.getPreferred_language());
        }
        return session;
    }
    
    public static Session createSession(AdapterConfig config, String remoteAddress, boolean flushOldSessionIfAny) {

        Session session = getSessionByInternalKey(config.getAdapterType(), config.getMyAddress(), remoteAddress);
        if (session != null && flushOldSessionIfAny) {
            session.drop();
        }
        session = createSession(config, remoteAddress);
        session.existingSession = false;
        return session;
    }
    
    public static Session createSession(AdapterConfig config, String remoteAddress) {
        return createSession(config, config.getMyAddress(), remoteAddress);
    }

    public static Session createSession(AdapterConfig config, String localAddress, String remoteAddress) {

        String internalSessionKey = config.getAdapterType() + "|" + localAddress + "|" + remoteAddress;
        Session session = new Session();
        session.setAdapterID(config.getConfigId());
        session.setRemoteAddress(remoteAddress);
        session.setLocalAddress(localAddress);
        session.setType(config.getAdapterType());
        session.setKeyword(config.getKeyword());
        session.internalSession = internalSessionKey.toLowerCase();
        session.key = UUID.randomUUID().toString();
        session.creationTimestamp = String.valueOf(TimeUtils.getServerCurrentTimeInMillis());
        session.storeSession();
        log.info("new session created with id: " + session.key);
        session.existingSession = false;
        return session;
    }
    
    /**
     * returns the session without creating a new default one
     * @param sessionKey
     * @return
     */
    @JsonIgnore
    public static Session getSession(String sessionKey) {

        if (sessionKey != null) {
            TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
            return datastore.load(Session.class, sessionKey.toLowerCase());
        }
        return null;
    }
    
    /**
     * Returns the first session found for the localName. Localname is used for
     * E.g. SMS adapter with myAddress TEST but senderName is 0612345678 while
     * initiating an outbound request. LocalName is attached to all TextMessages
     * 
     * @param internalSessionKey
     * @return
     */
    public static Session getSessionByLocalName(String adapterType, String remoteAddress, String localName) {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<Session> sessionFindCommand = datastore.find().type(Session.class);
        if (adapterType != null) {
            sessionFindCommand = sessionFindCommand.addFilter("type", FilterOperator.EQUAL, adapterType.toLowerCase());
        }
        if (remoteAddress != null) {
            sessionFindCommand = sessionFindCommand.addFilter("remoteAddress", FilterOperator.EQUAL, remoteAddress);
        }
        if (localName != null) {
            sessionFindCommand = sessionFindCommand.addFilter("localName", FilterOperator.EQUAL, localName);
        }
        sessionFindCommand.addSort("creationTimestamp", SortDirection.DESCENDING);
        sessionFindCommand.fetchMaximum(1);
        QueryResultIterator<Session> sessionIterator = sessionFindCommand.now();
        if (sessionIterator != null && sessionIterator.hasNext()) {
            return sessionIterator.next();
        }
        return null;
    }
    
    /**
     * Returns the first session found for the external session id given
     * 
     * @param externalSessionKey
     * @return
     */
    public static Session getSessionByExternalKey(String externalSessionKey) {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        QueryResultIterator<Session> sessionIterator = datastore.find().type(Session.class)
                                        .addFilter("externalSession", FilterOperator.EQUAL, externalSessionKey).now();
        if (sessionIterator != null && sessionIterator.hasNext()) {
            return sessionIterator.next();
        }
        return null;
    }
    
    /**
     * Parses the sessionKey from the method parameters and tries to fetch it.
     * Does an match with the internalSessionKey first. If not found, tries to
     * fetch it by the localName. Still not found, returns null
     * 
     * @param adapterType
     * @param localAddress
     * @param remoteAddress
     * @return
     */
    public static Session getSessionByInternalKey(String adapterType, String localAddress, String remoteAddress) {

        adapterType = adapterType != null ? adapterType.toLowerCase() : null;
        String sessionKey = adapterType + "|" + localAddress + "|" + remoteAddress;
        Session sessionByInternalKey = getSessionByInternalKey(sessionKey);
        if (sessionByInternalKey == null) {
            //try fetching the session by localName
            sessionByInternalKey = getSessionByLocalName(adapterType, remoteAddress, localAddress);
        }
        return sessionByInternalKey;
    }
    
    /**
     * parses the sessionKey from the method parameters and tries to fetch it
     * 
     * @param adapterType
     * @param localAddress
     * @param remoteAddress
     * @return
     */
    public static String getInternalSessionKey(AdapterConfig config, String remoteAddress) {

        if (config != null && remoteAddress != null) {
            return (config.getAdapterType().toLowerCase() + "|" + config.getMyAddress() + "|" + remoteAddress)
                                            .toLowerCase();
        }
        else {
            return null;
        }
    }

    
    public static List<Session> findSessionByLocalAndRemoteAddress(String localAddress, String remoteAddress) {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        return datastore.find().type(Session.class).addFilter("localAddress", FilterOperator.EQUAL, localAddress)
                                        .addFilter("remoteAddress", FilterOperator.EQUAL, remoteAddress)
                                        .addFilter("releaseTimestamp", FilterOperator.EQUAL, null).now().toArray();
    }
    
    public static List<Session> getAllSessions() {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        return datastore.find().type(Session.class).now().toArray();
    }
    
    @JsonIgnore
    public AdapterConfig getAdapterConfig() {

        if(adapterConfig == null && adapterID != null) {
            adapterConfig = AdapterConfig.getAdapterConfig(adapterID);
        }
        return adapterConfig;
    }
    
    @JsonIgnore
    public DDRRecord getDDRRecord() {

        if (ddrRecord == null && ddrRecordId != null) {
            try {
                ddrRecord = DDRRecord.getDDRRecord(ddrRecordId, accountId);
            }
            catch (Exception e) {
                e.printStackTrace();
                log.severe(e.toString());
            }
        }
        return ddrRecord;
    }
	
    @JsonProperty("extras")
    public Map<String, String> getAllExtras()
    {
        extras = extras != null ? extras : new HashMap<String, String>();
        return extras;
    }
    
    /**
     * Used to fetch all extra data but not return sensitive data like provider etc
     * @return
     */
    @JsonIgnore
    public Map<String, String> getPublicExtras() {

        HashMap<String, String> extrasCopy = extras != null ? new HashMap<String, String>(extras)
                                                           : new HashMap<String, String>();
        extrasCopy.remove(AdapterConfig.ADAPTER_PROVIDER_KEY);
        extrasCopy.remove(Dialog.DIALOG_BASIC_AUTH_HEADER_KEY);
        extrasCopy.remove(AdapterConfig.ACCESS_TOKEN_KEY);
        extrasCopy.remove(AdapterConfig.ACCESS_TOKEN_SECRET_KEY);
        extrasCopy.remove(AdapterConfig.XSI_USER_KEY);
        extrasCopy.remove(AdapterConfig.XSI_PASSWORD_KEY);
        extrasCopy.remove(DialogAgent.BEARER_TOKEN_KEY);
        extrasCopy.remove(Session.EXTERNAL_CONFERENCE_KEY);
        return extrasCopy;
    }
    
    public void setExtras( Map<String, String> extras )
    {
        this.extras = extras;
    }

    /**
     * Adds an extra info into this session. Doesnt persist it to the DB. 
     * Additional methodcall needed.
     * @param key
     * @param value
     */
    public void addExtras(String key, String value) {

        extras = extras != null ? extras : new HashMap<String, String>();
        extras.put(key, value);
    }
    
    /**
     * used to mimick the String store entity.
     * 
     * @return the first value found in the {@link Session#extras}
     */
    public static String getString(String sessionKey) {

        Session session = getSession(sessionKey);
        return session != null && !session.getAllExtras().isEmpty() ? session.getAllExtras().values().iterator().next()
                                                                   : null;
    }
    
    public String getKey()
    {
        return key;
    }
    public void setKey( String key )
    {
        this.key = key.toLowerCase();
    }
    
    /**
     * mimicks the old string store entity by creating a new session entity with 
     * @param string
     * @param answer_input
     */
    public static Session storeString( String key, String valueTobeStored )
    {
        Session session = new Session();
        session.setKey( key );
        session.getAllExtras().put( key, valueTobeStored );
        session.storeSession();
        return session;
    }
    
    public String getLanguage()
    {
        return language;
    }
    public void setLanguage( String language )
    {
        this.language = language;
    }
    public Question getQuestion()
    {
        return question;
    }

    public void setQuestion(Question question) {

        if (question != null && question.getPreferred_language() != null) {
            this.language = question.getPreferred_language();
        }
        this.question = question;
    }
    
    public Integer getRetryCount()
    {
        return retryCount;
    }
    public void setRetryCount( Integer retryCount )
    {
        this.retryCount = retryCount;
    }
    
    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    
    public String getStartUrl() {
        return startUrl;
    }

    
    public void setStartUrl(String url) {
        this.startUrl = url;
    }

    
    public String getRemoteAddress() {
        return this.remoteAddress;
    }

    
    public String getDirection() {
        return this.direction;
    }

    
    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress=remoteAddress;
    }

    
    public void setDirection(String direction) {
        this.direction=direction;
    }

    
    public String getLocalAddress() {
        return this.localAddress;
    }

    
    public void setLocalAddress(String localAddress) {
        this.localAddress=localAddress;
    }

    
    public String getType() {
        return this.type;
    }

    
    public void setType(String type) {
        this.type=type;
    }

    /**
     * Session id maintained by our providers
     * @param externalSession
     */
    public void setExternalSession(String externalSession) {

        this.externalSession = externalSession;
    }
    
    /**
     * Session id maintained by our providers
     */
    public String getExternalSession() {
        return this.externalSession;
    }

    
    public String getAdapterID() {
        return this.adapterID;
    }

    
    public void setAdapterID(String adapterID) {
        this.adapterID = adapterID;
    }
    
    
    public String getStartTimestamp()
    {
        return startTimestamp;
    }

    public void setStartTimestamp( String startTimestamp )
    {
        this.startTimestamp = startTimestamp;
    }

    public String getAnswerTimestamp()
    {
        return answerTimestamp;
    }

    public void setAnswerTimestamp( String answerTimestamp )
    {
        this.answerTimestamp = answerTimestamp;
    }

    public String getReleaseTimestamp()
    {
        return releaseTimestamp;
    }

    public void setReleaseTimestamp( String releaseTimestamp )
    {
        this.releaseTimestamp = releaseTimestamp;
    }
    
    public AccountType getAccountType() {
        
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
    
        this.accountType = accountType;
    }

    /**
     * this returns a Question from a different session which is created for the direction and adapterId, when 
     * the extrasKey in {@link Session#extras} returns the same value.
     * @param adapterId
     * @param direction
     * @param extrasKey
     * @param extrasValue
     * @return
     */
    public static Question getQuestionFromDifferentSession( String adapterId, String direction, String extrasKey,
        String extrasValue )
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<Session> query = datastore.find().type( Session.class );
        //fetch accounts that match
        if ( adapterId != null )
        {
            query = query.addFilter( "adapterID", FilterOperator.EQUAL, adapterId );
        }
        if ( direction != null )
        {
            query = query.addFilter( "direction", FilterOperator.EQUAL, direction );
        }
        QueryResultIterator<Session> resultIterator = query.now();
        while ( resultIterator.hasNext() )
        {
            Session session = resultIterator.next();
            if (session != null && session.getAllExtras().get(extrasKey) != null &&
                session.getAllExtras().get(extrasKey).equals(extrasValue)) {
                return session.getQuestion();
            }
        }
        return null;
    }

    public String getKeyword()
    {
        return keyword;
    }

    public void setKeyword( String keyword )
    {
        this.keyword = keyword;
    }

    public String getDdrRecordId()
    {
        return ddrRecordId;
    }

    public void setDdrRecordId( String ddrRecordId )
    {
        this.ddrRecordId = ddrRecordId;
    }

    public boolean isKilled()
    {
        return killed;
    }

    public void setKilled( boolean killed )
    {
        this.killed = killed;
    }
    
    public String getCreationTimestamp() {
        
        return creationTimestamp;
    }
    
    public void setCreationTimestamp(String creationTimestamp) {
    
        this.creationTimestamp = creationTimestamp;
    }
    
//    public void pushSessionToQueue() {
//
//        try {
//            log.info(String.format("---------Pushing session for post processing: %s---------", key));
//            rabbitMQConnectionFactory = rabbitMQConnectionFactory != null ? rabbitMQConnectionFactory
//                                                                         : new ConnectionFactory();
//            rabbitMQConnectionFactory.setHost("localhost");
//            Connection connection = rabbitMQConnectionFactory.newConnection();
//            Channel channel = connection.createChannel();
//            channel.queueDeclare(SESSION_QUEUE_NAME, false, false, false, null);
//            channel.basicPublish("", SESSION_QUEUE_NAME, null, key.getBytes());
//            channel.close();
//            connection.close();
//        }
//        catch (Exception e) {
//            log.severe("Error seen: " + e.getLocalizedMessage());
//        }
//    }
    
    public String getLocalName() {
    
        return localName;
    }
    public void setLocalName(String localName) {
    
        this.localName = localName;
    }

    /**
     * This field is set to false if a new session is created in the {@link Session#getOrCreateSession} methods.
     * If an old session is fetched, this should return true.
     * @return
     */
    @JsonIgnore
    public boolean isExistingSession() {
        
        return existingSession;
    }
    
    public String getInternalSession() {
        
        return internalSession;
    }

    public void setInternalSession(String internalSession) {
    
        internalSession = internalSession != null ? internalSession.toLowerCase() : null;
        this.internalSession = internalSession;
    }

    /**
     * Returns true if the call associated with this session is answered
     * @return
     */
    @JsonIgnore
    public boolean isCallPickedUp() {
        String callStatus = getAllExtras().get(IS_CALL_PICKED_UP);
        return Boolean.parseBoolean(callStatus);
    }
    
    /**
     * Sets a call pickup status for this session
     * @param isCallPickedUp
     */
    public void setCallPickedUpStatus(boolean isCallPickedUp) {

        addExtras(IS_CALL_PICKED_UP, String.valueOf(isCallPickedUp));
    }
    
    /**
     * Returns the call status of the ongoing call
     * @return 
     */
    public String getCallStatus() {
        return callStatus;
    }
    
    /**
     * Stores the call status of the ongoing call
     * @param callStatus
     */
    public void setCallStatus(String callStatus) {
        this.callStatus = callStatus;
    }
    
    /**
     * Returns true if the call associated with this session is answered
     * @return
     */
    @JsonIgnore
    public boolean isCallConnected() {
        String callStatus = getAllExtras().get(IS_CALL_CONNECTED);
        return Boolean.parseBoolean(callStatus);
    }
    
    /**
     * Sets a call pickup status for this session
     * @param isCallConnected
     */
    public void setCallConnectedStatus(boolean isCallConnected) {

        addExtras(IS_CALL_CONNECTED, String.valueOf(isCallConnected));
    }
    
    /**
     * Add a child session key to this session for reference
     * @param childSessionKey
     */
    public void addChildSessionKey(String childSessionKey) {
        String childSessionKeys = getAllExtras().get(CHILD_SESSION_KEY);
        if(childSessionKeys==null || childSessionKeys.isEmpty()) {
            childSessionKeys = childSessionKey;
        } else {
            childSessionKeys += ","+childSessionKey;
        }
        getAllExtras().put( CHILD_SESSION_KEY, childSessionKeys );
    }
    
    /**
     * Get all the child sessions linked to this session
     * @return List with session keys
     */
    @JsonIgnore
    public List<String> getChildSessionKeys() {
        String keys = getAllExtras().get( CHILD_SESSION_KEY );
        if(keys==null || keys.isEmpty()) {
            return new ArrayList<String>();
        } else {
            return Arrays.asList( keys.split( "," ));
        }
    }
    
    
    /**
     * Gets the child session linked this this session
     * 
     * @return
     */
    @JsonIgnore
    public List<Session> getLinkedChildSession() {

        List<Session> sessions = new ArrayList<Session>();
        List<String> childSessionKeys = getChildSessionKeys();
        for (String childSessionKey : childSessionKeys) {
            Session session = getSession(childSessionKey);
            if (session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }
    
    /**
     * Gets the parent session linked this this session
     * @return
     */
    @JsonIgnore
    public Session getParentSession() {

        String parentSessionKey = getParentSessionKey();
        if (parentSessionKey != null) {
            return getSession(parentSessionKey);
        }
        return null;
    }
    
    /**
     * Gets the parent session key is found
     * 
     * @return
     */
    @JsonIgnore
    public String getParentSessionKey() {

        return getAllExtras().get(PARENT_SESSION_KEY);
    }
    
    /**
     * Gets the conference key if found in the session
     * 
     * @return
     */
    @JsonIgnore
    public String getConferenceKey() {

        return getAllExtras().get(EXTERNAL_CONFERENCE_KEY);
    }
    
    /**
     * Used to fetch the session created because of a referral communication.
     * The parentExternalKey is used to fetch the parent sesison and then the
     * child (linked/referred) session is fetched
     * 
     * @param childExternalKey Updates the externalKey of the child session if missing
     * @param parentExternalKey Used to load the child session via a parent session
     * @return
     */
    public static Session getSessionFromParentExternalId(String childExternalKey, String parentExternalKey, String remoteID) {

        //fetch the parent session for this preconnect
        Session parentSession = Session.getSessionByExternalKey(parentExternalKey);
        if (parentSession != null) {
            
            List<Session> childSessions = parentSession.getLinkedChildSession();
            for(Session childSession : childSessions) {
                if(childSession.getRemoteAddress().equals(remoteID)) {
                    // Set the externalKey from the childSession
                    if (childSession != null && childSession.getExternalSession() == null) {
                        childSession.setExternalSession(childExternalKey);
                        childSession.storeSession();
                    }
                    return childSession;
                }
            }
        }
        return null;
    }

    @JsonIgnore
    public Session reload() {

        return getSession(key);
    }
    
    /**
     * Marks the session as a test session. no ddr records are created for this.
     */
    @JsonIgnore
    public void setAsTestSession() {
        addExtras(Session.IS_TEST_SESSION_KEY, "true");
    }
    
    /**
     * Returns if the session is market as for testing. no ddr records must be created.
     * @return
     */
    @JsonIgnore
    public boolean isTestSession() {

        return Boolean.parseBoolean(getAllExtras().get(IS_TEST_SESSION_KEY));
    }
    
    /**
     * Creates a new session with same of the following values: <br>
     * 1. {@link Session#accountId} <br>
     * 2. {@link Session#startUrl} <br>
     * 3. {@link Session#remoteAddress} <br>
     * 4. {@link Session#localAddress} <br>
     * 5. {@link Session#type} <br>
     * 6. {@link Session#internalSession} <br>
     * 7. {@link Session#adapterID} <br>
     * 8. {@link Session#question} <br>
     * 9. {@link Session#localName} <br>
     * 10. {@link Session#keyword} <br>
     * @param session
     * @return
     */
    public static Session cloneSession(Session session, String direction) {
        
        Session clonedSession = null;
        if (session != null) {
            clonedSession = createSession(session.getAdapterConfig(), session.getRemoteAddress());
            clonedSession.setAccountId(session.getAccountId());
            clonedSession.setStartUrl(session.getStartUrl());
            clonedSession.setQuestion(session.getQuestion());
            clonedSession.setDirection(direction);
            clonedSession.setLocalName(session.localName);
            clonedSession.setKeyword(session.keyword);
            clonedSession.storeSession();
        }
        return clonedSession;
    }
    
    /**
     * Returns the first session found for the internal session id given
     * @param internalSessionKey
     * @return
     */
    private static Session getSessionByInternalKey(String internalSessionKey) {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<Session> sessionFindCommand = datastore.find().type(Session.class);
        sessionFindCommand = sessionFindCommand.addFilter("internalSession", FilterOperator.EQUAL,
                                                          internalSessionKey.toLowerCase());
        sessionFindCommand.addSort("creationTimestamp", SortDirection.DESCENDING);
        sessionFindCommand.fetchMaximum(1);
        QueryResultIterator<Session> sessionIterator = sessionFindCommand.now();
        if (sessionIterator != null && sessionIterator.hasNext()) {
            return sessionIterator.next();
        }
        return null;
    }
}
