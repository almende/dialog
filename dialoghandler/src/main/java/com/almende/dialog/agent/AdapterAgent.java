package com.almende.dialog.agent;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.ws.rs.core.Response;
import org.jivesoftware.smack.XMPPException;
import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.adapter.MailServlet;
import com.almende.dialog.adapter.TwitterServlet;
import com.almende.dialog.adapter.TwitterServlet.TwitterEndpoint;
import com.almende.dialog.adapter.XMPPServlet;
import com.almende.dialog.adapter.tools.Broadsoft;
import com.almende.dialog.adapter.tools.Twilio;
import com.almende.dialog.broadsoft.Registration;
import com.almende.dialog.broadsoft.UserProfile;
import com.almende.dialog.exception.ConflictException;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.almende.dialog.util.TimeUtils;
import com.almende.eve.protocol.jsonrpc.annotation.Access;
import com.almende.eve.protocol.jsonrpc.annotation.AccessType;
import com.almende.eve.protocol.jsonrpc.annotation.Name;
import com.almende.eve.protocol.jsonrpc.annotation.Optional;
import com.almende.eve.protocol.jsonrpc.formats.JSONRPCException;
import com.almende.eve.protocol.jsonrpc.formats.JSONRPCException.CODE;
import com.almende.eve.protocol.jsonrpc.formats.JSONRequest;
import com.almende.util.jackson.JOM;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.uuid.UUID;
import com.askfast.commons.RestResponse;
import com.askfast.commons.Status;
import com.askfast.commons.agent.ScheduleAgent;
import com.askfast.commons.agent.intf.AdapterAgentInterface;
import com.askfast.commons.agent.intf.DialogAgentInterface;
import com.askfast.commons.entity.AccountType;
import com.askfast.commons.entity.Adapter;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.Language;
import com.askfast.commons.entity.ScheduledTask;
import com.fasterxml.jackson.databind.node.ArrayNode;

@Access(AccessType.PUBLIC)
public class AdapterAgent extends ScheduleAgent implements AdapterAgentInterface {
	
    public static final int EMAIL_SCHEDULER_INTERVAL = 30 * 1000; //30seconds
    public static final int TWITTER_SCHEDULER_INTERVAL = 61 * 1000; //61seconds
    private static final Logger log = Logger.getLogger(AdapterAgent.class.getSimpleName());
    
