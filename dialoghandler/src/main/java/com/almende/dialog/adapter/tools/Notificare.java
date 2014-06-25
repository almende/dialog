package com.almende.dialog.adapter.tools;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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
import com.thetransactioncompany.cors.HTTPMethod;

public class Notificare {
	
    static final ObjectMapper om = ParallelInit.getObjectMapper();
	private static final Integer MAX_RETRY_COUNT = 3;
    private static HashMap<String, Integer> retryCounter = new HashMap<String, Integer>();
    private static String serverUrlDevice = "https://push.notifica.re/notification/user/";
    private static String serveletpath = "sandbox.ask-fast.com/dialoghandler/push";
    private static final Logger log = Logger.getLogger(Notificare.class.getName()); 

	public Notificare(){

	}
	
    public int sendMessage( String message, String subject, String from, String fromName,
            String to, String toName, Map<String, Object> extras, AdapterConfig config ) throws Exception {
    		
    		HashMap<String, Object> jsonMap = new HashMap<String, Object>();
    		jsonMap.put("message", message);
    		jsonMap.put( "sound","default");
    		
    		Client client = ParallelInit.getClient();
    		WebResource webResource = client.resource("");
    		// if the question type is 'open' prepare an html form with a reaction field to push to the client.
    		if( extras.get("questionType").equals("open") && !to.isEmpty()){
    			
    			Object[] content = new Object[1];
    			HashMap<String, Object> contentMap = new HashMap<String, Object>();
    			contentMap.put("type", "re.notifica.content.HTML");
    			contentMap.put("data",makeHtml(message, (String) extras.get("sessionKey")));
    			content[0]=contentMap;
        		jsonMap.put("type","re.notifica.notification.WebView");
    			jsonMap.put("content", content);
    			webResource = client.resource(serverUrlDevice+URLEncoder.encode(to,"UTF-8"));
    		}
    		//is it a closed question prepare a menu with 2 callback buttons. 
    		else if(extras.get("questionType").equals("closed") && !to.isEmpty()){
    			
    			Object[] actions = new Object[2];
    			HashMap<String, Object> noMap = new HashMap<String, Object>();
    			HashMap<String, Object> yesMap = new HashMap<String, Object>();
    			yesMap.put("type","re.notifica.action.Callback");
    			yesMap.put("label", "Yes");
    			yesMap.put("target", String.format(serveletpath+"?answer=Yes&sessionkey=%s",
    					URLEncoder.encode((String) extras.get("sessionKey"),"UTF-8")));
    			noMap.put("type", "re.notifica.action.Callback");
    			noMap.put("label", "No");
    			noMap.put("target",String.format(serveletpath+"?answer=No&sessionkey=%s",
    					URLEncoder.encode((String) extras.get("sessionKey"),"UTF-8")) );
    			actions[0]=yesMap;
    			actions[1]=noMap;
        		jsonMap.put("type","re.notifica.notification.Alert");
    			jsonMap.put("actions", actions);
    			webResource = client.resource(serverUrlDevice+URLEncoder.encode(to,"UTF-8"));
    		}
    		// if it is a broadcast change url
    		else{
        		jsonMap.put("type","re.notifica.notification.Alert");
    			webResource = client.resource(serverUrlDevice+URLEncoder.encode(to,"UTF-8"));
    		}

    		
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
                    log.info( "Result from Notificare: " + result );
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
    
    private String makeHtml(String message, String sessionKey) throws UnsupportedEncodingException{
    	
    	String html = " <html> <head> <style> "
    			+ ".basic-grey { "
    				+ "width: 80%;"
    				+ "margin-right: auto;"
    				+ " margin-left: auto; "
    				+ "background: #EEE; "
    				+ "padding: 20px 30px 20px 30px;"
    				+ "font: 12px Georgia, \"Times New Roman\", Times, serif;"
    				+ "color: #888;     text-shadow: 1px 1px 1px #FFF;"
    				+ "border:1px solid #DADADA; } "
    			+ ".basic-grey h1 { "
    				+ "font: 25px Georgia, \"Times New Roman\", "
    				+ "Times, serif;"
    				+ "padding: 0px 0px 10px 40px;"
    				+ "display: block;     "
    				+ "border-bottom: 1px solid #DADADA;     "
    				+ "margin: -10px -30px 30px -30px;     "
    				+ "color: #888; } "
    			+ ".basic-grey h1>span {     "
    				+ "display: block;     "
    				+ "font-size: 11px; } "
    			+ ".basic-grey label {     "
    				+ "display: block;     "
    				+ "margin: 0px 0px 5px; } "
    			+ ".basic-grey label>span {     "
    				+ "float: left;     "
    				+ "width: 80px;     "
    				+ "text-align: right;     "
    				+ "padding-right: 5px;     "
    				+ "margin-top: 10px;     "
    				+ "color: #888; } "
    			+ ".basic-grey input[type=\"text\"], "
    			+ ".basic-grey input[type=\"email\"], "
    			+ ".basic-grey textarea,.basic-grey select{   "
    				+ "padding-right: 5px;  border: 1px solid #DADADA;     "
    				+ "color: #888;     height: 24px;     margin-bottom: 16px;     "
    				+ "margin-right: 6px;     margin-top: 2px;     outline: 0 none;     "
    				+ "padding: 3px 3px 3px 5px;    "
    				+ "width: 70%;     "
    				+ "font: normal 12px/12px Georgia, \"Times New Roman\", Times, serif; }"
    			+ ".basic-grey select {     "
    				+ "appearance:none;     "
    				+ "-webkit-appearance:none;     "
    				+ "-moz-appearance: none;     text-indent: 0.01px;     "
    				+ "text-overflow: '';     "
    				+ "width: 72%;     "
    				+ "height: 30px; } "
    				+ ".basic-grey textarea{     "
    				+ "height:100px; } "
    			+ ".basic-grey .button {     "
    				+ "background: #E48F8F;     "
    				+ "border: none;     "
    				+ "padding: 10px 25px 10px 25px;     "
    				+ "color: #FFF;  } "
    			+ ".basic-grey .button:hover {     "
    			+ "background: #CF7A7A } "
    			+ "</style> "
    			+ "</head> "
    			+ "<body> "
    			+ "<form action=\"http://askfastvincent.ngrok.com/dialoghandler/push\" method=\"post\" class=\"basic-grey\">     "
    			+ "<h1>"+URLEncoder.encode(message, "UTF-8")+"</h1>     "
    			+ "<label>         <span>answer :</span>         "
    			+ "<input id=\"name\" type=\"text\" name=\"answer\"  /> "
    			+ "<input type=\"hidden\" name=\"sessionkey\" value=\""+URLEncoder.encode(sessionKey,"UTF-8")+"\">     </label>     "
    			+ " <label>         <span>&nbsp;</span>         "
    			+ "<input type=\"submit\" class=\"button\" value=\"Send\" />     </label>    "
    			+ "</form> "
    			+ "</body> "
    			+ "</html>";
    	
    	return html;
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
