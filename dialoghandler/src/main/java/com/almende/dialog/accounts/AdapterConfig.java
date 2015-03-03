package com.almende.dialog.accounts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import com.almende.dialog.Settings;
import com.almende.dialog.adapter.tools.Broadsoft;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.model.Session;
import com.almende.dialog.util.TimeUtils;
import com.almende.eve.rpc.jsonrpc.jackson.JOM;
import com.almende.util.twigmongo.FilterOperator;
import com.almende.util.twigmongo.QueryResultIterator;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore.RootFindCommand;
import com.almende.util.twigmongo.annotations.Id;
import com.almende.util.uuid.UUID;
import com.askfast.commons.entity.AccountType;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.entity.AdapterType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;


@Path("/adapters")
public class AdapterConfig {
	static final Logger log = Logger.getLogger(AdapterConfig.class.getName());
	static final ObjectMapper om = new ObjectMapper();
	public static final String ADAPTER_CREATION_TIME_KEY = "ADAPTER_CREATION_TIME";
	public static final String ADAPTER_PROVIDER_KEY = "PROVIDER";
	public static final String ACCESS_TOKEN_KEY = "ACCESS_TOKEN";
	public static final String ACCESS_TOKEN_SECRET_KEY = "ACCESS_SECRET_TOKEN";
	public static final String XSI_USER_KEY = "XSI_USER";
	public static final String XSI_PASSWORD_KEY = "XSI_PASSWORD";
	public static final String DIALOG_ID_KEY = "DIALOG_ID";

	@Id
	public String configId;
	String publicKey="";
	String adapterType = "";
	String preferred_language = "nl";
	String initialAgentURL = "";
	String address = "";
	String myAddress = "";
	String keyword = null;
	com.askfast.commons.Status status = null;
	//cache the dialog if its ever fetched to reduce read overhead
	@JsonIgnore
	private Dialog cachedDialog = null;
	   
	// Broadsoft:
	private String xsiURL = "";
	private String xsiUser = "";
	private String xsiPasswd = "";
	private String xsiSubscription = "";
	//OAuth
	private String accessToken="";
	private String accessTokenSecret="";
	Boolean anonymous=false;
	
	String owner=null;
	//reducdant information to avoid linking to the accountServer to fetch the Account again
	AccountType accountType = null;
	Collection<String> accounts=null;
	//store adapter specific data
	Map<String, Object> properties = null;

	public AdapterConfig() {
		accounts = new ArrayList<String>();
	};
	
    @POST
    @Consumes("application/json")
    @Produces("application/json")
    @JsonIgnore
    public Response createConfig(String json) {

        try {
            AdapterConfig newConfig = new AdapterConfig();
            newConfig.status = com.askfast.commons.Status.INACTIVE;

            newConfig = om.readerForUpdating(newConfig).readValue(json);
            newConfig.adapterType = newConfig.adapterType.toLowerCase();
            if (adapterExists(newConfig.getAdapterType(), newConfig.getMyAddress(), newConfig.getKeyword())) {
                return Response.status(javax.ws.rs.core.Response.Status.CONFLICT).build();
            }
            if (newConfig.getConfigId() == null) {
                newConfig.configId = new UUID().toString();
            }

            //change the casing to lower in case adatertype if email or xmpp
            if (newConfig.getAdapterType().equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_EMAIL) ||
                newConfig.getAdapterType().equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_XMPP)) {
                newConfig.setMyAddress(newConfig.getMyAddress() != null ? newConfig.getMyAddress().toLowerCase() : null);
            }
            newConfig.getProperties().put(ADAPTER_CREATION_TIME_KEY, TimeUtils.getServerCurrentTimeInMillis());
            TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
            datastore.store(newConfig);

            if (AdapterProviders.BROADSOFT.equals(DialogAgent.getProvider(newConfig.getAdapterType(), newConfig))) {
                Broadsoft bs = new Broadsoft(newConfig);
                bs.hideCallerId(newConfig.isAnonymous());
            }

