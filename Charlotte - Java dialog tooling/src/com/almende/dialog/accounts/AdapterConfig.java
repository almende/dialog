package com.almende.dialog.accounts;

import java.util.Iterator;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.eaio.uuid.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.code.twig.annotation.AnnotationObjectDatastore;
import com.google.code.twig.annotation.Id;
import static com.google.appengine.api.datastore.Query.FilterOperator.*;

@Path("/accounts/{accountid}/adapters")
public class AdapterConfig {
	static final Logger log = Logger.getLogger(AdapterConfig.class.getName());
	static final ObjectMapper om = new ObjectMapper();

	@Id String configId;
	String account;
	String adapterType = "";
	String preferred_language = "nl";
	String initialAgentURL = "";
	String myAddress = "";
	
	//Broadsoft:
	String xsiURL = "";
	String xsiUser = "";
	String xsiPasswd = "";
	
	public AdapterConfig(){};
	
	@POST
	@Consumes("application/json")
	@Produces("application/json")
	@JsonIgnore
	public Response createConfig(@PathParam("accountid") String accountid, String json){
		AnnotationObjectDatastore datastore  = new AnnotationObjectDatastore();
		try {
			AdapterConfig newConfig = new AdapterConfig();
			newConfig.configId = new UUID().toString();
			newConfig.account = new UUID(accountid).toString();
			
			om.readerForUpdating(newConfig).readValue(json);
			datastore.store(newConfig);
			return Response.ok(om.writeValueAsString(newConfig)).build();
		} catch (Exception e){
			log.severe("CreateConfig: Failed to store new config");
		}
		return Response.status(Status.BAD_REQUEST).build();
	}
	@PUT
	@Path("{uuid}")
	@Consumes("application/json")
	@Produces("application/json")
	@JsonIgnore
	public Response updateConfig(@PathParam("accountid") String accountid, @PathParam("uuid") String configid, String json){
		AnnotationObjectDatastore datastore  = new AnnotationObjectDatastore();
		try {
			
			AdapterConfig oldConfig = datastore.load(AdapterConfig.class,configid);
			om.readerForUpdating(oldConfig).readValue(json);
			datastore.update(oldConfig);
			return Response.ok(om.writeValueAsString(oldConfig)).build();
		} catch (Exception e){
			log.severe("UpdateConfig: Failed to update config:"+e.getMessage());
		}
		return Response.status(Status.BAD_REQUEST).build();
	}
	
	@GET
	@Path("{uuid}")
	@Produces("application/json")
	@JsonIgnore
	public Response getConfig(@PathParam("accountid") String accountid, @PathParam("uuid") String configid, String json){
		AnnotationObjectDatastore datastore  = new AnnotationObjectDatastore();
		try {
			AdapterConfig config = datastore.load(AdapterConfig.class,configid);
			return Response.ok(om.writeValueAsString(config)).build();
		} catch (Exception e){
			log.severe("getConfig: Failed to read config");
		}
		return Response.status(Status.BAD_REQUEST).build();
	}		
	public static AdapterConfig findAdapterConfig(String adapterType, String lookupKey ){
//		log.warning("Looking for config:'"+adapterType+"':'"+lookupKey+"'");
		AnnotationObjectDatastore datastore  = new AnnotationObjectDatastore();
		Iterator<AdapterConfig> config = datastore.find().type(AdapterConfig.class)
				.addFilter("myAddress",FilterOperator.EQUAL,lookupKey)
				.addFilter("adapterType",FilterOperator.EQUAL,adapterType)
				.now();
		if (config.hasNext()){
			return config.next();
		}
		log.severe("AdapterConfig not found:'"+adapterType+"':'"+lookupKey+"'");
		return null;
	}
	public static AdapterConfig findAdapterConfigForAccount(String adapterType, String accountid ){
		AnnotationObjectDatastore datastore  = new AnnotationObjectDatastore();
		
		Iterator<AdapterConfig> config = datastore.find().type(AdapterConfig.class)
				.addFilter("adapterType",EQUAL,adapterType).addFilter("account",EQUAL,accountid).now();
		if (config.hasNext()){
			return config.next();
		}
		//generate default config?
		log.severe("findAdapterConfig: Couldn't find adapterConfig: "+adapterType+"|"+accountid);
		return null;
	}
	
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

	public String getAccount() {
		return account;
	}

	public void setAccount(String account) {
		this.account = account;
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
}
