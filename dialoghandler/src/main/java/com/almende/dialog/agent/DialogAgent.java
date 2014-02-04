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
import com.almende.dialog.adapter.CMSmsServlet;
import com.almende.dialog.adapter.MBSmsServlet;
import com.almende.dialog.adapter.MailServlet;
import com.almende.dialog.adapter.TwitterServlet;
import com.almende.dialog.adapter.VoiceXMLRESTProxy;
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
import com.almende.util.ParallelInit;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.askfast.commons.agent.intf.DialogAgentInterface;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Access(AccessType.PUBLIC)
public class DialogAgent extends Agent implements DialogAgentInterface {
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
				throw new JSONRPCException(CODE.INVALID_REQUEST,
						"Invalid token given");
			}
		}
		log.info("KeyServer says ok!");
		log.info("Trying to find config");
		AdapterConfig config = null;
		if (adapterID != null) {
			config = AdapterConfig.getAdapterConfig(adapterID);
		} else {
			final List<AdapterConfig> adapterConfigs = AdapterConfig
					.findAdapters(adapterType, null, null);
			for (AdapterConfig cfg : adapterConfigs) {
				if (cfg.getPublicKey().equals(accountId)) {
					config = cfg;
					break;
				}
			}
		}
		if (config != null) {
			if (config.getPublicKey() != null
					&& !config.getPublicKey().equals(accountId)) {
				throw new JSONRPCException(
						"You are not allowed to use this adapter!");
			}
			
			log.info(String.format("Config found: %s of Type: %s",
					config.getConfigId(), config.getAdapterType()));
			adapterType = config.getAdapterType();
			try {
				/*
				 * if (adapterType.toUpperCase().equals("XMPP")) {
				 * resultSessionMap = new XMPPServlet().startDialog(
				 * addressMap, url, senderName, subject, config);
				 * } else
				 */
				if (adapterType.toUpperCase().equals("BROADSOFT")) {
					// fetch the first address in the map
					if (!addressMap.keySet().isEmpty()) {
						resultSessionMap = VoiceXMLRESTProxy.dial(addressMap,
								url, senderName, config);
					} else {
						throw new Exception(
								"Address should not be empty to setup a call");
					}
				} else if (adapterType.toUpperCase().equals("MAIL")) {
					resultSessionMap = new MailServlet().startDialog( addressMap, addressCcMap, addressBccMap, url,
		                    senderName, subject, config );
				} else if (adapterType.toUpperCase().equals("SMS")) {
					resultSessionMap = new MBSmsServlet().startDialog(
							addressMap, null, null, url, senderName, subject, config);
				} else if (adapterType.toUpperCase().equals("CM")) {
					resultSessionMap = new CMSmsServlet().startDialog(
							addressMap, null, null, url, senderName, subject, config);
				} else if (adapterType.toUpperCase().equals("TWITTER")) {
					resultSessionMap = new TwitterServlet().startDialog(
							addressMap, null, null, url, senderName, subject, config);
				} else {
					throw new Exception(
							"Unknown type given: either broadsoft or phone or mail:"
									+ adapterType.toUpperCase());
				}
			} catch (Exception e) {
				JSONRPCException jse = new JSONRPCException(
						CODE.REMOTE_EXCEPTION, "Failed to call out!", e);
				log.log(Level.WARNING,
						"OutboundCallWithMap, failed to call out!", e);
				throw jse;
			}
		} else {
			throw new JSONRPCException("Invalid adapter found");
		}
		
		return resultSessionMap;
	}
	
	public String changeAgent(@Name("url") String url,
			@Name("adapterType") @Optional String adapterType,
			@Name("adapterID") @Optional String adapterID,
			@Name("accountId") String accountId,
			@Name("bearerToken") String bearerToken) throws Exception {
		
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
		if (adapterID != null) {
			config = AdapterConfig.getAdapterConfig(adapterID);
		} else {
			final List<AdapterConfig> adapterConfigs = AdapterConfig
					.findAdapters(adapterType, null, null);
			for (AdapterConfig cfg : adapterConfigs) {
				if (cfg.getPublicKey().equals(accountId)) {
					config = cfg;
					break;
				}
			}
		}
		if (config != null) {
			if (config.getPublicKey() != null
					&& !config.getPublicKey().equals(accountId)) {
				throw new JSONRPCException(
						"You are not allowed to change this adapter!");
			}
			
			log.info("Config found: " + config.getConfigId());
			TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
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
	
	@Override
	public String getDescription() {
		return "Dialog handling agent";
	}
	
	@Override
	public String getVersion() {
		return "0.4.1";
	}
	
}