            return Response.ok(om.writeValueAsString(newConfig)).build();
        }
        catch (Exception e) {
            log.severe("CreateConfig: Failed to store new config");
        }
        return Response.status(Status.BAD_REQUEST).build();
    }

	@PUT
	@Path("{uuid}")
	@Consumes("application/json")
	@Produces("application/json")
	@JsonIgnore
	public Response updateConfig(@PathParam("uuid") String configid, String json) {
		try {
			AdapterConfig oldConfig = getAdapterConfig(configid);
			
			om.readerForUpdating(oldConfig).readValue(json);
			// TODO Check if fields myAddress, type and keyword have not been changed.
			oldConfig.update();
			
			return Response.ok(om.writeValueAsString(oldConfig)).build();
		} catch (Exception e) {
			e.printStackTrace();
			log.severe("UpdateConfig: Failed to update config:"
					+ e.getMessage());
		}
		return Response.status(Status.BAD_REQUEST).build();
	}

	@GET
	@Path("{uuid}")
	@Produces("application/json")
	@JsonIgnore
	public Response getConfig(@PathParam("uuid") String configid) {
		try {
			AdapterConfig config = getAdapterConfig(configid);
			return Response.ok(om.writeValueAsString(config)).build();
		} catch (Exception e) {
			log.severe("getConfig: Failed to read config");
			e.printStackTrace();
		}
		return Response.status(Status.BAD_REQUEST).build();
	}

	@DELETE
	@Path("{uuid}")
	@Produces("application/json")
	@JsonIgnore
	public Response deleteConfig(@PathParam("uuid") String configid, String json) {
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		try {
			AdapterConfig config = datastore
					.load(AdapterConfig.class, configid);
			datastore.delete(config);
			return Response.ok("").build();
		} catch (Exception e) {
			log.severe("getConfig: Failed to read config");
		}
		return Response.status(Status.BAD_REQUEST).build();
	}

	@GET
	@Produces("application/json")
	@JsonIgnore
	public Response getAllConfigs(@QueryParam("adapter") String adapterType,
			@QueryParam("address") String address,@QueryParam("keyword") String keyword ) {
		try {
			ArrayList<AdapterConfig> adapters = findAdapters(adapterType, address, keyword);
			return Response.ok(om.writeValueAsString(adapters)).build();
		} catch (Exception e) {
			log.severe("getConfig: Failed to read config");
			e.printStackTrace();
		}
		return Response.status(Status.BAD_REQUEST).build();
	}
	
	@GET
	@Path("reset")
	@Produces("application/json")
	@JsonIgnore
	public Response resetConfigs(@QueryParam("password") String password ) {
		if(password.equals("ikweethetzeker")) {
			try {
				ArrayList<AdapterConfig> adapters = findAdapters(null, null, null);
				for(AdapterConfig adapter : adapters) {
					adapter.setOwner(null);
					adapter.setAccounts(new ArrayList<String>());
					adapter.update();
				}
				return Response.ok(om.writeValueAsString(adapters)).build();
			} catch (Exception e) {
				log.severe("getConfig: Failed to read config");
				e.printStackTrace();
			}
		}
		return Response.status(Status.BAD_REQUEST).build();
	}
	
    public void update() {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        adapterType = adapterType.toLowerCase();
        datastore.update(this);
        if (AdapterProviders.BROADSOFT.equals(DialogAgent.getProvider(getAdapterType(), this))) {
            Broadsoft bs = new Broadsoft(this);
            bs.hideCallerId(this.isAnonymous());
        }
    }
	
    public static AdapterConfig getAdapterConfig(String adapterID) {

        if (adapterID != null) {
            TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
            return datastore.load(AdapterConfig.class, adapterID);
        }
        return null;
    }
	
	public static AdapterConfig findAdapterConfigFromList(String adapterID, String type, ArrayNode adapters) {
		
		if(adapterID==null) {
			
			for(JsonNode adapter : adapters) {
				if(adapter.get("adapterType").asText().toUpperCase().equals(type.toUpperCase()) && adapter.get("isDefault").asBoolean()) {
					adapterID = adapter.get("id").asText();
					break;
				}
			}
		}
		
		if(adapterID==null)
			return null;
		
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		AdapterConfig config = datastore.load(AdapterConfig.class, adapterID);
		
                try
                {
                    log.info( String.format( "config %s for adapterId: %s with adapters %s",
                                             om.writeValueAsString( config ), om.writeValueAsString( adapterID),
                                             adapters.toString() ) );
                }
                catch ( Exception e )
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
		
		// Check if config id from database is owned by agent
		if(config!=null) {
			if(Settings.KEYSERVER==null)
				return config;
			
			for(JsonNode adapter : adapters) {
				log.info("Checking: "+adapter.get("id").asText()+" matches: "+config.getConfigId());
				if(adapter.get("id").asText().equals(config.getConfigId()))
					return config;
			}
		} else {
			log.warning("Adapter with id: "+adapterID+" not found in db");
		}
		
		log.warning("ConfigId: "+adapterID+ " found in db. But is not owned by user??");
		
		return null;
	}
	
	public static List<AdapterConfig> findAdapterConfigFromList(String type, ArrayNode adapters) {
        
	    
        ArrayList<String> adapterIDs = new ArrayList<String>();
        for(JsonNode adapter : adapters) {
            if(type==null) {
                adapterIDs.add(adapter.get("id").asText());
            } else if(adapter.get("adapterType").asText().toUpperCase().equals(type.toUpperCase())) {
                adapterIDs.add(adapter.get("id").asText());
            }
        }
        return findAdaptersByList(adapterIDs);
    }

    public static AdapterConfig findAdapterConfig(String adapterType, String lookupKey) {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        Iterator<AdapterConfig> config = datastore.find().type(AdapterConfig.class)
                                        .addFilter("myAddress", FilterOperator.EQUAL, lookupKey)
                                        .addFilter("adapterType", FilterOperator.EQUAL, adapterType.toLowerCase())
                                        .now();
        if (config.hasNext()) {
            return config.next();
        }
        log.severe("AdapterConfig not found:'" + adapterType + "':'" + lookupKey + "'");
        return null;
    }
	
	public static AdapterConfig findAdapterConfig(String adapterType,
			String localAddress, String keyword) {
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		Iterator<AdapterConfig> config = datastore.find()
				.type(AdapterConfig.class)
				.addFilter("myAddress", FilterOperator.EQUAL, localAddress)
				.addFilter("adapterType", FilterOperator.EQUAL, adapterType.toLowerCase())
				.addFilter("keyword", FilterOperator.EQUAL, keyword)
				.now();
		if (config.hasNext()) {
			return config.next();
		}
		log.severe("AdapterConfig not found:'" + adapterType + "':'"
				+ localAddress + "':'"
				+ keyword + "'");
		return null;
	}
	
	public static AdapterConfig findAdapterConfigByUsername(String username) {
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		Iterator<AdapterConfig> config = datastore.find()
				.type(AdapterConfig.class)
				.addFilter("xsiUser", FilterOperator.EQUAL, username)
				.now();
		if (config.hasNext()) {
			return config.next();
		}
		log.severe("AdapterConfig not found:'" + username + "'");
		return null;
	}

	/**
	 * Fetches all adapters for the given matching filters
	 * @param adapterType Lowercased and queried
	 * @param myAddress case insensitive query is performed
	 * @param keyword if "null" then a search for null is performed. 
	 * @return
	 */
    public static ArrayList<AdapterConfig> findAdapters(String adapterType, String myAddress, String keyword) {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<AdapterConfig> cmd = datastore.find().type(AdapterConfig.class);

        if (adapterType != null)
            cmd.addFilter("adapterType", FilterOperator.EQUAL, adapterType.toLowerCase());

        if (keyword != null) {
            if (keyword.equals("null")) {
                cmd.addFilter("keyword", FilterOperator.EQUAL, null);
            }
            else {
                cmd.addFilter("keyword", FilterOperator.EQUAL, keyword);
            }
        }
        QueryResultIterator<AdapterConfig> resultIterator = cmd.now();
        ArrayList<AdapterConfig> adapters = new ArrayList<AdapterConfig>();
        while (resultIterator.hasNext()) {
            AdapterConfig adapterConfig = resultIterator.next();
            if(myAddress != null) {
                if(adapterConfig.getMyAddress().equalsIgnoreCase(myAddress)) {
                    adapters.add(adapterConfig);
                }
            }
            else {
                adapters.add(adapterConfig);
            }
        }
        return adapters;
    }
	
	/**
	 * Fetches all the adapters where the accountId only matches that of the owner of the adapter. Doesnt 
	 * return if the accountId is in the shared accounts list
	 * @param accountId
	 * @param adapterType
	 * @param address
	 * @return
	 */
    public static ArrayList<AdapterConfig> findAdapterByOwner(String accountId, String adapterType, String address) {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<AdapterConfig> cmd = datastore.find().type(AdapterConfig.class);
        if (adapterType != null)
            cmd.addFilter("adapterType", FilterOperator.EQUAL, adapterType.toLowerCase());
        if (address != null)
            cmd.addFilter("address", FilterOperator.EQUAL, address.toLowerCase());
        return fetchAllAdaptersForAccount(Arrays.asList(accountId), cmd.now(), true);
    }
    
    /**
     * Fetches all the adapters where the accountId matches that of the owner of the adapter or the 
     * linked accounts in the shared accounts list. 
     * @param accountId
     * @param adapterType
     * @param address
     * @return
     */
    public static ArrayList<AdapterConfig> findAdapterByAccount(String accountId, String adapterType, String address) {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<AdapterConfig> cmd = datastore.find().type(AdapterConfig.class);

        if (adapterType != null)
            cmd.addFilter("adapterType", FilterOperator.EQUAL, adapterType.toLowerCase());

        if (address != null)
            cmd.addFilter("address", FilterOperator.EQUAL, address.toLowerCase());

        return fetchAllAdaptersForAccount(Arrays.asList(accountId), cmd.now(), false);
    }

    /**
     * this returns the AdapterConfig if it is associated with the supplied
     * accountId. Else returns null
     * 
     * @param adapterId
     * @param accountId
     * @return
     */
    public static AdapterConfig getAdapterForOwner(String adapterId, String accountId) {

        AdapterConfig adapterConfig = getAdapterConfig(adapterId);
        //check if the accountId given is either the owner or in the list of allowed users
        if (adapterConfig != null &&
            (adapterConfig.getOwner().equalsIgnoreCase(accountId) || adapterConfig.getAccounts().contains(accountId))) {
            return adapterConfig;
        }
        return null;
    }
	
    /**
     * Fetches all the adapters whose ownerId is IN the specified adapterIds
     * adapterIds
     * @param adapterIds
     * @return A list of adapters matching the given adapterIds
     */
    public static List<AdapterConfig> findAdapterByOwnerList(List<String> adapterIds) {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        RootFindCommand<AdapterConfig> cmd = datastore.find().type(AdapterConfig.class);
        cmd.addFilter("owner", FilterOperator.IN, adapterIds);
        return cmd.now().toArray();
    }
	
    public static ArrayList<AdapterConfig> findAdaptersByList(Collection<String> adapterIDs) {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        Map<String, AdapterConfig> adaptersFromDS = datastore.loadAll(AdapterConfig.class, adapterIDs);
        Iterator<AdapterConfig> config = adaptersFromDS.values().iterator();
        ArrayList<AdapterConfig> adapters = new ArrayList<AdapterConfig>();
        while (config.hasNext()) {
            AdapterConfig nextConfig = config.next();
            if (nextConfig != null) {
                adapters.add(nextConfig);
            }
        }
        return adapters;
    }
	
    /**
     * Fetches all the adapters that has the given accountId as either the owner
     * or one of the shared accounts in the {@link AdapterConfig#getAccounts()}
     * @param accountId If null, only fetches the adapters that are not linked to any 
     * accounts (free adapters). Does not fetch all the adapters.
     * @return
     */
    public static ArrayList<AdapterConfig> findAdapterByAccount(String accountId) {

        return findAdapterByAccount(accountId, null, null);
    }

	public static boolean adapterExists(String adapterType, String myAddress, String keyword) {
		ArrayList<AdapterConfig> adapters = findAdapters(adapterType,
				myAddress, keyword);
		if (adapters.size() > 0)
			return true;

		return false;
	}
	
	public static boolean updateSubscription(String configid, String subscriptionId) {
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		try {

			AdapterConfig oldConfig = datastore.load(AdapterConfig.class,
					configid);
			oldConfig.setXsiSubscription(subscriptionId);
			datastore.update(oldConfig);
			
			return true;
		} catch (Exception e) {
			log.severe("UpdateConfig: Failed to update config:"
					+ e.getMessage());
		}
		return false;
	}
	
	// TODO: use this function
	/*private boolean checkUpdateAllowed(AdapterConfig oldConfig, AdapterConfig newConfig) {
		if(!oldConfig.getMyAddress().equals(newConfig.getMyAddress()))
			return false;
		if(!oldConfig.getAdapterType().equals(newConfig.getAdapterType()))
			return false;
		if(!oldConfig.getKeyword().equals(newConfig.getKeyword()))
			return false;
		
		return true;
	}*/

	public String getPreferred_language() {
		return preferred_language;
	}

	public void setPreferred_language(String preferred_language) {
		this.preferred_language = preferred_language;
	}

	public String getConfigId() {
		return configId;
	}

	public void setConfigId(String configId) {
		this.configId = configId;
	}
	
	public String getPublicKey() {
		return publicKey;
	}
	
	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}

	public String getAdapterType() {
		return adapterType;
	}

    public void setAdapterType(String adapterType) {

        adapterType = adapterType != null ? adapterType.toLowerCase() : null;
        this.adapterType = adapterType;
    }

    /**
     * Fetchs the initialAgentURL by looking up if there is any dialogId
     * configured in {@link AdapterConfig#properties} If it is not found.
     * returns the initialAgentURL
     * @param session if not null, stores the authorization credentials in this instance
     * @return
     */
    @JsonIgnore
    public String getURLForInboundScenario(Session session) {

        if (getProperties().get(DIALOG_ID_KEY) != null) {
            try {
                if (cachedDialog == null || !cachedDialog.getId().equals(getProperties().get(DIALOG_ID_KEY).toString())) {
                    cachedDialog = Dialog.getDialog(getProperties().get(DIALOG_ID_KEY).toString(), null);
                }
                if (cachedDialog != null) {
                    Dialog.addDialogCredentialsToSession(cachedDialog, session);
                    return cachedDialog.getUrl();
                }
                else { //remove the key tag if the dialog is not found
                    setDialogId(null);
                }
            }
            catch (Exception e) {
                log.severe(String.format("Dialog fetch failed for id: %s. Error: %s", getProperties()
                                                .get(DIALOG_ID_KEY), e.toString()));
            }
        }
        return initialAgentURL;
    }
    
    /**
     * Please use the {@link AdapterConfig#getURLForInboundScenario()} to fetch the url from 
     * either the linked dialog or the initialAgentURL configured
     * @return
     * @throws Exception
     */
    public String getInitialAgentURL() {
        
        return initialAgentURL;
    }

    /**
     * Please set the {@link Dialog} id from the{@link AdapterConfig#setDialogId()}
     * @return
     * @throws Exception
     */
    public void setInitialAgentURL(String initialAgentURL) throws Exception {

        this.initialAgentURL = initialAgentURL;
    }
    
    /**
     * Gets the dialog that is linked to the owner of this adapter.
     * 
     * @return
     * @throws Exception
     */
    @JsonIgnore
    public Dialog getDialog() {

        Object dialogId = properties != null ? properties.get(DIALOG_ID_KEY) : null;
        if (dialogId != null) {
            if (cachedDialog == null || !cachedDialog.getId().equals(dialogId.toString())) {
                cachedDialog = Dialog.getDialog(dialogId.toString(), null);
            }
        }
        return cachedDialog;
    }
    
	public String getAddress() {
		return address;
	}
	
	public void setAddress(String address) {
		this.address = address;
	}

	public String getMyAddress() {
		return myAddress;
	}

	public void setMyAddress(String myAddress) {
		this.myAddress = myAddress;
	}
	
	public String getKeyword() {
		return keyword;
	}
	
	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}
	
	public void setStatus(com.askfast.commons.Status status) {
		this.status = status;
	}
	
	public com.askfast.commons.Status getStatus() {
		return status;
	}
	
	public String getXsiURL() {
		return xsiURL;
	}

	public void setXsiURL(String xsiURL) {
		this.xsiURL = xsiURL;
	}

	public String getXsiUser() {
		return xsiUser;
	}

	public void setXsiUser(String xsiUser) {
		this.xsiUser = xsiUser;
	}

	public String getXsiPasswd() {
		return xsiPasswd;
	}

	public void setXsiPasswd(String xsiPasswd) {
		this.xsiPasswd = xsiPasswd;
	}
	
	public String getXsiSubscription() {
		return xsiSubscription;
	}

	public void setXsiSubscription(String xsiSubscription) {
		this.xsiSubscription = xsiSubscription;
	}
	
	public String getAccessToken() {
		return accessToken;
	}
	
	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}
	
	public String getAccessTokenSecret() {
		return accessTokenSecret;
	}
	
	public void setAccessTokenSecret(String accessTokenSecret) {
		this.accessTokenSecret = accessTokenSecret;
	}
	
	public String getOwner() {
		return owner;
	}
	
	public void setOwner(String owner) {
		this.owner = owner != null && !owner.trim().isEmpty() ? owner : null ;
	}
	
	public Collection<String> getAccounts() {
		return accounts;
	}
	
        public void addAccount(String accountId) {
    
            accounts = accounts != null ? accounts : new ArrayList<String>();
            if (accountId != null && !accounts.contains(accountId)) {
                accounts.add(accountId);
            }
            //make the first shared account as the owner (if its missing)
            if (accounts != null && !accounts.isEmpty() && (owner == null || owner.trim().isEmpty())) {
                owner = accounts.iterator().next();
            }
        }
	
    /**
     * Removes the accountId from the shared accounts list and reset the adapter
     * to {@link com.askfast.commons.Status#INACTIVE}. If this accountId matches
     * the ownerId, makes the next shared account in the list as the owner. If
     * no accounts are found. Makes this adapter free.
     * 
     * @param accountId
     */
    public void removeAccount(String accountId) {

        if (accountId != null) {
            status = com.askfast.commons.Status.INACTIVE;
            if (accounts != null) {
                accounts.remove(accountId);
            }
            //if owner is same as this accountId, free the adapter
            if (accountId.equals(owner)) {
                owner = null;
                accounts = null;
            }
        }
    }
	
    public void setAccounts(Collection<String> accounts) {

        this.accounts = accounts;
        //make the first shared account as the owner (if its missing)
        if (accounts != null && !accounts.isEmpty() && (owner == null || owner.trim().isEmpty())) {
            owner = accounts.iterator().next();
        }
    }
	
	public Boolean isAnonymous() {
		return anonymous;
	}
	
	public void setAnonymous(Boolean anonymous) {
		this.anonymous = anonymous;
	}

    public Map<String, Object> getProperties()
    {
        properties = properties != null ? properties : new HashMap<String, Object>(); 
        return properties;
    }
    
    public <T> T getProperties(String key, TypeReference<T> type) {

        Object value = getProperties().get(key);
        if (value != null) {
            return JOM.getInstance().convertValue(value, type);
        }
        return null;
    }

    public void setProperties( Map<String, Object> properties )
    {
        this.properties = properties;
    }
    
    public static void delete( String configId )
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        AdapterConfig config = datastore.load( AdapterConfig.class, configId );
        datastore.delete( config );
    }
    
    public void delete()
    {
        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        AdapterConfig config = datastore.load( AdapterConfig.class, configId );
        if (config != null) {
            datastore.delete(config);
        }
    }

    @JsonIgnore
    public void setDialogId(String dialogId) {

        if (dialogId == null || dialogId.isEmpty()) {
            getProperties().remove(DIALOG_ID_KEY);
        }
        else 
        {
            getProperties().put(DIALOG_ID_KEY, dialogId);
        }
    }
    
    public AccountType getAccountType() {
    
        return accountType;
    }
    
    public void setAccountType(AccountType accountType) {
    
        this.accountType = accountType;
    }
    
    /**
     * Simple helper method to check if the specified adapter is linked to the
     * given accountIds. The accoutnIds must be either an owner or must overlap
     * with any accounts linked to this account.
     * 
     * @param accountIds
     * @param adapters
     * @param adapterConfig
     */
    public static AdapterConfig checkIfAdapterMatchesForAccountId(Collection<String> accountIds,
        AdapterConfig adapterConfig, boolean checkOwnerOnly) {

        if(adapterConfig == null) {
            return null;
        }
        //if accountId is null, the adapter owner and accounts must also be null or empty
        else if (accountIds == null && adapterConfig.getOwner() == null &&
            (adapterConfig.getAccounts() == null || adapterConfig.getAccounts().isEmpty())) {
            return adapterConfig;
        }
        //check if the accountId given is either the owner or in the list of allowed users
        else if (accountIds != null) {
            if (accountIds.contains(adapterConfig.getOwner())) {
                return adapterConfig;
            }
            else if (!checkOwnerOnly && adapterConfig.getAccounts() != null) {
                ArrayList<String> accountsLinked = new ArrayList<String>(adapterConfig.getAccounts());
                accountsLinked.retainAll(accountIds);
                //check if the accountId given is either the owner or in the list of allowed users
                if (accountsLinked.size() > 0) {
                    return adapterConfig;
                }
            }
        }
        return null;
    }
    
    /**
     * Simple helper method to fetch all adapters based on the accountIds
     * 
     * @param accountId
     * @param config
     * @return
     */
    private static ArrayList<AdapterConfig> fetchAllAdaptersForAccount(Collection<String> accountIds,
        Iterator<AdapterConfig> config, boolean checkOwnerOnly) {

        ArrayList<AdapterConfig> adapters = new ArrayList<AdapterConfig>();
        while (config.hasNext()) {
            AdapterConfig adapterConfig = checkIfAdapterMatchesForAccountId(accountIds, config.next(), checkOwnerOnly);
            if (adapterConfig != null) {
                adapters.add(adapterConfig);
            }
        }
        return adapters;
    }
    
    @JsonIgnore
    public boolean isCallAdapter() {

        AdapterType type = AdapterType.getByValue(adapterType);

        //check the adapter type
        if (type != null) {
            if (AdapterType.CALL.equals(type)) {
                return true;
            }
            return false;
        }
        //check if the adapterType has the provider value itself
        else if(AdapterProviders.getByValue(adapterType) != null){
            return AdapterProviders.isCallAdapter(adapterType);
        }
        //check the properties
        else if(properties != null && properties.get(ADAPTER_PROVIDER_KEY) != null){
            Object provider = properties.get(ADAPTER_PROVIDER_KEY);
            return AdapterProviders.isCallAdapter(provider.toString());
        }
        return false;
    }
    
    @JsonIgnore    
    public boolean isSMSAdapter() {

        AdapterType type = AdapterType.getByValue(adapterType);

        //check the adapter type
        if (type != null) {
            if (AdapterType.SMS.equals(type)) {
                return true;
            }
            return false;
        }
        //check if the adapterType has the provider value itself
        else if(AdapterProviders.getByValue(adapterType) != null){
            return AdapterProviders.isSMSAdapter(adapterType);
        }
        //check the properties
        else if(properties != null && properties.get(ADAPTER_PROVIDER_KEY) != null){
            Object provider = properties.get(ADAPTER_PROVIDER_KEY);
            return AdapterProviders.isSMSAdapter(provider.toString());
        }
        return false;
    }

    @JsonIgnore
    public AdapterProviders getProvider() {

        AdapterProviders providers = AdapterProviders.getByValue(adapterType);
        //check the adapter type
        if (providers == null && getProperties().get(ADAPTER_PROVIDER_KEY) != null) {
            providers = getProperties(ADAPTER_PROVIDER_KEY, new TypeReference<AdapterProviders>() {
            });
        }
        return providers;
    }
    
    public void addMediaProperties(String key, Object value) {
        properties = properties != null ? properties : new HashMap<String, Object>();
        properties.put(key, value);
    }
}
