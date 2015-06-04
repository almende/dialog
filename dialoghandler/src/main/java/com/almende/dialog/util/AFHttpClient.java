package com.almende.dialog.util;

import java.io.IOException;
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

    public ResponseLog get(String url) throws Exception {

        return get(url, false, null, null, null);
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
    public ResponseLog get(String url, boolean createLog, String sessionKey, String accountId, String ddrRecordId)
        throws Exception {

        long startTimeStamp = TimeUtils.getServerCurrentTimeInMillis();
        Request request = getBuilderWIthBasicAuthHeader(url).build();
        Response response = null;
        ResponseLog responseLog = null;
        try {
            response = client.newCall(request).execute();
            responseLog = new ResponseLog(response, TimeUtils.getServerCurrentTimeInMillis() - startTimeStamp);
        }
        catch (Exception ex) {
            responseLog = new ResponseLog();
            Long responseTimestamp = TimeUtils.getServerCurrentTimeInMillis() - startTimeStamp;
            responseLog.setHttpResponseTime(responseTimestamp.intValue());
            responseLog.setResponseBody(ex.getLocalizedMessage());
            responseLog.setHttpCode(-1);
        }
        if (createLog && sessionKey != null) {
            RequestLog requestLog = new RequestLog(request);
            if (!ServerUtils.isInUnitTestingEnvironment()) {
                ParallelInit.getLoggerAgent().createLog(requestLog, responseLog, sessionKey, accountId, ddrRecordId,
                                                        response.isSuccessful());
            }
        }
        return responseLog;
    }

    public ResponseLog post(String json, String url) throws Exception {

        return post(json, url, null);
    }

    public ResponseLog post(String json, String url, String type) throws Exception {

        return post(json, url, type, false, null, null, null);
    }

    /**
     * Essentially used to store the logs by passing the sessionKey and
     * accoundId
     * 
     * @param json
     * @param url
     * @param type
     * @param createLog
     * @param sessionKey
     * @param accountId
     * @param ddrRecordId
     * @return
     * @throws IOException
     */
    public ResponseLog post(String json, String url, String type, boolean createLog, String sessionKey, String accountId,
        String ddrRecordId) throws Exception {

        if (type == null) {
            type = "application/json";
        }
        MediaType mediaType = MediaType.parse(type);
        RequestBody body = RequestBody.create(mediaType, json);
        long startTimeStamp = TimeUtils.getServerCurrentTimeInMillis();
        Request request = getBuilderWIthBasicAuthHeader(url).post(body).build();
        Response response = null;
        ResponseLog responseLog = null;
        try {
            response = client.newCall(request).execute();
            responseLog = new ResponseLog(response, TimeUtils.getServerCurrentTimeInMillis() - startTimeStamp);
        }
        catch (Exception ex) {
            responseLog = new ResponseLog();
            Long responseTimestamp = TimeUtils.getServerCurrentTimeInMillis() - startTimeStamp;
            responseLog.setHttpResponseTime(responseTimestamp.intValue());
            responseLog.setResponseBody(ex.toString());
            responseLog.setHttpCode(-1);
        }
        if (createLog && sessionKey != null) {
            RequestLog requestLog = new RequestLog(request);
            ParallelInit.getLoggerAgent().createLog(requestLog, responseLog, sessionKey, accountId, ddrRecordId,
                                                    response.isSuccessful());
        }
        return responseLog;
    }

    /**
     * Essentially used to store the logs by passing the sessionKey and
     * accoundId
     * 
     * @param json
     * @param url
     * @param type
     * @param createLog
     * @param sessionKey
     * @param accountId
     * @param ddrRecordId
     * @return
     * @throws IOException
     */
    public ResponseLog put(String json, String url, String type, boolean createLog, String sessionKey, String accountId,
        String ddrRecordId) throws Exception {

        if (type == null) {
            type = "application/json";
        }
        MediaType mediaType = MediaType.parse(type);
        RequestBody body = RequestBody.create(mediaType, json);
        long startTimeStamp = TimeUtils.getServerCurrentTimeInMillis();
        Request request = getBuilderWIthBasicAuthHeader(url).put(body).build();
        Response response = null;
        ResponseLog responseLog = null;
        try {
            response = client.newCall(request).execute();
            responseLog = new ResponseLog(response, TimeUtils.getServerCurrentTimeInMillis() - startTimeStamp);
        }
        catch (Exception ex) {
            responseLog = new ResponseLog();
            Long responseTimestamp = TimeUtils.getServerCurrentTimeInMillis() - startTimeStamp;
            responseLog.setHttpResponseTime(responseTimestamp.intValue());
            responseLog.setResponseBody(ex.toString());
            responseLog.setHttpCode(-1);
        }
        if (createLog && sessionKey != null) {
            RequestLog requestLog = new RequestLog(request);
            ParallelInit.getLoggerAgent().createLog(requestLog, responseLog, sessionKey, accountId, ddrRecordId,
                                                    response.isSuccessful());
        }
        return responseLog;
    }

    public ResponseLog put(String json, String url, String type) throws Exception {

        return put(json, url, type, false, null, null, null);
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
