package com.almende.util;

import com.almende.dialog.aws.AWSClient;
import com.almende.dialog.db.MongoManager;
import com.almende.dialog.util.AFHttpClient;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.DB;
import com.sun.jersey.api.client.Client;

public class ParallelInit {
	public static Boolean loadingRequest=true;
	
	public static Client client = null;
	public static boolean clientActive = false;
	public static boolean isTest = false;
	public static Thread conThread = new ClientConThread();
	
	public static AFHttpClient afhttpClient = null;
	public static boolean afclientActive = false;
	public static Thread afconThread = new AFHttpClientConThread();

	public static DB datastore = null;
	public static boolean datastoreActive = false;
	public static Thread datastoreThread = new DatastoreThread();
	
	public static MongoManager mm = null;
	public static boolean mongoActive = false;
	public static Thread mongoThread = new MongoThread();
	
	public static AWSClient awsClient = null;
        public static boolean awsClientActive = false;
        public static Thread awsThread = new AWSThread();
	
	public static ObjectMapper om = new ObjectMapper();
	
	public ParallelInit(boolean isTest)
        {
	    ParallelInit.isTest = isTest;
	    datastoreThread = new DatastoreThread( isTest );
        }
	
	public static boolean startThreads(){
		synchronized(conThread){
			if (!clientActive && !conThread.isAlive()) conThread.start();
		}
		synchronized(afconThread){
                    if (!afclientActive && !afconThread.isAlive()) afconThread.start();
                }
		synchronized(datastoreThread){
			if (!datastoreActive && !datastoreThread.isAlive()) datastoreThread.start();
		}
		synchronized(mongoThread){
                    if (!mongoActive && !mongoThread.isAlive()) mongoThread.start();
                }
		synchronized(awsThread) {
		    if (!awsClientActive && !awsThread.isAlive()) awsThread.start();
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
	
	public static AFHttpClient getAFHttpClient(){
            startThreads();
            while (!afclientActive){
                    try {
                            Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
            }
            afhttpClient.flushCredentials();
            return afhttpClient;
        }
	
	public static DB getDatastore(){
		startThreads();
		while (!datastoreActive){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
			}
		}
		return datastore;
	}
	
	public static MongoManager getMongoManager(){
            startThreads();
            while (!mongoActive){
                    try {
                            Thread.sleep(100);
                    } catch (InterruptedException e) {
                    }
            }
            return mm;
        }
	
	public static AWSClient getAWSClient() {
	    startThreads();
	    while(!awsClientActive) {
	        try {
                    Thread.sleep(100);
                }  catch (InterruptedException e) {
                }  
	    }
	    return awsClient;
	}
	
	public static ObjectMapper getObjectMapper(){
		startThreads();
		om.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
		return om;
	}
}


