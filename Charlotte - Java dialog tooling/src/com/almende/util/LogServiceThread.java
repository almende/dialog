package com.almende.util;

//import java.util.Date;
//import java.util.logging.Logger;

import com.google.appengine.api.log.LogServiceFactory;

public class LogServiceThread implements java.lang.Runnable {
//	private static final Logger log = Logger
//			.getLogger("DialogHandler");
	@Override
	public void run() {
		ParallelInit.logService = LogServiceFactory.getLogService();
		ParallelInit.logServiceActive =true;
//		log.warning("DatastoreThread took: "+(new Date().getTime()-ParallelInit.startTime)+" ms");
	}
}
