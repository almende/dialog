package com.almende.dialog.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.MailServlet;
import com.almende.dialog.adapter.TwitterServlet;
import com.almende.dialog.adapter.TwitterServlet.TwitterEndpoint;
import com.almende.dialog.adapter.tools.Broadsoft;
import com.almende.dialog.exception.ConflictException;
import com.almende.dialog.util.ServerUtils;
import com.almende.eve.agent.Agent;
import com.almende.eve.rpc.annotation.Access;
import com.almende.eve.rpc.annotation.AccessType;
import com.almende.eve.rpc.annotation.Name;
import com.almende.eve.rpc.annotation.Optional;
import com.almende.eve.rpc.jsonrpc.JSONRPCException;
import com.almende.eve.rpc.jsonrpc.JSONRPCException.CODE;
import com.almende.eve.rpc.jsonrpc.JSONRequest;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.uuid.UUID;
import com.askfast.commons.agent.intf.AdapterAgentInterface;
import com.askfast.commons.entity.Adapter;
import com.fasterxml.jackson.databind.node.ArrayNode;

@Access(AccessType.PUBLIC)
public class AdapterAgent extends Agent implements AdapterAgentInterface {
	
	public static final String ADAPTER_TYPE_BROADSOFT = "broadsoft";
	public static final String ADAPTER_TYPE_SMS = "SMS";
	public static final String ADAPTER_TYPE_EMAIL = "email";
	public static final String ADAPTER_TYPE_XMPP = "xmpp";
	public static final String ADAPTER_TYPE_TWITTER = "twitter";	
	public static final int EMAIL_SCHEDULER_INTERVAL = 30 * 1000; //30seconds
	public static final int TWITTER_SCHEDULER_INTERVAL = 60 * 1000; //60seconds
	private static final Logger log = Logger.getLogger( AdapterAgent.class.getSimpleName() );
	
    @Override
    protected void onCreate()
    {
        startAllInboundSceduler();
    }

    /**
     * starts scedulers for all inbound services such as Email, Twitter, XMPP etc
     */
    public void startAllInboundSceduler()
    {
        startEmailInboundSceduler();
        startTwitterInboundSceduler();
    }
    
    /**
     * stops scedulers for all inbound services such as Email, Twitter, XMPP etc
     */
    public void stopAllInboundSceduler()
    {
        stopEmailInboundSceduler();
        stopTwitterInboundSceduler();
    }
    
    /**
     * start scheduler for email only
     */
    public void startEmailInboundSceduler()
    {
        String id = getState().get( "emailScedulerTaskId", String.class );
        if ( id == null )
        {
            try
            {
                JSONRequest req = new JSONRequest( "checkInBoundEmails", null );
                getState().put( "emailScedulerTaskId",
                    getScheduler().createTask( req, EMAIL_SCHEDULER_INTERVAL, true, true ) );
            }
            catch ( Exception e )
            {
                e.printStackTrace();
                log.warning( "Exception in scheduler creation: "+ e.getLocalizedMessage() );
            }
        }
        else
        {
            log.warning( "Task already running" );
        }
    }
    
    /**
     * stops the scheduler which checks for inbound emails
     */
    public void stopEmailInboundSceduler()
    {
        getScheduler().cancelTask(getState().get("emailScedulerTaskId", String.class));
        getState().remove( "emailScedulerTaskId" );
    }
    
    /**
     * start scheduler for twitter only
     */
    public void startTwitterInboundSceduler()
    {
        String id = getState().get( "twitterScedulerTaskId", String.class );
        if ( id == null )
        {
            try
            {
                JSONRequest req = new JSONRequest( "checkInBoundTwitterPosts", null );
                getState().put( "twitterScedulerTaskId", getScheduler().createTask( req, TWITTER_SCHEDULER_INTERVAL, true, true ) );
            }
            catch ( Exception e )
            {
                e.printStackTrace();
                log.warning( "Exception in scheduler creation: "+ e.getLocalizedMessage() );
            }
        }
        else
        {
            log.warning( "Task already running" );
        }
    }
    
