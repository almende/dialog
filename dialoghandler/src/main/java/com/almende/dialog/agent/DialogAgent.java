package com.almende.dialog.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.adapter.CLXUSSDServlet;
import com.almende.dialog.adapter.CMSmsServlet;
import com.almende.dialog.adapter.MBSmsServlet;
import com.almende.dialog.adapter.MailServlet;
import com.almende.dialog.adapter.NotificareServlet;
import com.almende.dialog.adapter.TwitterServlet;
import com.almende.dialog.adapter.VoiceXMLRESTProxy;
import com.almende.dialog.adapter.XMPPServlet;
import com.almende.dialog.adapter.tools.Broadsoft;
import com.almende.dialog.model.Session;
import com.almende.dialog.util.KeyServerLib;
import com.almende.dialog.util.ServerUtils;
import com.almende.eve.agent.Agent;
import com.almende.eve.rpc.annotation.Access;
import com.almende.eve.rpc.annotation.AccessType;
import com.almende.eve.rpc.annotation.Name;
import com.almende.eve.rpc.annotation.Optional;
import com.almende.eve.rpc.jsonrpc.JSONRPCException;
import com.almende.eve.rpc.jsonrpc.JSONRPCException.CODE;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.almende.util.TypeUtil;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.askfast.commons.agent.intf.DialogAgentInterface;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.entity.DialogRequestDetails;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.QueueingConsumer.Delivery;

@Access(AccessType.PUBLIC)
public class DialogAgent extends Agent implements DialogAgentInterface {
	private static final Logger	log	= Logger.getLogger(DialogAgent.class
											.getName());

    //create a single static connection for collecting dialog requests
    private static ConnectionFactory rabbitMQConnectionFactory;
    private static final String DIALOG_PROCESS_QUEUE_NAME = "DIALOG_PUBLISH_QUEUE";
    private static final Integer MAXIMUM_DEFAULT_DIALOG_ALLOWED = 15;
    
	public ArrayList<String> getActiveCalls(@Name("adapterID") String adapterID) {
		
		try {
			AdapterConfig config = AdapterConfig.findAdapterConfigFromList(
					adapterID, null, null);
			if (config.getAdapterType().equalsIgnoreCase( AdapterAgent.ADAPTER_TYPE_BROADSOFT)) {
				return VoiceXMLRESTProxy.getActiveCalls(config);
			}
		} catch (Exception ex) {
		}
		
		return null;
	}
	
	public ArrayList<String> getActiveCallsInfo(
			@Name("adapterID") String adapterID) {
		
		try {
			AdapterConfig config = AdapterConfig.findAdapterConfigFromList(
					adapterID, null, null);
			if (config.getAdapterType().equalsIgnoreCase( AdapterAgent.ADAPTER_TYPE_BROADSOFT)) {
				return VoiceXMLRESTProxy.getActiveCallsInfo(config);
			}
		} catch (Exception ex) {
		}
		
		return null;
	}
	
