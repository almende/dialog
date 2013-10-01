package com.almende.dialog.util;

import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

import java.util.logging.Logger;

public class KeyServerLib {

	static final Logger log = Logger.getLogger(AdapterConfig.class.getName());
	
	public static ArrayNode getAllowedAdapterList(String pubKey, String privKey) {
		return getAllowedAdapterList(pubKey, privKey, null);
	}
	
	public static ArrayNode getAllowedAdapterList(String pubKey, String privKey, String adapterType) {
		
		ObjectMapper om = ParallelInit.getObjectMapper();
		ArrayNode adapterList = om.createArrayNode();
		if(Settings.KEYSERVER==null)
			return adapterList;
		
		String path="/rest/keys/checkkey/"+pubKey+"/"+privKey+"/outbound";
		String res="";

        try {
			Client client = ParallelInit.getClient();
			WebResource webResource = client.resource(Settings.KEYSERVER+path);
			res = webResource.get(String.class);
		} catch(Exception ex) {
			log.warning(ex.getMessage());
			log.warning("No response from keyserver so no validation");
			return adapterList;
		}
		
		try {
			JsonNode result = om.readValue(res, JsonNode.class);
			if(!result.get("valid").asBoolean())
				return null;
			if(adapterType==null) {
				adapterList = (ArrayNode) result.get("adapters");
			} else {
				for(JsonNode adapter : (ArrayNode) result.get("adapters")){
					if(adapter.get("adapterType").asText().equalsIgnoreCase(adapterType))
						adapterList.add(adapter);
				}
			}
			return adapterList;
			
		} catch(Exception ex) {
			log.warning("Unable to parse result");
		}
		
		return null;
	}
	
	public static boolean checkCredits(String pubKey) {
		
		if(pubKey==null || pubKey.equals(""))
			return true;
		
		String path = "/rest/keys/checkkey/"+pubKey+"/inbound";
		String res="";
		try {
			Client client = ParallelInit.getClient();
			WebResource webResource = client.resource(Settings.KEYSERVER+path);
			res = webResource.get(String.class);
		} catch(Exception ex) {
			log.warning(ex.getMessage());
			log.warning("No response from keyserver so no validation");
			return true;
		}
		
		ObjectMapper om = ParallelInit.getObjectMapper();
		try {
			JsonNode result = om.readValue(res, JsonNode.class);
			return result.get("valid").asBoolean();
			
		} catch(Exception ex) {
			log.warning("Unable to parse result");
		}
		
		return false;
	}
}
