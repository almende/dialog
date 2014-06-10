package com.almende.dialog.adapter;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.almende.dialog.DDRWrapper;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.CMStatus;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.PhoneNumberUtils;
import com.almende.dialog.util.RequestUtil;
import com.almende.util.ParallelInit;

@SuppressWarnings("serial")
abstract public class TextServlet extends HttpServlet {
	protected static final Logger	log				= Logger.getLogger(TextServlet.class
															.getSimpleName());
	protected static final int		LOOP_DETECTION	= 10;
	protected static final String	DEMODIALOG		= "/charlotte/";
	
	/**
	 * @deprecated use
	 *             {@link TextServlet#broadcastMessage(String,String,String, String, String, Map, AdapterConfig)
	 *             broadcastMessage} instead.
	 */
	@Deprecated
	protected abstract int sendMessage(String message, String subject,
			String from, String fromName, String to, String toName,
			Map<String, Object> extras, AdapterConfig config) throws Exception;
	
	/**
	 * update to the sendMessage function to cater broadcast functionality
	 * 
	 * @param addressNameMap
	 *            Map with address (e.g. phonenumber or email) as Key and name
	 *            as value
	 * @throws Exception
	 */
	protected abstract int broadcastMessage(String message, String subject,
			String from, String senderName, Map<String, String> addressNameMap,
			Map<String, Object> extras, AdapterConfig config) throws Exception;
	
    protected abstract DDRRecord createDDRForIncoming( AdapterConfig adapterConfig, String fromAddress ) throws Exception;

    protected abstract DDRRecord createDDRForOutgoing( AdapterConfig adapterConfig, Map<String, String> toAddress,
        String message ) throws Exception;
	
	protected abstract TextMessage receiveMessage(HttpServletRequest req,
			HttpServletResponse resp) throws Exception;
	
	protected abstract String getServletPath();
	
	protected abstract String getAdapterType();
	
	protected abstract void doErrorPost(HttpServletRequest req,
			HttpServletResponse res) throws IOException;
	
	private String	host	= "";
	
	protected class Return {
		String		reply;
		Question	question;
		
		public Return(String reply, Question question) {
			this.reply = reply;
			this.question = question;
		}
	}
	
	/**
	 * info for generating a Return when a user enters
	 * an escape command as input. E.g. /reset
	 * 
	 * @author Shravan
	 */
	private class EscapeInputCommand {
		boolean	skip;
		String	body;
		String	preferred_language;
		String	reply;
		
		@Override
		public String toString() {
			return String.format(
					"Skip: %s body: %s preferred_lang: %s reply %s", skip,
					body, preferred_language, reply);
		}
	}
	
	public Return formQuestion(Question question, String adapterID,	String address) {
    
            String reply = "";
    
            String preferred_language = "nl"; // TODO: Change to null??
            if (question != null)
                preferred_language = question.getPreferred_language();
    
            for (int count = 0; count <= LOOP_DETECTION; count++) {
                if (question == null)
                    break;
                question.setPreferred_language(preferred_language);
    
                if (!reply.equals(""))
                    reply += "\n";
                String qText = question.getQuestion_expandedtext();
                if (qText != null && !qText.equals(""))
                    reply += qText;
    
                if (question.getType().equalsIgnoreCase("closed")) {
                    reply += "\n[";
                    for (Answer ans : question.getAnswers()) {
                        reply += " " + ans.getAnswer_expandedtext(question.getPreferred_language()) + " |";
                    }
                    reply = reply.substring(0, reply.length() - 1) + " ]";
                    break; // Jump from forloop
                }
                else if (question.getType().equalsIgnoreCase("comment")) {
                    // Always returns null!
                    // So no need, but maybe in future?
                    question = question.answer(null, adapterID, null, null, null);
                }
                else if (question.getType().equalsIgnoreCase("referral")) {
                    question = Question.fromURL(question.getUrl(), adapterID, address);
                }
                else {
                    break; // Jump from forloop (open questions, etc.)
                }
            }
            return new Return(reply, question);
	}
	
