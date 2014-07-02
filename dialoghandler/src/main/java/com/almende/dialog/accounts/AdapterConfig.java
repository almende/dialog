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
import com.almende.dialog.util.TimeUtils;
import com.almende.util.twigmongo.FilterOperator;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore.RootFindCommand;
import com.almende.util.twigmongo.annotations.Id;
import com.almende.util.uuid.UUID;
import com.askfast.commons.entity.AccountType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;


@Path("/adapters")
public class AdapterConfig {
	static final Logger log = Logger.getLogger(AdapterConfig.class.getName());
	static final ObjectMapper om = new ObjectMapper();
	public static final String ADAPTER_CREATION_TIME_KEY = "ADAPTER_CREATION_TIME";
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
	String status = "";
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
	List<String> accounts=null;
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
            newConfig.status = "OPEN";

            newConfig = om.readerForUpdating(newConfig).readValue(json);
            if (adapterExists(newConfig.getAdapterType(), newConfig.getMyAddress(), newConfig.getKeyword())) {
                return Response.status(Status.CONFLICT).build();
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

            if (newConfig.getAdapterType().equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_BROADSOFT)) {
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
		datastore.update(this);
        if ( this.getAdapterType().equalsIgnoreCase( AdapterAgent.ADAPTER_TYPE_BROADSOFT ) )
        {
            Broadsoft bs = new Broadsoft( this );
            bs.hideCallerId( this.isAnonymous() );
        }
	}
	
