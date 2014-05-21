
package com.almende.dialog.model.impl;

import com.almende.dialog.model.intf.SessionIntf;

public class S_fields implements SessionIntf {
	private static final long serialVersionUID = -8914911091544995923L;

	String accountId;
	String startUrl;
	String remoteAddress;
	String localAddress;
	String direction;
	String type;
	String externalSession;
	String keyword;
	String adapterID;
	String trackingToken;
	String startTimestamp;
	String answerTimestamp;
	String releaseTimestamp;
	String ddrRecordId;
	
	@Override
	public String getAccountId() {
		return accountId;
	}

	@Override
	public void setAccountId(String accountId) {
		this.accountId = accountId;
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

	@Override
	public void setExternalSession(String externalSession) {
		this.externalSession = externalSession;
	}
	
	@Override
	public String getExternalSession() {
		return this.externalSession;
	}

	@Override
	public String getAdapterID() {
		return this.adapterID;
	}

	@Override
	public void setAdapterID(String adapterID) {
		this.adapterID = adapterID;
	}
	
	@Override
	public String getTrackingToken() {
		return this.trackingToken;
	}
	
	@Override
	public void setTrackingToken(String token) {
		this.trackingToken = token;		
	}

    public String getStartTimestamp()
    {
        return startTimestamp;
    }

    public void setStartTimestamp( String startTimestamp )
    {
        this.startTimestamp = startTimestamp;
    }

    public String getAnswerTimestamp()
    {
        return answerTimestamp;
    }

    public void setAnswerTimestamp( String answerTimestamp )
    {
        this.answerTimestamp = answerTimestamp;
    }

    public String getReleaseTimestamp()
    {
        return releaseTimestamp;
    }

    public void setReleaseTimestamp( String releaseTimestamp )
    {
        this.releaseTimestamp = releaseTimestamp;
    }

    public String getDDRRecordId()
    {
        return ddrRecordId;
    }

    public void setDDRRecordId( String ddrRecordId )
    {
        this.ddrRecordId = ddrRecordId;
    }
}
