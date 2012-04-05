package com.almende.dialog.model;

import com.almende.dialog.model.impl.*;
import com.almende.dialog.model.intf.EventCallbackIntf;

public class EventCallback implements EventCallbackIntf {
	private static final long serialVersionUID = -2431456288188062707L;

	EventCallbackIntf eventCallback;
	
	public EventCallback() {
		this.eventCallback = new E_fields();
	}
	@Override
	public String getEvent_id() { return eventCallback.getEvent_id(); }
	@Override
	public String getEvent() { return eventCallback.getEvent(); }
	@Override
	public String getCallback() { return eventCallback.getCallback(); }
	@Override
	public void setEvent_id(String event_id) { eventCallback.setEvent_id(event_id); }
	@Override
	public void setEvent(String event_type) { eventCallback.setEvent(event_type); }
	@Override
	public void setCallback(String callback) { eventCallback.setCallback(callback); }
}
