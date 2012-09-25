
package com.almende.dialog.model.impl;

import com.almende.dialog.model.intf.SessionIntf;

public class S_fields implements SessionIntf {
	private static final long serialVersionUID = -8914911091544995923L;

	String session_id;
	String account;
	String startUrl;
	String remoteAddress;
	String localAddress;
	String direction;
	String type;
	
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

	@Override
	public String getStartUrl() {
		return startUrl;
	}

	@Override
	public void setStartUrl(String url) {
		this.startUrl = url;
	}

	@Override
	public String getRemoteAddress() {
		return this.remoteAddress;
	}

	@Override
	public String getDirection() {
		return this.direction;
	}

	@Override
	public void setRemoteAddress(String remoteAddress) {
		this.remoteAddress=remoteAddress;
	}

	@Override
	public void setDirection(String direction) {
		this.direction=direction;
	}

	@Override
	public String getLocalAddress() {
		return this.localAddress;
	}

	@Override
	public void setLocalAddress(String localAddress) {
		this.localAddress=localAddress;
	}

	@Override
	public String getType() {
		return this.type;
	}

	@Override
	public void setType(String type) {
		this.type=type;
	}

}
