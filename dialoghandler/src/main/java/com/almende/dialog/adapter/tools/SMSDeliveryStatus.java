package com.almende.dialog.adapter.tools;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.EventCallback;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.annotations.Id;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.commons.utils.TimeUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This is the generic format for saving delivery status of SMS sent from any
 * provider. {@link CMStatus} is deprecated now.
 * 
 * @author Shravan
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SMSDeliveryStatus implements Serializable {

    private static final long serialVersionUID = 3674394844170200281L;
    private static final Logger log = Logger.getLogger(SMSDeliveryStatus.class.getSimpleName());
    //used to save the reference of the sms sent in the session
    public static final String SMS_REFERENCE_KEY = "SMS_REFERENCE";
    //used to split the reference and the host the sms is sent from
    public static final String SMS_REFERENCE_SPLIT_KEY = ";";

    /**
     * Explicit status code for SMS
     * @author shravan
     *
     */
    public enum SMSStatusCode {

            UNKNOWN, EXPIRED, DELETED, UNDELIVERED, REJECTED, ACKNOWLEDGED, ENROUTE, DELIVERED, ACCEPTED;
    }
    
    @Id
    public String reference;
    private String sms = "";
    private String adapterID = "";
    private String callback = "";
    private String host = "";
    private String remoteAddress = null;
    private String localAddress = "";
    private String sentTimeStamp = "";
    private String deliveredTimeStamp = "";
    private String code = "";
    private SMSStatusCode statusCode;
    private String description = "";
    private String accountId = null;
    private String provider = "";
    private String ddrRecordId = "";
    private Map<String, Object> extraInfos = null;

    @JsonIgnore
    private AdapterConfig adapterConfig = null;

    public void store() {

        if (reference != null) {
            TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
            sentTimeStamp = sentTimeStamp == null ? TimeUtils.getServerCurrentTime().toString() : sentTimeStamp;
            datastore.storeOrUpdate(this);
            log.info("SMS status saved: " + ServerUtils.serializeWithoutException(this));
        }
    }

    public static SMSDeliveryStatus fetch(String messageId) {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        return datastore.load(SMSDeliveryStatus.class, messageId);
    }

    /**
     * Fetch all the SMS delivery status
     * 
     * @return
     */
    public static List<SMSDeliveryStatus> fetchAll() {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        return datastore.find().type(SMSDeliveryStatus.class).now().toArray();
    }

    /**
     * Stores the sms related data in the CMStatus entity
     * 
     * @param address
     * @param config
     * @param ddrRecordId
     * @param localaddress
     * @param res
     * @param extras
     * @throws Exception
     */
    public static SMSDeliveryStatus storeSMSRelatedData(String referenceKey, String remoteAddress,
        AdapterConfig config, String accountId, Question question, String code, String description, String ddrRecordId,
        String provider, Session session) throws Exception {

        if (referenceKey != null && config.isSMSAdapter()) {
            SMSDeliveryStatus smsStatus = new SMSDeliveryStatus();
            smsStatus.setAdapterID(config.getConfigId());
            smsStatus.setLocalAddress(config.getMyAddress());
            smsStatus.setRemoteAddress(remoteAddress);
            smsStatus.setReference(referenceKey);
            smsStatus.setAccountId(accountId);
            String smsText = question != null ? question.getTextWithAnswerTexts(session) : null;
            smsStatus.setSms(smsText);
            smsStatus.setHost(Settings.HOST);
            smsStatus.setProvider(provider);
            smsStatus.setCode(code);
            smsStatus.setDescription(description);
            EventCallback deliveryEventCallback = question != null ? question.getEventCallback("delivered") : null;
            if (deliveryEventCallback != null) {
                smsStatus.setCallback(deliveryEventCallback.getCallback());
            }
            smsStatus.setDdrRecordId(ddrRecordId);
            smsStatus.store();
            return smsStatus;
        }
        return null;
    }

    public String getReference() {

        return reference;
    }

    public void setReference(String reference) {

        this.reference = reference;
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

        if (host != null && !host.startsWith("http")) {
            host = "http://" + host;
        }
        this.host = host;
    }

    /**
     * Returns the HOST path set in the reference when an SMS is sent. <br>
     * First checks if the message is saved on this messageId, else tries to
     * fetch the host from a format <messageId:host>
     * 
     * @param messageId
     * @return
     */
    public static String getHostFromReference(String messageId) {

        if (messageId != null) {
            SMSDeliveryStatus routeSMSStatus = fetch(messageId);
            if (routeSMSStatus != null) {
                return routeSMSStatus.getHost();
            }
            else {
                String[] messageIdAndHost = messageId.split(":");
                if (messageIdAndHost.length > 2) {
                    return messageIdAndHost[1];
                }
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

    public Map<String, Object> getExtraInfos() {

        extraInfos = extraInfos != null ? extraInfos : new HashMap<String, Object>();
        return extraInfos;
    }

    public void setExtraInfos(Map<String, Object> extraInfos) {

        this.extraInfos = extraInfos;
    }

    public Map<String, Object> addExtraInfo(String key, Object value) {

        getExtraInfos().put(key, value);
        return getExtraInfos();
    }

    public String getDdrRecordId() {

        return ddrRecordId;
    }

    public void setDdrRecordId(String ddrRecordId) {

        this.ddrRecordId = ddrRecordId;
    }
    
    public SMSStatusCode getStatusCode() {

        return statusCode;
    }

    public void setStatusCode(SMSStatusCode statusCode) {

        this.statusCode = statusCode;
    }

    /**
     * Returns the linked delivery status based on the remoteAddress
     * 
     * @param remoteAddress
     * @return
     */
    public SMSDeliveryStatus getLinkedSmsDeliveryStatus(String remoteAddress) {

        String formatedToNumber = PhoneNumberUtils.formatNumber(remoteAddress, null);
        if (!formatedToNumber.equals(PhoneNumberUtils.formatNumber(getRemoteAddress(), null))) {
            //try to fetch the linked sms status
            Object linkedSMSStatus = getExtraInfos().get(formatedToNumber);
            if (linkedSMSStatus != null) {
                log.info("Linked SMS delivery status fetched.. " + linkedSMSStatus.toString());
                return fetch(linkedSMSStatus.toString());
            }
            else {
                log.warning("No link SMS delivery status found: " + ServerUtils.serializeWithoutException(this));
                return null;
            }
        }
        return this;
    }
    
    /**
     * Trims off the important details that are not show to hte client in the
     * callback E.g. provider etc
     * 
     * @param status
     * @return
     * @throws JsonProcessingException
     */
    public static String getDeliveryStatusForClient(SMSDeliveryStatus status) throws JsonProcessingException {

        if (status != null) {
            status.setProvider(null);
            status.setExtraInfos(null);
            //make sure that the object mapper is set to setSerializationInclusion( Include.NON_NULL )
            ObjectMapper mapper = ParallelInit.getObjectMapper().setSerializationInclusion(Include.NON_NULL);
            return mapper.writeValueAsString(status);
        }
        return null;
    }
    
    /**
     * gives a mapping of the error code to the error description according to
     * Section 4. of the http://docs.cm.nl/http_SR.pdf
     * 
     * @param errorCode
     * @return
     */
    public static SMSStatusCode statusCodeMapping(AdapterProviders provider, String errorCode) {

        switch (provider) {
            case ROUTE_SMS: {
                switch (errorCode) {
                    case "UNKNOWN":
                        return SMSStatusCode.UNKNOWN;
                    case "EXPIRED":
                        return SMSStatusCode.EXPIRED;
                    case "DELETED":
                        return SMSStatusCode.DELETED;
                    case "UNDELIV":
                        return SMSStatusCode.UNDELIVERED;
                    case "REJECTD":
                        return SMSStatusCode.REJECTED;
                    case "ACKED":
                        return SMSStatusCode.ACKNOWLEDGED;
                    case "ENROUTE":
                        return SMSStatusCode.ENROUTE;
                    case "DELIVRD":
                        return SMSStatusCode.DELIVERED;
                    case "ACCEPTED":
                        return SMSStatusCode.ACCEPTED;
                    default:
                        return SMSStatusCode.UNKNOWN;
                }
            }
            case CM: {
                switch (errorCode) {
                    case "0":
                    case "200":
                        //Message is delivered
                        return SMSStatusCode.DELIVERED;
                    case "5":
                        //"The message has been confirmed as undelivered but no detailed information related to the failure is known.";
                        return SMSStatusCode.UNDELIVERED;
                    case "7":
                        //"Used to indicate to the client that the message has not yet been delivered due to insufficient subscriber credit but is being retried within the network.";
                        return SMSStatusCode.ENROUTE;
                    case "8":
                        //"Temporary Used when a message expired (could not be delivered within the life time of the message) within the operator SMSC but is not associated with a reason for failure. ";
                        return SMSStatusCode.EXPIRED;
                    case "20":
                        //"Used when a message in its current form is undeliverable.";
                        return SMSStatusCode.UNDELIVERED;
                    case "21":
                        //"Temporary Only occurs where the operator accepts the message before performing the subscriber credit check. If there is insufficient credit then the operator will retry the message until the subscriber tops up or the message expires. If the message expires and the last failure reason is related to credit then this error code will be used.";
                        return SMSStatusCode.ENROUTE;
                    case "22":
                        //"Temporary Only occurs where the operator performs the subscriber credit check before accepting the message and rejects messages if there are insufficient funds available.";
                        return SMSStatusCode.REJECTED;
                    case "23":
                        //"Used when the message is undeliverable due to an incorrect / invalid / blacklisted / permanently barred MSISDN for this operator. This MSISDN should not be used again for message submissions to this operator.";
                        return SMSStatusCode.REJECTED;
                    case "24":
                        //"Used when a message is undeliverable because the subscriber is temporarily absent, e.g. his/her phone is switch off, he/she cannot be located on the network. ";
                        return SMSStatusCode.UNDELIVERED;
                    case "25":
                        //"Used when the message has failed due to a temporary condition in the operator network. This could be related to the SS7 layer, SMSC or gateway. ";
                        return SMSStatusCode.UNDELIVERED;
                    case "26":
                        //"Used when a message has failed due to a temporary phone related error, e.g. SIM card full, SME busy, memory exceeded etc. This does not mean the phone is unable to receive this type of message/content (refer to error code 27).";
                        return SMSStatusCode.UNDELIVERED;
                    case "27":
                        //"Permanent Used when a handset is permanently incompatible or unable to receive this type of message. ";
                        return SMSStatusCode.UNDELIVERED;
                    case "28":
                        //"Used if a message fails or is rejected due to suspicion of SPAM on the operator network. This could indicate in some geographies that the operator has no record of the mandatory MO required for an MT. ";
                        return SMSStatusCode.REJECTED;
                    case "29":
                        //"Used when this specific content is not permitted on the network / shortcode. ";
                        return SMSStatusCode.REJECTED;
                    case "30":
                        //"Used when message fails or is rejected because the subscriber has reached the predetermined spend limit for the current billing period.";
                        return SMSStatusCode.REJECTED;
                    case "31":
                        //"Used when the MSISDN is for a valid subscriber on the operator but the message fails or is rejected because the subscriber is unable to be billed, e.g. the subscriber account is suspended (either voluntarily or involuntarily), the subscriber is not enabled for bill-to-phone services, the subscriber is not eligible for bill-to-phone services, etc.";
                        return SMSStatusCode.REJECTED;
                    case "33":
                        //"Used when the subscriber cannot receive adult content because of a parental lock. ";
                        return SMSStatusCode.UNDELIVERED;
                    case "34":
                        //"Permanent Used when the subscriber cannot receive adult content because they have previously failed the age verification process. ";
                        return SMSStatusCode.UNDELIVERED;
                    case "35":
                        //"Temporary Used when the subscriber cannot receive adult content because they have not previously completed age verification. ";
                        return SMSStatusCode.UNDELIVERED;
                    case "36":
                        //"Temporary Used when the subscriber cannot receive adult content because a temporary communication error prevents their status being verified on the age verification platform.";
                        return SMSStatusCode.UNDELIVERED;
                    case "37":
                        //"The MSISDN is on the national blacklist (currently only for NL: SMS dienstenfilter)";
                        return SMSStatusCode.DELETED;
                    default:
                        return SMSStatusCode.UNKNOWN;
                }
            }
            default:
                log.severe(String.format(
                    "Provider: %s is either not implemented for status delivery or adapter not of SMS type", provider));
                break;
        }
        return SMSStatusCode.UNKNOWN;
    }
}
