package com.almende.dialog.adapter;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.CM;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.util.PhoneNumberUtils;

public class NexmoSmsServlet extends TextServlet {

	protected static final Logger log = Logger
			.getLogger("DialogHandler");
	private static final long serialVersionUID = 2762467148217411999L;
	
	// Info of MessageBird
	private static final String servletPath = "/sms/nm/";
	private static final String adapterType = "SMS";
	private static final boolean USE_KEYWORDS = true;
	
	@Override
	protected int sendMessage(String message, String subject, String from,
			String fromName, String to, String toName, AdapterConfig config) throws Exception {
		
		String[] tokens = config.getAccessToken().split("\\|");
		
		CM cm = new CM(tokens[0], tokens[1], config.getAccessTokenSecret());
		return cm.sendMessage(message, subject, from, fromName, to, toName, config);
	}

    @Override
    protected int broadcastMessage( String message, String subject, String from, String senderName,
        Map<String, String> addressNameMap, AdapterConfig config ) throws Exception
    {
        String[] tokens = config.getAccessToken().split( "\\|" );
        CM cm = new CM( tokens[0], tokens[1], config.getAccessTokenSecret() );
        return cm.broadcastMessage( message, subject, from, senderName, addressNameMap, config );
    }

	@Override
	protected TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp)
			throws Exception {
		TextMessage msg=null;
			
		String localAddress = req.getParameter("to").replaceFirst("31", "0");
		String address = PhoneNumberUtils.formatNumber( req.getParameter("msisdn").replaceFirst("31", "0"), null);
		msg = new TextMessage(USE_KEYWORDS);
		msg.setLocalAddress(localAddress);
		msg.setAddress(address);
		try {
			msg.setBody(req.getParameter("text"));
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
	
	@Override
	protected String getNoConfigMessage() {
		return "U dient het juiste keyword mee te geven.";
	}
}
