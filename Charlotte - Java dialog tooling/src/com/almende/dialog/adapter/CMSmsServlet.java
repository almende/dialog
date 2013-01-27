package com.almende.dialog.adapter;

import java.io.IOException;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.znerd.xmlenc.XMLOutputter;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.util.ParallelInit;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

public class CMSmsServlet extends TextServlet {

	private static final long serialVersionUID = 408503132941968804L;
	
	private static final String servletPath = "/_ah/sms/";
	private static final String adapterType = "CM";
	
	// TODO: needs to be moved to the adapter config
	private static final String userID = "2630";
	private static final String userName = "Ask54de";
	private static final String password = "hwdkt6";
	
	private static final String url = "http://smssgateway.clubmessage.nl/cm/gateway.ashx";
	
	@Override
	protected void sendMessage(String message, String subject, String from,
			String fromName, String to, String toName, AdapterConfig config) {
		
		// TODO: Check message for special chars, if so change dcs.		
		String type="TEXT";
		String dcs="8";
		
		StringWriter sw = new StringWriter();
		try {
			XMLOutputter outputter = new XMLOutputter(sw, "UTF-8");
			outputter.declaration();
			outputter.startTag("MESSAGES");
				outputter.startTag("CUSTOMER");
					outputter.attribute("ID", userID);
				outputter.endTag();
				
				outputter.startTag("USER");
					outputter.attribute("LOGIN", userName);
					outputter.attribute("PASSWORD", password);
				outputter.endTag();
				
				// TODO: Create delivery reference
				
				outputter.startTag("MSG");
				outputter.startTag("CONCATENATIONTYPE");
					outputter.cdata(type);
				outputter.endTag();
				
				outputter.startTag("FROM");
					outputter.cdata(from);
				outputter.endTag();
				
				outputter.startTag("BODY");
					outputter.attribute("TYPE", type);
					outputter.cdata(message);
				outputter.endTag();
				
				outputter.startTag("TO");
					outputter.cdata(to);
				outputter.endTag();
		
				outputter.startTag("DCS");
					outputter.cdata(dcs);
				outputter.endTag();
				
				outputter.endTag(); //MSG
			outputter.endTag(); //MESSAGES
			outputter.endDocument();
		} catch (Exception e) {
			log.severe("Exception in creating question XML: "+ e.toString());
			return;
		}


		Client client = ParallelInit.getClient();

		WebResource webResource = client.resource(url);
		String result = webResource.type("text/plain").post(String.class, sw.toString());
		log.info("Result from CM: "+result);
	}

	@Override
	protected TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp)
			throws Exception {
		// TODO: Needs implementation, but service not available at CM
		return null;
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

	
}