    public static final String ADAPTER_TYPE_CALL = AdapterType.CALL.toString();
    public static final String ADAPTER_TYPE_SMS = AdapterType.SMS.toString();
    public static final String ADAPTER_TYPE_FACEBOOK = AdapterType.FACEBOOK.toString();
    public static final String ADAPTER_TYPE_EMAIL = AdapterType.EMAIL.toString();
    public static final String ADAPTER_TYPE_XMPP = AdapterType.XMPP.toString();
    public static final String ADAPTER_TYPE_TWITTER = AdapterType.TWITTER.toString();
    public static final String ADAPTER_TYPE_USSD = AdapterType.USSD.toString();
    public static final String ADAPTER_TYPE_PUSH = AdapterType.PUSH.toString();
    
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
     * start scheduler for email only.
     * @return schedulerId
     */
    public String startEmailInboundSceduler()
    {
        String id = getState().get( "emailScedulerTaskId", String.class );
        if ( id == null )
        {
            try
            {
                JSONRequest req = new JSONRequest( "checkInBoundEmails", null );
                id = schedule( req, EMAIL_SCHEDULER_INTERVAL, true );
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
     * Get details about emails check task
     * @return 
     * @return
     */
    public ScheduledTask getEmailInboundScedulerDetails() {

        String emailScedulerTaskId = getState().get("emailScedulerTaskId", String.class);
        return getScheduledTaskDetails(emailScedulerTaskId);
    }
    
    /**
     * stops the scheduler which checks for inbound emails
     * @return scheduler id
     */
    public String stopEmailInboundSceduler()
    {
        String schedulerId = getState().get("emailScedulerTaskId", String.class);
        stopScheduledTask( schedulerId );
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
                id = schedule( req, TWITTER_SCHEDULER_INTERVAL, true );
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
        if (schedulerId != null) {
            stopScheduledTask( schedulerId );
        }
        getState().remove( "twitterScedulerTaskId" );
        return schedulerId;
    }
    
    /**
    * Get details about twitter check task
     * @return 
    * @return
    */
   public ScheduledTask getTwitterInboundScedulerDetails() {

       String twitterScedulerTaskId = getState().get("twitterScedulerTaskId", String.class);
       return getScheduledTaskDetails(twitterScedulerTaskId);
   }

    /**
     * check inbound email
     */
    public void checkInBoundEmails()
    {
        log.info( "starting email scheduler check for inbound emails..." );
        ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters( AdapterType.EMAIL.toString(), null, null );
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
        ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters( AdapterType.TWITTER.toString(), null, null );
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
     * Adds a new broadsoft adapter
     * 
     * @param address
     * @param username
     * @param password
     * @param preferredLanguage
     * @param accountId
     * @param anonymous
     * @return AdapterId
     * @throws Exception
     */
    public RestResponse createBroadSoftAdapter(@Name("username") @Optional String username,
        @Name("password") @Optional String password, @Name("preferredLanguage") @Optional String preferredLanguage,
        @Name("accountId") @Optional String accountId, @Name("anonymous") boolean anonymous,
        @Name("accountType") @Optional String accountType, @Name("isPrivate") @Optional Boolean isPrivate)
        throws Exception {

        preferredLanguage = Language.getByValue(preferredLanguage).getCode();
        AdapterConfig config = new AdapterConfig();
        config.setAdapterType(AdapterType.CALL.toString());
        config.setPreferred_language(preferredLanguage);
        config.setPublicKey(accountId);
        config.setXsiUser(username);
        config.setXsiPasswd(password);
        config.setOwner(accountId);
        config.addAccount(accountId);
        config.setAnonymous(anonymous);
        config.setAccountType(AccountType.fromJson(accountType));
        config.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.BROADSOFT);
        
        boolean isValidCredentials = false;
        if (!ServerUtils.isInUnitTestingEnvironment()) {
            //check if adapter credentials are true
            Broadsoft broadsoft = new Broadsoft(config);
            UserProfile userProfile = broadsoft.getUserProfile();
            Registration registration = broadsoft.getUserProfileRegistration();
            if(userProfile != null && registration != null) {
                config.setMyAddress(registration.getLinePort());
                config.setAddress(userProfile.getNumber());
                isValidCredentials = true;
            }
        }
        else {
            isValidCredentials = true;
            config.setMyAddress(username);
        }
        if (isValidCredentials) {
            AdapterConfig newConfig = createAdapter(config, isPrivate);
            return new RestResponse(getVersion(), newConfig.getConfigId(), Response.Status.OK.getStatusCode(), "OK");
        }
        else {
            return new RestResponse(getVersion(), null, Response.Status.FORBIDDEN.getStatusCode(),
                                    "Unauthorized credentials. Please try again.");
        }
    }
    
    public Map<String, String> createMultipleTwilioAdapters(@Name("accountSid") String accountSid,
        @Name("authToken") String authToken, @Name("count") Integer count, @Name("countryCode") String countryCode,
        @Name("contains") @Optional String contains, @Name("preferredLanguage") @Optional String preferredLanguage,
        @Name("accountId") @Optional String accountId, @Name("anonymous") @Optional Boolean anonymous,
        @Name("isPrivate") @Optional Boolean isPrivate) throws Exception {

        Map<String, String> boughtNumbers = new HashMap<String, String>();
        Twilio twilio = new Twilio(accountSid, authToken);
        List<String> availableNumbers = twilio.getFreePhoneNumbers(countryCode, contains);

        if (availableNumbers.size() < count) {
            throw new Exception("Can't buy more then: " + availableNumbers.size() + " numbers");
        }

        for (int i = 0; i < count; i++) {
            String number = availableNumbers.get(i);
            String id = createTwilioAdapter(number, accountSid, authToken, preferredLanguage, accountId, anonymous,
                                            isPrivate);
            boughtNumbers.put(id, number);
        }

        return boughtNumbers;
    }
    
    /**
     * Adds a new broadsoft adapter
     * 
     * @param address
     * @param username
     * @param password
     * @param preferredLanguage
     * @param accountId
     * @param anonymous
     * @return AdapterId
     * @throws Exception
     */
    public String createTwilioAdapter(@Name("address") String address, @Name("accountSid") String accountSid,
        @Name("authToken") String authToken, @Name("preferredLanguage") @Optional String preferredLanguage,
        @Name("accountId") @Optional String accountId, @Name("anonymous") @Optional Boolean anonymous,
        @Name("isPrivate") @Optional Boolean isPrivate) throws Exception {

        preferredLanguage = (preferredLanguage == null ? "nl" : preferredLanguage);
        anonymous = (anonymous == null ? false : anonymous);

        String normAddress = address.replaceFirst("^0", "").replace("+31", "");
        String externalAddress = "+31" + normAddress;

        Twilio twilio = new Twilio(accountSid, authToken);
        String id = twilio.buyPhoneNumber(address, getApplicationId());

        if (id != null) {

            AdapterConfig config = new AdapterConfig();
            config.setAdapterType(AdapterType.CALL.toString());
            config.setMyAddress(externalAddress);
            config.setAddress(externalAddress);
            config.setPreferred_language(preferredLanguage);
            config.setPublicKey(accountId);
            config.setOwner(accountId);
            config.setAccessToken(accountSid);
            config.setAccessTokenSecret(authToken);
            config.addAccount(accountId);
            config.setAnonymous(anonymous);
            config.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.TWILIO);
            AdapterConfig newConfig = createAdapter(config, isPrivate);
            return newConfig.getConfigId();
        }
        return null;
    }
	
    public String createEmailAdapter(@Name("emailAddress") String emailAddress, @Name("password") String password,
        @Name("name") @Optional String name, @Name("preferredLanguage") @Optional String preferredLanguage,
        @Name("sendingProtocol") @Optional String sendingProtocol, @Name("sendingHost") @Optional String sendingHost,
        @Name("sendingPort") @Optional String sendingPort,
        @Name("receivingProtocol") @Optional String receivingProtocol,
        @Name("receivingHost") @Optional String receivingHost, @Name("accountId") @Optional String accountId,
        @Name("initialAgentURL") @Optional String initialAgentURL, @Name("accountType") @Optional String accountType,
        @Name("isPrivate") @Optional Boolean isPrivate) throws Exception {
        
        preferredLanguage = ( preferredLanguage == null ? "nl" : preferredLanguage );
        AdapterConfig config = new AdapterConfig();
        config.setAdapterType( AdapterType.EMAIL.toString() );
        //by default create gmail account adapter
        config.getProperties().put( MailServlet.SENDING_PROTOCOL_KEY, sendingProtocol != null ? sendingProtocol : MailServlet.GMAIL_SENDING_PROTOCOL );
        config.getProperties().put( MailServlet.SENDING_HOST_KEY, sendingHost != null ? sendingHost : MailServlet.GMAIL_SENDING_HOST );
        config.getProperties().put( MailServlet.SENDING_PORT_KEY, sendingPort != null ? sendingPort : MailServlet.GMAIL_SENDING_PORT );
        config.getProperties().put( MailServlet.RECEIVING_PROTOCOL_KEY, receivingProtocol != null ? receivingProtocol : MailServlet.GMAIL_RECEIVING_PROTOCOL );
        config.getProperties().put( MailServlet.RECEIVING_HOST_KEY, receivingHost != null ? receivingHost : MailServlet.GMAIL_RECEIVING_HOST );
        emailAddress = getEnvironmentSpecificEmailAddress(emailAddress);
        config.setMyAddress( emailAddress );
        config.setAddress( name );
        config.setXsiUser( emailAddress );
        config.setXsiPasswd( password );
        config.setPreferred_language( preferredLanguage );
        config.setPublicKey( accountId );
        config.setOwner( accountId );
        config.addAccount( accountId );
        config.setAnonymous( false );
        config.setAccountType(AccountType.fromJson(accountType));
        config.setInitialAgentURL(initialAgentURL );
        AdapterConfig newConfig = createAdapter(config, isPrivate);
        return newConfig.getConfigId();
    }
	
    public String createXMPPAdapter(@Name("xmppAddress") String xmppAddress, @Name("password") String password,
                                    @Name("name") @Optional String name,
                                    @Name("preferredLanguage") @Optional String preferredLanguage,
                                    @Name("host") @Optional String host, @Name("port") @Optional String port,
                                    @Name("service") @Optional String service,
                                    @Name("accountId") @Optional String accountId,
                                    @Name("initialAgentURL") @Optional String initialAgentURL,
                                    @Name("accountType") @Optional String accountType, @Name("isPrivate") @Optional Boolean isPrivate) throws Exception {

        AdapterConfig newConfig = createSimpleXMPPAdapter(xmppAddress, password, name, preferredLanguage, host, port,
                                                          service, accountId, initialAgentURL, accountType, isPrivate);
        //set for incoming requests
        XMPPServlet xmppServlet = new XMPPServlet();
        xmppServlet.listenForIncomingChats(newConfig);
        return newConfig.getConfigId();
    }
    
    public String createUSSDAdapter(@Name("address") String address, @Name("keyword") @Optional String keyword,
        @Name("username") String username, @Name("password") String password,
        @Name("preferredLanguage") @Optional String preferredLanguage, @Name("accountId") @Optional String accountId,
        @Name("accountType") @Optional String accountType, @Name("isPrivate") @Optional Boolean isPrivate)
        throws Exception {

        preferredLanguage = (preferredLanguage == null ? "nl" : preferredLanguage);

        AdapterConfig config = new AdapterConfig();
        config.setAdapterType(AdapterType.USSD.toString());
        config.setMyAddress(address);
        config.setKeyword(keyword);
        config.setPreferred_language(preferredLanguage);
        config.setPublicKey(accountId);
        config.setOwner(accountId);
        config.addAccount(accountId);
        config.setAnonymous(false);
        config.setAccessToken(username);
        config.setAccessTokenSecret(password);
        config.setAccountType(AccountType.fromJson(accountType));
        config.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.CLX);
        AdapterConfig newConfig = createAdapter(config, isPrivate);
        return newConfig.getConfigId();
    }
	
    public String createPushAdapter(@Name("address") String address, @Name("keyword") @Optional String keyword,
        @Name("username") String username, @Name("password") String password,
        @Name("preferredLanguage") @Optional String preferredLanguage, @Name("accountId") @Optional String accountId,
        @Name("accountType") @Optional String accountType, @Name("isPrivate") @Optional Boolean isPrivate)
        throws Exception {

        preferredLanguage = (preferredLanguage == null ? "nl" : preferredLanguage);

        AdapterConfig config = new AdapterConfig();
        config.setAdapterType(AdapterType.PUSH.toString());
        config.setMyAddress(address);
        config.setKeyword(keyword);
        config.setPreferred_language(preferredLanguage);
        config.setPublicKey(accountId);
        config.setOwner(accountId);
        config.addAccount(accountId);
        config.setAnonymous(false);
        config.setAccessToken(username);
        config.setAccessTokenSecret(password);
        config.setAccountType(AccountType.fromJson(accountType));
        config.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.NOTIFICARE);
        AdapterConfig newConfig = createAdapter(config, isPrivate);
        return newConfig.getConfigId();
    }

