package com.almende.dialog;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.logging.Logger;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DDRWrapper {
	private static final Logger ddr = Logger.getLogger("DDR");
	private static ObjectMapper om = ParallelInit.getObjectMapper();
	private static DateFormat formatter = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss.SSS");
    
	public static void log(String url, String token, Session session, String type, AdapterConfig config){
		ObjectNode node= om.createObjectNode();
		node.put("timestamp", System.currentTimeMillis());
		node.put("dateTime", formatter.format(System.currentTimeMillis()));
		node.put("agent", url);
		node.put("remoteAddress", session.getRemoteAddress());
		node.put("direction", session.getDirection());
		node.put("type", type);
		node.put("adapterType", config.getAdapterType());
		node.put("adapterAddress", config.getMyAddress());
		node.put("trackingToken", token);
		node.put("account", session.getAccount());
		try {
			ddr.info(om.writeValueAsString(node));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static void log(Question question, Session session, String type, AdapterConfig config){
		log(question.getRequester(),question.getTrackingToken(),session,type,config);
	}
	
	public static void log(Question question, Session session, String type){
		AdapterConfig config = AdapterConfig.findAdapterConfig(session.getType(), session.getLocalAddress());
		log(question.getRequester(),question.getTrackingToken(),session,type,config);
	}
}
