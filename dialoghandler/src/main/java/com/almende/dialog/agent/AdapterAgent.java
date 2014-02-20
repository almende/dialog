package com.almende.dialog.agent;

import java.util.ArrayList;
import java.util.List;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.Broadsoft;
import com.almende.dialog.exception.ConflictException;
import com.almende.eve.agent.Agent;
import com.almende.eve.rpc.annotation.Access;
import com.almende.eve.rpc.annotation.AccessType;
import com.almende.eve.rpc.annotation.Name;
import com.almende.eve.rpc.annotation.Optional;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.uuid.UUID;
import com.askfast.commons.agent.intf.AdapterAgentInterface;
import com.askfast.commons.entity.Adapter;
import com.fasterxml.jackson.databind.node.ArrayNode;

@Access(AccessType.PUBLIC)
public class AdapterAgent extends Agent implements AdapterAgentInterface {
	
	private static final String ADAPTER_TYPE_BROADSOFT = "broadsoft";
	private static final String ADAPTER_TYPE_SMS = "SMS";
	private static final String ADAPTER_TYPE_EMAIL = "email";
	private static final String ADAPTER_TYPE_XMPP = "xmpp";
	private static final String ADAPTER_TYPE_TWITTER = "twitter";	
	
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
		
		String normAddress = address.replaceFirst("^0", "").replace("+31", "");;
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
	
	public String createEmailAdapter() {
		// TODO: implement
		return null;
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
	
	public String createTwitterAdapter() {
		// TODO: implement
		return null;
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
		if(config.getMyAddress() != null && (config.getAdapterType().toUpperCase().equals( "MAIL" ) || 
		    config.getAdapterType().toUpperCase().equals( "XMPP" )) )
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
