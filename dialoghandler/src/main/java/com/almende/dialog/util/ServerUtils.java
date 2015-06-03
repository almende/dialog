package com.almende.dialog.util;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import com.almende.dialog.LogLevel;
import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.agent.DialogAgent;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.util.ParallelInit;
import com.askfast.commons.Status;
import com.askfast.commons.entity.Account;
import com.askfast.commons.entity.Language;
import com.askfast.commons.entity.TTSInfo;
import com.askfast.commons.entity.TTSInfo.TTSProvider;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;


public class ServerUtils
{
    private static final Logger log = Logger.getLogger(DialogAgent.class.getName());
    private static ObjectMapper oMapper = ParallelInit.getObjectMapper();
    
    public static <T> T deserialize( String jsonString, Class<T> DeserializeClass )
    throws Exception
    {
        T deserializedEntity = null;
        if ( jsonString != null && !jsonString.isEmpty() )
        {
            deserializedEntity = oMapper.readValue(jsonString, DeserializeClass);
        }
        return deserializedEntity;
    }
    
    public static <T> T deserialize( String jsonString, boolean throwException, Class<T> DeserializeClass )
    throws Exception
    {
        T deserialized = null;
        try
        {
            deserialized = deserialize( jsonString, DeserializeClass );
        }
        catch ( Exception e )
        {
            if ( throwException )
            {
                throw e;
            }
            else
            {
                log.warning( String.format( "Failed to deserialize %s to class: %s", jsonString,
                    DeserializeClass.getSimpleName() ) );
            }
        }
        return deserialized;
    }

    public static <T> T deserialize( String jsonString, TypeReference<T> type ) throws Exception
    {
        return oMapper.readValue( jsonString, type );
    }

    public static <T> T deserialize( String jsonString, boolean throwException, TypeReference<T> type ) throws Exception
    {
        try
        {
            return oMapper.readValue( jsonString, type );
        }
        catch ( Exception e )
        {
            if(throwException)
            {
                throw e;
            }
            else
            {
                log.severe( e.getLocalizedMessage() );
                return null;
            }
        }
    }

    public static String serializeWithoutException( Object objectToBeSerialized )
    {
        try
        {
            return serialize( objectToBeSerialized );
        }
        catch ( Exception e )
        {
            log.severe( e.getLocalizedMessage() );
            return null;
        }
    }
    
    public static String serialize( Object objectToBeSerialized ) throws Exception
    {
        oMapper.setSerializationInclusion( Include.NON_NULL );
        //oMapper.setSerializationInclusion( Include.NON_EMPTY );
        String result = null;
        if(objectToBeSerialized != null )
        {
            result = oMapper.writeValueAsString( objectToBeSerialized );
        }
        return result;
    }
    
    /**
     * fetches the request data in a string format
     * @return
     * @throws IOException
     */
    public static String getRequestData(HttpServletRequest httpServletRequest) throws IOException
    {
        StringBuffer sb = new StringBuffer();
        BufferedReader reader = httpServletRequest.getReader();
        String line;
        while ( (line = reader.readLine()) != null )
        {
            sb.append( line );
        }
        return new String( sb );
    }

    public static boolean isInDevelopmentEnvironment()
    {
        return Settings.environment() == Settings.Development;
    }

    public static boolean isInProductionEnvironment()
    {
        return Settings.environment() == Settings.Production;
    }

    public static boolean isInUnitTestingEnvironment()
    {
        return (Settings.environment() == null || ParallelInit.isTest);
    }
    
