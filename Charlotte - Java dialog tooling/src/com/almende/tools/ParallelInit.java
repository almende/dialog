package com.almende.tools;


import java.util.Date;
import java.util.logging.Logger;

import com.google.appengine.api.ThreadManager;
import com.google.appengine.api.datastore.DatastoreService;
import com.sun.jersey.api.client.Client;


public class ParallelInit {
//	private static final Logger log = Logger
//			.getLogger("DialogHandler");
	
//	public static long startTime = new Date().getTime();
	public static Client client = null;
	public static boolean clientActive = false;
	public static Thread conThread = ThreadManager.createThreadForCurrentRequest(new ClientConThread());

	public static DatastoreService datastore = null;
	public static boolean datastoreActive = false;
	public static Thread datastoreThread = ThreadManager.createThreadForCurrentRequest(new DatastoreThread());
	
	public static void startThreads(){
//		log.warning("Starting threads: "+startTime+"/"+new Date().getTime());
		synchronized(conThread){
			if (!clientActive && !conThread.isAlive()) conThread.start();
		}
		synchronized(datastoreThread){
			if (!datastoreActive && !datastoreThread.isAlive()) datastoreThread.start();
		}
//		log.warning("Done starting threads: "+new Date().getTime());
	}
	public static Client getClient(){
//		long start= new Date().getTime();
//		boolean slept = false;
		while (!clientActive){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
//			slept=true;
		}
//		if (slept) log.warning("GetClient slept for: "+(new Date().getTime()-start)+" ms");
		return client;
	}
	public static DatastoreService getDatastore(){
//		long start= new Date().getTime();
//		boolean slept = false;
		while (!datastoreActive){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
//			slept=true;
		}
//		if (slept) log.warning("GetDatastore slept for: "+(new Date().getTime()-start)+" ms");
		return datastore;
	}
}


