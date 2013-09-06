package com.almende.dialog;

import static com.google.appengine.api.datastore.Query.FilterOperator.EQUAL;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.code.twig.FindCommand.RootFindCommand;
import com.google.code.twig.annotation.AnnotationObjectDatastore;

public class Logger {
	private AnnotationObjectDatastore datastore = null;
	
	public Logger() {
		datastore = new AnnotationObjectDatastore();
	}
	
	public void severe(String adapterID, String message) {
		this.log(LogLevel.SEVERE,adapterID,null,message);
	}

	public void warning(String adapterID, String message) {
		this.log(LogLevel.WARNING,adapterID,null,message);
	}

	public void info(String adapterID, String message) {
		this.log(LogLevel.INFO,adapterID,null,message);
	}

	public void debug(String adapterID, String message) {
		this.log(LogLevel.DEBUG,adapterID,null,message);
	}
	
	public void log(LogLevel level, String adapterID, String message) {
		this.log(level,adapterID,null,message);
	}
	
	public void log(LogLevel level, String adapterID, String adapterType, String message) {
		datastore.store(new Log(level, adapterID, adapterType, message));
	}
	
	public List<Log> find(List<String> adapters, LogLevel level, String adapterType, Long endTime, Integer offset, Integer limit) {

		RootFindCommand<Log> cmd = datastore.find().type(
				Log.class);
		
		if(adapters!=null) {
			for(String id : adapters) {
				cmd.addFilter("adapterID", EQUAL, id);
			}
		}

		if (level != null)
			cmd.addFilter("level", EQUAL, level);
		
		if (adapterType != null)
			cmd.addFilter("adapterType", EQUAL, adapterType);
		
		if(endTime!=null) {
			cmd.addFilter("timestamp", FilterOperator.LESS_THAN_OR_EQUAL, endTime);
		}
		
		if(limit==null)
			limit=50;
		
		cmd.fetchMaximum(limit);
		cmd.addSort("timestamp", SortDirection.DESCENDING);
		
		Iterator<Log> log = cmd.now();

		ArrayList<Log> logs = new ArrayList<Log>();
		while (log.hasNext()) {
			logs.add(log.next());
		}

		return logs;
	}
}
