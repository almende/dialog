package com.almende.dialog.model.impl;

import com.almende.dialog.model.intf.EventCallbackIntf;

public class E_fields implements EventCallbackIntf {
	private static final long serialVersionUID = -6982002209520180854L;

	String event_id;
	String event;
	String callback;
	
	public E_fields() {}

	@Override
	public String getEvent_id() {
		return event_id;
	}

	@Override
	public String getEvent() {
		return event;
	}

	@Override
	public String getCallback() {
		return callback;
	}

	@Override
	public void setEvent_id(String event_id) {
		this.event_id = event_id;
	}

	@Override
	public void setEvent(String event) {
		this.event = event;
	}

	@Override
	public void setCallback(String callback) {
		this.callback = callback;
	}

}
