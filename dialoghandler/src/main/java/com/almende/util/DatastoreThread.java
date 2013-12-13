package com.almende.util;

// import java.util.Date;
// import java.util.logging.Logger;

import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.mongodb.MongoClient;

public class DatastoreThread extends Thread implements java.lang.Runnable {
	private static final Logger	LOG	= Logger.getLogger("DialogHandler");
	
	@Override
	public void run() {
		MongoClient mongo = null;
		try {
			mongo = new MongoClient("localhost", 27017);
		} catch (UnknownHostException e) {
			LOG.log(Level.SEVERE, "Couldn't find mongoDB host!", e);
		}
		if (mongo != null) {
			ParallelInit.datastore = mongo.getDB("dialog");
			ParallelInit.datastoreActive = true;
		}
		// log.warning("DatastoreThread took: "+(new
		// Date().getTime()-ParallelInit.startTime)+" ms");
	}
}
