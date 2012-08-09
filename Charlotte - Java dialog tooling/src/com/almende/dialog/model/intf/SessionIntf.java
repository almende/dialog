package com.almende.dialog.model.intf;

import java.io.Serializable;

public interface SessionIntf extends Serializable  {
	
	public String getSession_id();
	public String getAccount();
	public String getStartUrl();
	public String getRemoteAddress();
	public String getDirection();
	
	public void setSession_id(String session_id);
	public void setAccount(String account);
	public void setStartUrl(String url);
	public void setRemoteAddress(String remoteAddress);
	public void setDirection(String direction);
	
}