    public String registerASKFastXMPPAdapter(@Name("xmppAddress") String xmppAddress,
        @Name("password") String password, @Name("name") @Optional String name, @Name("email") @Optional String email,
        @Name("preferredLanguage") @Optional String preferredLanguage, @Name("accountId") @Optional String accountId,
        @Name("initialAgentURL") @Optional String initialAgentURL, @Name("accountType") @Optional String accountType,
        @Name("isPrivate") @Optional Boolean isPrivate) throws Exception {

        xmppAddress = xmppAddress.endsWith("@xmpp.ask-fast.com") ? xmppAddress : (xmppAddress + "@xmpp.ask-fast.com");
        ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters(AdapterType.XMPP.toString(), xmppAddress, null);
        AdapterConfig newConfig = adapters != null && !adapters.isEmpty() ? adapters.iterator().next() : null;
        //check if adapter exists
        if (newConfig == null) {
            newConfig = createSimpleXMPPAdapter(xmppAddress, password, name, preferredLanguage,
                                                XMPPServlet.DEFAULT_XMPP_HOST,
                                                String.valueOf(XMPPServlet.DEFAULT_XMPP_PORT), null, accountId,
                                                initialAgentURL, accountType, isPrivate);
        }
        else if (accountId != null && !accountId.isEmpty() && !accountId.equals(newConfig.getOwner())) //check if the accountId owns this adapter
        {
            throw new Exception(String.format("Adapter exists but AccountId: %s does not own it", accountId));
        }
        try {
            XMPPServlet.registerASKFastXMPPAccount(xmppAddress, password, name, email);
            XMPPServlet xmppServlet = new XMPPServlet();
            xmppServlet.listenForIncomingChats(newConfig);
        }
        catch (XMPPException e) {
            if (e.getXMPPError().getCode() == 409) //just listen to incoming chats if account already exists.
            {
                XMPPServlet xmppServlet = new XMPPServlet();
                xmppServlet.listenForIncomingChats(newConfig);
            }
            log.severe("Error registering an ASK-Fast account. Error: " + e.getLocalizedMessage());
            throw e;
        }
        return newConfig != null ? newConfig.getConfigId() : null;
    }
    
