package com.almende.dialog;

import java.io.Serializable;
import org.jongo.marshall.jackson.oid.Id;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Session;
import com.almende.util.uuid.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class Log implements Serializable {

    private static final long serialVersionUID = -8797389516750753990L;

    @Id
    private String logId = null;
    private LogLevel level = null;
    private String adapterID = null;
    private String adapterType = null;
    private String message = null;
    private long timestamp = 0;
    private String ddrRecordId = null;
    private String sessionKey = null;
    private String trackingToken = null;
    private String accountId = null;
    @JsonIgnore
    private AdapterConfig cachedAdapterConfig = null;
    
    public Log() {

    }
    
    /**
     * Creates a Log instance if a session is found and a corresponding
     * trackingToken is attached to that session
     * @param level
     * @param adapterID
     * @param adapterType
     *            If null, and adapterId is not null, it fetches the adapter and
     *            updates the type.
     * @param message
     * @param ddrRecordId
     *            if null, will try to fetch the current ddrRecord from
     *            {@link Session#getDdrRecordId()}
     * @param sessionKey
     */
    public Log(LogLevel level, String adapterID, String adapterType, String message, String ddrRecordId,
               String sessionKey) {

        if (sessionKey != null) {
            Session session = Session.getSession(sessionKey);
            AdapterConfig adapterConfig = AdapterConfig.getAdapterConfig(adapterID);
            if (session != null && session.getTrackingToken() != null) {
                this.logId = new UUID().toString();
                this.level = level;
                this.adapterID = adapterID;
                //fetch the adapter type if empty
                if ((adapterType == null || adapterType.isEmpty()) && adapterID != null) {
                    adapterType = adapterConfig != null ? adapterConfig.getAdapterType() : null;
                }
                this.adapterType = adapterType;
                this.message = message;
                this.timestamp = System.currentTimeMillis();
                this.sessionKey = sessionKey;
                this.trackingToken = session.getTrackingToken();
                this.ddrRecordId = ddrRecordId != null ? ddrRecordId : session.getDdrRecordId();
                this.accountId = session.getAccountId();
            }
        }
    }

    /**
     * Creates a Log instance if a session is found and a corresponding
     * trackingToken is attached to that session
     * 
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

        if (session != null && session.getTrackingToken() != null) {
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
            this.accountId = session.getAccountId();
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

    public String getAccountId() {

        return accountId;
    }

    public void setAccountId(String accountId) {

        this.accountId = accountId;
    }
    
    /**
     * Returns the adapterConfig linked to this log. This also caches this
     * adapterConfig in this instance of the log.
     * 
     * @return
     */
    @JsonIgnore
    public AdapterConfig getAdapterConfig() {

        if (cachedAdapterConfig == null && adapterID != null) {
            cachedAdapterConfig = AdapterConfig.getAdapterConfig(adapterID);
        }
        return cachedAdapterConfig;
    }
}
