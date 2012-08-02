package com.almende.dialog.model.intf;

import java.io.Serializable;

public interface SessionIntf extends Serializable  {
	
	public String getSession_id();
	public String getAccount();
	public String getStartUrl();
	
	public void setSession_id(String session_id);
	public void setAccount(String account);
	public void setStartUrl(String url);
	
}
