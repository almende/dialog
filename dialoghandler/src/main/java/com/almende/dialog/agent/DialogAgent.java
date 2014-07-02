package com.almende.dialog.agent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
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
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.askfast.commons.agent.intf.DialogAgentInterface;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Access(AccessType.PUBLIC)
public class DialogAgent extends Agent implements DialogAgentInterface {
	private static final Logger	log	= Logger.getLogger(DialogAgent.class
											.getName());
	
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
	 * updated the outboundCall functionality to support broadcast
	 * functionality.
	 * 
	 * @param addressMap
	 *            Key: address and Value: name
	 * @throws Exception
	 */
	public HashMap<String, String> outboundCallWithMap(
			@Name("addressMap") Map<String, String> addressMap,
			@Name("addressCcMap") @Optional Map<String, String> addressCcMap,
			@Name("addressBccMap") @Optional Map<String, String> addressBccMap,
			@Name("senderName") @Optional String senderName,
			@Name("subject") @Optional String subject, @Name("url") String url,
			@Name("adapterType") @Optional String adapterType,
			@Name("adapterID") @Optional String adapterID,
			@Name("accountID") String accountId,
			@Name("bearerToken") String bearerToken) throws JSONRPCException {
    
            HashMap<String, String> resultSessionMap;
            if (adapterType != null && !adapterType.equals("") && adapterID != null && !adapterID.equals("")) {
                throw new JSONRPCException("Choose adapterType or adapterID not both");
            }
            log.setLevel(Level.INFO);
            log.info(String.format("pub: %s pri %s adapterType %s", accountId, bearerToken, adapterType));
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
                if (config.getOwner() != null && !config.getOwner().equals(accountId)) {
                    throw new JSONRPCException("You are not allowed to use this adapter!");
                }
    
                log.info(String.format("Config found: %s of Type: %s with address: %s", config.getConfigId(),
                                       config.getAdapterType(), config.getMyAddress()));
                adapterType = config.getAdapterType();
                try {
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
    
    public void deleteDialog( @Name( "accountId" ) String accountId, @Name( "id" ) String id ) throws Exception
    {
        Dialog.deleteDialog( id, accountId );
    }
    
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
		return "2.1.0";
	}
}