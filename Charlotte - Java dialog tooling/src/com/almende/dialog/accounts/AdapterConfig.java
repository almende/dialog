package com.almende.dialog.accounts;

import static com.google.appengine.api.datastore.Query.FilterOperator.EQUAL;

import java.util.ArrayList;
import java.util.Iterator;
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
import com.eaio.uuid.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.code.twig.FindCommand.RootFindCommand;
import com.google.code.twig.annotation.AnnotationObjectDatastore;
import com.google.code.twig.annotation.Id;

import flexjson.JSONException;

@Path("/adapters")
public class AdapterConfig {
	static final Logger log = Logger.getLogger(AdapterConfig.class.getName());
	static final ObjectMapper om = new ObjectMapper();

	@Id
	String configId;
	String publicKey="";
	String adapterType = "";
	String preferred_language = "nl";
	String initialAgentURL = "";
	String myAddress = "";
	String keyword = null;
	String status = "";
	// Broadsoft:
	String xsiURL = "";
	String xsiUser = "";
	String xsiPasswd = "";
	String xsiSubscription = "";
	//OAuth
	String accessToken="";
	String accessTokenSecret="";
	boolean anonymous=false;

	public AdapterConfig() {
	};

	@POST
	@Consumes("application/json")
	@Produces("application/json")
	@JsonIgnore
	public Response createConfig(String json) {
		AnnotationObjectDatastore datastore = new AnnotationObjectDatastore();
		try {
			AdapterConfig newConfig = new AdapterConfig();
			newConfig.status = "OPEN";

			newConfig = om.readerForUpdating(newConfig).readValue(json);
			if (adapterExists(newConfig.getAdapterType(),
					newConfig.getMyAddress(), newConfig.getKeyword()))
				return Response.status(Status.CONFLICT).build();
			if(configId == null)
			{
			    newConfig.configId = new UUID().toString();
			}
			newConfig.setMyAddress( newConfig.getMyAddress() != null ? newConfig.getMyAddress().toLowerCase() 
			                                                                     : null );
			datastore.store(newConfig);
			
			if(newConfig.getAdapterType().equals("broadsoft")) {
				Broadsoft bs = new Broadsoft(newConfig);
				bs.hideCallerId(newConfig.isAnonymous());
			}
			
			return Response.ok(om.writeValueAsString(newConfig)).build();
		} catch (Exception e) {
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
		AnnotationObjectDatastore datastore = new AnnotationObjectDatastore();
		try {

			AdapterConfig oldConfig = datastore.load(AdapterConfig.class,
					configid);
			om.readerForUpdating(oldConfig).readValue(json);
			// TODO Check if fields myAddress, type and keyword have not been changed.
			datastore.update(oldConfig);
			
			if(oldConfig.getAdapterType().equals("broadsoft")) {
				Broadsoft bs = new Broadsoft(oldConfig);
				bs.hideCallerId(oldConfig.isAnonymous());
			}
			
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
		}
		return Response.status(Status.BAD_REQUEST).build();
	}

	@DELETE
	@Path("{uuid}")
	@Produces("application/json")
	@JsonIgnore
	public Response deleteConfig(@PathParam("uuid") String configid, String json) {
		AnnotationObjectDatastore datastore = new AnnotationObjectDatastore();
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
		}
		return Response.status(Status.BAD_REQUEST).build();
	}
	
	public static AdapterConfig getAdapterConfig(String adapterID) {
		AnnotationObjectDatastore datastore = new AnnotationObjectDatastore();
		return datastore.load(AdapterConfig.class, adapterID);
	}
	
	public static AdapterConfig findAdapterConfigFromList(String adapterID, String type, ArrayNode adapters) throws JSONException {
		
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
		
		AnnotationObjectDatastore datastore = new AnnotationObjectDatastore();
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

	public static AdapterConfig findAdapterConfig(String adapterType,
			String lookupKey) {
		AnnotationObjectDatastore datastore = new AnnotationObjectDatastore();
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
		AnnotationObjectDatastore datastore = new AnnotationObjectDatastore();
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
		AnnotationObjectDatastore datastore = new AnnotationObjectDatastore();
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
		AnnotationObjectDatastore datastore = new AnnotationObjectDatastore();

		RootFindCommand<AdapterConfig> cmd = datastore.find().type(
				AdapterConfig.class);

		if (adapterType != null)
			cmd.addFilter("adapterType", EQUAL, adapterType);

		if (myAddress != null)
			cmd.addFilter("myAddress", EQUAL, myAddress);
		
		if (keyword != null) {
			if(keyword.equals("null")) {
				cmd.addFilter("keyword", EQUAL, null);
			} else {
				cmd.addFilter("keyword", EQUAL, keyword);
			}
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
		AnnotationObjectDatastore datastore = new AnnotationObjectDatastore();
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

	public String getInitialAgentURL() {
		return initialAgentURL;
	}

	public void setInitialAgentURL(String initialAgentURL) {
		this.initialAgentURL = initialAgentURL;
	}

	public String getMyAddress() {
		return myAddress;
	}

	public void setMyAddress(String myAddress) {
		this.myAddress = myAddress;
	}
	
	@JsonIgnore
	public String getKeyword() {
		return keyword;
	}
	
	@JsonProperty
	public void setKeyword(String keyword) {
		this.keyword = keyword;
	}
	
	public void setStatus(String status) {
		this.status = status;
	}
	
	public String getStatus() {
		return status;
	}
	
	@JsonIgnore
	public String getXsiURL() {
		return xsiURL;
	}

	@JsonProperty
	public void setXsiURL(String xsiURL) {
		this.xsiURL = xsiURL;
	}

	public String getXsiUser() {
		return xsiUser;
	}

	public void setXsiUser(String xsiUser) {
		this.xsiUser = xsiUser;
	}

	@JsonIgnore
	public String getXsiPasswd() {
		return xsiPasswd;
	}

	@JsonProperty
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
	
	@JsonIgnore
	public String getAccessTokenSecret() {
		return accessTokenSecret;
	}
	
	@JsonProperty
	public void setAccessTokenSecret(String accessTokenSecret) {
		this.accessTokenSecret = accessTokenSecret;
	}
	
	public boolean isAnonymous() {
		return anonymous;
	}
	
	public void setAnonymous(boolean anonymous) {
		this.anonymous = anonymous;
	}
}
