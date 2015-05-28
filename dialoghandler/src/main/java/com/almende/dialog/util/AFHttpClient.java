package com.almende.dialog.util;

import java.io.IOException;
import java.util.Map;
import com.almende.dialog.util.http.UserAgentInterceptor;
import com.almende.util.ParallelInit;
import com.askfast.commons.entity.RequestLog;
import com.askfast.commons.entity.ResponseLog;
import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Request.Builder;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

public class AFHttpClient {

    private OkHttpClient client = null;
    private String basicAuthCredentials = null;

    public AFHttpClient() {

        client = new OkHttpClient();
        client.networkInterceptors().add(new UserAgentInterceptor("ASK-Fast/1.0"));
    }

    public String get(String url) throws IOException {

        return get(url, null, false, null, null, null);
    }

    /**
     * Use this exclusively when askfast adds some query params like the
     * sessionKey, responder etc. It will also add these query params to the url
     * 
     * @param url
     * @param askFastQueryParams
     * @return
     * @throws IOException
     */
    public String get(String url, Map<String, String> askFastQueryParams, boolean createLog, String sessionKey,
        String accountId, String ddrRecordId) throws IOException {

        long startTimeStamp = TimeUtils.getServerCurrentTimeInMillis();
        url = ServerUtils.getURLWithQueryParams(url, askFastQueryParams);
        Request request = getBuilderWIthBasicAuthHeader(url).build();
        Response response = client.newCall(request).execute();
        if (createLog && sessionKey != null) {
            ResponseLog responseLog = new ResponseLog(response, TimeUtils.getServerCurrentTimeInMillis() -
                startTimeStamp);
            RequestLog requestLog = new RequestLog(request, askFastQueryParams);
            ParallelInit.getLoggerAgent().createLog(requestLog, responseLog, sessionKey, accountId, ddrRecordId,
                                                    response.isSuccessful());
            //the response connection might be closed. So response.body.string() isnt accessible 
            return responseLog.getResponseBody();
        }
        return response.body().string();
    }

    public String post(String json, String url) throws IOException {

        return post(json, url, null);
    }

    public String post(String json, String url, String type) throws IOException {

        return post(json, url, type, null, false, null, null, null);
    }

    /**
     * Essentially used to store the logs by passing the sessionKey and
     * accoundId
     * 
     * @param json
     * @param url
     * @param type
     * @param askFastQueryParams
     * @param createLog
     * @param sessionKey
     * @param accountId
     * @param ddrRecordId
     * @return
     * @throws IOException
     */
    public String post(String json, String url, String type, Map<String, String> askFastQueryParams, boolean createLog,
        String sessionKey, String accountId, String ddrRecordId) throws IOException {

        if (type == null) {
            type = "application/json";
        }
        MediaType mediaType = MediaType.parse(type);
        RequestBody body = RequestBody.create(mediaType, json);
        url = ServerUtils.getURLWithQueryParams(url, askFastQueryParams);
        long startTimeStamp = TimeUtils.getServerCurrentTimeInMillis();
        Request request = getBuilderWIthBasicAuthHeader(url).post(body).build();
        Response response = client.newCall(request).execute();
        if (createLog && sessionKey != null) {
            ResponseLog responseLog = new ResponseLog(response, TimeUtils.getServerCurrentTimeInMillis() -
                startTimeStamp);
            RequestLog requestLog = new RequestLog(request, askFastQueryParams);
            ParallelInit.getLoggerAgent().createLog(requestLog, responseLog, sessionKey, accountId, ddrRecordId,
                                                    response.isSuccessful());
            //the response connection might be closed. So response.body.string() isnt accessible 
            return responseLog.getResponseBody();
        }
        return response.body().string();
    }

    /**
     * Essentially used to store the logs by passing the sessionKey and
     * accoundId
     * 
     * @param json
     * @param url
     * @param type
     * @param askFastQueryParams
     * @param createLog
     * @param sessionKey
     * @param accountId
     * @param ddrRecordId
     * @return
     * @throws IOException
     */
    public String put(String json, String url, String type, Map<String, String> askFastQueryParams, boolean createLog,
        String sessionKey, String accountId, String ddrRecordId) throws IOException {

        if (type == null) {
            type = "application/json";
        }
        MediaType mediaType = MediaType.parse(type);
        RequestBody body = RequestBody.create(mediaType, json);
        url = ServerUtils.getURLWithQueryParams(url, askFastQueryParams);
        long startTimeStamp = TimeUtils.getServerCurrentTimeInMillis();
        Request request = getBuilderWIthBasicAuthHeader(url).put(body).build();
        Response response = client.newCall(request).execute();
        if (createLog && sessionKey != null) {
            ResponseLog responseLog = new ResponseLog(response, TimeUtils.getServerCurrentTimeInMillis() -
                startTimeStamp);
            RequestLog requestLog = new RequestLog(request, askFastQueryParams);
            ParallelInit.getLoggerAgent().createLog(requestLog, responseLog, sessionKey, accountId, ddrRecordId,
                                                    response.isSuccessful());
            //the response connection might be closed. So response.body.string() isnt accessible 
            return responseLog.getResponseBody();
        }
        return response.body().string();
    }

    public String put(String json, String url, String type) throws IOException {

        return put(json, url, type, null, false, null, null, null);
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
     * 
     * @param credential
     *            . This is added to the client header
     */
    public void addBasicAuthorizationHeader(final String credential) {

        this.basicAuthCredentials = credential;
    }

    /**
     * Simply adds the basic authorization header to the builder
     * 
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

    /**
     * Flush the authentication credentials
     */
    public void flushCredentials() {

        this.basicAuthCredentials = null;
    }

    /**
     * Returns the basic authentication credentials
     * 
     * @return
     */
    public String getCredentials() {

        return this.basicAuthCredentials;
    }
}
