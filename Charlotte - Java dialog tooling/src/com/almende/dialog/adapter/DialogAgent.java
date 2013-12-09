package com.almende.dialog.adapter;

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
import com.almende.dialog.model.Session;
import com.almende.dialog.util.KeyServerLib;
import com.almende.dialog.util.ServerUtils;
import com.almende.eve.agent.Agent;
import com.almende.eve.json.JSONRPCException;
import com.almende.eve.json.annotation.Name;
import com.almende.eve.json.annotation.Required;
import com.almende.eve.json.jackson.JOM;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.code.twig.annotation.AnnotationObjectDatastore;

public class DialogAgent extends Agent {
	
	private static final Logger	log	= Logger.getLogger(DialogAgent.class
											.getName());
	
	public DialogAgent() {
		super();
		ParallelInit.startThreads();
	}
	
	public ArrayList<String> getActiveCalls(@Name("adapterID") String adapterID) {
		
		try {
			AdapterConfig config = AdapterConfig.findAdapterConfigFromList(
					adapterID, null, null);
			if (config.getAdapterType().toLowerCase().equals("broadsoft")) {
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
			if (config.getAdapterType().toLowerCase().equals("broadsoft")) {
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
			if (config.getAdapterType().toLowerCase().equals("broadsoft")) {
				
				return VoiceXMLRESTProxy.killActiveCalls(config);
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
			@Name("senderName") @Required(false) String senderName,
			@Name("subject") @Required(false) String subject,
			@Name("url") String url,
			@Name("adapterType") @Required(false) String adapterType,
			@Name("adapterID") @Required(false) String adapterID,
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
			@Name("senderName") @Required(false) String senderName,
			@Name("subject") @Required(false) String subject,
			@Name("url") String url,
			@Name("adapterType") @Required(false) String adapterType,
			@Name("adapterID") @Required(false) String adapterID,
			@Name("accountID") String accountID,
			@Name("bearerToken") String bearerToken) throws Exception {
		Map<String, String> addressNameMap = ServerUtils.putCollectionAsKey(
				addressList, "");
		return outboundCallWithMap(addressNameMap, senderName, subject, url,
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
			@Name("senderName") @Required(false) String senderName,
			@Name("subject") @Required(false) String subject,
			@Name("url") String url,
			@Name("adapterType") @Required(false) String adapterType,
			@Name("adapterID") @Required(false) String adapterID,
			@Name("accountID") String accountId,
			@Name("bearerToken") String bearerToken) throws JSONRPCException {
		HashMap<String, String> resultSessionMap;
		if (adapterType != null && !adapterType.equals("") && adapterID != null
				&& !adapterID.equals("")) {
			throw new JSONRPCException(
					"Choose adapterType or adapterID not both");
		}
		log.setLevel(Level.INFO);
		log.info(String.format("pub: %s pri %s adapterType %s", accountId,
				bearerToken, adapterType));
		// Check accountID/bearer Token against OAuth KeyServer
		if (Settings.KEYSERVER != null) {
			if (!KeyServerLib.checkAccount(accountId, bearerToken)) {
				throw new JSONRPCException("Invalid token given");
			}
		}
		log.info("KeyServer says ok!");
		
		// Get local adapterlist and config
		// filter out accountID against list of "allowed" accounts in
		// adapterConfig
		// From result list get best default
		// If adapter remains, do outbound call.
		
		// adapterList = KeyServerLib.getAllowedAdapterList( pubKey, privKey,
		// adapterType );
		
		// try {
		
		log.info("Trying to find config");
		AdapterConfig config = null;
		if (adapterID != null){
			config = AdapterConfig.getAdapterConfig(adapterID);
		} else {
			final List<AdapterConfig> adapterConfigs = AdapterConfig.findAdapters(adapterType, null, null);
			for (AdapterConfig cfg : adapterConfigs){
				if (cfg.getPublicKey().equals(accountId)){
					config=cfg;
					break;
				}
			}
		}
		
		if (config.getPublicKey() != null && !config.getPublicKey().equals(accountId)){
			throw new JSONRPCException("You are not allowed to use this adapter!");
		}

		
		if (config != null) {
			log.info(String.format("Config found: %s of Type: %s",
					config.getConfigId(), config.getAdapterType()));
			adapterType = config.getAdapterType();
			try {
				if (adapterType.toUpperCase().equals("XMPP")) {
					resultSessionMap = new XMPPServlet().startDialog(
							addressMap, url, senderName, subject, config);
				} else if (adapterType.toUpperCase().equals("BROADSOFT")) {
					// fetch the first address in the map
					if (!addressMap.keySet().isEmpty()) {
						resultSessionMap = VoiceXMLRESTProxy.dial(addressMap,
								url, senderName, config);
					} else {
						throw new Exception(
								"Address should not be empty to setup a call");
					}
				} else if (adapterType.toUpperCase().equals("MAIL")) {
					resultSessionMap = new MailServlet().startDialog(
							addressMap, url, senderName, subject, config);
				} else if (adapterType.toUpperCase().equals("SMS")) {
					resultSessionMap = new MBSmsServlet().startDialog(
							addressMap, url, senderName, subject, config);
				} else if (adapterType.toUpperCase().equals("CM")) {
					resultSessionMap = new CMSmsServlet().startDialog(
							addressMap, url, senderName, subject, config);
				} else if (adapterType.toUpperCase().equals("TWITTER")) {
					resultSessionMap = new TwitterServlet().startDialog(
							addressMap, url, senderName, subject, config);
				} else {
					throw new Exception(
							"Unknown type given: either broadsoft or xmpp or phone or mail");
				}
			} catch (Exception e) {
				JSONRPCException jse = new JSONRPCException();
				jse.initCause(e);
				throw jse;
			}
		} else {
			throw new JSONRPCException("Invalid adapter found");
		}
		
		return resultSessionMap;
	}
	
	public String changeAgent(@Name("url") String url,
			@Name("adapterType") @Required(false) String adapterType,
			@Name("adapterID") @Required(false) String adapterID,
			@Name("accountId") String accountId, @Name("bearerToken") String bearerToken)
			throws Exception {
		
		if (adapterType != null && !adapterType.equals("") && adapterID != null
				&& !adapterID.equals("")) {
			throw new Exception("Choose adapterType or adapterID not both");
		}
		log.setLevel(Level.INFO);
		log.info(String.format("pub: %s pri %s adapterType %s", accountId,
				bearerToken, adapterType));
		// Check accountID/bearer Token against OAuth KeyServer
		if (Settings.KEYSERVER != null) {
			if (!KeyServerLib.checkAccount(accountId, bearerToken)) {
				throw new JSONRPCException("Invalid token given");
			}
		}
		log.info("KeyServer says ok!");
		log.info("Trying to find config");
		AdapterConfig config = null;
		if (adapterID != null){
			config = AdapterConfig.getAdapterConfig(adapterID);
		} else {
			final List<AdapterConfig> adapterConfigs = AdapterConfig.findAdapters(adapterType, null, null);
			for (AdapterConfig cfg : adapterConfigs){
				if (cfg.getPublicKey().equals(accountId)){
					config=cfg;
					break;
				}
			}
		}
		if (config.getPublicKey() != null && !config.getPublicKey().equals(accountId)){
			throw new JSONRPCException("You are not allowed to change this adapter!");
		}
		
		if (config != null) {
			log.info("Config found: " + config.getConfigId());
			AnnotationObjectDatastore datastore = new AnnotationObjectDatastore();
			config.setInitialAgentURL(url);
			datastore.store(config);
			
			ObjectNode result = JOM.createObjectNode();
			result.put("id", config.getConfigId());
			result.put("type", config.getAdapterType());
			result.put("url", config.getInitialAgentURL());
			return result.toString();
		} else {
			throw new Exception("Invalid adapter found");
		}
	}
	
	public List<AdapterConfig> getOwnAdapters(
			@Name("adapterType") @Required(false) String adapterType,
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
		List<AdapterConfig> adapterConfigs = AdapterConfig.findAdapters(adapterType, null, null);
		return adapterConfigs;
	}
	
	@Override
	public String getDescription() {
		return "Dialog handling agent";
	}
	
	@Override
	public String getVersion() {
		return "0.4.1";
	}
	
}
