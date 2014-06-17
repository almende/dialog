package com.almende.dialog.model.intf;

import java.io.Serializable;

public interface EventCallbackIntf extends Serializable {
	public String getEvent_id();
	public String getEvent();
	public String getCallback();
	
	public void setEvent_id(String event_id);
	public void setEvent(String event_type);
	public void setCallback(String callback);
}
