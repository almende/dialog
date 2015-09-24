package com.almende.dialog.sim;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.client.utils.URIBuilder;

import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.twilio.sdk.verbs.TwiMLResponse;

public class TwilioSimulator {

    private TwiMLParser parser = new TwiMLParser();
    
    private String host = null;
    
    private String accountSid = null;
    private String callSid = null;
    private String from = null;
    private String to = null;
    private String direction = null;
    private String forwardedFrom = null;
    private String digits = null;
    private String dialCallStatus = null;
    private String dialCalSid = null;
    private String recordingUrl = null;
    
    private TwiMLResponse resp = null;
    
    public TwilioSimulator(String host, String accountSid) {
        this.host =  host;
        this.accountSid = accountSid;
    }
    
    public String initiateInboundCall(String callSid, String from, String to) {
        this.callSid = callSid;
        this.from = from;
        this.to = to;
        
        this.direction = "inbound";
        
        return this.doCall(true);
    }
    
    public String timeout() {
        
        return this.doCall(true);
    }
    
    public String nextQuestion(String digits) {
        this.digits = digits;
        
        return this.doCall(false);
    }
    
    public String getPreconnect() {
        return null;
    }
    
    private String doCall(boolean initial) {
        String baseUrl = this.host + "/rest/twilio/answer";
        if(initial) {
            baseUrl = this.host + "/rest/twilio/new";
        }
        URIBuilder builder = new URIBuilder(URI.create(baseUrl));
        builder.addParameter( "AccountSid", accountSid );
        builder.addParameter( "CallSid", callSid );
        builder.addParameter( "From", from );
        builder.addParameter( "To", to );
        builder.addParameter( "Direction", direction );
        builder.addParameter( "ForwardedFrom", forwardedFrom );
        builder.addParameter( "Digits", digits );
        builder.addParameter( "DialCallStatus", dialCallStatus );
        builder.addParameter( "DialCalSid", dialCalSid );
        builder.addParameter( "RecordingUrl", recordingUrl );
        
        String url = null;
        try {
            url = builder.build().toString();
        }
        catch ( URISyntaxException e ) {
            e.printStackTrace();
        }
        
        OkHttpClient client = new OkHttpClient();
        
        Request request = new Request.Builder()
        .url(url)
        .build();

        try {
            Response response = client.newCall(request).execute();
            String body = response.body().string();
            this.resp = parser.parseXML( body );
            return body;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public TwiMLResponse getReponse() {
        return this.resp;
    }
}
