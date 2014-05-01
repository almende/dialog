package com.almende.dialog.util;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import com.almende.dialog.Settings;
import com.almende.dialog.agent.DialogAgent;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;


public class ServerUtils
{
    private static final String serverTimezone = "Europe/Amsterdam";
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

    public static boolean isInLocalDevelopmentServerEnvironment()
    {
        return Settings.environment() == Settings.Development;
    }

    public static boolean isInDeployedAppspotEnvironment()
    {
        return Settings.environment() == Settings.Production;
    }

    public static boolean isInUnitTestingEnvironment()
    {
        return (Settings.environment() == null || ParallelInit.isTest);
    }
    
    /**
     * returns the url by adding the queryKey=queryValue based on if a query param is 
     * already seen in the url 
     * @return
     * @throws UnsupportedEncodingException 
     */
    public static String getURLWithQueryParams( String url, String queryKey, String queryValue ) throws UnsupportedEncodingException
    {
        String copyURL = new String( url );
        if ( copyURL.endsWith( "/" ) || copyURL.endsWith( URLEncoder.encode( "/" ,"UTF-8") ) )
        {
            copyURL = copyURL.substring( 0, copyURL.length() - 1 );
        }

        if ( ( copyURL.indexOf( "?" ) > 0 || copyURL.indexOf( URLEncoder.encode( "?" ,"UTF-8" ) ) > 0 )
            && !copyURL.endsWith( "?" ) )
        {
            copyURL = copyURL + "&";
        }
        else
        {
            copyURL = copyURL + "?";
        }
        return copyURL + queryKey + "=" + queryValue;
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
    
    public static DateTime getServerCurrentTime()
    {
        return DateTime.now( getServerDateTimeZone() );
    }
    
    public static String getStringFormatFromDateTime( long pDateTime, String format )
    {
        return new SimpleDateFormat( format ).format( new Date( pDateTime ) );
    }
    
    public static DateTimeZone getServerDateTimeZone()
    {
        return DateTimeZone.forID( serverTimezone );
    }

    public static long getServerCurrentTimeInMillis()
    {
        return getServerCurrentTime().getMillis();
    }
}