	public boolean killActiveCalls(@Name("adapterID") String adapterID) {
		
		try {
			AdapterConfig config = AdapterConfig.findAdapterConfigFromList(
					adapterID, null, null);
            if ( config.getAdapterType().equalsIgnoreCase( AdapterAgent.ADAPTER_TYPE_BROADSOFT ) )
            {
                return VoiceXMLRESTProxy.killActiveCalls( config );
            }
		} catch (Exception ex) {
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
	
	public HashMap<String, String> outboundCall(
			@Name("address") String address,
			@Name("senderName") @Optional String senderName,
			@Name("subject") @Optional String subject, @Name("url") String url,
			@Name("adapterType") @Optional String adapterType,
			@Name("adapterID") @Optional String adapterID,
			@Name("accountID") String accountID,
			@Name("bearerToken") String bearerToken) throws Exception {
		return outboundCallWithList(Arrays.asList(address), senderName,
				subject, url, adapterType, adapterID, accountID, bearerToken);
	}
	
	/**
	 * updated the outboundCall functionality to support broadcast functionality
	 * 
	 * @param addressList
	 *            list of addresses
	 * @throws Exception
	 */
	public HashMap<String, String> outboundCallWithList(
			@Name("addressList") Collection<String> addressList,
			@Name("senderName") @Optional String senderName,
			@Name("subject") @Optional String subject, @Name("url") String url,
			@Name("adapterType") @Optional String adapterType,
			@Name("adapterID") @Optional String adapterID,
			@Name("accountID") String accountID,
			@Name("bearerToken") String bearerToken) throws Exception {
		Map<String, String> addressNameMap = ServerUtils.putCollectionAsKey(
				addressList, "");
		return outboundCallWithMap(addressNameMap, null, null, senderName, subject, url,
				adapterType, adapterID, accountID, bearerToken);
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
        @Name("bearerToken") String bearerToken) throws JSONRPCException {

        HashMap<String, String> result = new HashMap<String, String>();
        for (String address : addressMap.keySet()) {
            Map<String, String> addreses = new HashMap<String, String>();
            addreses.put(address, addressMap.get(address));
            result.putAll(outboundCallWithMap(addreses, null, null, senderName, subject, url, adapterType, adapterID,
                                              accountId, bearerToken));
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
        @Name("bearerToken") String bearerToken, @Name("callProperties") @Optional Map<String, String> callProperties)
        throws JSONRPCException {

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
                                   adapterID, accountId, bearerToken);
    }
	
	/**
	 * updated the outboundCall functionality to support broadcast
	 * functionality.
	 * 
	 * @param addressMap
	 *            Key: address and Value: name
	 * @throws Exception
	 */
	public HashMap<String, String> outboundCallWithMap(
			@Name("addressMap") @Optional Map<String, String> addressMap,
			@Name("addressCcMap") @Optional Map<String, String> addressCcMap,
			@Name("addressBccMap") @Optional Map<String, String> addressBccMap,
			@Name("senderName") @Optional String senderName,
			@Name("subject") @Optional String subject, @Name("url") String url,
			@Name("adapterType") @Optional String adapterType,
			@Name("adapterID") @Optional String adapterID,
			@Name("accountID") String accountId,
			@Name("bearerToken") String bearerToken) throws JSONRPCException {
    
            HashMap<String, String> resultSessionMap = new HashMap<String, String>();
            if (adapterType != null && !adapterType.equals("") && adapterID != null && !adapterID.equals("")) {
                throw new JSONRPCException("Choose adapterType or adapterID not both");
            }
            //return if no address is fileed
            if(isNullOrEmpty(addressMap) && isNullOrEmpty(addressCcMap) && isNullOrEmpty(addressBccMap)) {
                resultSessionMap.put("Error", "No addresses given to communicate");
                return resultSessionMap;
            }
            log.info(String.format("accountId: %s bearer %s adapterType %s", accountId, bearerToken, adapterType));
            // Check accountID/bearer Token against OAuth KeyServer
            if (Settings.KEYSERVER != null) {
                if (!KeyServerLib.checkAccount(accountId, bearerToken)) {
                    throw new JSONRPCException(CODE.INVALID_REQUEST, "Invalid token given");
                }
            }
            log.info("KeyServer says ok!");
            log.info("Trying to find config");
            AdapterConfig config = null;
            if (adapterID != null) {
                config = AdapterConfig.getAdapterConfig(adapterID);
            } else {
                // If no adapterId is given. Load the first one of the type.
                // TODO: Add default field to adapter (to be able to load default adapter)
                final List<AdapterConfig> adapterConfigs = AdapterConfig.findAdapters(adapterType, null, null);
                for (AdapterConfig cfg : adapterConfigs) {
                    if (cfg.getOwner().equals(accountId)) {
                        config = cfg;
                        break;
                    }
                }
            }
            if (config != null) {
                if (AdapterConfig.checkIfAdapterMatchesForAccountId(Arrays.asList(accountId), config, false) == null) {
                    throw new JSONRPCException("You are not allowed to use this adapter!");
                }
    
                log.info(String.format("Config found: %s of Type: %s with address: %s", config.getConfigId(),
                                       config.getAdapterType(), config.getMyAddress()));
                adapterType = config.getAdapterType();
                try {
                    //log all addresses 
                    log.info(String.format("recepients of question at: %s are: %s", url, ServerUtils.serialize(addressMap)));
                    log.info(String.format("cc recepients are: %s", ServerUtils.serialize(addressCcMap)));
                    log.info(String.format("bcc recepients are: %s", ServerUtils.serialize(addressBccMap)));
                    
                    if (adapterType.equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_XMPP)) {
                        resultSessionMap = new XMPPServlet().startDialog(addressMap, addressCcMap, addressBccMap, url,
                                                                         senderName, subject, config);
                    }
                    else if (adapterType.equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_BROADSOFT)) {
                        // fetch the first address in the map
                        if (!addressMap.keySet().isEmpty()) {
                            resultSessionMap = VoiceXMLRESTProxy.dial(addressMap, url, senderName, config);
                        }
                        else {
                            throw new Exception("Address should not be empty to setup a call");
                        }
                    }
                    else if (adapterType.equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_EMAIL)) {
                        resultSessionMap = new MailServlet().startDialog(addressMap, addressCcMap, addressBccMap, url,
                                                                         senderName, subject, config);
                    }
                    else if (adapterType.equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_SMS)) {
                        resultSessionMap = new MBSmsServlet().startDialog(addressMap, null, null, url, senderName, subject,
                                                                          config);
                    }
                    else if (adapterType.toUpperCase().equals("CM")) {
                        resultSessionMap = new CMSmsServlet().startDialog(addressMap, null, null, url, senderName, subject,
                                                                          config);
                    }
                    else if (adapterType.equalsIgnoreCase( AdapterAgent.ADAPTER_TYPE_USSD)) {
    					resultSessionMap = new CLXUSSDServlet().startDialog(
    							addressMap, null, null, url, senderName, subject, config);
    				}
                    else if (adapterType.equalsIgnoreCase( AdapterAgent.ADAPTER_TYPE_PUSH)) {
    					resultSessionMap = new NotificareServlet().startDialog(
    							addressMap, null, null, url, senderName, subject, config);
    				}
                    else if (adapterType.equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_TWITTER)) {
                        HashMap<String, String> formattedTwitterAddresses = new HashMap<String, String>(addressMap.size());
                        //convert all addresses to start with @
                        for (String address : addressMap.keySet()) {
                            String formattedTwitterAddress = address.startsWith("@") ? address : ("@" + address);
                            formattedTwitterAddresses.put(formattedTwitterAddress, addressMap.get(address));
                        }
                        resultSessionMap = new TwitterServlet().startDialog(formattedTwitterAddresses, null, null, url,
                                                                            senderName, subject, config);
                    }
                    else {
                        throw new Exception("Unknown type given: either broadsoft or phone or mail:" +
                                            adapterType.toUpperCase());
                    }
                }
                catch (Exception e) {
                    JSONRPCException jse = new JSONRPCException(CODE.REMOTE_EXCEPTION, "Failed to call out!", e);
                    log.log(Level.WARNING, "OutboundCallWithMap, failed to call out!", e);
                    throw jse;
                }
            }
            else {
                throw new JSONRPCException("Invalid adapter found");
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
            Dialog dialog = Dialog.createDialog("Dialog created on agent update", config.getURLForInboundScenario(),
                                                config.getOwner());
            config.getProperties().put(AdapterConfig.DIALOG_ID_KEY, dialog.getId());
            datastore.store(config);

            ObjectNode result = JOM.createObjectNode();
            result.put("id", config.getConfigId());
            result.put("type", config.getAdapterType());
            result.put("url", config.getURLForInboundScenario());
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
        if ( oldDialog == null )
            throw new Exception( "Dialog not found" );

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
    
	@Override
	public String getDescription() {
		return "Dialog handling agent";
	}
	
	@Override
	public String getVersion() {
		return "1.4.2";
	}
	
    public void consumeDialogInitiationQueue() {

        try {
            rabbitMQConnectionFactory = rabbitMQConnectionFactory != null ? rabbitMQConnectionFactory
                                                                         : new ConnectionFactory();
            rabbitMQConnectionFactory.setHost("localhost");
            Connection connection = rabbitMQConnectionFactory.newConnection();
            Channel channel = connection.createChannel();
            //create a message
            channel.queueDeclare(DIALOG_PROCESS_QUEUE_NAME, false, false, false, null);
            QueueingConsumer consumer = new QueueingConsumer(channel);
            channel.basicConsume(DIALOG_PROCESS_QUEUE_NAME, true, consumer);

            Integer currentSessionsCountInQueue = getCurrentSessionsCountInQueue();
            Integer maxSessionsAllowedInQueue = getMaxSessionsAllowedInQueue();
            log.info(String.format("Waiting to process dialogs... current list size: %s. Maximum allowed: %s",
                                   currentSessionsCountInQueue, maxSessionsAllowedInQueue));
            while (currentSessionsCountInQueue < maxSessionsAllowedInQueue) {
                Delivery delivery = consumer.nextDelivery();
                ObjectMapper om = JOM.getInstance();
                try {
                    DialogRequestDetails dialogDetails = om.readValue(delivery.getBody(), DialogRequestDetails.class);
                    log.info(String.format("---------Received a dialog request to process: %s ---------",
                                           new String(delivery.getBody())));
                    outboundCall(dialogDetails);
                }
                catch (Exception e) {
                    log.severe(String.format("Dialog processing failed for payload: %s. Error: %s",
                                             new String(delivery.getBody()), e.toString()));
                }
            }
        }
        catch (Exception e) {
            log.severe("Error seen: " + e.getLocalizedMessage());
        }
    }
    
    private HashMap<String, String> outboundCall(DialogRequestDetails dialogDetails) throws Exception {

        HashMap<String, String> result = new HashMap<String, String>();
        if (dialogDetails != null && dialogDetails.getMethod() != null) {
            String dialogMethod = dialogDetails.getMethod();
            switch (dialogMethod) {
                case "outboundCall":
                    if (dialogDetails.getAddressMap() != null && !dialogDetails.getAddressMap().isEmpty()) {
                        result = outboundCall(dialogDetails.getAddressMap().keySet().iterator().next(),
                                              dialogDetails.getSenderName(), dialogDetails.getSubject(),
                                              dialogDetails.getUrl(), dialogDetails.getAdapterType(),
                                              dialogDetails.getAdapterID(), dialogDetails.getAccountId(),
                                              dialogDetails.getBearerToken());
                    }
                    else {
                        result.put("Error!. No addresses found", null);
                    }
                    break;
                case "outboundCallWithList":
                    if (dialogDetails.getAddressMap() != null && !dialogDetails.getAddressMap().isEmpty()) {
                        result = outboundCallWithList(dialogDetails.getAddressMap().keySet(),
                                                      dialogDetails.getSenderName(), dialogDetails.getSubject(),
                                                      dialogDetails.getUrl(), dialogDetails.getAdapterType(),
                                                      dialogDetails.getAdapterID(), dialogDetails.getAccountId(),
                                                      dialogDetails.getBearerToken());
                    }
                    else {
                        result.put("Error!. No addresses found", null);
                    }
                    break;
                case "outboundSeperateCallWithMap":
                    if (dialogDetails.getAddressMap() != null && !dialogDetails.getAddressMap().isEmpty()) {
                        result = outboundSeperateCallWithMap(dialogDetails.getAddressMap(),
                                                             dialogDetails.getSenderName(), dialogDetails.getSubject(),
                                                             dialogDetails.getUrl(), dialogDetails.getAdapterType(),
                                                             dialogDetails.getAdapterID(),
                                                             dialogDetails.getAccountId(),
                                                             dialogDetails.getBearerToken());
                    }
                    else {
                        result.put("Error!. No addresses found", null);
                    }
                    break;
                case "outboundCallWithProperties":
                    result = outboundCallWithProperties(dialogDetails.getAddressMap(), dialogDetails.getAddressCcMap(),
                                                        dialogDetails.getAddressBccMap(),
                                                        dialogDetails.getSenderName(), dialogDetails.getSubject(),
                                                        dialogDetails.getUrl(), dialogDetails.getAdapterType(),
                                                        dialogDetails.getAdapterID(), dialogDetails.getAccountId(),
                                                        dialogDetails.getBearerToken(),
                                                        dialogDetails.getCallProperties());
                case "outboundCallWithMap":
                    result = outboundCallWithMap(dialogDetails.getAddressMap(), dialogDetails.getAddressCcMap(),
                                                 dialogDetails.getAddressBccMap(), dialogDetails.getSenderName(),
                                                 dialogDetails.getSubject(), dialogDetails.getUrl(),
                                                 dialogDetails.getAdapterType(), dialogDetails.getAdapterID(),
                                                 dialogDetails.getAccountId(), dialogDetails.getBearerToken());
                    break;
                default:
                    break;
            }
        }
        return result;
    }
    
    /**
     * Returns all the sessionKeys that are currently being processed and in queue.
     * @return
     */
    public Collection<String> getCurrentSessionsInQueue() {
        return getState().get("sessionsInQueue", new TypeUtil<Collection<String>>() {});
    }
    
    /**
     * Returns the number of sessions currently being handled.
     * @return
     */
    public Integer getCurrentSessionsCountInQueue() {
        Collection<String> currentSessions = getCurrentSessionsInQueue();
        return currentSessions != null ? currentSessions.size() : 0;
    }
    
    /**
     * Returns the maximum number of sessions allowed by the processor
     * @return
     */
    public Integer getMaxSessionsAllowedInQueue() {
        Integer maxSessionsAllowed = getState().get("max_sessionsInQueue", Integer.class);
        if(maxSessionsAllowed == null) {
            maxSessionsAllowed = MAXIMUM_DEFAULT_DIALOG_ALLOWED;
            getState().put("max_sessionsInQueue", maxSessionsAllowed);
        }
        return maxSessionsAllowed;
    }
    
    /**
     * Set the maximum number of sessions that can be handled in the queue.
     * @return
     */
    public Integer setMaxSessionsAllowedInQueue(@Name("maxSessionsInQueue") Integer maxSessionsInQueue) {

        getState().put("max_sessionsInQueue", maxSessionsInQueue);
        return getMaxSessionsAllowedInQueue();
    }
    
    /**
     * Returns the number of sessions currently being handled.
     * @return
     */
    private Collection<String> addSessionToProcessQueue(String sessionKey) {

        if (sessionKey != null) {
            Collection<String> currentSessions = getCurrentSessionsInQueue();
            currentSessions = currentSessions != null ? currentSessions : new HashSet<String>();
            currentSessions.add(sessionKey);
            getState().put("sessionsInQueue", currentSessions);
        }
        return getCurrentSessionsInQueue();
    }
	
    /**
     * basic check to see if a map is empty or null
     * 
     * @param mapObject
     * @return
     */
    private boolean isNullOrEmpty(Map<String, String> mapObject) {
        return mapObject == null || mapObject.isEmpty() ? true : false;
    }
}