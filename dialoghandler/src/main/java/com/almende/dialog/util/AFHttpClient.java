package com.almende.dialog.util;

import java.io.IOException;
import com.almende.dialog.util.http.UserAgentInterceptor;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Request.Builder;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

public class AFHttpClient{

    private OkHttpClient client = null;
    private String basicAuthCredentials = null;
    
    public AFHttpClient() {
        client = new OkHttpClient();
        client.networkInterceptors().add(new UserAgentInterceptor("ASK-Fast/1.0"));
    }
    
    public String get(String url) throws IOException {

        Request request = getBuilderWIthBasicAuthHeader(url).build();
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
        Request request = getBuilderWIthBasicAuthHeader(url).post(body).build();
        Response response = client.newCall(request).execute();
        return response.body().string();
    }
    
    public String put(String json, String url, String type) throws IOException {
        
        if(type==null) {
            type = "application/json";
        }
        MediaType mediaType = MediaType.parse( type );
        RequestBody body = RequestBody.create(mediaType, json);
        Request request = getBuilderWIthBasicAuthHeader(url).put(body).build();
        Response response = client.newCall(request).execute();
        return response.body().string();
    }
    
    /**
     * Builds the client instance for basic authorization with given username
     * and password
     * 
     * @param username
     * @param password
     */
    public void authorizeClient(final String username, final String password) {

        this.basicAuthCredentials = Credentials.basic(username, password);
    }
    
    /**
     * Builds the client instance for basic authorization with given header
     * @param credential. This is added to the client header
     */
    public void addBasicAuthorizationHeader(final String credential) {

        this.basicAuthCredentials = credential;
    }
    
    /**
     * Simply adds the basic authorization header to the builder
     * @param builder
     * @return
     */
    private Builder getBuilderWIthBasicAuthHeader(String url) {

        Builder builder = new Request.Builder().url(url);
        if (basicAuthCredentials != null) {
            builder.addHeader("Authorization", basicAuthCredentials);
        }
        return builder;
    }
}
