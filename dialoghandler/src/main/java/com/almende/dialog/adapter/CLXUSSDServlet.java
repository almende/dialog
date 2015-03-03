package com.almende.dialog.adapter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.lang.NotImplementedException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.CLXUSSD;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.askfast.commons.entity.AdapterProviders;

public class CLXUSSDServlet extends TextServlet {

	private static final long serialVersionUID = -1195021911106214263L;
	protected static final com.almende.dialog.Logger dialogLog =  new com.almende.dialog.Logger();
	private static final String servletPath = "/ussd/";
	
    @Override
    protected int sendMessage(String message, String subject, String from, String fromName, String to, String toName,
        Map<String, Object> extras, AdapterConfig config, String accountId) throws Exception {

        //CLXUSSD clxussd = new CLXUSSD(config.getAccessToken(),
        //	config.getAccessTokenSecret(), config);

        if (!ServerUtils.isInUnitTestingEnvironment()) {
            CLXUSSD clxussd = new CLXUSSD();
            clxussd.sendMessage(message, subject, from, fromName, to, toName, extras, config);
        }

        //TODO: implement subscrtipion for 2 way communication

        /*
         * String subId = config.getXsiSubscription();
         * 
         * 
         * if (subId == "" || subId ==null) { subId =
         * clxussd.startSubScription(to, config); }
         * 
         * String xml = ""; if (extras.get("questionType").equals("comment")) {
         * 
         * 
         * }
         */

        return 1;
    }

    @Override
    protected int broadcastMessage(String message, String subject, String from, String senderName,
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config, String accountId)
        throws Exception {

        int count = 0;
        for (String address : addressNameMap.keySet()) {
            count += sendMessage(message, subject, from, senderName, address, addressNameMap.get(address), extras,
                                 config, accountId);
        }
        return count;
    }
	

	
	@Override
	protected TextMessage receiveMessage(HttpServletRequest req,
			HttpServletResponse resp) throws Exception {
		
		String postBody= extractPostRequestBody(req);
//		Session ses = Session.getSession(AdapterAgent.ADAPTER_TYPE_USSD, "", getTo(postBody));
		
		 
		log.info("message resieved: "+ postBody );
		
		if(postBody.contains("open_session")){
			String sessionId = getSessionId(postBody);
			
			resp.getWriter().println("<?xml version=\"1.0\"?>	<umsprot version=\"1\"><prompt_req reqid=\"0\" sessionid=\"" + sessionId+ "\">Carl, which is your favorite band? 1. The Rolling Stones 2. The XX 3. Joy Division</prompt_req></umsprot>");
			log.info("responded with session id:"+sessionId);
			return null;
		}
		
		else if(postBody.contains("promp_req")){
			TextMessage msg = buildMessageFromXML(postBody);
			if (msg != null) {
				return msg;
			} else {
				log.warning("USSD no return message ");
				return null;
			}
		}
		else{
			resp.getWriter().println("nothing to report");
			return null;
		}
		
	}
	
	static String extractPostRequestBody(HttpServletRequest request) throws IOException {
//	    if ("POST".equalsIgnoreCase(request.getMethod())) {
//	      
//			Scanner s = new Scanner(request.getInputStream(), "UTF-8").useDelimiter("\\A");
//	        return s.hasNext() ? s.next() : "";
//	    }
	    return "";
	}
	
	static String getSessionId (String postBody){
		
		String sessionId =postBody;
		sessionId = sessionId.substring(sessionId.indexOf("sessionid")+11);
		sessionId = sessionId.substring(0,sessionId.indexOf("\" requesttime"));
		
		return sessionId;
	}
	
	static String getTo (String postBody){
		
		String sessionId =postBody;
		sessionId = sessionId.substring(sessionId.indexOf("sessionid")+11);
		sessionId = sessionId.substring(0,sessionId.indexOf("\" requesttime"));
		
		return sessionId;
	}
	
	@Override
    protected void doPost( HttpServletRequest req, HttpServletResponse resp ) throws ServletException, IOException
    {
        super.doPost( req, resp );
    }

	@Override
	protected String getServletPath() {
		return servletPath;
	}

	@Override
	protected String getAdapterType() {
		return AdapterAgent.ADAPTER_TYPE_USSD;
	}

	@Override
	protected void doErrorPost(HttpServletRequest req, HttpServletResponse res)
			throws IOException {}
	
	
	@SuppressWarnings("unused")
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
	@SuppressWarnings("unused")
	private String buildXmlQuestion (String question, String subId) {
		String xml = "<?xml version=\"1.0\"?><umsprot version=\"1\"><prompt_req reqid=\"0\" sessionid=\""+subId+"\">"+question+"</prompt_req></umsprot>";
		
		return xml;
	}
	
	private TextMessage buildMessageFromXML(String xml) {
		log.info("input xml :" + xml+" :end");
		TextMessage msg = new TextMessage();
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			
			Document dom = db.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
			Node root = dom.getDocumentElement();
			msg.setBody(root.getTextContent());
			
			String sessionId = xml.substring(xml.indexOf("sessionid=\"")+11);
			sessionId = sessionId.substring(0, sessionId.indexOf("\""));
			
			
			
		} catch(Exception ex){
			ex.printStackTrace();
		}
		return msg;
	}

    @Override
    protected DDRRecord createDDRForIncoming(AdapterConfig adapterConfig, String accountId, String fromAddress,
        String message, String sessionKey) throws Exception {

        throw new NotImplementedException("Incoming cost processing is not implemented for " +
                                          this.getClass().getSimpleName());
    }

    @Override
    protected DDRRecord createDDRForOutgoing(AdapterConfig adapterConfig, String accountId, String senderName,
        Map<String, String> toAddress, String message, Map<String, String> sessionKeyMap) throws Exception {

        return DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig, accountId, null, toAddress, 1, message,
                                                               sessionKeyMap);
    }

    @Override
    protected String getProviderType() {

        return AdapterProviders.CLX.toString();
    }
}

