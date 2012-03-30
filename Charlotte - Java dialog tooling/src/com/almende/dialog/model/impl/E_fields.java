package com.almende.dialog.model.impl;

import com.almende.dialog.model.intf.EventCallbackIntf;

public class E_fields implements EventCallbackIntf {
	private static final long serialVersionUID = -6982002209520180854L;

	String event_id;
	String event;
	String callback;
	
	public E_fields() {}

	public String getEvent_id() {
		return event_id;
	}

	public String getEvent() {
		return event;
	}

	public String getCallback() {
		return callback;
	}

	public void setEvent_id(String event_id) {
		this.event_id = event_id;
	}

	public void setEvent(String event) {
		this.event = event;
	}

	public void setCallback(String callback) {
		this.callback = callback;
	}

}
