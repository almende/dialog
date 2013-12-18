package com.almende.dialog;

import java.io.Serializable;

import com.almende.util.twigmongo.annotations.Id;
import com.almende.util.uuid.UUID;

public class Log implements Serializable {
	private static final long	serialVersionUID	= -8797389516750753990L;
	
	@Id
	private String				logId				= null;
	private LogLevel			level				= null;
	private String				adapterID			= null;
	private String				adapterType			= null;
	private String				message				= null;
	private long				timestamp			= 0;
	
	public Log() {
	}
	
	public Log(LogLevel level, String adapterID, String adapterType,
			String message) {
		this.logId = new UUID().toString();
		this.level = level;
		this.adapterID = adapterID;
		this.adapterType = adapterType;
		this.message = message;
		this.timestamp = System.currentTimeMillis();
	}
	
	public LogLevel getLevel() {
		return level;
	}
	
	public void setLevel(LogLevel level) {
		this.level = level;
	}
	
	public String getAdapterID() {
		return adapterID;
	}
	
	public void setAdapterID(String adapterID) {
		this.adapterID = adapterID;
	}
	
	public String getAdapterType() {
		return adapterType;
	}
	
	public void setAdapterType(String adapterType) {
		this.adapterType = adapterType;
	}
	
	public String getMessage() {
		return message;
	}
	
	public void setMessage(String message) {
		this.message = message;
	}
	
	public long getTimestamp() {
		return timestamp;
	}
	
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	
	public String getLogId() {
		return logId;
	}
	
	public void setLogId(String logId) {
		this.logId = logId;
	}
	
}
