
package com.almende.dialog.model.impl;

import com.almende.dialog.model.intf.SessionIntf;

public class S_fields implements SessionIntf {
	private static final long serialVersionUID = -8914911091544995923L;

	String session_id;
	String account;
	
	@Override
	public String getSession_id() {
		return this.session_id;
	}

	@Override
	public String getAccount() {
		return this.account;
	}

	@Override
	public void setSession_id(String session_id) {
		this.session_id=session_id;
	}

	@Override
	public void setAccount(String account) {
		this.account = account;
	}

}
