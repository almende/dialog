package com.almende.util;

//import java.util.Date;
//import java.util.logging.Logger;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.config.ClientConfig;
import com.sun.jersey.api.client.config.DefaultClientConfig;

public class ClientConThread extends Thread {
//	private static final Logger log = Logger
//			.getLogger("DialogHandler");
	@Override
	public void run() {
		ClientConfig cc = new DefaultClientConfig();
		cc.getProperties().put(ClientConfig.PROPERTY_THREADPOOL_SIZE,10);
		ParallelInit.client = Client.create(cc);
		ParallelInit.client.setConnectTimeout(1000);
		ParallelInit.client.setReadTimeout(30000);
		ParallelInit.clientActive = true;
//		log.warning("ClientConThread took: "+(new Date().getTime()-ParallelInit.startTime)+" ms");
	}
}
