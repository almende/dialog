package com.almende.dialog.util;

import java.io.IOException;

import com.almende.dialog.util.http.UserAgentInterceptor;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

public class AFHttpClient{

    private OkHttpClient client = null;
    
    public AFHttpClient() {
        client = new OkHttpClient();
        client.networkInterceptors().add(new UserAgentInterceptor("ASK-Fast/1.0"));
    }
    
    public String get(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        Response response = client.newCall(request).execute();
        return response.body().string();
    }
    public String post(String json, String url) throws IOException {
        return post(json, url, null);
    }
    
    public String post(String json, String url, String type) throws IOException {
        
        if(type==null) {
            type = "application/json";
        }
        
        MediaType mediaType = MediaType.parse( type );
        
        RequestBody body = RequestBody.create(mediaType, json);
        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .build();
        
        Response response = client.newCall(request).execute();
        return response.body().string();
    }
    
    public String put(String json, String url, String type) throws IOException {
        
        if(type==null) {
            type = "application/json";
        }
        
        MediaType mediaType = MediaType.parse( type );
        
        RequestBody body = RequestBody.create(mediaType, json);
        Request request = new Request.Builder()
            .url(url)
            .put(body)
            .build();
        
        Response response = client.newCall(request).execute();
        return response.body().string();
    }
}