    /**
	 * @deprecated use
	 *             {@link TextServlet#startDialog(Map, String, String, AdapterConfig)
	 *             startDialog} instead.
	 */
	@Deprecated
	public String startDialog(String address, String url, AdapterConfig config)
			throws Exception {
		if (config.getAdapterType().equalsIgnoreCase("CM")
				|| config.getAdapterType().equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_SMS)) {
			address = PhoneNumberUtils.formatNumber(address, null);
		}
		String localaddress = config.getMyAddress();
		String sessionKey = getAdapterType() + "|" + localaddress + "|" + address;
		Session session = Session.getOrCreateSession(sessionKey, config.getKeyword());
		if (session == null) {
			log.severe("XMPPServlet couldn't start new outbound Dialog, adapterConfig not found? "
					+ sessionKey);
			return "";
		}
		session.setAccountId( config.getOwner());
		session.setDirection("outbound");
		session.setTrackingToken(UUID.randomUUID().toString());
        String preferred_language = session.getLanguage();
        if ( preferred_language == null )
        {
            preferred_language = config.getPreferred_language();
            session.setLanguage( preferred_language );
        }
		session.storeSession();
		
		url = encodeURLParams(url);
		Question question = Question.fromURL(url, config.getConfigId(), address);
		
		question.setPreferred_language(preferred_language);
		Return res = formQuestion(question, config.getConfigId(), address);
		String fromName = getNickname(res.question);
		
		Map<String, Object> extras = null;
		if (res.question != null) {
			Session.storeString("question_" + address + "_" + localaddress,
					res.question.toJSON());
		}
		if (question != null) {
			extras = CMStatus.storeSMSRelatedData(address, localaddress, config,
					question, res.reply, extras);
		}
		
