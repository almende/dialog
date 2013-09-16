package com.almende.dialog;

import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.code.twig.FindCommand.RootFindCommand;
import com.google.code.twig.annotation.AnnotationObjectDatastore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static com.google.appengine.api.datastore.Query.FilterOperator.EQUAL;

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
	
	public List<Log> find(List<String> adapters, Collection<LogLevel> levels, String adapterType, Long endTime, Integer offset, Integer limit) {

		RootFindCommand<Log> cmd = datastore.find().type(
				Log.class);
		
		if(adapters!=null) {
			cmd.addFilter("adapterID", FilterOperator.IN, adapters);
		}

		if (levels != null)
			cmd.addFilter("level", FilterOperator.IN, levels);
		
		if (adapterType != null)
			cmd.addFilter("adapterType", EQUAL, adapterType);
		
		if(endTime!=null) {
			cmd.addFilter("timestamp", FilterOperator.LESS_THAN_OR_EQUAL, endTime);
		}		
		if(offset==null)
			offset=0;
		if(limit==null)
			limit=50;

		cmd.startFrom(offset);
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
