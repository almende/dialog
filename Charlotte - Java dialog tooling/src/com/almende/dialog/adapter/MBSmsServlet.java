package com.almende.dialog.adapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.CM;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MBSmsServlet extends TextServlet {

	protected static final Logger log = Logger
			.getLogger("DialogHandler");
	private static final long serialVersionUID = 2762467148217411999L;
	private static ObjectMapper om = ParallelInit.getObjectMapper();
	
	// Info of MessageBird
	private static final String servletPath = "/sms/mb/";
	private static final String adapterType = "MB";
	
	@Override
	protected int sendMessage(String message, String subject, String from,
			String fromName, String to, String toName, AdapterConfig config) throws Exception {
		
		String[] tokens = config.getAccessToken().split("&");
		
		CM cm = new CM(tokens[0], tokens[1], config.getAccessTokenSecret());
		return cm.sendMessage(message, subject, from, fromName, to, toName, config);
	}

	@Override
	protected TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp)
			throws Exception {
		
		HashMap<String, String> data = getPostData(req);
		
		TextMessage msg=null;
			
		String localAddress = URLDecoder.decode(data.get("receiver"),"UTF-8").replaceFirst("31", "0");
		String address = formatNumber(URLDecoder.decode(data.get("sender"),"UTF-8").replaceFirst("31", "0"));
		msg = new TextMessage();
		msg.setLocalAddress(localAddress);
		msg.setAddress(address);
		try {
			msg.setBody(URLDecoder.decode(data.get("message"), "UTF-8"));
		} catch(Exception ex) {
			log.warning("Failed to parse phone number");
		}
		
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
