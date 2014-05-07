package com.almende.dialog.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.jivesoftware.smack.XMPPException;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.MailServlet;
import com.almende.dialog.adapter.TwitterServlet;
import com.almende.dialog.adapter.TwitterServlet.TwitterEndpoint;
import com.almende.dialog.adapter.XMPPServlet;
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
	public static final String ADAPTER_TYPE_SMS = "sms";
	public static final String ADAPTER_TYPE_EMAIL = "email";
	public static final String ADAPTER_TYPE_XMPP = "xmpp";
	public static final String ADAPTER_TYPE_TWITTER = "twitter";	
	public static final int EMAIL_SCHEDULER_INTERVAL = 30 * 1000; //30seconds
	public static final int TWITTER_SCHEDULER_INTERVAL = 61 * 1000; //61seconds
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
    public String startEmailInboundSceduler()
    {
        String id = getState().get( "emailScedulerTaskId", String.class );
        if ( id == null )
        {
            try
            {
                JSONRequest req = new JSONRequest( "checkInBoundEmails", null );
                id = getScheduler().createTask( req, EMAIL_SCHEDULER_INTERVAL, true, true );
                getState().put( "emailScedulerTaskId", id );
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
        return id;
    }
    
    /**
     * stops the scheduler which checks for inbound emails
     */
    public String stopEmailInboundSceduler()
    {
        String schedulerId = getState().get("emailScedulerTaskId", String.class);
        getScheduler().cancelTask(schedulerId);
        getState().remove( "emailScedulerTaskId" );
        return schedulerId;
    }
    
    /**
     * start scheduler for twitter only
     */
    public String startTwitterInboundSceduler()
    {
        String id = getState().get( "twitterScedulerTaskId", String.class );
        if ( id == null )
        {
            try
            {
                JSONRequest req = new JSONRequest( "checkInBoundTwitterPosts", null );
                id = getScheduler().createTask( req, TWITTER_SCHEDULER_INTERVAL, true, true );
                getState().put( "twitterScedulerTaskId", id );
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
        return id;
    }
    
    /**
     * stops the scheduler which checks for inbound twitter updates
     */
    public String stopTwitterInboundSceduler()
    {
        String schedulerId = getState().get("twitterScedulerTaskId", String.class);
        getScheduler().cancelTask(schedulerId);
        getState().remove( "twitterScedulerTaskId" );
        return schedulerId;
    }

    /**
     * check inbound email
     */
    public void checkInBoundEmails()
    {
        log.info( "starting email scheduler check for inbound emails..." );
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
        log.info( "starting twitter scheduler check for mentions and direct messages..." );
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
		
		String normAddress = address.replaceFirst("^0", "").replace("+31", "").replace( "@ask.ask.voipit.nl", "" );
		String externalAddress = "+31" +normAddress; 
        String myAddress = "0"
            + ( normAddress.contains( "@ask.ask.voipit.nl" ) ? normAddress : ( normAddress + "@ask.ask.voipit.nl" ) );
		
		if(username==null)
			username = myAddress;
		
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
        config.getProperties().put( MailServlet.SENDING_PROTOCOL_KEY, sendingProtocol != null ? sendingProtocol : MailServlet.GMAIL_SENDING_PROTOCOL );
        config.getProperties().put( MailServlet.SENDING_HOST_KEY, sendingHost != null ? sendingHost : MailServlet.GMAIL_SENDING_HOST );
        config.getProperties().put( MailServlet.SENDING_PORT_KEY, sendingPort != null ? sendingPort : MailServlet.GMAIL_SENDING_PORT );
        config.getProperties().put( MailServlet.RECEIVING_PROTOCOL_KEY, receivingProtocol != null ? receivingProtocol : MailServlet.GMAIL_RECEIVING_PROTOCOL );
        config.getProperties().put( MailServlet.RECEIVING_HOST_KEY, receivingHost != null ? receivingHost : MailServlet.GMAIL_RECEIVING_HOST );
        config.setMyAddress( emailAddress );
        config.setAddress( name );
        config.setXsiUser( emailAddress );
        config.setXsiPasswd( password );
        config.setPreferred_language( preferredLanguage );
        config.setPublicKey( accountId );
        config.setOwner( accountId );
        config.addAccount( accountId );
        config.setAnonymous( false );
        config.setDialogWithURL( "Agent for: "+ emailAddress, initialAgentURL );
        AdapterConfig newConfig = createAdapter( config );
        return newConfig.getConfigId();
    }
	
    public String createXMPPAdapter( @Name( "xmppAddress" ) String xmppAddress, @Name( "password" ) String password,
        @Name( "name" ) @Optional String name, @Name( "preferredLanguage" ) @Optional String preferredLanguage,
        @Name( "host" ) @Optional String host, @Name( "port" ) @Optional String port,
        @Name( "service" ) @Optional String service, @Name( "accountId" ) @Optional String accountId,
        @Name( "initialAgentURL" ) @Optional String initialAgentURL ) throws Exception
    {
        AdapterConfig newConfig = createSimpleXMPPAdapter( xmppAddress, password, name, preferredLanguage, host, port,
            service, accountId, initialAgentURL );
        //set for incoming requests
        XMPPServlet xmppServlet = new XMPPServlet();
        xmppServlet.listenForRosterChanges( newConfig );
        xmppServlet.listenForIncomingChats( newConfig );
        return newConfig.getConfigId();
    }

    public String registerASKFastXMPPAdapter( @Name( "xmppAddress" ) String xmppAddress,
        @Name( "password" ) String password, @Name( "name" ) @Optional String name,
        @Name( "email" ) @Optional String email, @Name( "preferredLanguage" ) @Optional String preferredLanguage,
        @Name( "accountId" ) @Optional String accountId, @Name( "initialAgentURL" ) @Optional String initialAgentURL )
    throws Exception
    {
        xmppAddress = xmppAddress.endsWith( "@xmpp.ask-fast.com" ) ? xmppAddress
                                                                  : ( xmppAddress + "@xmpp.ask-fast.com" );
        ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters( ADAPTER_TYPE_XMPP, xmppAddress, null );
        AdapterConfig newConfig = adapters != null && !adapters.isEmpty() ? adapters.iterator().next() : null;
        //check if adapter exists
        if ( newConfig == null )
        {
            newConfig = createSimpleXMPPAdapter( xmppAddress, password, name, preferredLanguage,
                XMPPServlet.DEFAULT_XMPP_HOST, String.valueOf( XMPPServlet.DEFAULT_XMPP_PORT ), null, accountId,
                initialAgentURL );
        }
        else if ( accountId != null && !accountId.isEmpty() && !accountId.equals( newConfig.getOwner() ) ) //check if the accountId owns this adapter
        {
            throw new Exception( String.format( "Adapter exists but AccountId: %s does not own it", accountId ) );
        }
        try
        {
            XMPPServlet.registerASKFastXMPPAccount( xmppAddress, password, name, email );
            XMPPServlet xmppServlet = new XMPPServlet();
            xmppServlet.listenForRosterChanges( newConfig );
            xmppServlet.listenForIncomingChats( newConfig );
        }
        catch ( XMPPException e )
        {
            if(e.getXMPPError().getCode() == 409) //just listen to incoming chats if account already exists.
            {
                XMPPServlet xmppServlet = new XMPPServlet();
                xmppServlet.listenForRosterChanges( newConfig );
                xmppServlet.listenForIncomingChats( newConfig );
            }
            log.severe( "Error registering an ASK-Fast account. Error: "+ e.getLocalizedMessage() );
            throw e;
        }
        return newConfig != null ? newConfig.getConfigId() : null;
    }
    
    public String deregisterASKFastXMPPAdapter( @Name( "xmppAddress" ) @Optional String xmppAddress,
        @Name( "accountId" ) String accountId, 
        @Name( "adapterId" ) @Optional String adapterId)
    throws Exception
    {
        xmppAddress = xmppAddress.endsWith( "@xmpp.ask-fast.com" ) ? xmppAddress
                                                                  : ( xmppAddress + "@xmpp.ask-fast.com" );
        ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters( ADAPTER_TYPE_XMPP, xmppAddress, null );
        AdapterConfig adapterConfig = adapters != null && !adapters.isEmpty() ? adapters.iterator().next() : null;
        //check if adapter is owned by the accountId
        if ( adapterConfig != null && accountId.equals( adapterConfig.getOwner() ))
        {
            XMPPServlet.deregisterASKFastXMPPAccount( adapterConfig );
            adapterConfig.delete();
        }
        else 
        {
            throw new Exception( String.format( "Adapter either doesnt exist or now owned by AccountId: %s", accountId ) );
        }
        return adapterConfig != null ? adapterConfig.getConfigId() : null;
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
            if( adapterConfig.getOwner()!= null && !accountId.equals( adapterConfig.getOwner()))
            {
                throw new JSONRPCException( CODE.INVALID_PARAMS, "Adapter not owned by the accountId: "+ accountId );
            }
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
	
    public Object updateAdapter( @Name( "accoutId" ) String accountId, @Name( "adapterId" ) String adapterId,
        @Name( "adapter" ) Adapter adapter ) throws Exception
    {
        AdapterConfig config = (AdapterConfig) getAdapter( accountId, adapterId );
        if ( config != null )
        {
            if ( adapter.getInitialAgentURL() != null )
            {
                config.setDialogWithURL( "Agent for: "+ config.getMyAddress(), adapter.getInitialAgentURL() );
            }
            if ( adapter.isAnonymous() != null )
            {
                config.setAnonymous( adapter.isAnonymous() );
            }
            if ( adapter.getDialogId() != null )
            {
                config.setDialogId( adapter.getDialogId() );
            }
            config.update();
            return config;
        }
        else
        {
            throw new Exception( String.format( "Adapter: %s with address:%s probably does not belong to account: %s",
                adapter, adapter.getMyAddress(), accountId ) );
        }
    }
	
	public void removeAccount(@Name("adapterId") String adapterId, @Name("accountId") String accountId) throws Exception {
		
		AdapterConfig config = AdapterConfig.getAdapterConfig(adapterId);
		if(config==null || !config.getOwner().equals(accountId))
			throw new Exception("No adapter with this id owned by you");
		
		config.setOwner(null);
		config.removeAccount(accountId);
		config.update();
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
	
	/**
	 * saves the AdapterConfig in the datastore
	 * @param config
	 * @return
	 * @throws Exception
	 */
	private AdapterConfig createAdapter(AdapterConfig config) throws Exception {
		
		if (AdapterConfig.adapterExists(config.getAdapterType(), config.getMyAddress(), config.getKeyword()))
		{
			throw new ConflictException("Adapter already exists");
		}
		if(config.getConfigId() == null)
		{
		    config.configId = new UUID().toString();
		}
		//add creation timestamp to the adapter
        config.getProperties().put( AdapterConfig.ADAPTER_CREATION_TIME_KEY, ServerUtils.getServerCurrentTimeInMillis() );
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
	
	/** creates a simple adapter of type XMPP. doesnt register and listen for messages
     * @param xmppAddress
     * @param password
     * @param name
     * @param preferredLanguage
     * @param host
     * @param port
     * @param service
     * @param accountId
     * @param initialAgentURL
     * @return
     * @throws Exception
     */
    private AdapterConfig createSimpleXMPPAdapter( String xmppAddress, String password, String name,
        String preferredLanguage, String host, String port, String service, String accountId, String initialAgentURL )
    throws Exception
    {
        preferredLanguage = ( preferredLanguage == null ? "nl" : preferredLanguage );
        AdapterConfig config = new AdapterConfig();
        config.setAdapterType( ADAPTER_TYPE_XMPP );
        //by default create gmail xmpp adapter
        config.getProperties().put( XMPPServlet.XMPP_HOST_KEY, host != null ? host : XMPPServlet.GTALK_XMPP_HOST );
        config.getProperties().put( XMPPServlet.XMPP_PORT_KEY, port != null ? port : XMPPServlet.DEFAULT_XMPP_PORT );
        if(service != null)
        {
            config.getProperties().put( XMPPServlet.XMPP_SERVICE_KEY, service );
        }
        config.setMyAddress( xmppAddress.toLowerCase() );
        config.setAddress( name );
        config.setXsiUser( xmppAddress.toLowerCase().split( "@" )[0] );
        config.setXsiPasswd( password );
        config.setPreferred_language( preferredLanguage );
        config.setPublicKey( accountId );
        config.setOwner( accountId );
        config.addAccount( accountId );
        config.setAnonymous( false );
        config.setDialogWithURL( "Agent for: "+ xmppAddress, initialAgentURL );
        AdapterConfig newConfig = createAdapter( config );
        return newConfig;
    }
}
