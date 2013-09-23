package com.almende.dialog.util;


import com.almende.util.ParallelInit;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.appengine.api.utils.SystemProperty;
import com.google.appengine.api.utils.SystemProperty.Environment.Value;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;


public class ServerUtils
{
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
    
    public static <T> T deserialize( String jsonString, boolean throwException, Class<T> DeserializeClass ) throws Exception
    {
        return deserialize( jsonString, DeserializeClass );
    }

    public static <T> T deserialize( String jsonString, TypeReference<T> type ) throws Exception
    {
        return oMapper.readValue( jsonString, type );
    }

    public static String serialize( Object objectToBeSerialized ) throws Exception
    {
        oMapper.setSerializationInclusion( Include.NON_NULL );
        String result = oMapper.writeValueAsString( objectToBeSerialized );
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
