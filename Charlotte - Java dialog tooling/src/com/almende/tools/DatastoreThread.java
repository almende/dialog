package com.almende.tools;

//import java.util.Date;
//import java.util.logging.Logger;

import com.google.appengine.api.datastore.DatastoreServiceFactory;

public class DatastoreThread implements java.lang.Runnable {
//	private static final Logger log = Logger
//			.getLogger("DialogHandler");
	@Override
	public void run() {
		ParallelInit.datastore = DatastoreServiceFactory
				.getDatastoreService();
		ParallelInit.datastoreActive =true;
//		log.warning("DatastoreThread took: "+(new Date().getTime()-ParallelInit.startTime)+" ms");
	}
}
