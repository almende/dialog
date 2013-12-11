package com.almende.dialog.adapter.tools;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.util.ParallelInit;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.thetransactioncompany.cors.HTTPMethod;

public class Broadsoft {
	
	private static final Logger log = Logger.getLogger(Broadsoft.class.getName());
	protected static final com.almende.dialog.Logger dialogLog = new com.almende.dialog.Logger();
	
	public static final String XSI_URL="http://xsp.voipit.nl";
	public static final String XSI_ACTIONS="/com.broadsoft.xsi-actions/v2.0/user/";
	public static final String XSI_EVENTS="/com.broadsoft.xsi-events/v1.0/";
	public static final String XSI_START_CALL="/calls/new";
	public static final String XSI_CALLS="/calls";
	public static final String XSI_HIDE_CALLER_ID="/services/CallingLineIDDeliveryBlocking";
	
	private String user = null;
	private HTTPBasicAuthFilter auth = null;
	private Client client = null;
	private AdapterConfig config = null;
	private static HashMap<String, Integer> retryCounter = new HashMap<String, Integer>();
	private static Integer MAX_RETRY_COUNT = 3;
	
	public Broadsoft(AdapterConfig config) {
		
		this.config = config;
		this.user = config.getXsiUser();
		this.auth = new HTTPBasicAuthFilter(config.getXsiUser(), config.getXsiPasswd());
		
		client = ParallelInit.getClient();
	}
	
    public String startCall( String address )
    {
        String formattedAddress = new String( address );
        WebResource webResource = client.resource( XSI_URL + XSI_ACTIONS + user + XSI_START_CALL );
        webResource.addFilter( this.auth );
        
        if(retryCounter.get( webResource.toString() ) == null)
        {
            retryCounter.put( webResource.toString(), 0 );
        }
        HashMap<String, String> queryKeyValue = new HashMap<String, String>();
        try
        {
            queryKeyValue.put( "address", URLEncoder.encode( formattedAddress, "UTF-8" ) );
        }
        catch ( UnsupportedEncodingException ex )
        {
            log.severe( "Problems dialing out:" + ex.getMessage() );
            dialogLog.severe( config.getConfigId(),
                "Failed to start call to: " + formattedAddress + " Error: " + ex.getMessage() );
        }
        
        while ( retryCounter.get( webResource.toString() ) != null && retryCounter.get( webResource.toString() ) < MAX_RETRY_COUNT )
        {
            try
            {
                String result = sendRequestWithRetry(webResource, queryKeyValue, HTTPMethod.POST, null);
                log.info( "Result from BroadSoft: " + result );
                dialogLog.info( config.getConfigId(), "Start outbound call to: " + formattedAddress );
                //flush retryCount
                retryCounter.remove( webResource.toString() );
                return getCallId( result );
            }
            catch ( Exception ex )
            {
                Integer retry = retryCounter.get( webResource.toString() );
                log.severe( "Problems dialing out:" + ex.getMessage() );
                dialogLog.severe(
                    config.getConfigId(),
                    String.format( "Failed to start call to: %s Error: %s. Retrying! Count: %s", formattedAddress,
                        ex.getMessage(), retry ) );
                retryCounter.put( webResource.toString(), ++retry );
            }
        }
        return null;
    }
	
