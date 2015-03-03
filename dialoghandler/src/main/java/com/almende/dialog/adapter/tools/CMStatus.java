package com.almende.dialog.adapter.tools;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Session;
import com.almende.util.twigmongo.TwigCompatibleMongoDatastore;
import com.almende.util.twigmongo.annotations.Id;
import com.fasterxml.jackson.annotation.JsonIgnore;

public class CMStatus implements Serializable {

    private static final long serialVersionUID = 3674394844170200281L;

    @Id
    public String reference;
    private String sms = "";
    private String adapterID = "";
    private String callback = "";
    private HashSet<String> remoteAddresses = null;
    private String localAddress = "";
    private String sentTimeStamp = "";
    private String deliveredTimeStamp = "";
    private String code = "";
    private String errorCode = "";
    private String errorDescription = "";
    private String accountId = null;
    @JsonIgnore
    private Session session = null;
    @JsonIgnore
    private AdapterConfig adapterConfig = null;

    public static CMStatus fetch(String reference) {

        TwigCompatibleMongoDatastore datastore = new TwigCompatibleMongoDatastore();
        return datastore.load(CMStatus.class, reference);
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

    public String getErrorCode() {

        return errorCode;
    }

    public void setErrorCode(String errorCode) {

        this.errorCode = errorCode;
    }

    public String getErrorDescription() {

        return errorDescription;
    }

    public void setErrorDescription(String errorDescription) {

        this.errorDescription = errorDescription;
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

    public HashSet<String> getRemoteAddresses() {

        return remoteAddresses;
    }

    public void setRemoteAddresses(Set<String> remoteAddresses) {

        if (remoteAddresses != null) {
            this.remoteAddresses = new HashSet<String>(remoteAddresses);
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

    /**
     * returns the HOST path set in the reference when an SMS is sent
     * 
     * @param reference
     * @return
     */
    public static String getHostFromReference(String reference) {

        if (reference != null) {
            String[] referenceArray = reference.split("_");
            if (referenceArray.length == 4) {
                return referenceArray[3].startsWith("http") ? referenceArray[3] : null;
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

    /**
     * just adds the given address to the list of addresses
     * 
     * @param address
     */
    public void addRemoteAddress(String address) {

        remoteAddresses = remoteAddresses != null ? remoteAddresses : new HashSet<String>();
        remoteAddresses.add(address);
    }
}
