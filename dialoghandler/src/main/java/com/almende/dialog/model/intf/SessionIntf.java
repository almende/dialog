package com.almende.dialog.model.intf;

import java.io.Serializable;

public interface SessionIntf extends Serializable  {
	
	public String getAccountId();
	public String getStartUrl();
	public String getRemoteAddress();
	public String getLocalAddress();
	public String getDirection();
	public String getType();
	public String getExternalSession();
	public String getAdapterID();
	public String getTrackingToken();
    public String getStartTimestamp();
    public String getAnswerTimestamp();
    public String getReleaseTimestamp();
    public String getDDRRecordId();  
	
	public void setAccountId(String accountId);
	public void setStartUrl(String url);
	public void setRemoteAddress(String remoteAddress);
	public void setLocalAddress(String localAddress);
	public void setDirection(String direction);
	public void setType(String type);
	public void setExternalSession(String externalSession);
	public void setAdapterID(String adapterID);
	public void setTrackingToken(String token);
    public void setStartTimestamp( String startTimestamp );
    public void setReleaseTimestamp( String releaseTimestamp );
    public void setAnswerTimestamp( String answerTimestamp );
    public void setDDRRecordId(String ddrRecordId);
}