    public String startSubscription()
    {
        // Extend the current subscription
        if ( config.getXsiSubscription() != null && !config.getXsiSubscription().equals( "" ) )
        {
            String subId = updateSubscription();
            if ( subId != null )
                return subId;
        }

        // If there is no subscription or the extending failed, create a new one.
        WebResource webResource = client.resource( XSI_URL + XSI_EVENTS + "user/" + user );
        webResource.addFilter( this.auth );
        final String EVENT_LEVEL = "Advanced Call 2";
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
            + "<Subscribe xmlns=\"http://schema.broadsoft.com/xsi-events\"> " + "  <event>" + EVENT_LEVEL + "</event> "
            + "  <contact transport=\"http\"> " + "    <uri>http://" + Settings.HOST + "/vxml/cc</uri> "
            + "  </contact> " + "  <expires>100</expires> " + "  <applicationId>cc</applicationId> " + "</Subscribe> ";

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
                String result = sendRequestWithRetry( webResource, queryKeyValue, HTTPMethod.POST, xml );
                log.info( "Subscription result from BroadSoft: " + result );
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
                log.severe( "Problems dialing out:" + ex.getMessage() );
                Integer retry = retryCounter.get( webResource.toString() );
                retryCounter.put( webResource.toString(), ++retry );
            }
        }
        return null;
    }
	
    public String updateSubscription()
    {
        WebResource webResource = client
            .resource( XSI_URL + XSI_EVENTS + "subscription/" + config.getXsiSubscription() );
        webResource.addFilter( this.auth );
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
            + "<SubscriptionUpdate xmlns=\"http://schema.broadsoft.com/xsi-events\"> " + "  <expires>3600</expires> "
            + "</SubscriptionUpdate>";
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
                String result = sendRequestWithRetry( webResource, queryKeyValue, HTTPMethod.PUT, xml );
                log.info( "Subscription result from BroadSoft: " + result );
                //flush retryCount
                retryCounter.remove( webResource.toString() );
                log.info( "Subscription result from BroadSoft: " + result );

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
                log.severe( "Problems updating subscription:" + ex.getMessage() );
                Integer retry = retryCounter.get( webResource.toString() );
                retryCounter.put( webResource.toString(), ++retry );
            }
        }
        return null;
    }
	
	public boolean endCall(String callId) {
		
		WebResource webResource = client.resource(XSI_URL+XSI_ACTIONS+user+XSI_CALLS+"/"+callId);
		webResource.addFilter(this.auth);
		try {
			webResource.delete();
			
			log.info("Call ended: "+callId);
			return true;
		} catch (Exception e) {
			log.severe("Problems dialing out:"+e.getMessage());
		}
		
		return false;
	}
	
	public ArrayList<String> getActiveCalls() {
		WebResource webResource = client.resource(XSI_URL+XSI_ACTIONS+user+XSI_CALLS);
		webResource.addFilter(this.auth);
		try {
			String result = webResource.type("text/plain").get(String.class);
			return getCallIDsFromXML(result);
		} catch (Exception e) {
			log.severe("Problems dialing out:"+e.getMessage());
		}
		
		return null;
	}
	
	public ArrayList<String> getActiveCallsInfo() {
		
		ArrayList<String> callInfo = new ArrayList<String>();
		ArrayList<String> ids = getActiveCalls();
		for(String id : ids) {
			callInfo.add(getCallInfo(id));
		}
		return callInfo;
	}
	
	public boolean killActiveCalls() {
		
		ArrayList<String> ids = getActiveCalls();
		for(String id : ids) {
			endCall(id);
		}
		
		return true;
	}
	
	public String getCallInfo(String callId) {
		WebResource webResource = client.resource(XSI_URL+XSI_ACTIONS+user+XSI_CALLS+"/"+callId);
		webResource.addFilter(this.auth);
		try {
			return webResource.type("text/plain").get(String.class);
		} catch (Exception e) {
			log.severe("Problems dialing out:"+e.getMessage());
		}
		return "";
	}
	
	public void hideCallerId(boolean hide) {
		
		String hci = "false";
		if(hide)
			hci="true";
		
		String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?> "+
					 "<CallingLineIDDeliveryBlocking xmlns=\"http://schema.broadsoft.com/xsi\">" +
					   "<active>"+hci+"</active>" +
					 "</CallingLineIDDeliveryBlocking>";
		
		
		WebResource webResource = client.resource(XSI_URL+XSI_ACTIONS+user+XSI_HIDE_CALLER_ID);
		webResource.addFilter(this.auth);
		
		try {
			String result = webResource.put(String.class, xml);
			log.info("Result from BroadSoft: "+result);
		} catch (Exception e) {
			log.severe("Problems hidden caller id out:"+e.getMessage());
			log.severe("XML: "+xml);
		}
	}
	
	private String getSubscriptionId(String xml) {
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			
			Document dom = db.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
			Node root = dom.getDocumentElement();
			
			if(root.getNodeName().equals("ErrorInfo")) {
				return null;
			}
			
			Node sub = root.getFirstChild();
			
			return sub.getTextContent();
			
		} catch(Exception ex){
			ex.printStackTrace();
		}
		
		return null;
	}
	
	private String getCallId(String xml) {
		int startPos = xml.indexOf("callId>");
		int endPos = xml.indexOf("</callId");
		
		String callId = xml.substring(startPos+7, endPos);
		
		return callId;
	}
	
	private ArrayList<String> getCallIDsFromXML(String xml) {
		
		ArrayList<String> ids = new ArrayList<String>();
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			
			Document dom = db.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
			NodeList list = dom.getDocumentElement().getChildNodes();
			for(int i=0;i<list.getLength();i++) {
				Node call = list.item(i).getFirstChild();
				ids.add(call.getTextContent());
			}
		} catch(Exception ex){
			ex.printStackTrace();
		}
		
		return ids;
	}
	
    private String sendRequestWithRetry( WebResource webResource, Map<String, String> queryKeyValue, HTTPMethod method, String payload )
    throws UnsupportedEncodingException
    {
        for ( String queryKey : queryKeyValue.keySet() )
        {
            webResource = webResource.queryParam( queryKey, queryKeyValue.get( queryKey ) );
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
