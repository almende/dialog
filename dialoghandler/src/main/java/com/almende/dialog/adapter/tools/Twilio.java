package com.almende.dialog.adapter.tools;

import java.util.HashMap;
import java.util.Map;

import com.almende.dialog.Settings;
import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.TwilioRestException;
import com.twilio.sdk.resource.factory.CallFactory;
import com.twilio.sdk.resource.factory.IncomingPhoneNumberFactory;
import com.twilio.sdk.resource.instance.Account;
import com.twilio.sdk.resource.instance.Call;
import com.twilio.sdk.resource.instance.IncomingPhoneNumber;

public class Twilio {

    private TwilioRestClient client = null;
    private Account account = null;
    
    public Twilio(String accountSid, String authToken) {
        client = new TwilioRestClient(accountSid, authToken);
        account = client.getAccount();
    }
    
    /**
     * Starts a call with twilio
     * @param from
     * @param to
     * @param applicationId
     * @return callSid
     * @throws TwilioRestException
     */
    public String startCall(String from, String to, String applicationId) throws TwilioRestException {
        if(account!=null) {
            CallFactory callFactory = account.getCallFactory();
            Map<String, String> callParams = new HashMap<String, String>();
            callParams.put("To", to); // Replace with a valid phone number
            callParams.put("From", from); // Replace with a valid phone
            // number in your account
            callParams.put("ApplicationSid", applicationId);
            
            callParams.put("StatusCallback", "http://" + Settings.HOST + "/dialoghandler/rest/twilio/cc");
            callParams.put("StatusCallbackMethod", "GET");
            callParams.put("IfMachine", "Hangup");
            callParams.put("Record", "false");
    
            Call call = callFactory.create(callParams);
            return call.getSid();
        }
        
        return null;
    }
    
    /**
     * Return the parentCallSid of a particular call
     * @param callSid
     * @return parentCallSid
     */
    public String getParentCallSid(String callSid) {
        if(account!=null) {
            Call call = account.getCall(callSid);
            return call.getParentCallSid();
        }
        return null;
    }
    
    /**
     * Return information from a certain call
     * @param callSid
     * @return
     */
    public Call getCallDetails(String callSid){
        if(account!=null) {
            return account.getCall(callSid);
        }
        
        return null;
    }
    
    public String buyPhoneNumber(String address, String applicationId) throws TwilioRestException {
        HashMap<String, String> params = new HashMap<String, String>();               
        params.put( "PhoneNumber", address );
        params.put( "VoiceApplicationSid", applicationId);
        
        IncomingPhoneNumberFactory numberFactory = account.getIncomingPhoneNumberFactory(); 
        IncomingPhoneNumber number = numberFactory.create(params);
        if(number!=null) {
            return number.getSid();
        }
        
        return null;
    }
}
