
package com.almende.dialog.adapter.tools;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Session;
import com.almende.util.ParallelInit;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.config.DefaultClientConfig;
import com.sun.jersey.client.urlconnection.HTTPSProperties;
import com.thetransactioncompany.cors.HTTPMethod;



public class CLXUSSD {

	public static final String USSD_DELIVERY_STATUS_KEY = "CLX_USSD_Status";
    private static final Logger log = Logger.getLogger(CLXUSSD.class.getName()); 
	private static HashMap<String, Integer> retryCounter = new HashMap<String, Integer>();

//	private static String server = "http://93.158.78.4:3800";
//	private static String server_bu = "http://195.84.167.34:3800";   // backup http server of clx ussd
	private static String server_ssl = "https://93.158.78.4:3801";   // primary https server of clx ussd
//	private static String server_ssl_bu = "https://195.84.167.34:3801";   // backup https server of clx ussd
//	private static String localdevelopmentServer = "http://localhost:8082/dialoghandler/rest/xssdTest/";
	private static String keyWord = "AskFastBV_USSDgw0_gpbLurkJ";
	private static Integer MAX_RETRY_COUNT = 3;
	
	
    public int sendMessage( String message, String subject, String from, String fromName,
        String to, String toName, Map<String, Object> extras,AdapterConfig config ) throws Exception
    {
        
        if(fromName==null || fromName=="")
            fromName = from;

        // construct the URL for the request
        String url = server_ssl + "/sendsms?username=";
        url += URLEncoder.encode(config.getAccessToken(), "UTF-8") ;
        url += "&password=";
        url += URLEncoder.encode(config.getAccessTokenSecret(), "UTF-8");
        url += "&from=";
        url += URLEncoder.encode(from, "UTF-8");
        url += "&to=";
        url += URLEncoder.encode(to, "UTF-8");
        url += "&text=";
        url += URLEncoder.encode(message, "UTF-8");

        Client client = hostIgnoringClient();
		WebResource webResource = client.resource(url  );

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
                String result = sendRequestWithRetry( webResource, queryKeyValue, HTTPMethod.GET,null);
                log.info( "Send result from USSD: " + result + " url: "+url );
                retryCounter.remove( webResource.toString() );
                break;
            }
            catch ( Exception ex )
            {
                log.severe( "Problems getting USSD connection out:" + ex.getMessage() + " url: "+ url );
                Integer retry = retryCounter.get( webResource.toString() );
                retryCounter.put( webResource.toString(), ++retry );
            }
            finally{
                client.destroy();
            }
		}
        
        return countMessageParts( message );
    }
    
    // Not used 
    public int sendXML( String message, String subject, String from, String fromName,
            String to, Map<String, Object> extras, AdapterConfig config ) throws Exception {
//    	
//    	Client client = ParallelInit.getClient();
//    	WebResource webResource = client.resource(server_ssl);
//    	//String result = sendRequestWithRetry( webResource, null, HTTPMethod.POST, message);
    	return 0;
    }
    
	public Client hostIgnoringClient() {
		try {
			SSLContext sslcontext = SSLContext.getInstance("TLS");
			sslcontext.init(null, null, null);
			DefaultClientConfig config = new DefaultClientConfig();
			Map<String, Object> properties = config.getProperties();
			HTTPSProperties httpsProperties = new HTTPSProperties(
					new HostnameVerifier() {

						@Override
						public boolean verify(String s, SSLSession sslSession) {
							return true;
						}
					}, sslcontext);
			properties.put(HTTPSProperties.PROPERTY_HTTPS_PROPERTIES,
					httpsProperties);
			config.getClasses().add(CLXUSSD.class);
			return Client.create(config);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

    
    // not used CLX does not allow for this
    public String startSubScription(String to, AdapterConfig config) {
    	
    	System.out.println("start subscription");
    	
    	if ( config.getXsiSubscription() != null && !config.getXsiSubscription().equals( "" ) )
        {
			String subId = updateSubscription();
            if ( subId != null )
				return subId;
		}
    	
    	//TODO: setup real url
    	
    	String URL =  server_ssl+ "/sendsms?username=ASKFastBV_h_ugw0&password=qMA3gBY5&to="+to+"&text="+keyWord+"&from=31624107792";
    	System.out.println(URL);
    	
    	 Client client = ParallelInit.getClient();
		WebResource webResource = client.resource(URL  );
         
		
		
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
                 String result = sendRequestWithRetry( webResource, queryKeyValue, HTTPMethod.GET, null);
                 log.info( "Subscription result from USSD: " + result );
                 //flush retryCount
                 retryCounter.remove( webResource.toString() );
                 String subId = getSubscriptionId( result );
                 if ( subId != null )
                 {
                     if ( AdapterConfig.updateSubscription( config.getConfigId(), subId ) )
                    	
 					return subId;
 			}
                 break;
             }
             catch ( Exception ex )
             {
                 log.severe( "Problems getting USSD connection out:" + ex.getMessage() );
                 Integer retry = retryCounter.get( webResource.toString() );
                 retryCounter.put( webResource.toString(), ++retry );
             }
 		}
		return null;
    	
    }
    
    // not used because CLX api does not allow for this
    private String getSubscriptionId(String xml) {
    	try {
    		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			
			Document dom = db.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
			Node root = dom.getDocumentElement();
			NodeList subscriptionIdNode = dom.getElementsByTagName("data" );
			if(root.getNodeName().equals("ErrorInfo")) {
				return null;
			}
			else if( subscriptionIdNode != null && subscriptionIdNode.item(0 ) != null)
			{
			    return subscriptionIdNode.item(9 ).getTextContent();
			}
		} catch(Exception ex){
			ex.printStackTrace();
		}
		return null;
	}

	public String updateSubscription() {
		return null;
    	//TODO: implement
    }
        
    /**
     * create CLX XML request. This is refactored from {@link CLXUSSD#sendMessage} /
     * {@link CLXUSSD#broadcastMessage}
     * 
     * @since 9/9/2013 for v0.4.0
     * @return
     */
    @SuppressWarnings("unused")
	private StringWriter createXMLRequest( String message, String from, String fromName, String reference,
        Map<String, String> addressNameMap, String dcs )
    {
   	      
    	// TODO: implement the XML body for USSD Menu from CLX
        StringWriter sw = new StringWriter();
        return sw;
    }
	
	public boolean isGSMSeven(CharSequence str0) {
        if (str0 == null) {
            return true;
        }

        int len = str0.length();
        for (int i = 0; i < len; i++) {
            // get the char in this string
            char c = str0.charAt(i);
            // simple range checks for most common characters (0x20 -> 0x5F) or (0x61 -> 0x7E)
            if ((c >= ' ' && c <= '_') || (c >= 'a' && c <= '~')) {
                continue;
            } else {
                // 10X more efficient using a switch statement vs. a lookup table search
                switch (c) {
                    case '\u00A3':	// £
                    case '\u00A5':	// ¥
                    case '\u00E8':	// è
                    case '\u00E9':	// é
                    case '\u00F9':	// ù
                    case '\u00EC':	// ì
                    case '\u00F2':	// ò
                    case '\u00C7':	// Ç
                    case '\n':          // newline
                    case '\u00D8':	// Ø
                    case '\u00F8':	// ø
                    case '\r':          // carriage return
                    case '\u00C5':	// Å
                    case '\u00E5':	// å
                    case '\u0394':	// Δ
                    case '\u03A6':	// Φ
                    case '\u0393':	// Γ
                    case '\u039B':	// Λ
                    case '\u03A9':	// Ω
                    case '\u03A0':	// Π
                    case '\u03A8':	// Ψ
                    case '\u03A3':	// Σ
                    case '\u0398':	// Θ
                    case '\u039E':	// Ξ
                    case '\u00C6':	// Æ
                    case '\u00E6':	// æ
                    case '\u00DF':	// ß
                    case '\u00C9':	// É
                    case '\u00A4':	// ¤
                    case '\u00A1':	// ¡
                    case '\u00C4':	// Ä
                    case '\u00D6':	// Ö
                    case '\u00D1':	// Ñ
                    case '\u00DC':	// Ü
                    case '\u00A7':	// §
                    case '\u00BF':	// ¿
                    case '\u00E4':	// ä
                    case '\u00F6':	// ö
                    case '\u00F1':	// ñ
                    case '\u00FC':	// ü
                    case '\u00E0':	// à
                    case '\u20AC':	// €
                        continue;
                    default:
                        return false;
                }
            }
        }
        return true;
    }
	
	private int countMessageParts(String message) {

		// TODO: implement the character counts 
		int count = 0;
		return count;
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
			                    result = webResource.type( "text/plain" ).post( String.class, payload );
			                }
			                else
			                {
			                    result = webResource.type( "text/plain" ).post( String.class );
			                }
			                break;
			            case PUT:
			                if(payload != null)
			                {
			                    result = webResource.type( "text/plain" ).put( String.class, payload );
			                }
			                else
			                {
			                    result = webResource.type( "text/plain" ).put( String.class );
			                }
			                break;
			            case GET:
			                result = webResource.type( "text/plain" ).get( String.class );
			                break;
			            default:
			                break;
			        }
			        return result;
			    }

	public static Session createSession(String sessionKey) {
		// TODO Auto-generated method stub
		return Session.storeString(sessionKey, "");
	}
}
