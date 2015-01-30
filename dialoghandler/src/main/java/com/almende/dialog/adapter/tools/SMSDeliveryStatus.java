package com.almende.dialog.adapter.tools;

import java.io.Serializable;
import java.util.logging.Logger;
import org.bson.types.ObjectId;
import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.EventCallback;
import com.almende.dialog.model.Question;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.annotations.Id;
import com.askfast.commons.entity.AdapterType;
import com.askfast.commons.utils.TimeUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * This is the generic format for saving delivery status of SMS sent from any provider.
 * {@link CMStatus} is deprecated now.  
 * @author Shravan
 */
public class SMSDeliveryStatus implements Serializable {

    private static final long serialVersionUID = 3674394844170200281L;
    private static final Logger log = Logger.getLogger(SMSDeliveryStatus.class.getSimpleName());
    //used to save the reference of the sms sent in the session
    public static final String SMS_REFERENCE_KEY = "SMS_REFERENCE";
    
    @Id
    public String messageId;
    private String sms = "";
    private String adapterID = "";
    private String callback = "";
    private String host = "";
    private String remoteAddress = null;
    private String localAddress = "";
    private String sentTimeStamp = "";
    private String deliveredTimeStamp = "";
    private String code = "";
    private String description = "";
    private String accountId = null;
    private String provider = "";
    private Object extraInfos = null;
    
    
    @JsonIgnore
    private AdapterConfig adapterConfig = null;

    public void store() {

        if (messageId != null) {
            TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
            sentTimeStamp = sentTimeStamp == null ? TimeUtils.getServerCurrentTime().toString() : sentTimeStamp;
            datastore.storeOrUpdate(this);
            log.info("CM status saved: " + ServerUtils.serializeWithoutException(this));
        }
    }

    public static SMSDeliveryStatus fetch(String messageId) {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        return datastore.load(SMSDeliveryStatus.class, messageId);
    }

    /**
     * Stores the sms related data in the CMStatus entity
     * 
     * @param address
     * @param config
     * @param localaddress
     * @param res
     * @param extras
     * @throws Exception
     */
    public static SMSDeliveryStatus storeSMSRelatedData(String referenceKey, String remoteAddress,
        AdapterConfig config, String accountId, Question question, String code, String description) throws Exception {

        String smsStatusKey = referenceKey != null ? referenceKey : generateSMSReferenceKey(config.getConfigId(),
                                                                                            config.getMyAddress());
        // check if SMS delivery notification is requested
        if (AdapterType.isSMSAdapter(config.getAdapterType())) {
            SMSDeliveryStatus smsStatus = new SMSDeliveryStatus();
            smsStatus.setAdapterID(config.getConfigId());
            smsStatus.setLocalAddress(config.getMyAddress());
            smsStatus.setRemoteAddress(remoteAddress);
            smsStatus.setReference(smsStatusKey);
            smsStatus.setAccountId(accountId);
            String smsText = question != null ? question.getQuestion_expandedtext() : null;
            smsStatus.setSms(smsText);
            smsStatus.setHost(Settings.HOST);
            smsStatus.setProvider(config.getAdapterType());
            smsStatus.setCode(code);
            smsStatus.setDescription(description);
            EventCallback deliveryEventCallback = question != null ? question.getEventCallback("delivered") : null;
            if (deliveryEventCallback != null) {
                smsStatus.setCallback(deliveryEventCallback.getCallback());
            }
            smsStatus.store();
            return smsStatus;
        }
        return null;
    }

    /**
     * generates a reference key used to track delivery status of an SMS
     * 
     * @param localaddress
     * @param address
     * @return
     */
    public static String generateSMSReferenceKey(String adapterId, String localaddress) {

        return ObjectId.get().toStringMongod() + "_" + adapterId + "_" + localaddress + "_" + "http://" + Settings.HOST;
    }

    public String getReference() {

        return messageId;
    }

    public void setReference(String reference) {

        this.messageId = reference;
    }

    public String getSentTimeStamp() {

        return sentTimeStamp;
    }

    public void setSentTimeStamp(String sentTimeStamp) {

        this.sentTimeStamp = sentTimeStamp;
    }

    public String getDeliveredTimeStamp() {

        return deliveredTimeStamp;
    }

    public void setDeliveredTimeStamp(String deliveredTimeStamp) {

        this.deliveredTimeStamp = deliveredTimeStamp;
    }

    public String getCode() {

        return code;
    }

    public void setCode(String code) {

        this.code = code;
    }

    public String getDescription() {

        return description;
    }

    public void setDescription(String description) {

        this.description = description;
    }

    public String getSms() {

        return sms;
    }

    public void setSms(String sms) {

        this.sms = sms;
    }

    public String getAdapterID() {

        return adapterID;
    }

    public void setAdapterID(String adapterID) {

        this.adapterID = adapterID;
    }

    public String getCallback() {

        return callback;
    }

    public void setCallback(String callback) {

        this.callback = callback;
    }

    public String getRemoteAddress() {

        return remoteAddress;
    }

    public void setRemoteAddress(String remoteAddress) {

        if (remoteAddress != null) {
            this.remoteAddress = remoteAddress;
        }
    }

    public String getLocalAddress() {

        return localAddress;
    }

    public void setLocalAddress(String localAddress) {

        this.localAddress = localAddress;
    }

    public void setAccountId(String accountId) {

        this.accountId = accountId;
    }

    public String getAccountId() {

        return accountId;
    }
    
    public String getHost() {

        return host;
    }

    public void setHost(String host) {

        this.host = host;
    }

    /**
     * returns the HOST path set in the reference when an SMS is sent
     * 
     * @param messageId
     * @return
     */
    public static String getHostFromReference(String messageId) {

        if (messageId != null) {
            SMSDeliveryStatus routeSMSStatus = fetch(messageId);
            if(routeSMSStatus != null) {
                return routeSMSStatus.getHost();
            }
        }
        return null;
    }

    @JsonIgnore
    public AdapterConfig getAdapterConfig() {

        if (adapterConfig == null && adapterID != null) {
            adapterConfig = AdapterConfig.getAdapterConfig(adapterID);
        }
        return adapterConfig;
    }

    public String getProvider() {

        return provider;
    }

    public void setProvider(String provider) {

        this.provider = provider;
    }

    public Object getExtraInfos() {

        return extraInfos;
    }

    public void setExtraInfos(Object extraInfos) {

        this.extraInfos = extraInfos;
    }
}
