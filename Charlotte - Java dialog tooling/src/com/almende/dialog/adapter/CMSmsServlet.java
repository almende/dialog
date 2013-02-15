package com.almende.dialog.adapter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
	private static final String userID = "";
	private static final String userName = "";
	private static final String password = "";
	
	private static final String MIN_MESSAGE_PARTS="1";
	private static final String MAX_MESSAGE_PARTS="6";
	
	private static final String MESSAGE_TYPE_GSM7 = "0";
	private static final String MESSAGE_TYPE_UTF8 = "8";
	private static final String MESSAGE_TYPE_BIN = "4";
		
	
	private static final String url = "http://smssgateway.clubmessage.nl/cm/gateway.ashx";
	
	@Override
	protected int sendMessage(String message, String subject, String from,
			String fromName, String to, String toName, AdapterConfig config) throws Exception {
		
		
		String type="TEXT";
		String dcs="";
		if(!isGSMSeven(message)) {
			dcs=MESSAGE_TYPE_UTF8;
		} else {
			dcs=MESSAGE_TYPE_GSM7;
		}
		
		// TODO: Check message for special chars, if so change dcs.		
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
				
				outputter.startTag("MINIMUMNUMBEROFMESSAGEPARTS");
					outputter.cdata(MIN_MESSAGE_PARTS);
				outputter.endTag();
				
				outputter.startTag("MAXIMUMNUMBEROFMESSAGEPARTS");
					outputter.cdata(MAX_MESSAGE_PARTS);
				outputter.endTag();
				
				outputter.endTag(); //MSG
			outputter.endTag(); //MESSAGES
			outputter.endDocument();
		} catch (Exception e) {
			log.severe("Exception in creating question XML: "+ e.toString());
			return 0;
		}


		Client client = ParallelInit.getClient();

		WebResource webResource = client.resource(url);
		String result = webResource.type("text/plain").post(String.class, sw.toString());
		if(!result.equals(""))
			throw new Exception(result);
		log.info("Result from CM: "+result);
		
		int count = countMessageParts(message, dcs);
		return count;
	}
	
	private boolean isGSMSeven(String message) {
		
		String[] chs = {"40", "0394", "20", "30", "a1", "50", "bf", "70",
						"a3", "5f", "21", "31", "41", "51", "61", "71",
						"24", "03a6", "22", "32", "42", "52", "62", "72",
						"a5", "0393", "23", "33", "43", "53", "63", "73",
						"e8", "039b", "a4", "34", "35", "44", "54", "64", "74",
						"e9", "03a9", "25", "45", "45", "55", "65", "75",
						"f9", "03a0", "26", "36", "46", "56", "66", "76",
						"ec", "03a8", "27", "37", "47", "57", "67", "77", 
						"f2", "03a3", "28", "38", "48", "58", "68", "78",
						"c7", "0398", "29", "39", "49", "59", "69", "79",
						"0a", "039e", "2a", "3a", "4a", "5a", "6a", "7a",
						"d8", "1b", "2b", "3b", "4b", "c4", "6b", "e4",
						"f8", "c6", "2c", "3c", "4c", "d6", "6c", "f6",
						"0d", "e6", "2d", "3d", "4d", "d1", "6d", "f1",
						"c5", "df", "2e", "3e", "4e", "dc", "6e", "fc",
						"e5", "c9", "2f", "3f", "4f", "a7", "6f", "e0", "0", "20ac"};
		
		Set<String> chars = new HashSet<String>(Arrays.asList(chs));
		
		
		for(char ch: message.toCharArray()) {
			if(!chars.contains(Integer.toHexString(ch))) {
				log.info("Special char: "+ch+" : "+Integer.toHexString(ch));
				return false;
			}
		}
		
		return true;
	}
	
	private int countMessageParts(String message, String type) {
		
		
		int maxChars = 0;
		
		if(type.equals(MESSAGE_TYPE_GSM7)) {
			maxChars=160;
			if(message.toCharArray().length<maxChars) // Test if concatenated message
				maxChars = 153;				
		} else if(type.equals(MESSAGE_TYPE_UTF8)) {
			maxChars=70;
			if(message.toCharArray().length<maxChars)
				maxChars = 67;
		} else if (type.equals(MESSAGE_TYPE_BIN)) {
			maxChars=280;
			if(message.toCharArray().length<maxChars)
				maxChars = 268;
		}
		
		int count = Math.round((message.toCharArray().length-1) / maxChars) + 1;
		return count;
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
