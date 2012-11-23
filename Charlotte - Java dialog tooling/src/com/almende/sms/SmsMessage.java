package com.almende.sms;

import java.io.Serializable;

public class SmsMessage implements Serializable {
	
	private static final long serialVersionUID = -6199026685594642787L;
	private String to="";
	private String message="";
	
	public SmsMessage() {}
	
	public SmsMessage(String to, String message) {
		this.to = to;
		this.message = message;
	}
	
	public String getTo() {
		return to;
	}
	
	public void setTo(String to) {
		this.to = to;
	}
	
	public String getMessage() {
		return message;
	}
	
	public void setMessage(String message) {
		this.message = message;
	}
}
