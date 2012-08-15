package com.almende.util;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.appengine.api.ThreadManager;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.log.LogService;
import com.sun.jersey.api.client.Client;


public class ParallelInit {
	public static Boolean loadingRequest=true;
	
	public static Client client = null;
	public static boolean clientActive = false;
	public static Thread conThread = ThreadManager.createThreadForCurrentRequest(new ClientConThread());

	public static DatastoreService datastore = null;
	public static boolean datastoreActive = false;
	public static Thread datastoreThread = ThreadManager.createThreadForCurrentRequest(new DatastoreThread());
	
	public static LogService logService = null;
	public static boolean logServiceActive = false;
	public static Thread logServiceThread = ThreadManager.createThreadForCurrentRequest(new LogServiceThread());
	
	public static ObjectMapper om = new ObjectMapper();
	
	public static boolean startThreads(){
		synchronized(conThread){
			if (!clientActive && !conThread.isAlive()) conThread.start();
		}
		synchronized(logServiceThread){
			if (!logServiceActive && !logServiceThread.isAlive()) logServiceThread.start();
		}
		synchronized(datastoreThread){
			if (!datastoreActive && !datastoreThread.isAlive()) datastoreThread.start();
		}
		synchronized(loadingRequest){
			if (loadingRequest) {
				loadingRequest=false;
				return true;
			}
		}
		return false;
	}
	public static Client getClient(){
		startThreads();
		while (!clientActive){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
		return client;
	}
	public static DatastoreService getDatastore(){
		startThreads();
		while (!datastoreActive){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
		return datastore;
	}
	public static ObjectMapper getObjectMapper(){
		startThreads();
		om.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
		return om;
	}
	public static LogService getLogService(){
		startThreads();
		while (!logServiceActive){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e){
			}
		}
		return logService;
	}
}


