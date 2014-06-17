package com.almende.dialog.adapter.tools;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.FacebookApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.oauth.OAuthService;

import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class Facebook {
	private static final Logger log = Logger.getLogger(Facebook.class.getName());
	
	public static final String OAUTH_KEY = "140987596051826";
	public static final String OAUTH_SECRET = "86a60ac205c8618129966deaa7e8c559";
	
	private OAuthService service;
	private Token accessToken;
	
	public Facebook(Token accessToken) {
		this(accessToken, null);
	}
	
	public Facebook(Token accessToken, String callback){
		ServiceBuilder sb = new ServiceBuilder()
        .provider(FacebookApi.class)
        .apiKey(Facebook.OAUTH_KEY)
        .apiSecret(Facebook.OAUTH_SECRET);
		
		if(callback!=null) {
			sb.callback(callback);
		}
        
		service = sb.build();
		
		this.accessToken = accessToken;
	}
	
	public ArrayNode getWallMessages(String since) {
		
		String url = "https://graph.facebook.com/me/feed";
		OAuthRequest request = new OAuthRequest(Verb.GET, url);
		if(since!=null && Long.parseLong(since) > 0)
			request.addQuerystringParameter("since", since);
		service.signRequest(accessToken, request);
		Response response = request.send();
		
		ObjectMapper om = ParallelInit.getObjectMapper();
		
		try {
			
			String res = inputStreamToString(response.getStream());
			ObjectNode body = om.readValue(res, ObjectNode.class);		
			
			return (ArrayNode) body.get("data");
			
		} catch(Exception ex) {
			log.warning("Failed to parse messages");
		}
		
		return om.createArrayNode();
	}
	
	public ArrayNode getComments(String postId, String since) {
		
		String url = "https://graph.facebook.com/"+postId+"/comments";
		OAuthRequest request = new OAuthRequest(Verb.GET, url);
		if(since!=null && Long.parseLong(since) > 0)
			request.addQuerystringParameter("since", since);
		service.signRequest(accessToken, request);
		Response response = request.send();
		
		ObjectMapper om = ParallelInit.getObjectMapper();
		
		try {
			
			String res = inputStreamToString(response.getStream());
			ObjectNode body = om.readValue(res, ObjectNode.class);		
			
			return (ArrayNode) body.get("data");
			
		} catch(Exception ex) {
			log.warning("Failed to parse comments");
		}
		
		return om.createArrayNode();
	}
	
	public ArrayList<String> getThreads() {
		
		ArrayList<String> threads = new ArrayList<String>(); 
		
		String url = "https://graph.facebook.com/me?fields=threads";
		OAuthRequest request = new OAuthRequest(Verb.GET, url);
		service.signRequest(accessToken, request);
		Response response = request.send();
		
		try {
			ObjectMapper om = ParallelInit.getObjectMapper();
			String res = inputStreamToString(response.getStream());
			ObjectNode body = om.readValue(res, ObjectNode.class); 
			ArrayNode threadsData = (ArrayNode) body.get("threads").get("data");
			
			for(JsonNode thread : threadsData) {
				if(thread.get("unread_count")!=null && thread.get("unread_count").asInt()>0)
					threads.add(thread.get("id").asText());
			}
		} catch(Exception ex) {
			log.warning("Failed to parse messages");
		}
		
		return threads;
	}
	
	public ArrayNode getMessages(String threadId, String since) {
		
		String url = "https://graph.facebook.com/"+threadId+"/messages";
		if(since!=null && Long.parseLong(since) > 0)
			url+="?since="+since;
		OAuthRequest request = new OAuthRequest(Verb.GET, url);
		service.signRequest(accessToken, request);
		Response response = request.send();

		ObjectMapper om = ParallelInit.getObjectMapper();
		
		try {
			String res = inputStreamToString(response.getStream());
			
			ObjectNode body = om.readValue(res, ObjectNode.class); 
		
			return (ArrayNode) body.get("data");
		} catch(Exception ex) {
			log.warning("Failed to parse messages");
		}
		
		return om.createArrayNode();
	}
	
	public boolean sendComment(String message, String to, String name) {
		
		ObjectMapper om = ParallelInit.getObjectMapper();
		
		String url = "https://graph.facebook.com/"+to+"/comments";
		OAuthRequest request = new OAuthRequest(Verb.POST, url);
		request.addQuerystringParameter("message", message);
		
		service.signRequest(accessToken, request);
		Response response = request.send();
		
		String res = inputStreamToString(response.getStream());
		ObjectNode result=null;
		try {
			result = om.readValue(res, ObjectNode.class);
		} catch(Exception ex) {
			log.warning("Failed to parse send message result");
		}
		
		if(result!= null && result.get("error")!=null) {
			log.info("Message send, got response: "+res);
			return true;
		}
		
		log.info("Message not send, got response: "+res);
		return false;
	}
	
	public boolean sendWallMessage(String message, String to, String toName) {
		
		ObjectMapper om = ParallelInit.getObjectMapper();
				
		String accessToken = getAccessToken();
		String url = "https://graph.facebook.com/"+to+"/feed";
		OAuthRequest request = new OAuthRequest(Verb.POST, url);
		request.addQuerystringParameter("message", message);
		
		service.signRequest(new Token(accessToken, null), request);
		Response response = request.send();
		
		String res = inputStreamToString(response.getStream());
		ObjectNode result=null;
		try {
			result = om.readValue(res, ObjectNode.class);
		} catch(Exception ex) {
			log.warning("Failed to parse send message result");
		}
		
		if(result!= null && result.get("error")!=null) {
			log.info("Message send, got response: "+res);
			return true;
		}
		
		log.info("Message not send, got response: "+res);
		return false;
	}
	
	public boolean sendMessage(String message, String to, String name) {
		
		return false;
	}
	
	private String inputStreamToString(InputStream in) {
		
		InputStreamReader is = new InputStreamReader(in);
		StringBuilder sb=new StringBuilder();
		BufferedReader br = new BufferedReader(is);
		String read="";
		
		try {
			read = br.readLine();
	
			while(read != null) {
			    //System.out.println(read);
			    sb.append(read);
			    read =br.readLine();
			}
			
			return sb.toString();
		} catch(Exception ex) {
			log.warning("Failed to parse inputstream");
		}
		
		return null;
	}
	
	private String getAccessToken() {
		String url = "https://graph.facebook.com/oauth/access_token";
		OAuthRequest request = new OAuthRequest(Verb.GET, url);
		request.addQuerystringParameter("client_id", OAUTH_KEY);
		request.addQuerystringParameter("client_secret", OAUTH_SECRET);
		request.addQuerystringParameter("grant_type", "client_credentials");
		service.signRequest(new Token("", null), request);
		Response response = request.send();
		String res = inputStreamToString(response.getStream());
		return res.split("=")[1];
	}
}
