package com.almende.dialog.util;

import java.util.logging.Logger;

import javax.ws.rs.core.MultivaluedMap;

import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.util.ParallelInit;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

public class KeyServerLib {
	
	static final Logger	log	= Logger.getLogger(AdapterConfig.class.getName());
	
	public static boolean checkAccount(String accountId, String bearerToken) {
		Client client = ParallelInit.getClient();
		WebResource webResource = client.resource(Settings.KEYSERVER);
		MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
		queryParams.add("bearer", bearerToken);
		queryParams.add("accountId", accountId);
		ClientResponse response = webResource.queryParams(queryParams).get(
				ClientResponse.class);
		int status = response.getStatus();
		if (status != 200) {
			return false;
		}
		return true;
	};
}
