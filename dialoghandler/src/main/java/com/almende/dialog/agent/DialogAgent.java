package com.almende.dialog.agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.core.Response.Status;
import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.accounts.Recording;
import com.almende.dialog.adapter.CLXUSSDServlet;
import com.almende.dialog.adapter.CMSmsServlet;
import com.almende.dialog.adapter.MBSmsServlet;
import com.almende.dialog.adapter.MailServlet;
import com.almende.dialog.adapter.NotificareServlet;
import com.almende.dialog.adapter.RouteSmsServlet;
import com.almende.dialog.adapter.TPAdapter;
import com.almende.dialog.adapter.TwilioAdapter;
import com.almende.dialog.adapter.TwitterServlet;
import com.almende.dialog.adapter.VoiceXMLRESTProxy;
import com.almende.dialog.adapter.XMPPServlet;
import com.almende.dialog.adapter.tools.Broadsoft;
import com.almende.dialog.model.Session;
import com.almende.dialog.util.KeyServerLib;
import com.almende.dialog.util.ServerUtils;
import com.almende.eve.agent.Agent;
import com.almende.eve.protocol.jsonrpc.annotation.Access;
import com.almende.eve.protocol.jsonrpc.annotation.AccessType;
import com.almende.eve.protocol.jsonrpc.annotation.Name;
import com.almende.eve.protocol.jsonrpc.annotation.Optional;
import com.almende.eve.protocol.jsonrpc.formats.JSONRPCException;
import com.almende.eve.protocol.jsonrpc.formats.JSONRPCException.CODE;
import com.almende.util.TypeUtil;
import com.almende.util.jackson.JOM;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.askfast.commons.RestResponse;
import com.askfast.commons.agent.intf.DialogAgentInterface;
import com.askfast.commons.entity.AccountType;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.DialogRequest;
import com.askfast.commons.entity.TTSInfo;
import com.askfast.commons.entity.TTSInfo.TTSProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Access(AccessType.PUBLIC)
public class DialogAgent extends Agent implements DialogAgentInterface {

    private static final Logger log = Logger.getLogger(DialogAgent.class.getName());
    private static final String GLOBAL_ADAPTER_SWITCH_MAPPING_KEY = "GLOBAL_ADAPTER_SWITCH_MAPPING";
    private static final String DEFAULT_ADAPTER_PROVIDER_MAPPING_KEY = "ADAPTER_PROVIDER_MAPPING";
    public static final String ADAPTER_PROVIDER_CREDENTIALS_KEY = "ADAPTER_PROVIDER_CREDENTIALS";
    public static final String ADAPTER_CREDENTIALS_GLOBAL_KEY = "GLOBAL";
    public static final String BEARER_TOKEN_KEY = "BEARER_TOKEN";
    private static final String DEFAULT_TTS_ACCOUNT_ID_KEY = "DEFAULT_TTS_ACCOUNTID";
    private static final String NO_QUESTION_FOUND_MESSAGE = "Question not fetched from: %s. Request for outbound call rejected ";
    public static final String INVALID_ADDRESS_MESSAGE = "Address %s is invalid";
    public static final String INVALID_METHOD_TYPE_MESSAGE = "Unknown outbound method type: ";
    
    /**
     * Used by the Tests to store any default communication providers. Usually 
     * saved in this agent state. 
     */
    private static Map<AdapterType, AdapterProviders> DEFAULT_PROVIDERS = null;
    static {
        if (DEFAULT_PROVIDERS == null) {
            DEFAULT_PROVIDERS = new HashMap<AdapterType, AdapterProviders>();
            DEFAULT_PROVIDERS.put(AdapterType.CALL, AdapterProviders.BROADSOFT);
            DEFAULT_PROVIDERS.put(AdapterType.SMS, AdapterProviders.CM);
        }
    }
    