    /**
     * returns the url by adding the queryKey=queryValue based on if a query
     * param is already seen in the url
     * 
     * @return
     * @throws UnsupportedEncodingException
     */
    public static String getURLWithQueryParams(String url, String queryKey, String queryValue)
    throws UnsupportedEncodingException {

        if (queryKey != null && queryValue != null) {
            try {
                url = url.replace(" ", URLEncoder.encode(" ", "UTF-8"));
                URIBuilder uriBuilder = new URIBuilder(new URI(url));
                URIBuilder returnResult = new URIBuilder(new URI(url)).removeQuery();
                returnResult.addParameter(queryKey, queryValue);
                for (NameValuePair nameValue : uriBuilder.getQueryParams()) {

                    if (!nameValue.getName().equals(queryKey)) {
                        returnResult.addParameter(nameValue.getName(), nameValue.getValue());
                    }
                }
                return returnResult.toString();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        return url;
    }
    
    /**
     * Appends all the query params in the given askFastQueryParams to the url
     * @param url
     * @param queryParams
     * @return
     * @throws UnsupportedEncodingException
     */
    public static String getURLWithQueryParams(String url, Map<String, String> queryParams)
        throws UnsupportedEncodingException {

        if (queryParams != null) {
            for (String queryKey : queryParams.keySet()) {
                url = ServerUtils.getURLWithQueryParams(url, queryKey, queryParams.get(queryKey));
            }
        }
        return url;
    }
    
    /**
     * Returns all the query parameters in the url given.
     * @return
     * @throws Exception
     */
    public static HashMap<String, String> getAllQuerParameters(String url) throws Exception {

        url = url.replace(" ", URLEncoder.encode(" ", "UTF-8"));
        URIBuilder uriBuilder = new URIBuilder(new URI(url));
        HashMap<String, String> result = new HashMap<String, String>();
        for (NameValuePair nameValue : uriBuilder.getQueryParams()) {
            result.put(nameValue.getName(), nameValue.getValue());
        }
        return result;
    }
    
    /**
     * associates the same value corresponding to keys listed in keyCollection
     */
    public static <T> Map<T, T> putCollectionAsKey( Collection<T> keyCollection, T value )
    {
        Map<T, T> mapToBePopulated = new HashMap<T, T>();
        for ( T key : keyCollection )
        {
            mapToBePopulated.put( key, value );
        }
        return mapToBePopulated;
    }
    
    /**
     * Simple check to see if the string is empty or null
     * @param stringToCheck
     * @return
     */
    public static boolean isNullOrEmpty(String stringToCheck) {
        if(stringToCheck == null || stringToCheck.isEmpty()) {
            return true;
        }
        return false;
    }
    
    public static String encodeURLParams(String url) {

        try {
            URL remoteURL = new URL(url);
            return new URI(remoteURL.getProtocol(), remoteURL.getUserInfo(), remoteURL.getHost(), remoteURL.getPort(),
                           remoteURL.getPath(), remoteURL.getQuery(), remoteURL.getRef()).toString();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return url;
    }
    
    /**
     * Picks up the bearer token from the session and checks if its valid by
     * confirming with the Keyserver agent. If its not valid, drops the session
     * 
     * @param session
     *            Picks up the accountId and the bearerToken from the session
     * @param dialogLog
     *            Logs for any message
     * @return
     */
    public static boolean isValidBearerToken(Session session, AdapterConfig config, com.almende.dialog.Logger dialogLog) {

        String bearerToken = session.getAllExtras().get(DialogAgent.BEARER_TOKEN_KEY);
        if (!isInUnitTestingEnvironment() && Settings.KEYSERVER != null) {
            
            if(bearerToken != null) {
                return KeyServerLib.checkAccount(session.getAccountId(), bearerToken);
            }
            else if(config != null && Status.ACTIVE.equals(config.getStatus())) {
                return true;
            }
            dialogLog.log(LogLevel.INFO, session.getAdapterConfig(),
                          String.format("Not enough credits to start communication from: %s to: %s",
                                        session.getLocalAddress(), session.getRemoteAddress()), session);
            session.drop();
            return false;
        }
        return true;
    }
    
    /**
     * Gets the insufficient credits messaged based on the language 
     * @param language
     * @return
     */
    public static String getInsufficientMessage(Language language) {
        language = language != null ? language : Language.getByValue(null);
        switch (language) {
            case DUTCH:
                return "Uw account heeft geen tegoed meer.";
            default:
                return "Insufficient credits. Please recharge.";
        }
    }
    
    /**
     * Gets the language based on the following order of precedence <br>
     * 1. {@link Question#getPreferred_language()} <br>
     * 2. {@link TTSInfo#getLanguage()} <br>
     * 3. {@link AdapterConfig#getPreferred_language()} <br>
     * 4. {@link Account#getLanguage()}
     * 
     * @param question
     * @param config
     * @param account
     * @return
     */
    public static Language getLanguage(Question question, TTSInfo ttsInfo, AdapterConfig config, Account account) {

        String language = null;
        if (question != null && question.getPreferred_language() != null) {
            language = question.getPreferred_language();
        }
        else if (ttsInfo != null && ttsInfo.getLanguage() != null) {
            language = ttsInfo.getLanguage().getCode();
        } 
        else if (config != null && config.getPreferred_language() != null) {
            language = config.getPreferred_language();
        }
        else if (account != null && account.getLanguage() != null) {
            return account.getLanguage();
        }
        return Language.getByValue(language);
    }
    
    /**
     * returns the TTS URL from tts.ask-fast
     * 
     * @param ttsInfo
     * 
     * @param textForSpeech
     * @param language
     * @param contentType
     * @return
     */
    public static String getTTSURL(TTSInfo ttsInfo, String textForSpeech, Session session) {

        String language = Language.getByValue(null).getCode();
        String speed = "0";
        String codec = "WAV";
        String format = "8khz_8bit_mono";
        String voice = null;
        String serviceProvider = null;
        String ttsAccountId = null;
        String accountId = null;
        
        if (ttsInfo != null) {
            language = ttsInfo.getLanguage().getCode();
            speed = ttsInfo.getSpeed();
            codec = ttsInfo.getCodec();
            format = ttsInfo.getFormat();
            voice = ttsInfo.getVoiceUsed();
            serviceProvider = ttsInfo.getProvider() != null ? ttsInfo.getProvider().name() : null;
            format = ttsInfo.getFormat();
            ttsAccountId = ttsInfo.getTtsAccountId();
            
            if (ttsAccountId != null && !TTSProvider.VOICE_RSS.equals(ttsInfo.getProvider())) {

                if (session != null) {
                    accountId = session.getAccountId();
                    try {
                        DDRUtils.createDDRForTTSService(ttsInfo.getProvider(), ttsAccountId, session, true);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        log.severe("Applying service charge for TTS processing failed.");
                    }
                }
            }
        }
        textForSpeech = textForSpeech.replace("text://", "");

        String url = "http://tts.ask-fast.com/api/parse";
        try {
            url = ServerUtils.getURLWithQueryParams(url, "text", textForSpeech);
            url = ServerUtils.getURLWithQueryParams(url, "lang", language);
            url = ServerUtils.getURLWithQueryParams(url, "codec", codec);
            url = ServerUtils.getURLWithQueryParams(url, "speed", speed);
            url = ServerUtils.getURLWithQueryParams(url, "format", format);
            url = ServerUtils.getURLWithQueryParams(url, "voice", voice);
            url = ServerUtils.getURLWithQueryParams(url, "service", serviceProvider);
            url = ServerUtils.getURLWithQueryParams(url, "id", ttsAccountId);
            url = ServerUtils.getURLWithQueryParams(url, "askFastAccountId", accountId);
            url += "&type=.wav";
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        return url;
    }
    
    /**
     * Returns a ttsInfo if it finds one in the session. If not returns a new
     * instance with the language format got from:
     * {@link ServerUtils#getLanguage(Question, TTSInfo, AdapterConfig, Account)}
     * 
     * @param session
     * @return
     */
    public static TTSInfo getTTSInfoFromSession(Question question, Session session) {

        TTSInfo ttsInfo = Dialog.getTTSInfoFromSession(session);
        question = question != null ? question : session != null ? session.getQuestion() : null;
        Language language = getLanguage(question, ttsInfo, session != null ? session.getAdapterConfig() : null, null);
        if (ttsInfo != null) {
            ttsInfo.setLanguage(language);
        }
        else {
            ttsInfo = new TTSInfo();
            ttsInfo.setLanguage(language);
        }
        return ttsInfo;
    }

    /**
     * Returns a ttsInfo if it finds one in the session. If not returns a new
     * instance with the language format got from:
     * {@link ServerUtils#getLanguage(Question, TTSInfo, AdapterConfig, Account)}
     * 
     * @param session
     * @return
     */
    public static TTSInfo getTTSInfoFromSession(Question question, String sessionKey) {

        return getTTSInfoFromSession(question, Session.getSession(sessionKey));
    }
}
