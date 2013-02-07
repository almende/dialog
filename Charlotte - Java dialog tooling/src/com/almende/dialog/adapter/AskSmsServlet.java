package com.almende.dialog.adapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.sms.SmsMessage;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.code.twig.annotation.AnnotationObjectDatastore;

public class AskSmsServlet extends TextServlet {

	protected static final Logger log = Logger
			.getLogger("DialogHandler");
	private static final long serialVersionUID = 2762467148217411999L;
	private static ObjectMapper om = ParallelInit.getObjectMapper();
	
	private static final String servletPath = "/_ah/sms/ask/";
	private static final String adapterType = "SMS";
	
	@Override
	protected void sendMessage(String message, String subject, String from,
			String fromName, String to, String toName, AdapterConfig config) {
		
		try {
			to = URLDecoder.decode(to, "UTF-8");
		} catch(Exception ex) {
			log.warning("Failed to parse phone number");
		}
		AnnotationObjectDatastore datastore  = new AnnotationObjectDatastore();
		SmsMessage msg = new SmsMessage(to, message);
		datastore.store(msg);
		
		log.info("Stored message: "+msg.getMessage()+ " for: "+msg.getTo());
	}

	@Override
	protected TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp)
			throws Exception {
		
		HashMap<String, String> data = getPostData(req);
		
		boolean success=false;
		TextMessage msg=null;
		if(data.get("secret").equals("askask")) {
			
			String address = formatNumber(URLDecoder.decode(data.get("from"),"UTF-8")).replaceFirst("\\+31", "0");
			msg = new TextMessage();
			msg.setLocalAddress("0615004624");
			msg.setAddress(address);
			try {
				msg.setBody(URLDecoder.decode(data.get("message"), "UTF-8"));
			} catch(Exception ex) {
				log.warning("Failed to parse phone number");
			}
			
			success=true;
		}
		
		ObjectNode response = om.createObjectNode();
		response.put("success", success);
		ObjectNode payload = om.createObjectNode();
		payload.put("payload", response);
		
		resp.getWriter().println(om.writeValueAsString(payload));
		
		return msg;
	}

	@Override
	protected String getServletPath() {
		return servletPath;
	}

	@Override
	protected String getAdapterType() {
		return adapterType;
	}

	@Override
	protected void doErrorPost(HttpServletRequest req, HttpServletResponse res)
			throws IOException {}

	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		String query = req.getQueryString();
		log.info("Received get: "+query);
		
		AnnotationObjectDatastore datastore  = new AnnotationObjectDatastore();
		ArrayList<SmsMessage> messages = new ArrayList<SmsMessage>();
		QueryResultIterator<SmsMessage> it = datastore.find(SmsMessage.class);
		while(it.hasNext()) {
			SmsMessage message = it.next();
			messages.add(message);
				
			datastore.delete(message);
		}
		
		if(messages.size()>0) {
			ObjectNode payload = om.createObjectNode();
			payload.put("task", "send");
			payload.put("secret", "askask");
			payload.put("messages", om.convertValue(messages, JsonNode.class) );
			ObjectNode json = om.createObjectNode();
			json.put("payload", payload);
			
			String result = om.writeValueAsString(json);
			
			resp.getWriter().print(result);
			log.info("Send "+messages.size()+" messages");
			return;
		}
		
		log.info("No messages to send");
	}
	
	private HashMap<String, String> getPostData(HttpServletRequest req) {
		StringBuilder sb = new StringBuilder();
	    try {
	        BufferedReader reader = req.getReader();
	        reader.mark(10000);

	        String line;
	        do {
	            line = reader.readLine();
	            sb.append(line).append("\n");
	        } while (line != null);
	        reader.reset();
	        // do NOT close the reader here, or you won't be able to get the post data twice
	    } catch(IOException e) {
	        log.warning("getPostData couldn't.. get the post data");  // This has happened if the request's reader is closed    
	    }
	    
	    log.info("Received data: "+sb.toString());

	    HashMap<String, String> data = new HashMap<String, String>();
		String[] params = sb.toString().split("&");
		for(String param : params) {
			String[] qp = param.split("=");
			if(qp.length>0)
				data.put(qp[0], (qp.length>1?qp[1]:""));
		}
		
		return data;
	}
}
