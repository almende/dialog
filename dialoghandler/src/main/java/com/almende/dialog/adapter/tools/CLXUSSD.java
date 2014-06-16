package com.almende.dialog.adapter.tools;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.znerd.xmlenc.XMLOutputter;

import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.example.agent.TestServlet;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.thetransactioncompany.cors.HTTPMethod;



public class CLXUSSD {

	public static final String USSD_DELIVERY_STATUS_KEY = "CLX_USSD_Status";
    private static final Logger log = Logger.getLogger(CLXUSSD.class.getName()); 
	private static HashMap<String, Integer> retryCounter = new HashMap<String, Integer>();

	private static String server = "http://93.158.78.4:3800";
	private static String server_bu = "http://195.84.167.34:3800";   // backup http server of clx ussd
	private static String server_ssl = "https://93.158.78.4:3801";   // primary https server of clx ussd
	private static String server_ssl_bu = "https://195.84.167.34:3801";   // backup https server of clx ussd
	private static String localdevelopmentServer = "http://localhost:8082/dialoghandler/rest/xssdTest/";
	private static String keyWord = "AskFastBV_USSDgw0_gpbLurkJ";
	
	
	private AdapterConfig config = null;
	
	private static Integer MAX_RETRY_COUNT = 3;
	
	private final String USER_AGENT = "Mozilla/5.0";
	
	private String userName = "";
	private String password = "";
	private String userID = "";

	
	public CLXUSSD(String userName, String password, AdapterConfig config) {
		this.userName = userName;
		this.password = password;
		this.config = config;
		
	}
	
    public int sendMessage( String message, String subject, String from, String fromName,
        String to, String toName, Map<String, Object> extras,AdapterConfig config ) throws Exception
    {
        
        if(fromName==null)
            fromName = from;

        // TODO: Check message for special chars, if so change dcs.		
        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put( to, toName );
        String reference = null;
        if(extras != null)
        {
            reference = extras.get( USSD_DELIVERY_STATUS_KEY ) != null ? extras.get( USSD_DELIVERY_STATUS_KEY )
                .toString() : null;
        }
        
        Boolean fusion = false;
        if(fusion){
        	this.userName = "ASKFastBV_h_gw4";
        	this.password = "zSY3bXE2";
        }
        
        
        //Change spaces for %20 for the url
        message = message.replace(" ", "%20");
        // construct the URL for the request
        String url = server + "?username=";
        url += userName;
        url += "&password=";
        url += password;
        url += "&from=";
        url += from;
        url += "&to=";
        url += to;
        url += "&text=";
        url += message;

        Client client = ParallelInit.getClient();
		WebResource webResource = client.resource(url  );
		String response = webResource.get(String.class);
		System.out.println("ussd send message response: "+response);
 
        
        return countMessageParts( message );
    }
    
    public int sendXML( String message, String subject, String from, String fromName,
            String to, Map<String, Object> extras, AdapterConfig config ) throws Exception {
    	
    	Client client = ParallelInit.getClient();
    	WebResource webResource = client.resource(server);
    	String result = sendRequestWithRetry( webResource, null, HTTPMethod.POST, message);
    	return 0;
    }


    public String startSubScription(String to, AdapterConfig config) {
    	
    	System.out.println("start subscription");
    	
    	if ( config.getXsiSubscription() != null && !config.getXsiSubscription().equals( "" ) )
        {
			String subId = updateSubscription();
            if ( subId != null )
				return subId;
		}
    	
    	//TODO: setup real url
    	
    	String URL =  server+ "/sendsms?username=ASKFastBV_h_ugw0&password=qMA3gBY5&to="+to+"&text="+keyWord+"&from=31624107792";
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
}
