package com.almende.dialog.util;


import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.appengine.api.utils.SystemProperty;
import com.google.appengine.api.utils.SystemProperty.Environment.Value;


public class ServerUtils
{
    private static final Logger log = Logger.getLogger( ServerUtils.class.getName() );
    
    public static <T> T deserialize( String jsonString, Class<T> DeserializeClass )
    throws Exception
    {
        T deserializedEntity = null;
        if ( jsonString != null && !jsonString.isEmpty() )
        {
            deserializedEntity = new ObjectMapper().readValue( jsonString, DeserializeClass );
        }
        return deserializedEntity;
    }
    
    public static <T> T deserialize( String jsonString, boolean throwException, Class<T> DeserializeClass ) throws Exception
    {
        return deserialize( jsonString, DeserializeClass );
    }

    public static <T> T deserialize( String jsonString, TypeReference<T> type ) throws Exception
    {
        T deserializedEntity = new ObjectMapper().readValue( jsonString, type );
        return deserializedEntity;
    }

    public static String serialize( Object objectToBeSerialized ) throws Exception
    {
        ObjectMapper oMapper = new ObjectMapper();
        oMapper.setSerializationInclusion( Include.NON_NULL );
        String result = null;
        result = oMapper.writeValueAsString( objectToBeSerialized );
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
        String line = null;
        while ( (line = reader.readLine()) != null )
        {
            sb.append( line );
        }
        return new String( sb );
    }

    public static boolean isInLocalDevelopmentServerEnvironment()
    {
        return SystemProperty.environment.value() == Value.Development;
    }

    public static boolean isInDeployedAppspotEnvironment()
    {
        return SystemProperty.environment.value() == Value.Production;
    }

    public static boolean isInUnitTestingEnvironment()
    {
        return SystemProperty.environment.value() == null;
    }
    
    /**
     * returns the url by adding the queryKey=queryValue based on if a query param is 
     * already seen in the url 
     * @return
     */
    public static String getURLWithQueryParams(String url, String queryKey, String queryValue)
    {
        if(url.endsWith( "/" ))
        {
            url = url.substring( 0, url.length() - 1 );
        }
        
        if(url.indexOf( "?" ) > 0)
        {
            url = url + "&";
        }
        else 
        {
            url = url + "?";
        }
        return url + queryKey + "=" + queryValue;
    }
    
    /**
     * associates the same value corresponding to keys listed in keyCollection
     */
    public static <T extends Object> Map<T, T> putCollectionAsKey( Map<T, T> mapToBePopulated, Collection<T> keyCollection, T value )
    {
        for ( T key : keyCollection )
        {
            mapToBePopulated.put( key, value );
        }
        return mapToBePopulated;
    }
}