    /**
     * stops the scheduler which checks for inbound twitter updates
     */
    public void stopTwitterInboundSceduler()
    {
        getScheduler().cancelTask(getState().get("twitterScedulerTaskId", String.class));
        getState().remove( "twitterScedulerTaskId" );
    }

    /**
     * check inbound email
     */
    public void checkInBoundEmails()
    {
        ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters( ADAPTER_TYPE_EMAIL, null, null );
        for ( AdapterConfig adapterConfig : adapters )
        {
            Runnable mailServlet = new MailServlet( adapterConfig );
            Thread mailServletThread = new Thread( mailServlet );
            mailServletThread.run();
            try
            {
                mailServletThread.join();
            }
            catch ( InterruptedException e )
            {
                e.printStackTrace();
                log.warning( "Failed to join the thread. Message" + e.getLocalizedMessage() );
            }
        }
    }
    
    /**
     * check inbound twitter updates
     */
    public void checkInBoundTwitterPosts()
    {
        ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters( ADAPTER_TYPE_TWITTER, null, null );
        for ( AdapterConfig adapterConfig : adapters )
        {
            Runnable twitterDirectMessageServlet = new TwitterServlet( adapterConfig, TwitterEndpoint.DIRECT_MESSAGE );
            Thread twitterDirectMessageThread = new Thread( twitterDirectMessageServlet );
            Runnable twitterTweetMentionServlet = new TwitterServlet( adapterConfig, TwitterEndpoint.MENTIONS );
            Thread twitterTweetMentionThread = new Thread( twitterTweetMentionServlet );
            twitterDirectMessageThread.run();
            twitterTweetMentionThread.run();
            try
            {
                twitterDirectMessageThread.join();
                twitterTweetMentionThread.join();
            }
            catch ( InterruptedException e )
            {
                e.printStackTrace();
                log.warning( "Failed to join the thread. Message" + e.getLocalizedMessage() );
            }
        }
    }
	
	/**
	 *  Adds a new broadsoft adapter
	 * @param address
	 * @param username
	 * @param password
	 * @param preferredLanguage
	 * @param accountId
	 * @param anonymous
	 * @return AdapterId
	 * @throws Exception 
	 */
	public String createBroadSoftAdapter(@Name("address") String address,
										@Name("username") @Optional String username,
										@Name("password") String password,
										@Name("preferredLanguage") @Optional String preferredLanguage,
										@Name("accountId") @Optional String accountId,
										@Name("anonymous") boolean anonymous) throws Exception {
		
		preferredLanguage = (preferredLanguage==null ? "nl" : preferredLanguage);
		
		String normAddress = address.replaceFirst("^0", "").replace("+31", "");
		String myAddress = "+31" +normAddress; 
		String externalAddress = "0"+normAddress+"@ask.ask.voipit.nl";
		
		if(username==null)
			username = externalAddress;
		
		AdapterConfig config = new AdapterConfig();
		config.setAdapterType(ADAPTER_TYPE_BROADSOFT);
		config.setMyAddress(myAddress);
		config.setAddress(externalAddress);
		config.setXsiUser(username);
		config.setXsiPasswd(password);
		config.setPreferred_language(preferredLanguage);
		config.setPublicKey(accountId);
		config.setOwner(accountId);
		config.addAccount(accountId);
		config.setAnonymous(anonymous);
		AdapterConfig newConfig = createAdapter(config);
		return newConfig.getConfigId();
	}
	
