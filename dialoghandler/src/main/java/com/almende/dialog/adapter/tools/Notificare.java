package com.almende.dialog.adapter.tools;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.ws.rs.core.MediaType;

import org.apache.http.HttpException;

import com.almende.dialog.Settings;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.NotificareServlet;
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
    private static String serveletpath = Settings.HOST+"/dialoghandler"+NotificareServlet.servletPath;
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
    			if(!subject.isEmpty()){
    				jsonMap.replace("message", subject);
    			}
    			
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
                    if(retryCounter.get( webResource.toString() ) == MAX_RETRY_COUNT){
                    	throw new HttpException(ex.getMessage());
                    }
                }
    		}  
            return 0; 
        }
    
    private String makeHtml(String message, String sessionKey) throws UnsupportedEncodingException{
    	
    	String html = "<html> "
    			+ "<head> "
    			+ "<link rel=\"stylesheet\" type=\"text/css\" href=\"http://static.ask-cs.com/askfast/base.css\"> "
    			+ "<link rel=\"stylesheet\" type=\"text/css\" href=\"hhttp://static.ask-cs.com/askfast/layout.css\"> "
    			+ "<link rel=\"stylesheet\" type=\"text/css\" href=\"http://static.ask-cs.com/askfast/skeleton.css\"> "
    			+ "</head> <div class=\"container\" padding-top=100px>"
    			+ "<div class=\"sixteen columns\">"
    			+ "<h4>"+message+"</h4> "
    			+ "<div class=\"sixteen columns\">"
    			+ "<form action=\""+serveletpath+"\" method=\"post\">     <!-- Label and text input -->  <hr >  "
    			+ "<label for=\"regularInput\">answer</label>   "
    			+ "<input type=\"text\" name=\"answer\" />   "
    			+ "<input type=\"hidden\" name=\"sessionkey\" value=\""+sessionKey+"\"/>"
    			+ "<button type=\"submit\">Submit Form</button>  "
    			+ "</form></div> </div></div>"
    			+ "</body><html>";
    	
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
