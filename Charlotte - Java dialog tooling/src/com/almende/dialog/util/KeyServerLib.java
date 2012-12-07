package com.almende.dialog.util;

import java.util.logging.Logger;

import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

public class KeyServerLib {

	static final Logger log = Logger.getLogger(AdapterConfig.class.getName());
	
	public static ArrayNode getAllowedAdapterList(String pubKey, String privKey, String adapterType) {
		
		String path="/askAnywaysServices/rest/keys/checkkey/"+pubKey+"/"+privKey+"/outbound";
		
		String res="";
		try {
			Client client = ParallelInit.getClient();
			WebResource webResource = client.resource(Settings.KEYSERVER+path);
			res = webResource.get(String.class);
		} catch(Exception ex) {
			log.warning(ex.getMessage());
			log.warning("No response from keyserver so no validation");
		}
		
		ObjectMapper om = ParallelInit.getObjectMapper();
		try {
			JsonNode result = om.readValue(res, JsonNode.class);
			if(!result.get("valid").asBoolean())
				return null;
			
			return (ArrayNode) result.get("adapters");
			
		} catch(Exception ex) {
			log.warning("Unable to parse result");
		}
		
		return null;
	}
	
	public static boolean checkCredits(String pubKey) {
		
		if(pubKey==null || pubKey.equals(""))
			return false;
		
		String path = "/askAnywaysServices/rest/keys/checkkey/"+pubKey+"/inbound";
		String res="";
		try {
			Client client = ParallelInit.getClient();
			WebResource webResource = client.resource(Settings.KEYSERVER+path);
			res = webResource.get(String.class);
		} catch(Exception ex) {
			log.warning(ex.getMessage());
			log.warning("No response from keyserver so no validation");
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