	public static AdapterConfig getAdapterConfig(String adapterID) {
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		return datastore.load(AdapterConfig.class, adapterID);
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

	public static AdapterConfig findAdapterConfig(String adapterType,
			String lookupKey) {
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		Iterator<AdapterConfig> config = datastore.find()
				.type(AdapterConfig.class)
				.addFilter("myAddress", FilterOperator.EQUAL, lookupKey)
				.addFilter("adapterType", FilterOperator.EQUAL, adapterType)
				.now();
		if (config.hasNext()) {
			return config.next();
		}
		log.severe("AdapterConfig not found:'" + adapterType + "':'"
				+ lookupKey + "'");
		return null;
	}
	
	public static AdapterConfig findAdapterConfig(String adapterType,
			String lookupKey, String keyword) {
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		Iterator<AdapterConfig> config = datastore.find()
				.type(AdapterConfig.class)
				.addFilter("myAddress", FilterOperator.EQUAL, lookupKey)
				.addFilter("adapterType", FilterOperator.EQUAL, adapterType)
				.addFilter("keyword", FilterOperator.EQUAL, keyword)
				.now();
		if (config.hasNext()) {
			return config.next();
		}
		log.severe("AdapterConfig not found:'" + adapterType + "':'"
				+ lookupKey + "':'"
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

	public static ArrayList<AdapterConfig> findAdapters(String adapterType,
			String myAddress, String keyword) {
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		
		RootFindCommand<AdapterConfig> cmd = datastore.find().type(
				AdapterConfig.class);

		if (adapterType != null)
			cmd.addFilter("adapterType", FilterOperator.EQUAL, adapterType);

		if (myAddress != null)
			cmd.addFilter("myAddress", FilterOperator.EQUAL, myAddress);
		
		if (keyword != null) {
			if(keyword.equals("null")) {
				cmd.addFilter("keyword", FilterOperator.EQUAL, null);
			} else {
				cmd.addFilter("keyword", FilterOperator.EQUAL, keyword);
			}
		}

		Iterator<AdapterConfig> config = cmd.now();

		ArrayList<AdapterConfig> adapters = new ArrayList<AdapterConfig>();
		while (config.hasNext()) {
			adapters.add(config.next());
		}

		return adapters;
	}
	
	public static ArrayList<AdapterConfig> findAdapterByOwner(String owner,
															String adapterType,
															String address) {
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		
		RootFindCommand<AdapterConfig> cmd = datastore.find().type(
				AdapterConfig.class);

		cmd.addFilter("owner", FilterOperator.EQUAL, owner);
		
		if (adapterType != null)
			cmd.addFilter("adapterType", FilterOperator.EQUAL, adapterType.toLowerCase());

		if (address != null)
			cmd.addFilter("address", FilterOperator.EQUAL, address.toLowerCase());

		Iterator<AdapterConfig> config = cmd.now();

		ArrayList<AdapterConfig> adapters = new ArrayList<AdapterConfig>();
		while (config.hasNext()) {
			adapters.add(config.next());
		}

		return adapters;
	}
	
	/**
	 * this returns the AdapterConfig if it is associated with the supplied ownerId.
	 * Else returns null
	 * @param adapterId
	 * @param ownerId
	 * @return
	 */
	public static AdapterConfig getAdapterForOwner(String adapterId, String ownerId)
	{
	    AdapterConfig adapterConfig = getAdapterConfig( adapterId );
	    if(adapterConfig != null && adapterConfig.getOwner().equalsIgnoreCase( ownerId ))
	    {
	        return adapterConfig;
	    }
	    return null;
	}
	
	public static ArrayList<AdapterConfig> findAdapterByOwnerList(List<String> owners) {
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		
		RootFindCommand<AdapterConfig> cmd = datastore.find().type(
				AdapterConfig.class);

		cmd.addFilter("owner", FilterOperator.IN, owners);

		Iterator<AdapterConfig> config = cmd.now();

		ArrayList<AdapterConfig> adapters = new ArrayList<AdapterConfig>();
		while (config.hasNext()) {
			adapters.add(config.next());
		}

		return adapters;
	}
	
	public static ArrayList<AdapterConfig> findAdaptersByList(Collection<String> adapterIDs)
	{
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		Map<String, AdapterConfig> adaptersFromDS = datastore.loadAll(AdapterConfig.class, adapterIDs);
	    Iterator<AdapterConfig> config = adaptersFromDS.values().iterator();
        ArrayList<AdapterConfig> adapters = new ArrayList<AdapterConfig>();
        while (config.hasNext()) 
        {
            AdapterConfig nextConfig = config.next();
            if(nextConfig != null)
            {
                adapters.add(nextConfig);
            }
        }
        return adapters;
	}
	
	public static ArrayList<AdapterConfig> findAdapterByAccount(String accountId) {
		
		TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
		RootFindCommand<AdapterConfig> cmd = datastore.find().type(
				AdapterConfig.class);

		if (accountId != null) {
			cmd.addFilter("accounts", FilterOperator.IN, Arrays.asList(accountId));
		}

		Iterator<AdapterConfig> config = cmd.now();

		ArrayList<AdapterConfig> adapters = new ArrayList<AdapterConfig>();
		while (config.hasNext()) {
			adapters.add(config.next());
		}

		return adapters;
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
		this.adapterType = adapterType;
	}

    /**
     * Fetchs the initialAgentURL by looking up if there is any dialogId
     * configured in {@link AdapterConfig#properties} If it is not found.
     * returns the initialAgentURL
     * 
     * @return
     */
    @JsonIgnore
    public String getURLForInboundScenario() {

        if (getProperties().get(DIALOG_ID_KEY) != null) {
            try {
                if (cachedDialog == null || !cachedDialog.getId().equals(getProperties().get(DIALOG_ID_KEY).toString())) {
                    cachedDialog = Dialog.getDialog(getProperties().get(DIALOG_ID_KEY).toString(), null);
                }
                if (cachedDialog != null) {
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
	
	public void setStatus(String status) {
		this.status = status;
	}
	
	public String getStatus() {
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
	
	public List<String> getAccounts() {
		return accounts;
	}
	
	public void addAccount(String accountId) {
		if(accountId!=null && !accounts.contains( accountId )){
			accounts.add(accountId);
		}
	}
	
	public void removeAccount(String accountId) {
		int idx = accounts.indexOf( accountId );
		if(accountId!=null && idx!=-1) {
			accounts.remove(idx);
		}
	}
	
	public void setAccounts(List<String> accounts) {
		this.accounts = accounts;
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

    public void setProperties( Map<String, Object> extras )
    {
        this.properties = extras;
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
        datastore.delete( config );
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
}
