package com.almende.dialog;

import java.io.Serializable;
import org.jongo.marshall.jackson.oid.Id;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Session;
import com.almende.util.uuid.UUID;

public class Log implements Serializable {

    private static final long serialVersionUID = -8797389516750753990L;

    @Id
    public String logId = null;
    private LogLevel level = null;
    private String adapterID = null;
    private String adapterType = null;
    private String message = null;
    private long timestamp = 0;
    private String ddrRecordId = null;
    private String sessionKey = null;
    private String trackingToken = null;

    public Log() {

    }
    
    /**
     * detailed constructor
     * @param level
     * @param adapterID
     * @param adapterType
     * @param message
     * @param ddrRecordId if null, will try to fetch the current ddrRecord from {@link Session#getDdrRecordId()}
     * @param sessionKey
     */
    public Log(LogLevel level, String adapterID, String adapterType, String message, String ddrRecordId,
               String sessionKey) {

        if (sessionKey != null) {
            Session session = Session.getSession(sessionKey);
            if (session != null && session.getTrackingToken() != null) {
                this.logId = new UUID().toString();
                this.level = level;
                this.adapterID = adapterID;
                //fetch the adapter type if empty
                if ((adapterType == null || adapterType.isEmpty()) && adapterID != null) {
                    AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapterID);
                    adapterType = adapterConfig != null ? adapterConfig.getAdapterType() : null;
                }
                this.adapterType = adapterType;
                this.message = message;
                this.timestamp = System.currentTimeMillis();
                this.sessionKey = sessionKey;
                this.trackingToken = session.getTrackingToken();
                this.ddrRecordId = ddrRecordId != null ? ddrRecordId : session.getDdrRecordId();
            }
        }
    }
    
    /**
     * detailed constructor
     * @param level
     * @param adapterID
     * @param adapterType
     * @param message
     * @param ddrRecordId
     *            if null, will try to fetch the current ddrRecord from
     *            {@link Session#getDdrRecordId()}
     * @param sessionKey
     */
    public Log(LogLevel level, AdapterConfig adapter, String message, Session session) {

        if (session != null) {
            this.logId = new UUID().toString();
            this.level = level;
            //fetch the adapter type if empty
            if (adapter != null) {
                this.adapterID = adapter.getConfigId();
                this.adapterType = adapter.getAdapterType();
            }
            this.message = message;
            this.timestamp = System.currentTimeMillis();
            this.sessionKey = session.getKey();
            this.trackingToken = session.getTrackingToken();
            this.ddrRecordId = ddrRecordId != null ? ddrRecordId : session.getDdrRecordId();
        }
    }
	
    public LogLevel getLevel() {
        return level;
    }

    public void setLevel(LogLevel level) {
        this.level = level;
    }

    public String getAdapterID() {
        return adapterID;
    }

    public void setAdapterID(String adapterID) {
        this.adapterID = adapterID;
    }
	
    public String getAdapterType() {

        return adapterType;
    }
	
    public void setAdapterType(String adapterType) {
        this.adapterType = adapterType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getLogId() {
        return logId;
    }

    public void setLogId(String logId) {
        this.logId = logId;
    }
    
    public String getDdrRecordId() {
    
        return ddrRecordId;
    }
    
    public void setDdrRecordId(String ddrRecordId) {
    
        this.ddrRecordId = ddrRecordId;
    }
    
    public String getSessionKey() {
    
        return sessionKey;
    }
    
    public void setSessionKey(String sessionKey) {
    
        this.sessionKey = sessionKey;
    }
    public String getTrackingToken() {
    
        return trackingToken;
    }
    public void setTrackingToken(String trackingToken) {
    
        this.trackingToken = trackingToken;
    }
}
