package com.almende.dialog.adapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.CM;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;
import com.askfast.commons.entity.AdapterProviders;
import com.askfast.commons.utils.PhoneNumberUtils;

public class MBSmsServlet extends TextServlet {

	protected static final Logger log = Logger
			.getLogger("DialogHandler");
	private static final long serialVersionUID = 2762467148217411999L;
	
	// Info of MessageBird
	private static final String servletPath = "/sms/mb/";
	private static final boolean USE_KEYWORDS = true;
	

    @Override
    protected int sendMessage(String message, String subject, String from, String fromName, String to, String toName,
        Map<String, Object> extras, AdapterConfig config, String accountId, DDRRecord ddrRecord) throws Exception {

        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(to, toName);
        return broadcastMessage(message, subject, from, fromName, addressNameMap, extras, config, accountId, ddrRecord);
    }

    @Override
    protected int broadcastMessage(String message, String subject, String from, String senderName,
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config, String accountId,
        DDRRecord ddrRecord) throws Exception {

        String[] tokens = config.getAccessToken().split("\\|");
        CM cm = new CM(tokens[0], tokens[1], config.getAccessTokenSecret());
        return cm.broadcastMessage(message, subject, from, senderName, addressNameMap, extras, config, accountId,
                                   ddrRecord);
    }

    @Override
    protected TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        HashMap<String, String> data = getPostData(req);
        return receiveMessage(data);
    }

    /**
     * this function is extracted from the
     * {@link #receiveMessage(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)}
     * method so that it can be unit tested
     * 
     * @param data
     * @return
     * @author Shravan
     * @since 0.4.0
     * @throws UnsupportedEncodingException
     */
    private TextMessage receiveMessage(HashMap<String, String> data) throws UnsupportedEncodingException {

        TextMessage msg = null;

        String localAddress = URLDecoder.decode(data.get("receiver"), "UTF-8").replaceFirst("31", "0");
        String address = PhoneNumberUtils.formatNumber(URLDecoder.decode(data.get("sender"), "UTF-8").replaceFirst("31", "0"), null);
        String reference = data.get("reference");
        if(address != null) {
        msg = new TextMessage(USE_KEYWORDS);
        msg.setLocalAddress(localAddress);
        msg.setAddress(address);
        msg.setReference(reference);
            msg.setBody(URLDecoder.decode(data.get("message"), "UTF-8"));
        }
        else {
            log.severe("Sender address is invalid: "+ data.get("sender"));
        }
        return msg;
    }
    
    @Override
    protected DDRRecord createDDRForIncoming(AdapterConfig adapterConfig, String accountId, String fromAddress,
        String message, Session session) throws Exception {

        return DDRUtils.createDDRRecordOnIncomingCommunication(adapterConfig, accountId, fromAddress, message,
                                                               session);
    }

    @Override
    protected DDRRecord createDDRForOutgoing(AdapterConfig adapterConfig, String accountId, String senderName,
        Map<String, String> toAddress, String message, Map<String, Session> sessionKeyMap) throws Exception {

        //add costs with no.of messages * recipients
        return DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig, accountId, senderName, toAddress,
            CM.countMessageParts(message, toAddress.size()), message, sessionKeyMap);
    }

    @Override
    protected String getServletPath() {

        return servletPath;
    }

	@Override
	protected String getAdapterType() {
		return AdapterAgent.ADAPTER_TYPE_SMS;
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
	            if(line!=null)
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
	
	@Override
	protected String getNoConfigMessage() {
		return "U dient het juiste keyword mee te geven.";
	}

    @Override
    protected String getProviderType() {

        return AdapterProviders.MB.toString();
    }
}
