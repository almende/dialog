package com.almende.dialog.model;


import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

public class ClientCon {
	private static class SingletonHandler {
		static Client client = startClient();
		private static Client startClient(){
			
			ClientConfig cc = new DefaultClientConfig();
			cc.getProperties().put(ClientConfig.PROPERTY_THREADPOOL_SIZE,10);
			Client client = Client.create(cc);
			client.setConnectTimeout(1000);
			client.setReadTimeout(10000);
			return client;
		}
	}
	
	public static Client getClient(){
		return SingletonHandler.client;
	}
}
