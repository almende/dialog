package com.almende.dialog.sim;

import com.askfast.strowger.sdk.actions.StrowgerAction;
import com.askfast.strowger.sdk.model.Call;
import com.askfast.strowger.sdk.model.ControlResult;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

public class TPSimulator {

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    private String host = null;
    
    private String tenantKey = null;
    private String callId = null;
    private String caller = null;
    private String called = null;
    private String type = null;
    private String digits = null;
    
    private StrowgerAction resp = null;
    
    public TPSimulator(String host, String tenantKey) {
        this.host =  host;
        this.tenantKey = tenantKey;
    }
    
    public String initiateInboundCall(String callId, String caller, String called) {
        this.callId = callId;
        this.caller = caller;
        this.called = called;
        
        this.type = "inbound";
        
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
        String baseUrl = this.host + "/rest/strowger/answer";
        if(initial) {
            baseUrl = this.host + "/rest/strowger/new";
        }
        
        Call call = new Call();
        call.setCallId( callId );
        call.setCalled( this.called );
        call.setCaller( this.caller );
        call.setCallType( this.type );
        
        ControlResult res = new ControlResult(call, this.digits);
        
        OkHttpClient client = new OkHttpClient();
        RequestBody reqBody = RequestBody.create(JSON, res.toJson());
        Request request = new Request.Builder()
        .url(baseUrl)
        .post( reqBody )
        .build();

        try {
            Response response = client.newCall(request).execute();
            String body = response.body().string();
            System.out.println("Resp: " + body);
            this.resp = StrowgerAction.fromJson( body );
            return body;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public StrowgerAction getReponse() {
        return this.resp;
    }
}
