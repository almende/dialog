package com.almende.dialog.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.TextServlet;
import com.almende.dialog.adapter.VoiceXMLRESTProxy;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.almende.util.twigmongo.FilterOperator;
import com.almende.util.twigmongo.QueryResultIterator;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore.RootFindCommand;
import com.almende.util.twigmongo.annotations.Id;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class Session {
	private static final Logger log = Logger.getLogger("DialogHandler");
	
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
	@JsonIgnore
	public void storeSession() {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        datastore.storeOrUpdate( this );
	}
	@JsonIgnore
	public void drop() {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        datastore.delete( this );
	}
	
    public static void drop( String key )
    {
        Session session = getSession( key );
        session.drop();
    }
    
	public static Session getSession(String key) {
		return getSession(key, null);
	}
	
	@JsonIgnore
	public static Session getSession(String key, String keyword) {
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        Session session = datastore.load(Session.class, key);
		// If there is no session create a new one for the user
		if (session == null){
			String[] split = key.split("\\|");
			
			if (split.length == 3){
				String type = split[0];
				String localaddress = split[1];
				AdapterConfig config = null; 
				ArrayList<AdapterConfig> configs = AdapterConfig.findAdapters(type, localaddress, null);
                if ( configs.size() == 0 )
                {
                    log.warning( "No adapter found for new session type: " + type + " address: " + localaddress );
                    return null;
                }
                else if ( configs.size() == 1 )
                {
                    config = configs.get( 0 );
                    log.info( "Adapter found for new session type: " + type + " address: " + localaddress );
                }
                else
                {
                    AdapterConfig defaultConfig = null;
                    for ( AdapterConfig conf : configs )
                    {
                        if ( conf.getKeyword() == null )
                        {
                            defaultConfig = conf;
                        }
                        else if ( keyword != null && conf.getKeyword().equals( keyword ) )
                        {
                            config = conf;
                        }
                    }
                    if ( config == null )
                    {
                        log.warning( "No adapter with right keyword so using default type: " + type + " address: "
                            + localaddress );
                        config = defaultConfig;
                    }
                    else
                    {
                        log.info( "Adapter found with right keyword type: " + type + " address: " + localaddress
                            + " keyword: " + keyword );
                    }
                }
				//TODO: check account/pubkey usage here
				session = new Session();
				session.setAdapterID(config.getConfigId());
				session.setAccountId( config.getOwner());
				session.setRemoteAddress(split[2]);
				session.setLocalAddress(localaddress);
				session.setType(type);
				session.key = key;
				session.storeSession();
				log.info( "new session created with id: "+ session.key );
			} else {
				log.severe("getSession: incorrect key given:"+key);
			}
		}
		return session;
	}
	
	
	@JsonIgnore
	public AdapterConfig getAdapterConfig() {
		if(getAdapterID()!=null)
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
    public static String getString( String sessionKey )
    {
        Session session = getSession( sessionKey );
        return !session.getExtras().isEmpty() ? session.getExtras().values().iterator().next() : null;
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
    public void setQuestion( Question question )
    {
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

    public String getDDRRecordId()
    {
        return ddrRecordId;
    }

    public void setDDRRecordId( String ddrRecordId )
    {
        this.ddrRecordId = ddrRecordId;
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
}
