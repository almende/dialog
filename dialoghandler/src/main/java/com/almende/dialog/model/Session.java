package com.almende.dialog.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.TextServlet;
import com.almende.dialog.adapter.VoiceXMLRESTProxy;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.model.impl.S_fields;
import com.almende.dialog.model.intf.SessionIntf;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.annotations.Id;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class Session implements SessionIntf {
	private static final long serialVersionUID = 2674975096455049670L;
	private static final Logger log = Logger.getLogger("DialogHandler");
	
	SessionIntf session;
	@Id
	String key = "";
	public boolean killed = false;
	String language = null;
	Question question = null;
	Map<String, String> extras = null;
	Integer retryCount = null;
	
	public Session() {
		this.session = new S_fields();
	}
	@Override
	public String getSession_id() {
		return session.getSession_id();
	}

	@Override
	public void setSession_id(String session_id) {
		session.setSession_id(session_id);
	}
	
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
				session.setPubKey(config.getOwner());
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
		if(session.getAdapterID()!=null)
			return AdapterConfig.getAdapterConfig(session.getAdapterID());
		
		return null;
	}
	@Override
	public String getStartUrl() {
		return session.getStartUrl();
	}
	@Override
	public void setStartUrl(String url) {
		session.setStartUrl(url);
	}
	@Override
	public String getRemoteAddress() {
		return this.session.getRemoteAddress();
	}
	@Override
	public String getDirection() {
		return this.session.getDirection();
	}
	@Override
	public void setRemoteAddress(String remoteAddress) {
		this.session.setRemoteAddress(remoteAddress);
	}
	@Override
	public void setDirection(String direction) {
		this.session.setDirection(direction);
	}
	@Override
	public String getLocalAddress() {
		return this.session.getLocalAddress();
	}
	@Override
	public void setLocalAddress(String localAddress) {
		this.session.setLocalAddress(localAddress);
	}
	@Override
	public String getType() {
		return this.session.getType();
	}
	@Override
	public void setType(String type) {
		this.session.setType(type);
	}
	@Override
	public String getPubKey() {
		
		return this.session.getPubKey();
	}
	@Override
	public String getPrivKey() {
		return this.session.getPrivKey();
	}
	@Override
	public void setPubKey(String pubKey) {
		this.session.setPubKey(pubKey);
	}
	@Override
	public void setPrivKey(String privKey) {
		this.session.setPrivKey(privKey);
	}
	
	@Override
	public String getExternalSession() {
		return this.session.getExternalSession();
	}
	
	@Override
	public void setExternalSession(String externalSession) {
		this.session.setExternalSession(externalSession);
	}
	
	@Override
	public String getAdapterID() {
		return this.session.getAdapterID();
	}
	
	@Override
	public void setAdapterID(String adapterID) {
		this.session.setAdapterID(adapterID);
	}
	
	@Override
	public String getTrackingToken() {
		return this.session.getTrackingToken();
	}
	
	@Override
	public void setTrackingToken(String token) {
		this.session.setTrackingToken(token);
	}
    @Override
    public String getStartTimestamp()
    {
        return this.session.getStartTimestamp();
    }
    @Override
    public String getAnswerTimestamp()
    {
        return this.session.getAnswerTimestamp();
    }
    @Override
    public String getReleaseTimestamp()
    {
        return this.session.getReleaseTimestamp();
    }
    @Override
    public void setStartTimestamp( String startTimestamp )
    {
        this.session.setStartTimestamp( startTimestamp );
    }
    @Override
    public void setReleaseTimestamp( String releaseTimestamp )
    {
        this.session.setReleaseTimestamp( releaseTimestamp );
    }
    @Override
    public void setAnswerTimestamp( String answerTimestamp )
    {
        this.session.setAnswerTimestamp( answerTimestamp );
    }
    @Override
    public String getDDRRecordId()
    {
        return this.session.getDDRRecordId();
    }
    @Override
    public void setDDRRecordId( String ddrRecordId )
    {
        this.session.setDDRRecordId( ddrRecordId );
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
}
