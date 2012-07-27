package com.almende.dialog.model.intf;

import java.io.Serializable;

public interface SessionIntf extends Serializable  {
	
	public String getSession_id();
	public String getAccount();
	
	public void setSession_id(String session_id);
	public void setAccount(String account);
	
}
