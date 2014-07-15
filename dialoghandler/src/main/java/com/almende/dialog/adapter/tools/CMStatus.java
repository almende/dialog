package com.almende.dialog.adapter.tools;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.model.EventCallback;
import com.almende.dialog.model.Question;
import com.almende.dialog.util.ServerUtils;
import com.almende.dialog.util.TimeUtils;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.annotations.Id;


public class CMStatus implements Serializable
{
    private static final long serialVersionUID = 3674394844170200281L;
    private static final Logger log = Logger.getLogger( CMStatus.class.getSimpleName() );
    
    @Id
    public String reference;
    private String sms = "";
    private String adapterID = "";
    private String callback = "";
    private String remoteAddress = "";
    private String localAddress = "";
    private String sentTimeStamp = "";
    private String deliveredTimeStamp = "";
    private String code = "";
    private String errorCode = "";
    private String errorDescription = "";
    private String sessionKey = null;
    private String accountId = null;
    
    public void store()
    {
        if ( reference != null )
        {
        	TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
            sentTimeStamp = sentTimeStamp == null ? TimeUtils.getServerCurrentTime().toString() : sentTimeStamp;
            datastore.storeOrUpdate( this );
            log.info( "CM status saved: " + ServerUtils.serializeWithoutException( this ) );
        }
    }
    
    public static CMStatus fetch(String reference)
    {
    	TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        return datastore.load( CMStatus.class, reference );
    }
    
    /** stores the sms related data in the CMStatus entity
     * @param address
     * @param config
     * @param localaddress
     * @param res
     * @param extras
     * @throws Exception
     */
    public static Map<String, Object> storeSMSRelatedData(String address, String localaddress, AdapterConfig config,
                                                          Question question, String smsText, String sessionKey,
                                                          Map<String, Object> extras) throws Exception {

        extras = extras != null ? extras : new HashMap<String, Object>();
        // check if SMS delivery notification is requested
        if (config.getAdapterType().equalsIgnoreCase("cm") ||
            config.getAdapterType().equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_SMS)) {
            String smsStatusKey = generateSMSReferenceKey(config.getConfigId(), localaddress, address);
            EventCallback deliveryEventCallback = question != null ? question.getEventCallback("delivered") : null;
            CMStatus cmStatus = new CMStatus();
            cmStatus.setAdapterID(config.getConfigId());
            cmStatus.setLocalAddress(localaddress);
            cmStatus.setRemoteAddress(address);
            cmStatus.setReference(smsStatusKey);
            cmStatus.setAccountId(config.getOwner());
            cmStatus.setSessionKey(sessionKey);
            cmStatus.setSms(smsText);
            extras.put(CM.SMS_DELIVERY_STATUS_KEY, smsStatusKey);
            if (deliveryEventCallback != null) {
                cmStatus.setCallback(deliveryEventCallback.getCallback());
            }
            cmStatus.store();
        }
        return extras;
    }
    
    /**
     * generates a reference key used to track delivery status of an SMS
     * @param localaddress
     * @param address
     * @return
     */
    public static String generateSMSReferenceKey(String adapterId, String localaddress, String address) {

        return adapterId + "_" + localaddress + "_" + address + "_" + "http://" + Settings.HOST;
    }
    
    public String getReference()
    {
        return reference;
    }

    public void setReference( String reference )
    {
        this.reference = reference;
    }

    public String getSentTimeStamp()
    {
        return sentTimeStamp;
    }

    public void setSentTimeStamp( String sentTimeStamp )
    {
        this.sentTimeStamp = sentTimeStamp;
    }

    public String getDeliveredTimeStamp()
    {
        return deliveredTimeStamp;
    }

    public void setDeliveredTimeStamp( String deliveredTimeStamp )
    {
        this.deliveredTimeStamp = deliveredTimeStamp;
    }

    public String getCode()
    {
        return code;
    }

    public void setCode( String code )
    {
        this.code = code;
    }

    public String getErrorCode()
    {
        return errorCode;
    }

    public void setErrorCode( String errorCode )
    {
        this.errorCode = errorCode;
    }

    public String getErrorDescription()
    {
        return errorDescription;
    }

    public void setErrorDescription( String errorDescription )
    {
        this.errorDescription = errorDescription;
    }

    public String getSms()
    {
        return sms;
    }

    public void setSms( String sms )
    {
        this.sms = sms;
    }

    public String getAdapterID()
    {
        return adapterID;
    }

    public void setAdapterID( String adapterID )
    {
        this.adapterID = adapterID;
    }

    public String getCallback()
    {
        return callback;
    }

    public void setCallback( String callback )
    {
        this.callback = callback;
    }

    public String getRemoteAddress()
    {
        return remoteAddress;
    }

    public void setRemoteAddress( String remoteAddress )
    {
        this.remoteAddress = remoteAddress;
    }

    public String getLocalAddress()
    {
        return localAddress;
    }

    public void setLocalAddress( String localAddress )
    {
        this.localAddress = localAddress;
    }
    
    public String getSessionKey() {
    
        return sessionKey;
    }
    
    public void setSessionKey(String sessionKey) {
    
        this.sessionKey = sessionKey;
    }
    
    public void setAccountId(String accountId) {

        this.accountId  = accountId;
    }
    
    public String getAccountId() {

        return accountId;
    }

    /**
     * returns the HOST path set in the reference when an SMS is sent
     * @param reference
     * @return
     */
    public static String getHostFromReference(String reference) {
        if(reference != null) {
            String[] referenceArray = reference.split("_");
            if(referenceArray.length == 4) {
                return referenceArray[3].startsWith("http") ? referenceArray[3] : null;
            }
        }
        return null;
    }
}