    public ArrayList<String> getActiveCalls(@Name("adapterID") String adapterID) {

        try {
            AdapterConfig config = AdapterConfig.findAdapterConfigFromList(adapterID, null, null);
            if (config != null &&
                AdapterProviders.BROADSOFT.equals(config.getProvider())) {
                return VoiceXMLRESTProxy.getActiveCalls(config);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
	
    public ArrayList<String> getActiveCallsInfo(@Name("adapterID") String adapterID) {

        try {
            AdapterConfig config = AdapterConfig.findAdapterConfigFromList(adapterID, null, null);
            if (AdapterProviders.BROADSOFT.equals(config.getProvider())) {
                return VoiceXMLRESTProxy.getActiveCallsInfo(config);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
	
    public boolean killActiveCalls(@Name("adapterID") String adapterID) {

        try {
            AdapterConfig config = AdapterConfig.findAdapterConfigFromList(adapterID, null, null);
            if (AdapterProviders.BROADSOFT.equals(config.getProvider())) {
                return VoiceXMLRESTProxy.killActiveCalls(config);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return false;
    }
	
	public String killCall(@Name("session") String sessionKey) {
		Session session = Session.getSession(sessionKey);
		if (session == null) return "unknown";
		session.kill();
		return "ok";
	}
	
    public HashMap<String, String> outboundCall(@Name("address") String address,
        @Name("senderName") @Optional String senderName, @Name("subject") @Optional String subject,
        @Name("url") String url, @Name("adapterType") @Optional String adapterType,
        @Name("adapterID") @Optional String adapterID, @Name("accountID") String accountID,
        @Name("bearerToken") String bearerToken, @Name("accountType") @Optional AccountType accountType) throws Exception {

        return outboundCallWithList(Arrays.asList(address), senderName, subject, url, adapterType, adapterID,
            accountID, bearerToken, accountType);
    }
	
    /**
     * updated the outboundCall functionality to support broadcast functionality
     * 
     * @param addressList
     *            list of addresses
     * @throws Exception
     */
    public HashMap<String, String> outboundCallWithList(@Name("addressList") Collection<String> addressList,
        @Name("senderName") @Optional String senderName, @Name("subject") @Optional String subject,
        @Name("url") String url, @Name("adapterType") @Optional String adapterType,
        @Name("adapterID") @Optional String adapterID, @Name("accountID") String accountID,
        @Name("bearerToken") String bearerToken, @Name("accountType") @Optional AccountType accountType) throws Exception {

        Map<String, String> addressNameMap = ServerUtils.putCollectionAsKey(addressList, "");
        return outboundCallWithMap(addressNameMap, null, null, senderName, subject, url, adapterType, adapterID,
            accountID, bearerToken, accountType);
    }
	
    /**
     * Triggers seperate call for each member in the address map.
     * 
     * @throws Exception
     */
    public HashMap<String, String> outboundSeperateCallWithMap(@Name("addressMap") Map<String, String> addressMap,
        @Name("senderName") @Optional String senderName, @Name("subject") @Optional String subject,
        @Name("url") String url, @Name("adapterType") @Optional String adapterType,
        @Name("adapterID") @Optional String adapterID, @Name("accountID") String accountId,
        @Name("bearerToken") String bearerToken, @Name("accountType") @Optional AccountType accountType) throws Exception {

        HashMap<String, String> result = new HashMap<String, String>();
        for (String address : addressMap.keySet()) {
            Map<String, String> addreses = new HashMap<String, String>();
            addreses.put(address, addressMap.get(address));
            result.putAll(outboundCallWithMap(addreses, null, null, senderName, subject, url, adapterType, adapterID,
                accountId, bearerToken, accountType));
        }
        return result;
    }
    
    /**
     * Similar call to
     * {@link DialogAgent#outboundCallWithMap(Map, Map, Map, String, String, String, String, String, String, String)}
     * , but passes any call related properties
     * 
     * @param callProperties
     *            call specific properties can be passed here.
     * @throws JSONRPCException
     */
    public HashMap<String, String> outboundCallWithProperties(@Name("addressMap") Map<String, String> addressMap,
        @Name("addressCcMap") @Optional Map<String, String> addressCcMap,
        @Name("addressBccMap") @Optional Map<String, String> addressBccMap,
        @Name("senderName") @Optional String senderName, @Name("subject") @Optional String subject,
        @Name("url") String url, @Name("adapterType") @Optional String adapterType,
        @Name("adapterID") @Optional String adapterID, @Name("accountID") String accountId,
        @Name("bearerToken") String bearerToken, @Name("callProperties") @Optional Map<String, String> callProperties,
        @Name("accountType") @Optional AccountType accountType) throws Exception {

        if (callProperties != null && !callProperties.isEmpty()) {
            log.info("outbound call with properties: " + ServerUtils.serializeWithoutException(callProperties));
            if (callProperties.get("ANONYMOUS") != null) {
                AdapterConfig adapter = AdapterConfig.getAdapterForOwner(adapterID, accountId);
                if (adapter != null && AdapterType.CALL.equals(AdapterType.getByValue(adapter.getAdapterType()))) {
                    Broadsoft bs = new Broadsoft(adapter);
                    bs.hideCallerId(Boolean.parseBoolean(callProperties.get("ANONYMOUS")));
                }
            }
        }
        return outboundCallWithMap(addressMap, addressCcMap, addressBccMap, senderName, subject, url, adapterType,
            adapterID, accountId, bearerToken, accountType);
    }

    /**
     * Method used to broadcast the same message to multiple addresses
     * 
     * @param addressNameMap
     *            Map with address (e.g. phonenumber or email) as Key and name
     *            as value. The name is useful for email and not used for SMS
     *            etc
     * @param addressCcNameMap
     *            Cc list of the address to which this message is broadcasted.
     *            This is only used by the email servlet.
     * @param addressBccNameMap
     *            Bcc list of the address to which this message is broadcasted.
     *            This is only used by the email servlet.
     * @param senderName
     *            The sendername, used only by the email servlet, SMS
     * @param subject
     *            This is used only by the email servlet
     * @param dialogIdOrUrl
     *            if a String with leading "http" is found its considered as a
     *            url. Else a Dialog of this id is tried t The URL on which a
     *            GET HTTPRequest is performed and expected a question JSON
     * @param adapterType
     *            Either an adapterType must be given (in which case it will
     *            fetch the first adapter on this type), if null an adapterId is
     *            expected
     * @param adapterID
     *            AdapterId for a channel configuration. This configuration is
     *            used to perform this outbound communication
     * @param accountId
     *            This accountId is charged with the costs for this
     *            communication
     * @param bearerToken
     *            Secure token used for verifying this communication request
     * @param accountType
     *            Indicates if the account triggering the call is E.g. trial or
     *            not. Used to add trial messages etc
     * @return
     * @throws JSONRPCException
     */
    public HashMap<String, String> outboundCallWithMap(@Name("addressMap") @Optional Map<String, String> addressMap,
        @Name("addressCcMap") @Optional Map<String, String> addressCcMap,
        @Name("addressBccMap") @Optional Map<String, String> addressBccMap,
        @Name("senderName") @Optional String senderName, @Name("subject") @Optional String subject,
        @Name("url") String dialogIdOrUrl, @Name("adapterType") @Optional String adapterType,
        @Name("adapterID") @Optional String adapterID, @Name("accountID") String accountId,
        @Name("bearerToken") String bearerToken, @Name("accountType") @Optional AccountType accountType) throws Exception {

        log.info("outbound call with map");
        HashMap<String, String> resultSessionMap = new HashMap<String, String>();
        if (adapterType != null && !adapterType.equals("") && adapterID != null && !adapterID.equals("")) {
            throw new JSONRPCException("Choose adapterType or adapterID not both");
        }
        //return if no address is fileed
        if (isNullOrEmpty(addressMap) && isNullOrEmpty(addressCcMap) && isNullOrEmpty(addressBccMap)) {
            resultSessionMap.put("Error", "No addresses given to communicate");
            return resultSessionMap;
        }
        log.info(String.format("accountId: %s bearer %s adapterType %s", accountId, bearerToken, adapterType));
        // Check accountID/bearer Token against OAuth KeyServer
        if (Settings.KEYSERVER != null && !ServerUtils.isInUnitTestingEnvironment()) {
            if (!KeyServerLib.checkAccount(accountId, bearerToken)) {
                throw new JSONRPCException(CODE.INVALID_REQUEST, "Invalid token given");
            }
        }
        log.info("KeyServer says ok!");
        log.info("Trying to find config");
        AdapterConfig config = null;
        if (adapterID != null) {
            config = AdapterConfig.getAdapterConfig(adapterID);
        }
        else {
            // If no adapterId is given. Load the first one of the type.
            // TODO: Add default field to adapter (to be able to load default adapter)
            adapterType = adapterType != null && AdapterType.getByValue(adapterType) != null ? AdapterType.getByValue(
                adapterType).getName() : adapterType;
            final List<AdapterConfig> adapterConfigs = AdapterConfig.findAdapterByAccount(accountId, adapterType, null);
            if (adapterConfigs.size() > 0) {
                config = adapterConfigs.get(0);
            }
        }
        if (config != null) {
            if (AdapterConfig.checkIfAdapterMatchesForAccountId(Arrays.asList(accountId), config, false) == null) {
                throw new JSONRPCException("You are not allowed to use this adapter!");
            }
            
            accountType = (accountType==null ? config.getAccountType() : accountType); 

            log.info(String.format("Config found: %s of Type: %s with address: %s", config.getConfigId(),
                config.getAdapterType(), config.getMyAddress()));

            adapterType = adapterType != null ? adapterType : config.getAdapterType();
            //log all addresses 
            log.info(String.format("recepients of question at: %s are: %s", dialogIdOrUrl,
                ServerUtils.serialize(addressMap)));
            log.info(String.format("cc recepients are: %s", ServerUtils.serialize(addressCcMap)));
            log.info(String.format("bcc recepients are: %s", ServerUtils.serialize(addressBccMap)));

            AdapterProviders globalSwitchAdapter = getGlobalAdapterSwitchSettingsForType(AdapterType.getByValue(adapterType));
            //apply global switch if application
            if (globalSwitchAdapter != null) {
                switchAdapterIfGlobalSettingsFound(config, globalSwitchAdapter);
            }
            adapterType = config.getAdapterType();
            AdapterProviders provider = getProvider(adapterType, config);

            //update adapter credentials if not found locally and found globally
            if (ServerUtils.isNullOrEmpty(config.getAccessToken()) &&
                ServerUtils.isNullOrEmpty(config.getAccessTokenSecret()) &&
                ServerUtils.isNullOrEmpty(config.getXsiUser()) && ServerUtils.isNullOrEmpty(config.getXsiPasswd())) {

                switchAdapterIfGlobalSettingsFound(config, provider);
            }

            if (adapterType.equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_XMPP)) {
                resultSessionMap = new XMPPServlet().startDialog(addressMap, addressCcMap, addressBccMap,
                    dialogIdOrUrl, senderName, subject, config, accountId, accountType);
            }
            else if (config.isCallAdapter() && provider != null) {
                switch (provider) {
                    case BROADSOFT: {
                        // fetch the first address in the map
                        if (!addressMap.keySet().isEmpty()) {
                            resultSessionMap = VoiceXMLRESTProxy.dial(addressMap, dialogIdOrUrl, config, accountId,
                                bearerToken, accountType);
                        }
                        else {
                            throw new JSONRPCException("Address should not be empty to setup a call");
                        }
                    }
                        break;
                    case TWILIO: {
                        // fetch the first address in the map
                        if (!addressMap.keySet().isEmpty() && getApplicationId() != null) {
                            resultSessionMap = TwilioAdapter.dial(addressMap, dialogIdOrUrl, config, accountId,
                                getApplicationId(), bearerToken);
                        }
                        else {
                            throw new JSONRPCException("Address should not be empty to setup a call");
                        }
                        break;
                    }
                    case TP: {
                        // fetch the first address in the map
                        if (!addressMap.keySet().isEmpty()) {
                            resultSessionMap = TPAdapter.dial(addressMap, dialogIdOrUrl, config, accountId,
                                senderName, bearerToken);
                        }
                        else {
                            throw new JSONRPCException("Address should not be empty to setup a call");
                        }
                        break;
                    }
                    default:
                        throw new JSONRPCException(String.format(
                            "No calling provider found for adapter: %s with id: %s", config.getMyAddress(),
                            config.getConfigId()));
                }
            }
            else if (adapterType.equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_EMAIL)) {
                resultSessionMap = new MailServlet().startDialog(addressMap, addressCcMap, addressBccMap,
                    dialogIdOrUrl, senderName, subject, config, accountId, accountType);
            }
            else if (config.isSMSAdapter()) {

                if (provider != null) {
                    switch (provider) {
                        case MB:
                            resultSessionMap = new MBSmsServlet().startDialog(addressMap, null, null, dialogIdOrUrl,
                                senderName, subject, config, accountId, accountType);
                            break;
                        case CM:
                            resultSessionMap = new CMSmsServlet().startDialog(addressMap, null, null, dialogIdOrUrl,
                                senderName, subject, config, accountId, accountType);
                            break;
                        case ROUTE_SMS:
                            resultSessionMap = new RouteSmsServlet().startDialog(addressMap, null, null, dialogIdOrUrl,
                                senderName, subject, config, accountId, accountType);
                            break;
                        default:
                            throw new Exception(String.format("No SMS provider found for adapter: %s with id: %s",
                                config.getMyAddress(), config.getConfigId()));
                    }
                }
                else {
                    throw new JSONRPCException(String.format("No SMS provider found for adapter: %s with id: %s",
                        config.getMyAddress(), config.getConfigId()));
                }
            }
            else if (adapterType.equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_USSD)) {
                resultSessionMap = new CLXUSSDServlet().startDialog(addressMap, null, null, dialogIdOrUrl, senderName,
                    subject, config, accountId, accountType);
            }
            else if (adapterType.equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_PUSH)) {
                resultSessionMap = new NotificareServlet().startDialog(addressMap, null, null, dialogIdOrUrl,
                    senderName, subject, config, accountId, accountType);
            }
            else if (adapterType.equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_TWITTER)) {
                HashMap<String, String> formattedTwitterAddresses = new HashMap<String, String>(addressMap.size());
                //convert all addresses to start with @
                for (String address : addressMap.keySet()) {
                    String formattedTwitterAddress = address.startsWith("@") ? address : ("@" + address);
                    formattedTwitterAddresses.put(formattedTwitterAddress, addressMap.get(address));
                }
                resultSessionMap = new TwitterServlet().startDialog(formattedTwitterAddresses, null, null,
                    dialogIdOrUrl, senderName, subject, config, accountId, accountType);
            }
            else {
                throw new JSONRPCException("Unknown type given: either broadsoft or phone or mail:" +
                    adapterType.toUpperCase());
            }
        }
        else {
            throw new JSONRPCException("Invalid adapter. No adapter found of " +
                (adapterID != null ? ("adapterId: " + adapterID) : ("type: " + adapterType)));
        }
        return resultSessionMap;
    }

    public String changeAgent(@Name("url") String url, @Name("adapterType") @Optional String adapterType,
                              @Name("adapterID") @Optional String adapterID, @Name("accountId") String accountId,
                              @Name("bearerToken") String bearerToken) throws Exception {

        if (adapterType != null && !adapterType.equals("") && adapterID != null && !adapterID.equals("")) {
            throw new Exception("Choose adapterType or adapterID not both");
        }
        log.setLevel(Level.INFO);
        log.info(String.format("pub: %s pri %s adapterType %s", accountId, bearerToken, adapterType));
        // Check accountID/bearer Token against OAuth KeyServer
        if (Settings.KEYSERVER != null) {
            if (!KeyServerLib.checkAccount(accountId, bearerToken)) {
                throw new JSONRPCException("Invalid token given");
            }
        }
        log.info("KeyServer says ok!");
        log.info("Trying to find config");
        AdapterConfig config = null;
        if (adapterID != null) {
            config = AdapterConfig.getAdapterConfig(adapterID);
        }
        else {
            final List<AdapterConfig> adapterConfigs = AdapterConfig.findAdapters(adapterType, null, null);
            for (AdapterConfig cfg : adapterConfigs) {
                if (cfg.getPublicKey().equals(accountId)) {
                    config = cfg;
                    break;
                }
            }
        }
        if (config != null) {
            if (config.getPublicKey() != null && !config.getPublicKey().equals(accountId)) {
                throw new JSONRPCException("You are not allowed to change this adapter!");
            }

            log.info("Config found: " + config.getConfigId());
            TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
            Dialog dialog = Dialog.createDialog("Dialog created on agent update",
                                                config.getURLForInboundScenario(null), config.getOwner());
            config.getProperties().put(AdapterConfig.DIALOG_ID_KEY, dialog.getId());
            datastore.store(config);

            ObjectNode result = JOM.createObjectNode();
            result.put("id", config.getConfigId());
            result.put("type", config.getAdapterType());
            result.put("url", config.getURLForInboundScenario(null));
            return result.toString();
        }
        else {
            throw new Exception("Invalid adapter found");
        }
    }
	
	public List<AdapterConfig> getOwnAdapters(
			@Name("adapterType") @Optional String adapterType,
			@Name("accountID") String accountId,
			@Name("bearerToken") String bearerToken) throws Exception {
		
		log.setLevel(Level.INFO);
		// Check accountID/bearer Token against OAuth KeyServer
		if (Settings.KEYSERVER != null) {
			if (!KeyServerLib.checkAccount(accountId, bearerToken)) {
				throw new JSONRPCException("Invalid token given");
			}
		}
		log.info("KeyServer says ok!");
		List<AdapterConfig> adapterConfigs = AdapterConfig.findAdapters(
				adapterType, null, null);
		return adapterConfigs;
	}
	
    public Object createDialog( @Name( "accountId" ) String accountId, @Name( "name" ) String name,
        @Name( "url" ) String url ) throws Exception
    {
        Dialog dialog = new Dialog( name, url );
        dialog.setOwner( accountId );
        dialog.storeOrUpdate();
        return dialog;
    }
    
    public Object getDialog( @Name( "accountId" ) String accountId, @Name( "id" ) String id ) throws Exception
    {
        if ( accountId != null && id != null )
        {
            return Dialog.getDialog( id, accountId );
        }
        return null;
    }
    
    public Object updateDialog( @Name( "accountId" ) String accountId, @Name( "id" ) String id,
        @Name( "dialog" ) Object dialog ) throws Exception
    {
        Dialog oldDialog = Dialog.getDialog( id, accountId );
        if ( oldDialog == null ) {
            throw new Exception( "Dialog not found" );
        }
        String dialogString = JOM.getInstance().writeValueAsString( dialog );
        JOM.getInstance().readerForUpdating( oldDialog ).readValue( dialogString );
        oldDialog.storeOrUpdate();
        return oldDialog;
    }
    
    /**
     * Removes a specific dialog attached to this accountId
     * @param accountId
     * @param dialogId
     * @throws Exception
     */
    public void deleteDialog( @Name( "accountId" ) String accountId, @Name( "id" ) String id ) throws Exception
    {
        Dialog.deleteDialog( id, accountId );
    }
    
    /**
     * Removes all the dialogs attached to this accountId
     * @param accountId
     * @throws Exception
     */
    public void deleteAllDialogs( @Name( "accountId" ) String accountId ) throws Exception
    {
        ArrayNode dialogs = getDialogs( accountId );
        for ( JsonNode jsonNode : dialogs )
        {
            deleteDialog( accountId, jsonNode.get( "id" ).asText() );
        }
    }
    
    public ArrayNode getDialogs( @Name( "accountId" ) String accountId ) throws Exception
    {
        List<Dialog> dialogs = Dialog.getDialogs( accountId );
        return JOM.getInstance().convertValue( dialogs, ArrayNode.class );
    }
    
    public String getApplicationId() {

        if (!ServerUtils.isInUnitTestingEnvironment()) {
            return getState().get("applicationId", String.class);
        }
        else {
            return UUID.randomUUID().toString();
        }
    }
    
    public void setApplicationId(@Name("applicationId") String applicationId) {
    	getState().put("applicationId", applicationId);
    }
    
    public void setAWSInfo(@Name("bucketName") String bucketName, @Name("accessKey") String accessKey,
                           @Name("accessKeySecret") String accessKeySecret) {
        Map<String, String> awsInfo = new HashMap<String, String>();
        awsInfo.put( "bucketName", bucketName );
        awsInfo.put( "accessKey", accessKey );
        awsInfo.put( "accessKeySecret", accessKeySecret );
        
        getState().put( "awsInfo", awsInfo );
    }
    
    public Map<String, String> getAWSInfo() {
        Map<String, String> awsInfo = getState().get( "awsInfo", new TypeUtil<Map<String, String>>() {} );
        if(awsInfo==null) {
            awsInfo = new HashMap<String, String>();
        }
        return awsInfo;
    }
    
    public Object getRecording(@Name("accountId") String accountId, @Name("filename") String filename) {
        String id = filename.replace( ".wav", "" );
        return Recording.getRecording( id, accountId );
    }
    
    public ArrayNode getRecordings(@Name("accountId") String accountId,
                                   @Name("ddrId") @Optional String ddrId,
                                   @Name("adapterId") @Optional String adapterId) {
        Set<Recording> recordings = Recording.getRecordings( accountId, ddrId, adapterId );
        return JOM.getInstance().convertValue(recordings, ArrayNode.class);
    }
	
    /**
     * Returns all the sessionKeys that are currently being processed and in queue.
     * @return
     */
    public HashSet<String> getAllSessionsInQueue() {

        Collection<String> allSessions = getState().get("sessionsInQueue", new TypeUtil<Collection<String>>() {});
        if(allSessions != null) {
            return new HashSet<String>(allSessions);
        }
        return null;
    }
    
    /**
     * Returns the number of sessions currently being handled.
     * @return
     */
    public Integer getCurrentSessionsCountInQueue() {
        Collection<String> currentSessions = getAllSessionsInQueue();
        return currentSessions != null ? currentSessions.size() : 0;
    }
    
    public boolean clearSessionFromCurrentQueue(@Name("sessionKey") String sessionKey) {

        Collection<String> allSessionsInQueue = getAllSessionsInQueue();
        if (allSessionsInQueue != null) {
            boolean isSessionExisting = allSessionsInQueue.contains(sessionKey);
            log.info(String.format("SessionKey lookup in currentQueue is: %s for sessionKey: %s", isSessionExisting,
                                   sessionKey));
            allSessionsInQueue.remove(sessionKey);
            getState().put("sessionsInQueue", allSessionsInQueue);
            return isSessionExisting;
        }
        else {
            return false;
        }
    }
    
    @Override
    public String getVersion() {
        return Settings.DIALOG_HANDLER_VERSION;
    }
    
    /**
     * Get the default providers for channels. Doesnt return null, returns the
     * {@link DialogAgent#DEFAULT_PROVIDERS} in case the agent info is null. 
     * 
     * @return
     */
    public Map<AdapterType, AdapterProviders> getDefaultProviderSettings() {

        if (!ServerUtils.isInUnitTestingEnvironment()) {
            Map<AdapterType, AdapterProviders> defaultProviders = getState()
                                            .get(DEFAULT_ADAPTER_PROVIDER_MAPPING_KEY,
                                                 new TypeUtil<Map<AdapterType, AdapterProviders>>() {
                                                 });
            return defaultProviders != null ? defaultProviders : DEFAULT_PROVIDERS;
        }
        else {
            return DEFAULT_PROVIDERS;
        }
    }
   
    /**
     * Set the default provider for a particular channel
     * @return
     */
    public Map<AdapterType, AdapterProviders> setDefaultProviderSettings(
        @Name("adapterType") @Optional AdapterType adapterType,
        @Name("adapterProvider") AdapterProviders adapterProvider) {

        Map<AdapterType, AdapterProviders> defaultProviderSettings = getDefaultProviderSettings();
        if (!ServerUtils.isInUnitTestingEnvironment()) {
            defaultProviderSettings = defaultProviderSettings != null ? defaultProviderSettings
                                                                     : new HashMap<AdapterType, AdapterProviders>();
            defaultProviderSettings.put(adapterType, adapterProvider);
            getState().put(DEFAULT_ADAPTER_PROVIDER_MAPPING_KEY, defaultProviderSettings);
            return getDefaultProviderSettings();
        }
        else {
            DEFAULT_PROVIDERS = DEFAULT_PROVIDERS != null ? DEFAULT_PROVIDERS
                                                         : new HashMap<AdapterType, AdapterProviders>();
            DEFAULT_PROVIDERS.put(adapterType, adapterProvider);
            return DEFAULT_PROVIDERS;
        }
    }
    
    public AdapterProviders getGlobalAdapterSwitchSettingsForType(@Name("adapterType") AdapterType adapterType) {

        Map<AdapterType, AdapterProviders> globalAdapterSwitchSettings = getGlobalAdapterSwitchSettings();
        return globalAdapterSwitchSettings != null ? globalAdapterSwitchSettings.get(adapterType) : null;
    }
    
    public Map<AdapterType, AdapterProviders> getGlobalAdapterSwitchSettings() {

        if (!ServerUtils.isInUnitTestingEnvironment()) {
            return getState().get(GLOBAL_ADAPTER_SWITCH_MAPPING_KEY,
                                  new TypeUtil<Map<AdapterType, AdapterProviders>>() {
                                  });
        }
        else {
            return DEFAULT_PROVIDERS;
        }
    }
   
    /**
     * Set the default provider for a particular channel
     * 
     * @return
     */
    public Map<AdapterType, AdapterProviders> setGlobalAdapterSwitchSettings(
        @Name("adapterType") @Optional AdapterType adapterType,
        @Name("adapterProvider") AdapterProviders adapterProvider) {

        Map<AdapterType, AdapterProviders> adapterProviderMapping = getGlobalAdapterSwitchSettings();
        adapterProviderMapping = adapterProviderMapping != null ? adapterProviderMapping
                                                               : new HashMap<AdapterType, AdapterProviders>();
        if (adapterType == null) {
            adapterType = adapterProvider.getAdapterType();
        }
        adapterProviderMapping.put(adapterType, adapterProvider);
        getState().put(GLOBAL_ADAPTER_SWITCH_MAPPING_KEY, adapterProviderMapping);
        return getGlobalAdapterSwitchSettings();
    }
    
    /**
     * Gets all the credentials stored in the DialogAgent
     * @return
     */
    public Map<AdapterProviders, Map<String, AdapterConfig>> getGlobalProviderCredentials() {

        if (!ServerUtils.isInUnitTestingEnvironment()) {
            return getState().get(ADAPTER_PROVIDER_CREDENTIALS_KEY,
                                  new TypeUtil<Map<AdapterProviders, Map<String, AdapterConfig>>>() {
                                  });
        }
        return null;
    }

    /**
     * Set the default credentials for the channels used. This enables a global
     * switch on which provider to user.
     * 
     * @return
     * @throws IOException
     */
    public Map<AdapterProviders, Map<String, AdapterConfig>> setGlobalProviderCredentials(
        @Name("adapterProvider") AdapterProviders adapterProviders,
        @Name("specificAdapterId") @Optional String adapterId, @Name("config") AdapterConfig adapterCredentials)
        throws IOException {

        Object defaultProviderCredentials = getGlobalProviderCredentials();
        if (defaultProviderCredentials == null) {
            defaultProviderCredentials = new HashMap<AdapterProviders, Map<String, AdapterConfig>>();
        }
        String defaultCredentials = JOM.getInstance().writeValueAsString(defaultProviderCredentials);
        Map<AdapterProviders, Map<String, AdapterConfig>> defaultCredentialsAsMap = JOM
                                        .getInstance()
                                        .readValue(defaultCredentials,
                                                   new TypeReference<Map<AdapterProviders, Map<String, AdapterConfig>>>() {
                                                   });
        Map<String, AdapterConfig> defaultCredentialForProvider = defaultCredentialsAsMap.get(adapterProviders);
        if (defaultCredentialForProvider == null) {
            defaultCredentialForProvider = new HashMap<String, AdapterConfig>();
        }
        String credentialsKey = adapterId != null ? adapterId : ADAPTER_CREDENTIALS_GLOBAL_KEY;
        adapterCredentials.setMyAddress(null);
        adapterCredentials.setAdapterType(null);
        adapterCredentials.setConfigId(null);
        adapterCredentials.setPreferred_language(null);
        adapterCredentials.setOwner(null);
        adapterCredentials.setAccounts(null);
        adapterCredentials.addMediaProperties(AdapterConfig.ADAPTER_PROVIDER_KEY, adapterProviders);
        defaultCredentialForProvider.put(credentialsKey, adapterCredentials);
        defaultCredentialsAsMap.put(adapterProviders, defaultCredentialForProvider);
        getState().put(ADAPTER_PROVIDER_CREDENTIALS_KEY, defaultCredentialsAsMap);
        return getGlobalProviderCredentials();
    }
    
    @Override
    public Object setTTSCredentials(@Name("dialogId") String dialogId, @Name("accountId") String accountId,
        @Name("ttsProvider") TTSProvider ttsProvider, @Name("ttsAccountId") String ttsAccountId) {

        Dialog dialog = Dialog.getDialog(dialogId, accountId);
        if (dialog != null) {
            TTSInfo ttsInfo = dialog.getTtsInfo();
            ttsInfo = ttsInfo != null ? ttsInfo : new TTSInfo();
            ttsInfo.setTtsAccountId(ttsAccountId);
            ttsInfo.setProvider(ttsProvider);
            dialog.setTtsInfo(ttsInfo);
            dialog.storeOrUpdate();
        }
        return dialog;
    }
    
    @Override
    public String setDefaultTTSAccountId(@Name("accountId") String accountId, @Name("ttsAccountId") String ttsAccountId)
            throws Exception {

        Map<String, String> defaultTTSAccountIds = getAllDefaultTTSAccountIds();
        defaultTTSAccountIds.put(accountId, ttsAccountId);
        getState().put(DEFAULT_TTS_ACCOUNT_ID_KEY, defaultTTSAccountIds);
        return getDefaultTTSAccountId(accountId);
    }

    @Override
    public String getDefaultTTSAccountId(@Name("accountId") String accountId) {

        return getAllDefaultTTSAccountIds().get(accountId);
    }
    
    /**
     * Triggers an outbound call by calling the appropriate call based on the
     * {@link DialogRequest#getMethod()}
     * 
     * @param dialogDetails
     * @return
     * @throws Exception
     */
    @Override
    public RestResponse outboundCallWithDialogRequest(DialogRequest dialogDetails) {

        HashMap<String, String> result = new HashMap<String, String>();
        try {
            if (dialogDetails != null) {
                String dialogMethod = dialogDetails.getMethod();
                if(dialogMethod == null) {
                    if(dialogDetails.getAddress() != null && !dialogDetails.getAddress().trim().isEmpty()) {
                        dialogMethod = "outboundCall"; 
                    }
                    else if(dialogDetails.getAddressList() != null && !dialogDetails.getAddressList().isEmpty()) {
                        dialogMethod = "outboundCallWithList";
                    }
                    else if (!isNullOrEmpty(dialogDetails.getAddressMap()) ||
                        !isNullOrEmpty(dialogDetails.getAddressCcMap()) ||
                        !isNullOrEmpty(dialogDetails.getAddressBccMap())) {
                        
                        dialogMethod = "outboundCallWithMap";
                    }
                }
                
                switch (dialogMethod) {
                    case "outboundCall":
                        if (dialogDetails.getAddress() != null) {
                            result = outboundCall(dialogDetails.getAddress(), dialogDetails.getSenderName(),
                                dialogDetails.getSubject(), dialogDetails.getUrl(), dialogDetails.getAdapterType(),
                                dialogDetails.getAdapterID(), dialogDetails.getAccountID(),
                                dialogDetails.getBearerToken(), dialogDetails.getAccountType());
                        }
                        else {
                            result.put("Error!. No addresses found", null);
                        }
                        break;
                    case "outboundCallWithList":
                        if (dialogDetails.getAddressList() != null && !dialogDetails.getAddressList().isEmpty()) {
                            result = outboundCallWithList(dialogDetails.getAddressList(),
                                dialogDetails.getSenderName(), dialogDetails.getSubject(), dialogDetails.getUrl(),
                                dialogDetails.getAdapterType(), dialogDetails.getAdapterID(),
                                dialogDetails.getAccountID(), dialogDetails.getBearerToken(),
                                dialogDetails.getAccountType());
                        }
                        else {
                            result.put("Error!. No addresses found", null);
                        }
                        break;
                    case "outboundSeperateCallWithMap":
                        if (dialogDetails.getAddressMap() != null && !dialogDetails.getAddressMap().isEmpty()) {
                            result = outboundSeperateCallWithMap(dialogDetails.getAddressMap(),
                                dialogDetails.getSenderName(), dialogDetails.getSubject(), dialogDetails.getUrl(),
                                dialogDetails.getAdapterType(), dialogDetails.getAdapterID(),
                                dialogDetails.getAccountID(), dialogDetails.getBearerToken(),
                                dialogDetails.getAccountType());
                        }
                        else {
                            result.put("Error!. No addresses found", null);
                        }
                        break;
                    case "outboundCallWithProperties":
                        result = outboundCallWithProperties(dialogDetails.getAddressMap(),
                            dialogDetails.getAddressCcMap(), dialogDetails.getAddressBccMap(),
                            dialogDetails.getSenderName(), dialogDetails.getSubject(), dialogDetails.getUrl(),
                            dialogDetails.getAdapterType(), dialogDetails.getAdapterID(), dialogDetails.getAccountID(),
                            dialogDetails.getBearerToken(), dialogDetails.getCallProperties(),
                            dialogDetails.getAccountType());
                    case "outboundCallWithMap":
                        result = outboundCallWithMap(dialogDetails.getAddressMap(), dialogDetails.getAddressCcMap(),
                            dialogDetails.getAddressBccMap(), dialogDetails.getSenderName(),
                            dialogDetails.getSubject(), dialogDetails.getUrl(), dialogDetails.getAdapterType(),
                            dialogDetails.getAdapterID(), dialogDetails.getAccountID(), dialogDetails.getBearerToken(),
                            dialogDetails.getAccountType());
                        break;
                    default:
                        result.put("Error", INVALID_METHOD_TYPE_MESSAGE + dialogMethod);
                        break;
                }
            }

        }
        catch (Exception e) {
            return new RestResponse(Settings.DIALOG_HANDLER_VERSION, Status.BAD_REQUEST.getReasonPhrase(),
                                    Status.BAD_REQUEST.getStatusCode(), e.getLocalizedMessage());
        }
        //validate the result. If any call is triggered succesfully with invalid address return code: 201
        //if all are successful return 200
        if (result != null) {
            boolean isInvalidAddressFound = false;
            boolean isValidAddressFound = false;
            for (String address : result.keySet()) {
                
                String sessionMessage = result.get(address);
                if (sessionMessage.equals(String.format(INVALID_ADDRESS_MESSAGE, address)) ||
                    sessionMessage.startsWith(INVALID_METHOD_TYPE_MESSAGE)) {
                    
                    isInvalidAddressFound = true;
                }
                else {
                    isValidAddressFound = true;
                }
            }
            if(isInvalidAddressFound) {
                //if atleast one valid address is found, return 201 else return 400
                Status status = isValidAddressFound ? Status.CREATED : Status.BAD_REQUEST;
                return new RestResponse(getVersion(), result, status.getStatusCode(), status.getReasonPhrase());
            }
        }
        return RestResponse.ok(Settings.DIALOG_HANDLER_VERSION, result);
    }
    
    /**
     * Fetches all the default ttsAccountIds mapped for a askFastAccountId
     * @return
     */
    public Map<String, String> getAllDefaultTTSAccountIds() {

        Map<String, String> defaultTTSAccountIds = getState().get(DEFAULT_TTS_ACCOUNT_ID_KEY,
                                                                  new TypeUtil<Map<String, String>>() {
                                                                  });
        defaultTTSAccountIds = defaultTTSAccountIds != null ? defaultTTSAccountIds : new HashMap<String, String>();
        return defaultTTSAccountIds;
    }

    /**
     * Get the provider attached to this adapter by checking the
     * adapterProperties of the adapter or default provider settings
     * 
     * @param adapterType
     * @param config
     * @return
     */
    public static AdapterProviders getProvider(String adapterType, AdapterConfig config) {

        AdapterProviders provider = null;
        if (config != null) {
            provider = config.getProvider();
        }
        //check if the adapter type has the provider info in it
        else if (AdapterProviders.getByValue(adapterType) != null) {
            provider = AdapterProviders.getByValue(adapterType);
        }
        else {
            provider = DEFAULT_PROVIDERS != null ? DEFAULT_PROVIDERS.get(AdapterType.getByValue(adapterType)) : null;
        }
        return provider;
    }
    
    /**
     * Just returns the standard error message string, used to notify that the
     * question is not found in the given url or dialogId
     * 
     * @param dialogIdOrUrl
     * @return
     */
    public static String getQuestionNotFetchedMessage(String dialogIdOrUrl) {

        return String.format(NO_QUESTION_FOUND_MESSAGE, dialogIdOrUrl);
    }
    
    /**
     * Fetches the sessionKeyMap from the extras param. Looks for a <address,
     * Session> value corresponding to the {@link Session#SESSION_KEY} key
     * 
     * @param extras
     * @return
     */
    public static Map<String, Session> getSessionsFromExtras(Map<String, Object> extras) {

        Map<String, Session> sessionKeyMap = null;
        Object sessionsObject = extras != null ? extras.get(Session.SESSION_KEY) : null;
        if (sessionsObject != null) {
            sessionKeyMap = JOM.getInstance().convertValue(sessionsObject, new TypeReference<Map<String, Session>>() {
            });
        }
        return sessionKeyMap;
    }
    
//    /**
//     * Get the buffered outbound requests based on accountId
//     * @return
//     */
//    public Collection<DialogRequestDetails> getBufferedOutboundDialogRequests(String accountId) {
//
//        Map<String, Collection<DialogRequestDetails>> bufferedRequests = getAllBufferedOutboundDialogRequests();
//        return bufferedRequests != null ? bufferedRequests.get(accountId) : null;
//    }
//
//    /** Get all the buffered outbound dialog requests
//     * @return
//     */
//    private Map<String, Collection<DialogRequestDetails>> getAllBufferedOutboundDialogRequests() {
//
//        return getState().get("buffered_requestsInQueue",
//                              new TypeUtil<Map<String, Collection<DialogRequestDetails>>>() {});
//    }
    
//    /**
//     * Returns the number of sessions currently being handled.
//     * @return
//     */
//    private Collection<String> addSessionToProcessQueue(String sessionKey) {
//
//        if (sessionKey != null) {
//            Collection<String> allCurrentSessions = getAllSessionsInQueue();
//            allCurrentSessions = allCurrentSessions != null ? allCurrentSessions : new HashSet<String>();
//            allCurrentSessions.add(sessionKey);
//            getState().put("sessionsInQueue", allCurrentSessions);
//        }
//        return getAllSessionsInQueue();
//    }
    
//    /**
//     * Add some outbound requests to the buffer
//     * @return
//     */
//    private Collection<DialogRequestDetails> addOutboundDialogRequestsToBuffer(DialogRequestDetails dialogRequest) {
//
//        Map<String, Collection<DialogRequestDetails>> allBufferedOutboundDialogRequests = getAllBufferedOutboundDialogRequests();
//        Collection<DialogRequestDetails> bufferedOutboundDialogRequests = allBufferedOutboundDialogRequests != null 
//                                        ? allBufferedOutboundDialogRequests.get(dialogRequest.getAccountId()) : new ArrayList<DialogRequestDetails>();
//        bufferedOutboundDialogRequests.add(dialogRequest);
//        allBufferedOutboundDialogRequests.put(dialogRequest.getAccountId(), bufferedOutboundDialogRequests);
//        getState().put("buffered_requestsInQueue", allBufferedOutboundDialogRequests);
//        return bufferedOutboundDialogRequests;
//    }
	
    /**
     * basic check to see if a map is empty or null
     * 
     * @param mapObject
     * @return
     */
    private boolean isNullOrEmpty(Map<String, String> mapObject) {
        return mapObject == null || mapObject.isEmpty() ? true : false;
    }
    
    /**
     * Updates the Adapter with globally configured adapter credentials. Useful
     * when a particular provider goes down. Gives an option to switch globally.
     * Generally applicable to SMS and CALL type channels only
     * 
     * @param config
     * @param adapterProvider
     * @param globalSwitchProviderCredentials
     */
    private void switchAdapterIfGlobalSettingsFound(AdapterConfig config, AdapterProviders adapterProvider) {

        Map<AdapterProviders, Map<String, AdapterConfig>> globalSwitchProviderCredentials = getGlobalProviderCredentials();
        //check if there is a global switch on adapters
        if (mustCredentialsBeFetchedFromGlobal(adapterProvider, config) && globalSwitchProviderCredentials != null &&
            globalSwitchProviderCredentials.get(adapterProvider) != null) {

            Map<String, AdapterConfig> credentialsForSelectedAdapter = globalSwitchProviderCredentials
                                            .get(adapterProvider);
            //check if there is specific credentials for the given adapter
            AdapterConfig switchAdapterCredentials = credentialsForSelectedAdapter.get(config.getConfigId());
            switchAdapterCredentials = switchAdapterCredentials != null ? switchAdapterCredentials
                                                                       : credentialsForSelectedAdapter.get(ADAPTER_CREDENTIALS_GLOBAL_KEY);

            log.info(String.format("PERFORMING GLOBAL SWITCH FOR ADAPTER: %s ID: %s WITH CREDENTIALS: %s",
                                   config.getMyAddress(), config.getConfigId(),
                                   ServerUtils.serializeWithoutException(switchAdapterCredentials)));

            try {
                //ignore the myAddress and adapterType if given
                switchAdapterCredentials.setAdapterType(null);
                switchAdapterCredentials.setMyAddress(null);
                JOM.getInstance().readerForUpdating(config).readValue(ServerUtils.serialize(switchAdapterCredentials));
            }
            catch (Exception e) {
                e.printStackTrace();
                log.severe(String.format("Updating config: %s with id: %s failed. Error: %s", config.getMyAddress(),
                                         config.getConfigId(), e.toString()));
            }
        }
    }
    
    /**
     * Checks if the adapter credentials must be fetched from global scope or
     * not. If the given adapter misses the credentials and its available in the
     * global state, it returns true.
     * 
     * @return
     */
    private boolean mustCredentialsBeFetchedFromGlobal(AdapterProviders provider, AdapterConfig config) {

        //return true if the providers dont match or the adapter credentials are missing
        if (provider != null &&
            !provider.equals(config.getProvider()) ||
            (ServerUtils.isNullOrEmpty(config.getAccessToken()) &&
                ServerUtils.isNullOrEmpty(config.getAccessTokenSecret()) &&
                ServerUtils.isNullOrEmpty(config.getXsiUser()) && ServerUtils.isNullOrEmpty(config.getXsiPasswd()))) {

            return true;
        }
        return false;
    }

//    /** Count of all the addresses in this dialogDetails
//     * @param dialogDetails
//     * @param totalOutBoundCalls
//     * @return
//     */
//    private Integer getTotalAddressCountInDialogRequests(DialogRequestDetails dialogDetails) {
//
//        Integer totalOutBoundCalls = 0;
//        if (isNullOrEmpty(dialogDetails.getAddressMap())) {
//            totalOutBoundCalls += dialogDetails.getAddressMap().size();
//        }
//        if (isNullOrEmpty(dialogDetails.getAddressCcMap())) {
//            totalOutBoundCalls += dialogDetails.getAddressCcMap().size();
//        }
//        if (isNullOrEmpty(dialogDetails.getAddressBccMap())) {
//            totalOutBoundCalls += dialogDetails.getAddressBccMap().size();
//        }
//        return totalOutBoundCalls;
//    }
    
//    /**
//     * Resets the addresses given of the given dialogRequestDetails given and
//     * buffers it in the state information of this agent
//     * 
//     * @param currentSessionSize
//     * @param dialogRequestDetails
//     * @return
//     */
//    private DialogRequestDetails processDialogRequest(Integer currentSessionSize, Integer maxSessionsAllowedInQueue,
//        DialogRequestDetails dialogRequestDetails) {
//
//        //check if currentSession size allows some of the addresses 
//        Integer manageableBufferSize = maxSessionsAllowedInQueue - currentSessionSize;
//        DialogRequestDetails dialogRequestForSending = null;
//        dialogRequestForSending = new DialogRequestDetails(dialogRequestDetails);
//
//        if (!dialogRequestDetails.getAddressMap().isEmpty()) {
//            if (manageableBufferSize - dialogRequestDetails.getAddressMap().size() >= 0) {
//                dialogRequestForSending.setAddressMap(dialogRequestDetails.getAddressMap());
//            }
//            //buffer part of the addressMap
//            else {
//                Integer addressCountForBuffering = dialogRequestDetails.getAddressMap().size() - manageableBufferSize;
//                splitAddressesMapAndBuffer(dialogRequestForSending, dialogRequestDetails.getAddressMap(),
//                                           addressCountForBuffering);
//            }
//        }
//        return dialogRequestForSending;
//    }

//    /** Returns part of the addresses based on the length of the allowed address size. Rest of the addresses
//     * are buffered
//     * @param dialogRequestDetails
//     * @param addresses
//     * @param allowedAddressesSize
//     * @return
//     */
//    private DialogRequestDetails splitAddressesMapAndBuffer(DialogRequestDetails dialogRequestDetails,
//        Map<String, String> addresses, Integer allowedAddressesSize) {
//
//        Integer addressCount = 0;
//        if(dialogRequestDetails.getAddressMap() == null) {
//            dialogRequestDetails.setAddressMap(new HashMap<String, String>()); 
//        }
//        DialogRequestDetails dialogRequestForBuffer = null;
//        for (String address : addresses.keySet()) {
//            if (addressCount < allowedAddressesSize) {
//                dialogRequestDetails.getAddressMap().put(address, addresses.get(address));
//            }
//            else {
//                if (dialogRequestForBuffer == null) {
//                    dialogRequestForBuffer = new DialogRequestDetails(dialogRequestDetails);
//                    dialogRequestForBuffer.setAddressMap(new HashMap<String, String>());
//                }
//                dialogRequestForBuffer.getAddressMap().put(address, addresses.get(address));
//            }
//        }
////        if (dialogRequestForBuffer != null) {
////            addOutboundDialogRequestsToBuffer(dialogRequestForBuffer);
////        }
//        return dialogRequestDetails;
//    }
}