		DDRWrapper.log(question, session, "Start", config);
		int count = sendMessageAndAttachCharge(res.reply, "Message from DH", localaddress,
				fromName, address, "", extras, config);
		for (int i = 0; i < count; i++) {
			DDRWrapper.log(question, session, "Send", config);
		}
		return sessionKey;
	}
	
	/**
	 * updated startDialog with Broadcast functionality
	 * 
	 * @throws Exception
	 */
	public HashMap<String, String> startDialog(
			Map<String, String> addressNameMap,
			Map<String, String> addressCcNameMap,
			Map<String, String> addressBccNameMap, String url,
			String senderName, String subject, AdapterConfig config)
			throws Exception {
	    
	    
            senderName = senderName != null ? senderName : "Ask-Fast";
            addressNameMap = addressNameMap != null ? addressNameMap : new HashMap<String, String>();
            Map<String, String> formattedAddressNameToMap = new HashMap<String, String>();
            if (config.getAdapterType().equals("CM") || config.getAdapterType().equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_SMS)) {
                for (String address : addressNameMap.keySet()) {
                    String formattedAddress = PhoneNumberUtils.formatNumber(address, null);
                    formattedAddressNameToMap.put(formattedAddress, addressNameMap.get(address));
                }
            }
            else {
                formattedAddressNameToMap = addressNameMap != null && !addressNameMap.isEmpty() ? addressNameMap : (addressCcNameMap != null && !addressCcNameMap.isEmpty() ? addressCcNameMap : addressBccNameMap);
            }
            String localaddress = config.getMyAddress();            
            url = encodeURLParams(url);
    
            HashMap<String, String> sessionKeyMap = new HashMap<String, String>();
            ArrayList<Session> sessions = new ArrayList<Session>();
    
            // If it is a broadcast don't provide the remote address because it is
            // deceiving.
            String loadAddress = null;
            if (formattedAddressNameToMap.size() == 1)
                loadAddress = formattedAddressNameToMap.keySet().iterator().next();
    
            // fetch question
            Question question = Question.fromURL(url, config.getConfigId(), loadAddress);
            if (question != null) {
                // store the extra information
                Map<String, Object> extras = new HashMap<String, Object>();
                extras.put(Question.MEDIA_PROPERTIES, question.getMedia_properties());
                // add addresses in cc and bcc map
                HashMap<String, String> fullAddressMap = new HashMap<String, String>(addressNameMap);
                if (addressCcNameMap != null) {
                    fullAddressMap.putAll(addressCcNameMap);
                    extras.put(MailServlet.CC_ADDRESS_LIST_KEY, addressCcNameMap);
                }
                if (addressBccNameMap != null) {
                    fullAddressMap.putAll(addressBccNameMap);
                    extras.put(MailServlet.BCC_ADDRESS_LIST_KEY, addressBccNameMap);
                }
                
                Return res = null;
                if(loadAddress!=null) {
                    
                    Session session = Session.getOrCreateSession(config, loadAddress);
                    //Session session = Session.getOrCreateSession(sessionKey, keyword);
                    String preferred_language = session != null ? session.getLanguage() : null;
                    if (preferred_language == null) {
                        preferred_language = config.getPreferred_language();
                    }
                    question.setPreferred_language(preferred_language);
                    res = formQuestion(question, config.getConfigId(), loadAddress);
                    // Add key to the map (for the return)
                    sessionKeyMap.put(loadAddress, session.getKey());
                    sessions.add(session);
                    
                } else {
                    // Form the question without the responders address, because we don't know which one.
                    res = formQuestion(question, config.getConfigId(), null);
                    for (String address : fullAddressMap.keySet()) {
                        // store the session first
                        Session session = Session.getOrCreateSession(config, address);
                        session.setAccountId(config.getOwner());
                        session.setDirection("outbound");
        
                        if (res.question != null) {
                            session.setQuestion(res.question);
                        }
                        if (config.getAdapterType().equalsIgnoreCase("cm") ||
                            config.getAdapterType().equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_SMS)) {
                            extras = CMStatus.storeSMSRelatedData(address, localaddress, config, question, res.reply, extras);
                        }
                        //save this session
                        session.storeSession();
                        // Add key to the map (for the return)
                        sessionKeyMap.put(address, session.getKey());
                        sessions.add(session);
                        DDRWrapper.log(question, session, "Start", config);
                    }
                }
                String fromName = getNickname(res.question);
                log.info(String.format("fromName: %s senderName %s", fromName, senderName));
                // assign senderName with localAdress, if senderName is missing
                // priority is as: nickname >> senderName >> myAddress
                if (fromName == null || fromName.isEmpty()) {
                    senderName = senderName != null && !senderName.isEmpty() ? senderName : localaddress;
                } else {
                    senderName = fromName;
                }
                subject = subject != null && !subject.isEmpty() ? subject : "Message from Ask-Fast";
                // fix for bug: #15 https://github.com/almende/dialog/issues/15
                res.reply = URLDecoder.decode(res.reply, "UTF-8");
                int count = broadcastMessageAndAttachCharge(res.reply, subject, localaddress, senderName,
                                                            formattedAddressNameToMap, extras, config);
    
                for (Session session1 : sessions) {
                    for (int i = 0; i < count; i++) {
                        DDRWrapper.log(question, session1, "Send", config);
                    }
                }
                if (count < 1) {
                    log.severe("Error generating XML");
                }
            }
            else {
                sessionKeyMap.put("Ërror", "Question JSON not found in url: " + url);
            }
            return sessionKeyMap;
	}
	
	public static void killSession(Session session) {
	    session.drop();
	}
	
	@Override
	public void service(HttpServletRequest req, HttpServletResponse res)
			throws IOException {
		this.host = RequestUtil.getHost(req);
		// log.warning("Starting to handle xmpp post: "+startTime+"/"+(new
		// Date().getTime()));
		boolean loading = ParallelInit.startThreads();
		
		if (req.getServletPath().endsWith("/error/")) {
			doErrorPost(req, res);
			return;
		}
		
		if (loading) {
			ParallelInit.getClient();
			service(req, res);
			return;
		}
		
		TextMessage msg;
		try {
			msg = receiveMessageAndAttachCharge(req, res);
		} catch (Exception ex) {
			log.severe("Failed to parse received message: "
					+ ex.getLocalizedMessage());
			return;
		}
		
		try {
			processMessage(msg);
		} catch (Exception e) {
			log.severe(e.getLocalizedMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * processes any message (based on a Dialog) and takes actions like sending,
	 * broadcasting corresponding messages.
	 * 
	 * @param msg
	 * @return
	 * @throws Exception
	 */
	protected int processMessage(TextMessage msg) throws Exception {
		String localaddress = msg.getLocalAddress();
		String address = msg.getAddress();
		String subject = msg.getSubject();
		String body = msg.getBody();
		String toName = msg.getRecipientName();
		String keyword = msg.getKeyword();
		String fromName = "Ask-Fast";
		int count = 0;
		
            Map<String, Object> extras = msg.getExtras();
            AdapterConfig config;
            Session session = Session.getOrCreateSession(getAdapterType() + "|" + localaddress + "|" + address, keyword);
            // If session is null it means the adapter is not found.
            if (session == null) {
                log.info("No session so retrieving config");
                config = AdapterConfig.findAdapterConfig(getAdapterType(), localaddress);
                if (config.getAdapterType().equalsIgnoreCase("cm") ||
                    config.getAdapterType().equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_SMS)) {
                    extras = CMStatus.storeSMSRelatedData(address, localaddress, config, null, getNoConfigMessage(), extras);
                }
                count = sendMessageAndAttachCharge(getNoConfigMessage(), subject, localaddress, fromName, address, toName,
                                                   extras, config);
                // Create new session to store the send in the ddr.
                session = new Session();
                session.setDirection("inbound");
                session.setRemoteAddress(address);
                session.setTrackingToken(UUID.randomUUID().toString());
                for (int i = 0; i < count; i++) {
                    DDRWrapper.log(null, null, session, "Send", config);
                }
                session.storeSession();
                return count;
            }
    
            config = session.getAdapterConfig();
            // TODO: Remove this check, this is now to support backward
            // compatibility (write patch)
            if (config == null) {
                log.info("Session doesn't contain config, so searching it again");
                config = AdapterConfig.findAdapterConfig(getAdapterType(), localaddress, keyword);
                if (config == null) {
                    config = AdapterConfig.findAdapterConfig(getAdapterType(), localaddress);
                    try {
                        if (config.getAdapterType().equalsIgnoreCase("cm") ||
                            config.getAdapterType().equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_SMS)) {
                            extras = CMStatus.storeSMSRelatedData(address, localaddress, config, null,
                                                                  getNoConfigMessage(), extras);
                        }
                        count = sendMessageAndAttachCharge(getNoConfigMessage(), subject, localaddress, fromName, address,
                                                           toName, extras, config);
                    }
                    catch (Exception ex) {
                        log.severe(ex.getLocalizedMessage());
                    }
                    for (int i = 0; i < count; i++) {
                        DDRWrapper.log(null, null, session, "Send", config);
                    }
                    return count;
                }
                session.setAdapterID(config.getConfigId());
            }
    
            String preferred_language = session.getLanguage();
    
            EscapeInputCommand escapeInput = new EscapeInputCommand();
            escapeInput.skip = false;
            escapeInput.body = body;
            escapeInput.preferred_language = preferred_language;
            escapeInput.reply = "I'm sorry, I don't know what to say. Please retry talking with me at a later time.";
    
            if (!escapeInput.skip && escapeInput.body.toLowerCase().trim().charAt(0) == '/') {
                count = processEscapeInputCommand(msg, fromName, config, escapeInput, session);
                log.info(escapeInput.toString());
            }
            if (!escapeInput.skip) {
                if (escapeInput.preferred_language == null) {
                    escapeInput.preferred_language = "nl";
                }
    
                Question question = session.getQuestion();
                boolean start = false;
                if (question == null) {
                    if (config.getURLForInboundScenario() != null && config.getURLForInboundScenario().equals("")) {
                        question = Question.fromURL(this.host + DEMODIALOG, config.getConfigId(), address, localaddress);
                    }
                    else {
                        question = Question.fromURL(config.getURLForInboundScenario(), config.getConfigId(), address,
                                                    localaddress);
                    }
                    session.setDirection("inbound");
                    DDRWrapper.log(question, session, "Start", config);
                    start = true;
                }
    
                if (question != null) {
                    question.setPreferred_language(preferred_language);
                    // Do not answer a question, when it's the first and the type is
                    // comment or referral anyway.
                    if (!(start && (question.getType().equalsIgnoreCase("comment") || question.getType()
                                                    .equalsIgnoreCase("referral")))) {
                        question = question.answer(address, config.getConfigId(), null, escapeInput.body, null);
                    }
                    Return replystr = formQuestion(question, config.getConfigId(), address);
                    // fix for bug: #15 https://github.com/almende/dialog/issues/15
                    escapeInput.reply = URLDecoder.decode(replystr.reply, "UTF-8");
                    question = replystr.question;
                    fromName = getNickname(question);
    
                    extras = CMStatus.storeSMSRelatedData(address, localaddress, config, question, escapeInput.reply,
                                                          extras);
                    if (question == null) {
                        session.drop();
                        DDRWrapper.log(question, session, "Hangup", config);
                    }
                    else {
                        session.setQuestion(question);
                        session.storeSession();
                        DDRWrapper.log(question, session, "Answer", config);
                    }
                }
                else {
                    log.severe(String.format("Question is null. Couldnt fetch Question from session, nor initialAgentURL: %s nor from demoDialog",
                                             config.getURLForInboundScenario(), this.host + DEMODIALOG));
                }
            }
    
            try {
                count = sendMessageAndAttachCharge(escapeInput.reply, subject, localaddress, fromName, address, toName,
                                                   extras, config);
            }
            catch (Exception ex) {
                log.severe("Message sending failed. Message: " + ex.getLocalizedMessage());
            }
            for (int i = 0; i < count; i++) {
                DDRWrapper.log(null, null, session, "Send", config);
            }
            return count;
	}
	
    /**
	 * processses any escape command entered by the user
	 * @return
	 */
	private int processEscapeInputCommand(TextMessage msg, String fromName,
            AdapterConfig config, EscapeInputCommand escapeInput, Session session)
            throws Exception {
        log.info(String.format("escape charecter seen.. input %s",
                escapeInput.body));
        int result = 0;
        String cmd = escapeInput.body.toLowerCase().substring(1);
        if (cmd.startsWith("language=")) {
            escapeInput.preferred_language = cmd.substring(9);
            if (escapeInput.preferred_language.indexOf(' ') != -1) escapeInput.preferred_language = escapeInput.preferred_language
                    .substring(0, escapeInput.preferred_language.indexOf(' '));
            
            session.setLanguage( escapeInput.preferred_language );
            
            escapeInput.reply = "Ok, switched preferred language to:"
                    + escapeInput.preferred_language;
            escapeInput.body = "";
            
            HashMap<String, String> addressNameMap = new HashMap<String, String>(
                    1);
            addressNameMap.put(msg.getAddress(), msg.getRecipientName());
            Map<String, Object> extras = CMStatus.storeSMSRelatedData(msg.getAddress(),
                    msg.getLocalAddress(), config, null, escapeInput.reply,
                    null);
            result = broadcastMessageAndAttachCharge( escapeInput.reply, msg.getSubject(),
                    msg.getLocalAddress(), fromName, addressNameMap, extras,
                    config);
        } else if (cmd.startsWith("reset")) {
            session.drop();
        }
        
        else if (cmd.startsWith("help")) {
            String[] command = cmd.split(" ");
            if (command.length == 1) {
                escapeInput.reply = "The following commands are understood:\n"
                        + "/help <command>\n" + "/reset \n"
                        + "/language=<lang_code>\n";
            } else {
                if (command[1].equals("reset")) {
                    escapeInput.reply = "/reset will return you to Charlotte's initial question.";
                }
                
                if (command[1].equals("language")) {
                    escapeInput.reply = "/language=<lang_code>, switches the preferred language to the provided lang_code. (e.g. /language=nl)";
                }
                
                if (command[1].equals("help")) {
                    escapeInput.reply = "/help <command>, provides a help text about the provided command (e.g. /help reset)";
                }
            }
            
            escapeInput.skip = true;
        }
        session.storeSession();
        return result;
    }
	
	protected String getNoConfigMessage() {
		return "Sorry, I can't find the account associated with this chat address...";
	}
	
	private String getNickname(Question question) {
		
		String nickname = null;
		if (question != null) {
			HashMap<String, String> id = question.getExpandedRequester();
			nickname = id.get("nickname");
		}
		
		return nickname;
	}
	
	private String encodeURLParams(String url) {
		try {
			URL remoteURL = new URL(url);
			return new URI(remoteURL.getProtocol(), remoteURL.getUserInfo(),
					remoteURL.getHost(), remoteURL.getPort(),
					remoteURL.getPath(), remoteURL.getQuery(),
					remoteURL.getRef()).toString();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return url;
	}

	/**
	 * sends a message and charges the owner of the adapter for outbound communication
	 * @param message message to be sent
	 * @param subject
	 * @param from
	 * @param fromName
	 * @param to
	 * @param toName
	 * @param extras
	 * @param config
	 * @return the number of messages sent. Can be more than 1 when sending special charecters in SMS
	 * @throws Exception
	 */
    private int sendMessageAndAttachCharge( String message, String subject, String from, String fromName, String to,
        String toName, Map<String, Object> extras, AdapterConfig config ) throws Exception
    {
        int count = sendMessage( message, subject, from, fromName, to, toName, extras, config );
        //attach costs
        attachOutgoingCost( config, to, message );
        return count;
    }
	    
    /**
     * broadcasts a message and charges the owner of the adapter for outbound communication
     * @param message message to be sent
     * @param subject
     * @param from
     * @param senderName
     * @param addressNameMap
     * @param extras
     * @param config
     * @return the number of outbound messages done
     * @throws Exception
     */
    private int broadcastMessageAndAttachCharge( String message, String subject, String from, String senderName,
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config ) throws Exception
    {
        int count = broadcastMessage( message, subject, from, senderName, addressNameMap, extras, config );
        //attach costs
        DDRRecord ddrRecord = createDDRForOutgoing( config, addressNameMap, message );
        //push the cost to hte queue
        Double totalCost = DDRUtils.calculateCommunicationDDRCost( ddrRecord, true );
        DDRUtils.publishDDREntryToQueue( config.getOwner(), totalCost );
        return count;
    }
    
    private TextMessage receiveMessageAndAttachCharge( HttpServletRequest req, HttpServletResponse resp )
    throws Exception
    {
        TextMessage receiveMessage = receiveMessage( req, resp );
        //attach charges for incoming
        AdapterConfig config = AdapterConfig.findAdapterConfig( getAdapterType(), receiveMessage.getLocalAddress() );
        createDDRForIncoming( config, receiveMessage.getAddress() );
        return receiveMessage;
    }
	    
    /**
     * helper method to convert the address to map in turn calls {@link TextServlet#createDDRForOutgoing(AdapterConfig, Map, String)}
     * @param config
     * @param address
     * @param message
     * @throws Exception
     */
    private DDRRecord attachOutgoingCost( AdapterConfig config, String address, String message ) throws Exception
    {
        HashMap<String, String> toAddressMap = new HashMap<String, String>();
        toAddressMap.put( address, "" );
        DDRRecord ddrRecord = createDDRForOutgoing( config, toAddressMap, message );
        //push the cost to hte queue
        Double totalCost = DDRUtils.calculateCommunicationDDRCost( ddrRecord, true );
        DDRUtils.publishDDREntryToQueue( config.getOwner(), totalCost );
        return ddrRecord;
    }
}