    public String deregisterASKFastXMPPAdapter(@Name("xmppAddress") @Optional String xmppAddress,
        @Name("accountId") String accountId, @Name("adapterId") @Optional String adapterId) throws Exception {

        xmppAddress = xmppAddress.endsWith("@xmpp.ask-fast.com") ? xmppAddress : (xmppAddress + "@xmpp.ask-fast.com");
        ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters(AdapterType.XMPP.toString(), xmppAddress, null);
        AdapterConfig adapterConfig = adapters != null && !adapters.isEmpty() ? adapters.iterator().next() : null;
        //check if adapter is owned by the accountId
        if (adapterConfig != null && accountId.equals(adapterConfig.getOwner())) {
            XMPPServlet.deregisterASKFastXMPPAccount(adapterConfig);
            adapterConfig.delete();
        }
        else {
            throw new Exception(String.format("Adapter either doesnt exist or now owned by AccountId: %s", accountId));
        }
        return adapterConfig != null ? adapterConfig.getConfigId() : null;
    }
	
    public String createMBAdapter(@Name("address") String address, @Name("keyword") @Optional String keyword,
        @Name("username") String username, @Name("password") String password,
        @Name("preferredLanguage") @Optional String preferredLanguage, @Name("accountId") @Optional String accountId,
        @Name("accountType") @Optional String accountType, @Name("isPrivate") @Optional Boolean isPrivate)
        throws Exception {

        return createGenericSMSAdapter(address, AdapterProviders.MB, keyword, username, password, preferredLanguage,
                                       accountId, accountType, isPrivate);
    }
    
