package com.almende.dialog.accounts;

import java.util.Iterator;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.eaio.uuid.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
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

	public String getMyJID() {
		return myJID;
	}

	public void setMyJID(String myJID) {
		this.myJID = myJID;
	}
	String initialAgentURL = "";
	
	
	//TODO: will be moved to subclasses? Or in some form of hashmap?
	//XMPP:
	String myJID = "";
	
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
			
			AdapterConfig postConfig = om.readValue(json, AdapterConfig.class);
			if (!postConfig.adapterType.equals("")){
				newConfig.adapterType = postConfig.adapterType;
			}
			if (newConfig.adapterType.equals("XMPP") && !postConfig.myJID.equals("")){
				newConfig.myJID = postConfig.myJID;
			}
			if (!postConfig.preferred_language.equals("nl")){
				newConfig.preferred_language=postConfig.preferred_language;
			}
			datastore.store(newConfig);
			return Response.ok(om.writeValueAsString(newConfig)).build();
		} catch (Exception e){
			log.severe("CreateConfig: Failed to store new config");
		}
		return Response.status(Status.BAD_REQUEST).build();
	}
	public static AdapterConfig findAdapterConfig(String adapterType, String lookupKey ){
		AnnotationObjectDatastore datastore  = new AnnotationObjectDatastore();
		//TODO: make this more generic!
		if (adapterType.equals("XMPP")){
			Iterator<AdapterConfig> config = datastore.find(AdapterConfig.class,"myJID",lookupKey);
			if (config.hasNext()){
				return config.next();
			}
			//generate default config?
			log.severe("findAdapterConfig: Couldn't find adapterConfig: "+adapterType+"|"+lookupKey);
		} else {
			log.severe("findAdapterConfig: Unknown adapterType given:"+adapterType);
		}
		
		return null;
	}
	public static AdapterConfig findAdapterConfigForAccount(String adapterType, Account account ){
		AnnotationObjectDatastore datastore  = new AnnotationObjectDatastore();
		
		Iterator<AdapterConfig> config = datastore.find().type(AdapterConfig.class)
				.addFilter("type",EQUAL,adapterType).addFilter("acount",EQUAL,account.getId()).now();
		if (config.hasNext()){
			return config.next();
		}
		//generate default config?
		log.severe("findAdapterConfig: Couldn't find adapterConfig: "+adapterType+"|"+account.getId());
		return null;
	}
	
	
}
