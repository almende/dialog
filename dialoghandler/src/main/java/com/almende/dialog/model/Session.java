package com.almende.dialog.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.TextServlet;
import com.almende.dialog.adapter.VoiceXMLRESTProxy;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.util.ServerUtils;
import com.almende.dialog.util.TimeUtils;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.almende.util.twigmongo.FilterOperator;
import com.almende.util.twigmongo.QueryResultIterator;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore.RootFindCommand;
import com.almende.util.twigmongo.annotations.Id;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class Session{

    private static final Logger log = Logger.getLogger("DialogHandler");
    private static ConnectionFactory rabbitMQConnectionFactory;
    private static final String SESSION_QUEUE_NAME = "SESSION_POST_PROCESS_QUEUE";
    
    @Id
    public String key = "";

    String accountId;
    String startUrl;
    String remoteAddress;
    String localAddress;
    String direction;
    String type;
    String externalSession;
    String keyword;
    String adapterID;
    String trackingToken;
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
	
	@JsonIgnore
	public void kill(){
		this.killed=true;
		this.storeSession();
        if ( AdapterAgent.ADAPTER_TYPE_XMPP.equalsIgnoreCase( this.getType() )
            || AdapterAgent.ADAPTER_TYPE_EMAIL.equalsIgnoreCase( this.getType() )
            || AdapterAgent.ADAPTER_TYPE_SMS.equalsIgnoreCase( this.getType() ) )
        {
            TextServlet.killSession( this );
        }
        else
        {
            VoiceXMLRESTProxy.killSession( this );
        }
	}
	
	@JsonIgnore
	public String toJSON() {
		try {
			return JOM.getInstance().writeValueAsString(this);
		} catch (Exception e) {
			log.severe("Session toJSON: failed to serialize Session!");
		}
		return null;
	}
	@JsonIgnore
	public static Session fromJSON(String json) {
		try {
			return JOM.getInstance().readValue(json, Session.class);
		} catch (Exception e){
			log.severe("Session fromJSON: failed to parse Session JSON!: "+ json );
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
                oldSession.question = question;
                datastore.storeOrUpdate(oldSession);
            }
            else {
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
        datastore.delete(this);
    }
	
    public static void drop( String key )
    {
        Session session = getSession( key );
        if (session != null) {
            session.drop();
        }
    }
    
    public static Session getOrCreateSession(String key) {

        return getOrCreateSession(key, null);
    }

    @JsonIgnore
    public static Session getOrCreateSession(String key, String keyword) {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        Session session = datastore.load(Session.class, key);
        // If there is no session create a new one for the user
        if (session == null) {
            String[] split = key.split("\\|");

            if (split.length == 3) {
                String type = split[0];
                String localaddress = split[1];
                AdapterConfig config = null;
                ArrayList<AdapterConfig> configs = AdapterConfig.findAdapters(type, localaddress, null);
                // This assume there is a default keyword
                if (configs.size() == 0) {
                    log.warning("No adapter found for new session type: " + type + " address: " + localaddress);
                    return null;
                }
                else if (configs.size() == 1) {
                    config = configs.get(0);
                    log.info("Adapter found for new session type: " + type + " address: " + localaddress);
                }
                else {
                    AdapterConfig defaultConfig = null;
                    for (AdapterConfig conf : configs) {
                        if (conf.getKeyword() == null) {
                            defaultConfig = conf;
                        }
                        else if (keyword != null && conf.getKeyword().equals(keyword)) {
                            config = conf;
                        }
                    }
                    if (config == null) {
                        log.warning("No adapter with right keyword so using default type: " + type + " address: " +
                                    localaddress);
                        config = defaultConfig;
                    }
                    else {
                        log.info("Adapter found with right keyword type: " + type + " address: " + localaddress +
                                 " keyword: " + keyword);
                    }
                }
                session = getOrCreateSession(config, split[2]);
            }
            else {
                log.severe("getSession: incorrect key given:" + key);
            }
        }
        return session;
    }
    
    public static Session getOrCreateSession(AdapterConfig config, String remoteAddress) {
        
        String key = config.getAdapterType() + "|" + config.getMyAddress() + "|" + remoteAddress;
                                        
        //TODO: check account/pubkey usage here
        Session session = new Session();
        session.setAdapterID(config.getConfigId());
        session.setAccountId(config.getOwner());
        session.setRemoteAddress(remoteAddress);
        session.setLocalAddress(config.getMyAddress());
        session.setType(config.getAdapterType());
        session.key = key;
        session.creationTimestamp = String.valueOf(TimeUtils.getServerCurrentTimeInMillis());
        session.storeSession();
        log.info("new session created with id: " + session.key);
        
        return session;
    }
    
    /**
     * retusn teh session without creating a new default one
     * @param sessionKey
     * @return
     */
    @JsonIgnore
    public static Session getSession(String sessionKey) {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        return datastore.load(Session.class, sessionKey);
    }
	
    @JsonIgnore
    public AdapterConfig getAdapterConfig() {

        if (getAdapterID() != null)
            return AdapterConfig.getAdapterConfig(getAdapterID());
        return null;
    }
	
    public Map<String, String> getExtras()
    {
        extras = extras != null ? extras : new HashMap<String, String>();
        return extras;
    }
    public void setExtras( Map<String, String> extras )
    {
        this.extras = extras;
    }
    /**
     * used to mimick the String store entity. 
     * @return the first value found in the {@link Session#extras}
     */
    public static String getString(String sessionKey) {

        Session session = getSession(sessionKey);
        return session != null && !session.getExtras().isEmpty() ? session.getExtras().values().iterator().next()
                                                                : null;
    }
    
    public String getKey()
    {
        return key;
    }
    public void setKey( String key )
    {
        this.key = key;
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
        session.getExtras().put( key, valueTobeStored );
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

        if (question == null) {
            return;
        }
        else {
            this.question = question;
        }
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

    
    public void setExternalSession(String externalSession) {
        this.externalSession = externalSession;
    }
    
    
    public String getExternalSession() {
        return this.externalSession;
    }

    
    public String getAdapterID() {
        return this.adapterID;
    }

    
    public void setAdapterID(String adapterID) {
        this.adapterID = adapterID;
    }
    
    
    public String getTrackingToken() {
        return this.trackingToken;
    }
    
    
    public void setTrackingToken(String token) {
        this.trackingToken = token;     
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
            if ( session != null && session.getExtras().get( extrasKey ) != null
                && session.getExtras().get( extrasKey ).equals( extrasValue ) )
            {
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
    
    public void pushSessionToQueue() {

        try {
            log.info(String.format("---------Pushing session for post processing: %s---------", key));
            rabbitMQConnectionFactory = rabbitMQConnectionFactory != null ? rabbitMQConnectionFactory
                                                                         : new ConnectionFactory();
            rabbitMQConnectionFactory.setHost("localhost");
            Connection connection = rabbitMQConnectionFactory.newConnection();
            Channel channel = connection.createChannel();
            channel.queueDeclare(SESSION_QUEUE_NAME, false, false, false, null);
            channel.basicPublish("", SESSION_QUEUE_NAME, null, key.getBytes());
            channel.close();
            connection.close();
        }
        catch (Exception e) {
            log.severe("Error seen: " + e.getLocalizedMessage());
        }
    }
    
    /**
     * parses the sessionKey from the method parameters and tries to fetch it
     * @param adapterType
     * @param localAddress
     * @param remoteAddress
     * @return
     */
    public static Session getSession(String adapterType, String localAddress, String remoteAddress) {

        String sessionKey = adapterType + "|" + localAddress + "|" + remoteAddress;
        return getSession(sessionKey);
    }
}
