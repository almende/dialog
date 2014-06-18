package com.almende.dialog.adapter.tools;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.ws.rs.core.MediaType;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.api.client.filter.LoggingFilter;
import com.thetransactioncompany.cors.HTTPMethod;

public class Notificare {
	
    static final ObjectMapper om = ParallelInit.getObjectMapper();
	private static final Integer MAX_RETRY_COUNT = 3;
    private static HashMap<String, Integer> retryCounter = new HashMap<String, Integer>();
    private static String serverUrl = "https://push.notifica.re/notification/broadcast";
    private static final Logger log = Logger.getLogger(Notificare.class.getName()); 

	
	public Notificare(){

	}
	
    public int sendMessage( String message, String subject, String from, String fromName,
            String to, String toName, Map<String, Object> extras, AdapterConfig config ) throws Exception {
    		
    		HashMap<String, Object> jsonMap = new HashMap<String, Object>();
    		jsonMap.put("message", message);
    		jsonMap.put("type","re.notifica.notification.Alert");
    		jsonMap.put("ttl","3600");
    		jsonMap.put( "sound","default");
    		jsonMap.put( "schedule", "true");    		
    		
    		Client client = ParallelInit.getClient();
    		client.addFilter(new LoggingFilter(System.out));
    		WebResource webResource = client.resource(serverUrl) ;
    		
    		webResource.addFilter(new HTTPBasicAuthFilter(config.getAccessToken(),config.getAccessTokenSecret() ));
            
            if ( retryCounter.get( webResource.toString() ) == null )
            {
                retryCounter.put( webResource.toString(), 0 );
            }
            HashMap<String, String> queryKeyValue = new HashMap<String, String>();

            while ( retryCounter.get( webResource.toString() ) != null
                && retryCounter.get( webResource.toString() ) < MAX_RETRY_COUNT )
            {
                try
                {
                    String result = sendRequestWithRetry( webResource, queryKeyValue, HTTPMethod.POST, om.writeValueAsString(jsonMap));
                    log.info( "Subscription result from Notificare: " + result );
                    retryCounter.remove( webResource.toString() );
                    break;
                }
                catch ( Exception ex )
                {
                    log.severe( "Problems getting Notificare connection out:" + ex.getMessage() );
                    Integer retry = retryCounter.get( webResource.toString() );
                    retryCounter.put( webResource.toString(), ++retry );
                }
    		}  
            return 0; 
        }
    
    private String sendRequestWithRetry( WebResource webResource, Map<String, String> queryKeyValue, HTTPMethod method, String payload )
		    throws UnsupportedEncodingException
		    {
				if (queryKeyValue != null) {
					for (String queryKey : queryKeyValue.keySet()) {
						webResource = webResource.queryParam(queryKey,
								queryKeyValue.get(queryKey));
					}
				}
		        String result = null;
		        switch ( method )
		        {
		            case POST:
		                if(payload != null)
		                {
		                    result = webResource.type( MediaType.APPLICATION_JSON).post( String.class, payload );
		                }
		                else
		                {
		                    result = webResource.type( MediaType.APPLICATION_JSON ).post( String.class );
		                }
		                break;
		            case PUT:
		                if(payload != null)
		                {
		                    result = webResource.type( MediaType.APPLICATION_JSON ).put( String.class, payload );
		                }
		                else
		                {
		                    result = webResource.type( MediaType.APPLICATION_JSON ).put( String.class );
		                }
		                break;
		            case GET:
		                result = webResource.type( MediaType.APPLICATION_JSON ).get( String.class );
		                break;
		            default:
		                break;
		        }
		        return result;
		    }
	
}