    public String createEmailAdapter( @Name( "emailAddress" ) String emailAddress, @Name( "password" ) String password,
        @Name( "name" ) @Optional String name, @Name( "preferredLanguage" ) @Optional String preferredLanguage,
        @Name( "sendingProtocol" ) @Optional String sendingProtocol,
        @Name( "sendingHost" ) @Optional String sendingHost, @Name( "sendingPort" ) @Optional String sendingPort,
        @Name( "receivingProtocol" ) @Optional String receivingProtocol,
        @Name( "receivingHost" ) @Optional String receivingHost, @Name( "accountId" ) @Optional String accountId,
        @Name( "initialAgentURL" ) @Optional String initialAgentURL ) throws Exception
    {
        preferredLanguage = ( preferredLanguage == null ? "nl" : preferredLanguage );
        AdapterConfig config = new AdapterConfig();
        config.setAdapterType( ADAPTER_TYPE_EMAIL );
        //by default create gmail account adapter
        String connectionSettings = ( sendingProtocol != null ? sendingProtocol : MailServlet.GMAIL_SENDING_PROTOCOL )
            + ":" + ( sendingHost != null ? sendingHost : MailServlet.GMAIL_SENDING_HOST ) + ":"
            + ( sendingPort != null ? sendingPort : MailServlet.GMAIL_SENDING_PORT ) + "\n"
            + ( receivingProtocol != null ? receivingProtocol : MailServlet.GMAIL_RECEIVING_PROTOCOL ) + ":"
            + ( receivingHost != null ? receivingHost : MailServlet.GMAIL_RECEIVING_HOST );
        config.setXsiURL( connectionSettings );
        config.setMyAddress( emailAddress );
        config.setAddress( name );
        config.setXsiUser( emailAddress );
        config.setXsiPasswd( password );
        config.setPreferred_language( preferredLanguage );
        config.setPublicKey( accountId );
        config.setOwner( accountId );
        config.addAccount( accountId );
        config.setAnonymous( false );
        config.setInitialAgentURL( initialAgentURL );
        AdapterConfig newConfig = createAdapter( config );
        return newConfig.getConfigId();
    }
	
	public String createXmppAdapter() {
		// TODO: implement
		return null;
	}
	
	public String createMBAdapter(@Name("address") String address,
			@Name("keyword") @Optional String keyword,
			@Name("username") String username,
			@Name("password") String password,
			@Name("preferredLanguage") @Optional String preferredLanguage,
			@Name("accountId") @Optional String accountId) throws Exception {
		
		preferredLanguage = (preferredLanguage==null ? "nl" : preferredLanguage);
		
		AdapterConfig config = new AdapterConfig();
		config.setAdapterType(ADAPTER_TYPE_SMS);
		config.setMyAddress(address);
		config.setKeyword(keyword);
		config.setPreferred_language(preferredLanguage);
		config.setPublicKey(accountId);
		config.setOwner(accountId);
		config.addAccount(accountId);
		config.setAnonymous(false);
		config.setAccessToken(username);
		config.setAccessTokenSecret(password);		
		AdapterConfig newConfig = createAdapter(config);
		
		return newConfig.getConfigId();
	}
	
	public String createNexmoAdapter(@Name("address") String address,
			@Name("keyword") @Optional String keyword,
			@Name("username") String username,
			@Name("password") String password,
			@Name("preferredLanguage") @Optional String preferredLanguage,
			@Name("accountId") @Optional String accountId) throws Exception {
		preferredLanguage = (preferredLanguage==null ? "nl" : preferredLanguage);
		
		AdapterConfig config = new AdapterConfig();
		config.setAdapterType(ADAPTER_TYPE_SMS);
		config.setMyAddress(address);
		config.setKeyword(keyword);
		config.setPreferred_language(preferredLanguage);
		config.setPublicKey(accountId);
		config.setOwner(accountId);
		config.addAccount(accountId);
		config.setAnonymous(false);
		config.setAccessToken(username);
		config.setAccessTokenSecret(password);		
		AdapterConfig newConfig = createAdapter(config);
		
		return newConfig.getConfigId();
	}
	
    public String attachTwitterAdapterToUser( @Name( "adapterID" ) @Optional String adapterID,
        @Name( "twitterUserName" ) @Optional String twitterUserName, @Name( "accountId" ) String accountId )
    throws Exception
    {
        AdapterConfig adapterConfig = null;
        if ( adapterID != null && !adapterID.trim().isEmpty() )
        {
            adapterConfig = AdapterConfig.getAdapterConfig( adapterID.trim() );
        }
        if ( adapterConfig == null && twitterUserName != null && !twitterUserName.trim().isEmpty() )
        {
            twitterUserName = twitterUserName.startsWith( "@" ) ? twitterUserName : "@" + twitterUserName;
            ArrayList<AdapterConfig> adapterConfigs = AdapterConfig.findAdapters( ADAPTER_TYPE_TWITTER,
                twitterUserName, null );
            adapterConfig = !adapterConfigs.isEmpty() ? adapterConfigs.iterator().next() : null;
        }
        if ( adapterConfig != null )
        {
            adapterConfig.addAccount( accountId );
            adapterConfig.update();
            return ServerUtils.serialize( adapterConfig );
        }
        else
        {
            throw new JSONRPCException( CODE.INVALID_PARAMS, "Adapter not found" );
        }
    }
	