    /**
     * Creates a generic SMS adapter. Based on the adapterType, username and password, this can 
     * be assigned to a unique provider.  
     * @param address The address of the sender
     * @param adapterType The type of the SMS provider used
     * @param keyword 
     * @param username Username of the provider account
     * @param password 
     * @param preferredLanguage
     * @param accountId
     * @param accountType
     * @return
     * @throws Exception
     */
    public String createGenericSMSAdapter(@Name("address") String address,
        @Name("provider") @Optional AdapterProviders provider, @Name("keyword") @Optional String keyword,
        @Name("username") String username, @Name("password") String password,
        @Name("preferredLanguage") @Optional String preferredLanguage, @Name("accountId") @Optional String accountId,
        @Name("accountType") @Optional String accountType, @Name("isPrivate") @Optional Boolean isPrivate)
        throws Exception {

        preferredLanguage = (preferredLanguage == null ? "nl" : preferredLanguage);

        AdapterConfig config = new AdapterConfig();
        config.setAdapterType(AdapterType.SMS.toString());
        config.setMyAddress(address);
        config.setKeyword(keyword);
        config.setPreferred_language(preferredLanguage);
        config.setPublicKey(accountId);
        config.setOwner(accountId);
        config.addAccount(accountId);
        config.setAnonymous(false);
        config.setAccessToken(username);
        config.setAccessTokenSecret(password);
        config.setAccountType(AccountType.fromJson(accountType));
        if (provider != null) {
            config.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, provider);
        }
        AdapterConfig newConfig = createAdapter(config, isPrivate);
        return newConfig.getConfigId();
    }
	
    public String createNexmoAdapter(@Name("address") String address, @Name("keyword") @Optional String keyword,
        @Name("username") String username, @Name("password") String password,
        @Name("preferredLanguage") @Optional String preferredLanguage, @Name("accountId") @Optional String accountId,
        @Name("isPrivate") @Optional Boolean isPrivate)
        throws Exception {

        return createGenericSMSAdapter(address, AdapterProviders.NEXMO, keyword, username, password, preferredLanguage,
                                       accountId, null, isPrivate);
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
            ArrayList<AdapterConfig> adapterConfigs = AdapterConfig.findAdapters(AdapterType.TWITTER.toString(),
                                                                                 twitterUserName, null);
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
        if (config == null)
            throw new Exception("No adapter with this id");

        if (config.getOwner() != null) {
            throw new Exception("Adapter is already owned by someone else");
        }

        config.setOwner(accountId);
        config.addAccount(accountId);
        //add cost/ddr
        DDRUtils.createDDRRecordOnAdapterPurchase(config, true);
        config.update();
    }

	public void addAccount(@Name("adapterId") String adapterId, @Name("accountId") String accountId) throws Exception {
		
		AdapterConfig config = AdapterConfig.getAdapterConfig(adapterId);
		if(config==null)
			throw new Exception("No adapter with this id");
		config.setStatus(Status.ACTIVE);
		config.addAccount(accountId);
		config.update();
	}
	
    public RestResponse getAdapter(@Name("accoutId") String accountId, @Name("adapterId") String adapterId) {

        AdapterConfig config = AdapterConfig.getAdapterConfig(adapterId);
        if (config == null) {
            return RestResponse.ok(Settings.DIALOG_HANDLER_VERSION, null,
                                   "No adapter linked to this account or with this id");
        }

        if (AdapterConfig.checkIfAdapterMatchesForAccountId(Arrays.asList(accountId), config, false) == null) {
            return RestResponse.forbidden(Settings.DIALOG_HANDLER_VERSION,
                                          "No adapter linked to this account or with this id");
        }
        return RestResponse.ok(Settings.DIALOG_HANDLER_VERSION, config);
    }
	
    public Object updateAdapter(@Name("accoutId") String accountId, @Name("adapterId") String adapterId,
                                @Name("adapter") Adapter adapter) throws Exception {

        RestResponse adapterResponse = getAdapter(accountId, adapterId);
        AdapterConfig config = null;
        if(adapterResponse.getCode() == javax.ws.rs.core.Response.Status.OK.getStatusCode()) {
            config = adapterResponse.getResult(AdapterConfig.class);
        }
        if (config != null) {
            if (adapter.getInitialAgentURL() != null) {
                config.setInitialAgentURL(adapter.getInitialAgentURL());
            }
            if (adapter.isAnonymous() != null) {
                config.setAnonymous(adapter.isAnonymous());
            }
            if (adapter.getStatus() != null) {

                log.warning(String.format(" *** Updating status of adapter: %s with id: %s from: %s to: %s ***",
                                          config.getMyAddress(), config.getConfigId(), config.getStatus(),
                                          adapter.getStatus()));
                config.setStatus(Status.fromJson(adapter.getStatus()));
            }
            if (adapter.getDialogId() != null) {
                //check if the accoundId is the owner of this adapter. Incoming scenarios only work if 
                //one owns the adapter
                if(accountId.equals(config.getOwner())) {
                    config.setDialogId(adapter.getDialogId());
                }
                else {
                    throw new Exception(String.format("Account: %s does not own adapter: %s with address: %s",
                                                      accountId, config.getConfigId(), config.getAddress()));
                }
            }
            if (adapter.getAccountType() != null) {
                config.setAccountType(adapter.getAccountType());
            }
            if(adapter.isPrivate()) {
                config.markAsPrivate();
            }
            //allow keywords to be changed only for sms adapters
            if (config.getAdapterType() != null) {
                AdapterType adapterType = AdapterType.fromJson(config.getAdapterType());
                switch (adapterType) {
                    case EMAIL:
                    case PUSH:
                    case USSD:
                        if (adapter.getMyAddress() != null) {
                            config.setMyAddress(adapter.getMyAddress());
                        }
                        break;
                    case SMS:
                        if (adapter.getKeyword() != null) {
                            config.setKeyword(adapter.getKeyword());
                        }
                        //set the adapter myAddress if the accountId given is the owner
                        //This should be allowed only for an SMS adapter
                        if (adapter.getMyAddress() != null && accountId.equalsIgnoreCase(config.getOwner())) {
                            config.setMyAddress(adapter.getMyAddress());
                        }
                        break;
                    default:
                        //no updates needed for other adapter types
                }
            }
            config.update();
            return config;
        }
        else {
            throw new Exception(String.format("Adapter: %s with address:%s probably does not belong to account: %s",
                                              adapter, adapter.getMyAddress(), accountId));
        }
    }
	
    public void setAllAdapterAccountType(@Name("accountId") String accountId, @Name("accountType") AccountType type) {
        ArrayList<AdapterConfig> ownedAdapters = AdapterConfig.findAdapterByOwner(accountId, null, null);
        for(AdapterConfig adapter : ownedAdapters) {
            adapter.setAccountType( type );
            adapter.update();
        }
    }
    
    /**
     * detach an adapter from this account. if adapter is: <br> 
     * 1. XMPP: then this also deregisters him (if its an ask-fast account) <br>
     * 2. isPrivate: deletes the adpater from the datastore
     * 3. rest: unlinks the adapter.  <br>
     * @param adapterId
     * @param accountId If this is same as the adapter owner, it frees the adapter. If it is 
     * same as the accoundId, it just unlinks the accountId from the adapter
     * @throws Exception
     */
    public void removeAdapter(@Name("adapterId") String adapterId, @Name("accountId") String accountId)
        throws Exception {

        AdapterConfig config = AdapterConfig.getAdapterConfig(adapterId);
        if (config == null) {
            throw new Exception("No adapter with this id owned by you");
        }
        else {
            AdapterType adapterType = AdapterType.fromJson(config.getAdapterType());
            //if the accoutnId is the owner of the adapter requesting a remove operation
            //if the adapter is a private adapter, just delete it from the system
            if (AdapterConfig.checkIfAdapterMatchesForAccountId(Arrays.asList(accountId), config, true) != null &&
                config.isPrivate()) {

                config.delete();
            }
            //if its a shared adapter provided by ASK-Fast.
            else if (AdapterConfig.checkIfAdapterMatchesForAccountId(Arrays.asList(accountId), config, false) != null) {
                //if the accountId is the owner, mark adapter as inactive
                config.removeAccount(accountId);
                config.getProperties().remove(AdapterConfig.DIALOG_ID_KEY);
                switch (adapterType) {
                    case XMPP:
                        //deregister if its an askfast xmpp account
                        if (config.getMyAddress().contains("xmpp.ask-fast.com")) {
                            deregisterASKFastXMPPAdapter(config.getMyAddress(), accountId, adapterId);
                        }
                    default:
                        config.update();
                        break;
                }
            }
        }
    }
	
    public ArrayNode getAdapters(@Name("accoutId") String accountId, @Name("adapterType") @Optional String adapterType,
        @Name("address") @Optional String address) {

        List<AdapterConfig> adapters = AdapterConfig.findAdapterByAccount(accountId, adapterType, address);
        return JOM.getInstance().convertValue(adapters, ArrayNode.class);
    }

    public ArrayNode findAdapters(@Name("adapterType") @Optional String type,
        @Name("address") @Optional String address, @Name("keyword") @Optional String keyword) {

        ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters(type, address, keyword);
        return JOM.getInstance().convertValue(adapters, ArrayNode.class);
    }

    /**
     * Gets all the adapters owned by the given accountId
     * 
     * @param type
     * @param address
     * @param keyword
     * @return
     */
    public ArrayNode findOwnedAdapters(@Name("ownerId") String ownerId, @Name("adapterType") @Optional String type,
        @Name("address") @Optional String address) {

        ArrayList<AdapterConfig> ownedAdapters = AdapterConfig.findAdapterByOwner(ownerId, type, address);
        return JOM.getInstance().convertValue(ownedAdapters, ArrayNode.class);
    }
    
    /**
     * Set provider for a particular adapter
     * 
     * @param type
     * @param address
     * @param keyword
     * @return
     */
    public Object setProviderForAdapters(@Name("adapterId") String adapterId,
        @Name("provider") AdapterProviders provider) {

        AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapterId);
        if (adapterConfig != null) {
            adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, provider);
            adapterConfig.update();
        }
        return adapterConfig;
    }
    
    /**
     * Set provider for a particular adapter
     * 
     * @param type
     * @param address
     * @param keyword
     * @return
     */
    public Object removeProviderForAdapters(@Name("adapterId") String adapterId) {

        AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapterId);
        if (adapterConfig != null) {
            Map<String, Object> properties = adapterConfig.getProperties();
            properties.remove(AdapterConfig.ADAPTER_PROVIDER_KEY);
            adapterConfig.update();
        }
        return adapterConfig;
    }
	
    public ArrayNode findFreeAdapters(@Name("adapterType") @Optional String adapterType,
        @Name("address") @Optional String address) {

        ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapterByAccount(null, adapterType, address);
        return JOM.getInstance().convertValue(adapters, ArrayNode.class);
    }
    
    /**
     * Updates all adapters with the generic format
     * 
     * @return
     * @throws Exception
     */
    public HashMap<String, String> updateAllAdaptersWithProviders() throws Exception {

        HashMap<String, String> warnings = new HashMap<String, String>();
        ArrayList<AdapterConfig> allAdapters = AdapterConfig.findAdapters(null, null, null);
        if (allAdapters != null) {
            for (AdapterConfig adapterConfig : allAdapters) {
                switch (adapterConfig.getAdapterType().toLowerCase()) {
                    case "broadsoft":
                        adapterConfig.setAdapterType(ADAPTER_TYPE_CALL);
                        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.BROADSOFT);
                        break;
                    case "voxeo":
                        adapterConfig.setAdapterType(ADAPTER_TYPE_CALL);
                        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.TWILIO);
                        break;
                    case "cm":
                    case "sms":
                        adapterConfig.setAdapterType(ADAPTER_TYPE_SMS);
                        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.CM);
                        break;
                    case "route-sms":
                        adapterConfig.setAdapterType(ADAPTER_TYPE_SMS);
                        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.ROUTE_SMS);
                        break;
                    case "mb":
                        adapterConfig.setAdapterType(ADAPTER_TYPE_SMS);
                        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.ROUTE_SMS);
                        break;
                    case "ussd":
                        adapterConfig.setAdapterType(ADAPTER_TYPE_USSD);
                        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, AdapterProviders.CLX);
                        break;
                    case "notificare":
                        adapterConfig.setAdapterType(ADAPTER_TYPE_PUSH);
                        adapterConfig.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY,
                                                         AdapterProviders.NOTIFICARE);
                        break;
                    default:
                        warnings.put(adapterConfig.getConfigId(), String.format("Not updated. type: %s address: %s",
                                                                                adapterConfig.getAdapterType(),
                                                                                adapterConfig.getMyAddress()));
                        break;
                }
                adapterConfig.update();
            }
        }
        return warnings;
    }
    
    /**
     * Updates the dialog linked to this adapter
     * 
     * @return
     * @throws Exception
     */
    public AdapterConfig setDialogToAdapter(@Name("accountId") String accountId, @Name("adapterId") String adapterId,
        @Name("dialogId") String dialogId) throws Exception {

        AdapterConfig config = AdapterConfig.getAdapterConfig(adapterId);
        if (config != null && accountId.equals(config.getOwner())) {
            Dialog dialog = Dialog.getDialog(dialogId, accountId);
            if (dialog != null) {
                config.setDialogId(dialogId);
                config.update();
            }
        }
        return config;
    }
    
    /**
     * saves the AdapterConfig in the datastore
     * 
     * @param config
     * @return
     * @throws Exception
     */
    private AdapterConfig createAdapter(AdapterConfig config, Boolean isPrivate) throws Exception {

        if (AdapterConfig.adapterExists(config.getAdapterType(), config.getMyAddress(), config.getKeyword())) {
            throw new ConflictException("Adapter already exists");
        }
        if (config.getConfigId() == null) {
            config.configId = new UUID().toString();
        }
        //add creation timestamp to the adapter
        config.getProperties().put(AdapterConfig.ADAPTER_CREATION_TIME_KEY, TimeUtils.getServerCurrentTimeInMillis());
        //change the casing to lower in case adatertype if email or xmpp
        if (config.getMyAddress() != null &&
            (AdapterType.EMAIL.equals(AdapterType.getByValue(config.getAdapterType()))) ||
            AdapterType.XMPP.equals(AdapterType.getByValue(config.getAdapterType()))) {

            config.setMyAddress(config.getMyAddress().toLowerCase());
        }
        //check if there is an initialAgent url given. Create a dialog if it is
        if (config.getURLForInboundScenario(null) != null && !config.getURLForInboundScenario(null).isEmpty()) {
            Dialog dialog = Dialog.createDialog("Dialog created on adapter creation",
                                                config.getURLForInboundScenario(null), config.getOwner());
            config.getProperties().put(AdapterConfig.DIALOG_ID_KEY, dialog.getId());
        }

        config.setAdapterType(config.getAdapterType().toLowerCase());
        config.setStatus(Status.INACTIVE);
        if (Boolean.TRUE.equals(isPrivate)) {
            config.markAsPrivate();
        }
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        datastore.store(config);

        if (AdapterProviders.BROADSOFT.equals(DialogAgent.getProvider(config.getAdapterType(), config))) {
            Broadsoft bs = new Broadsoft(config);
            bs.hideCallerId(config.isAnonymous());
        }
        //add costs for creating this adapter
        DDRRecord ddrRecord = DDRUtils.createDDRRecordOnAdapterPurchase(config, true);
        //push the cost to hte queue
        Double totalCost = DDRUtils.calculateDDRCost(ddrRecord);
        DDRUtils.publishDDREntryToQueue(config.getOwner(), totalCost);
        //attach cost to ddr is prepaid type
        if (ddrRecord != null && !AccountType.POST_PAID.equals(ddrRecord.getAccountType())) {
            ddrRecord.setTotalCost(totalCost);
            ddrRecord.createOrUpdate();
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
    private AdapterConfig createSimpleXMPPAdapter(String xmppAddress, String password, String name,
        String preferredLanguage, String host, String port, String service, String accountId, String initialAgentURL,
        String accountType, Boolean isPrivate) throws Exception {

        preferredLanguage = (preferredLanguage == null ? "nl" : preferredLanguage);
        AdapterConfig config = new AdapterConfig();
        config.setAdapterType(ADAPTER_TYPE_XMPP);
        //by default create gmail xmpp adapter
        config.getProperties().put(XMPPServlet.XMPP_HOST_KEY, host != null ? host : XMPPServlet.DEFAULT_XMPP_HOST);
        config.getProperties().put(XMPPServlet.XMPP_PORT_KEY, port != null ? port : XMPPServlet.DEFAULT_XMPP_PORT);
        if (service != null) {
            config.getProperties().put(XMPPServlet.XMPP_SERVICE_KEY, service);
        }
        config.setMyAddress(xmppAddress.toLowerCase());
        config.setAddress(name);
        config.setXsiUser(xmppAddress.toLowerCase().split("@")[0]);
        config.setXsiPasswd(password);
        config.setPreferred_language(preferredLanguage);
        config.setPublicKey(accountId);
        config.setOwner(accountId);
        config.addAccount(accountId);
        config.setAnonymous(false);
        config.setInitialAgentURL(initialAgentURL);
        config.setAccountType(AccountType.fromJson(accountType));
        AdapterConfig newConfig = createAdapter(config, isPrivate);
        return newConfig;
    }
    
    /**
     * appends "appspotmail.com" for the given address based on the current
     * jetty environment.
     * 
     * @param address
     * @return
     */
    private static String getEnvironmentSpecificEmailAddress(String address) {

        if (ServerUtils.isInDevelopmentEnvironment()) {
            address = (address.contains("appspotmail.com") || address.contains("@")) ? address
                                                                                    : (address + "@char-a-lot.appspotmail.com");
        }
        else if (ServerUtils.isInProductionEnvironment()) {
            address = (address.contains("appspotmail.com") || address.contains("@")) ? address
                                                                                    : (address + "@ask-charlotte.appspotmail.com");
        }
        return address;
    }
    
    private DialogAgentInterface getDialogAgent(String id) {
        return createAgentProxy( URI.create("local:"+id), DialogAgentInterface.class );
    }
    
    private String getApplicationId() {
        DialogAgentInterface dialogAgent = getDialogAgent( "dialog" );
        return dialogAgent.getApplicationId();
    }
    
    public String getVersion() {
        return Settings.DIALOG_HANDLER_VERSION;
    }
}