	public String createFacebookAdapter() {
		// TODO: implement
		return null;
	}
	
	public void setOwner(@Name("adapterId") String adapterId, @Name("accountId") String accountId) throws Exception {
		AdapterConfig config = AdapterConfig.getAdapterConfig(adapterId);
		if(config==null)
			throw new Exception("No adapter with this id");
		
		if(config.getOwner() != null) {
			throw new Exception("Adapter is already owned by someone else");
		}
		
		config.setOwner(accountId);
		config.addAccount(accountId);
		config.update();
	}
	
	public void addAccount(@Name("adapterId") String adapterId, @Name("accountId") String accountId) throws Exception {
		
		AdapterConfig config = AdapterConfig.getAdapterConfig(adapterId);
		if(config==null)
			throw new Exception("No adapter with this id");
		
		config.addAccount(accountId);
		config.update();
	}
	
	public Object getAdapter(@Name("accoutId") String accountId, @Name("adapterId") String adapterId) throws Exception {
		
		AdapterConfig config = AdapterConfig.getAdapterConfig(adapterId);
		if(config==null)
			throw new Exception("No adapter linked to this account or with this id");
		
		if(config.getOwner()==null || !config.getOwner().equals(accountId))
			throw new Exception("No adapter linked to this account or with this id");
		
		return config;
	}
	
	public Object updateAdapter(@Name("accoutId") String accountId,
			@Name("adapterId") String adapterId,
			@Name("adapter") Adapter adapter) throws Exception {
		
		AdapterConfig config = (AdapterConfig) getAdapter(accountId,adapterId);
		
		if(adapter.getInitialAgentURL()!=null) {
			config.setInitialAgentURL(adapter.getInitialAgentURL());
		}
		
		if(adapter.isAnonymous()!=null) {
			config.setAnonymous(adapter.isAnonymous());
		}
		
		config.update();
		
		return config;
	}
	
	public ArrayNode getAdapters(@Name("accoutId") String accountId,
								@Name("adapterType") @Optional String adapterType,
								@Name("address") @Optional String address) {
		
		List<AdapterConfig> adapters = AdapterConfig.findAdapterByOwner(accountId, adapterType, address);
		return JOM.getInstance().convertValue(adapters, ArrayNode.class);
	}
	
	public ArrayNode findAdapters(@Name("adapterType") @Optional String type,
									@Name("address") @Optional String address,
									@Name("keyword") @Optional String keyword) {
		ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters(type, address, keyword);
		return JOM.getInstance().convertValue(adapters, ArrayNode.class);
	}
	
	public ArrayNode findFreeAdapters(@Name("adapterType") @Optional String adapterType,
			@Name("address") @Optional String address) {
		ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapterByOwner(null, adapterType, address);
		return JOM.getInstance().convertValue(adapters, ArrayNode.class);
	}
	
	private AdapterConfig createAdapter(AdapterConfig config) throws Exception {
		
		if (AdapterConfig.adapterExists(config.getAdapterType(), config.getMyAddress(), config.getKeyword()))
		{
			throw new ConflictException("Adapter already exists");
		}
		if(config.getConfigId() == null)
		{
		    config.configId = new UUID().toString();
		}
		
		//change the casing to lower in case adatertype if email or xmpp
		if(config.getMyAddress() != null && (config.getAdapterType().equalsIgnoreCase( ADAPTER_TYPE_EMAIL ) || 
		    config.getAdapterType().equalsIgnoreCase( ADAPTER_TYPE_XMPP )) )
		{
		    config.setMyAddress( config.getMyAddress().toLowerCase() );
		}
		
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		datastore.store(config);
		
		if(config.getAdapterType().equals("broadsoft")) {
			Broadsoft bs = new Broadsoft(config);
			bs.hideCallerId(config.isAnonymous());
		}
		
		return config;
	}
}
