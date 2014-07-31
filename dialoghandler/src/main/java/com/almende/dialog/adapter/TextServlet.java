package com.almende.dialog.adapter;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.almende.util.TypeUtil;
import com.askfast.commons.entity.AccountType;
import com.askfast.commons.utils.TimeUtils;

@SuppressWarnings("serial")
abstract public class TextServlet extends HttpServlet {

    protected static final Logger log = Logger.getLogger(TextServlet.class.getSimpleName());
    protected static final int LOOP_DETECTION = 10;
    protected static final String DEMODIALOG = "/charlotte/";
	
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
	
    protected abstract DDRRecord
        createDDRForIncoming(AdapterConfig adapterConfig, String fromAddress, String message) throws Exception;

    protected abstract DDRRecord createDDRForOutgoing(AdapterConfig adapterConfig, String senderName,
                                                      Map<String, String> toAddress, String message) throws Exception;
	
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
	
	public Return formQuestion(Question question, String adapterID,	String address, String ddrRecordId, String sessionKey) {
    
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
                    question = Question.fromURL(question.getUrl(), adapterID, address, ddrRecordId, sessionKey);
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
    public String startDialog(String address, String url, AdapterConfig config) throws Exception {

        if (config.getAdapterType().equalsIgnoreCase("CM") ||
            config.getAdapterType().equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_SMS)) {
            address = PhoneNumberUtils.formatNumber(address, null);
        }
        String localaddress = config.getMyAddress();
        String sessionKey = getAdapterType() + "|" + localaddress + "|" + address;
        Session session = Session.getOrCreateSession(sessionKey, config.getKeyword());
        if (session == null) {
            log.severe("XMPPServlet couldn't start new outbound Dialog, adapterConfig not found? " + sessionKey);
            return "";
        }
        session.setAccountId(config.getOwner());
        session.setDirection("outbound");
        String preferred_language = session.getLanguage();
        if (preferred_language == null) {
            preferred_language = config.getPreferred_language();
            session.setLanguage(preferred_language);
        }
        url = encodeURLParams(url);
        Question question = Question.fromURL(url, config.getConfigId(), address, null, sessionKey);

        question.setPreferred_language(preferred_language);
        Return res = formQuestion(question, config.getConfigId(), address, null, sessionKey);
        //store the question in the session
        session.setQuestion(res.question);
        session.setLocalName(getSenderName(question, config, null));
        session.storeSession();
        String fromName = getSenderName(question, config, null);

        Map<String, Object> extras = null;
        if (res.question != null) {
            Session.storeString("question_" + address + "_" + localaddress, res.question.toJSON());
        }
        if (question != null) {
            extras = CMStatus.storeSMSRelatedData(address, localaddress, config, question, res.reply, sessionKey, extras);
        }

        DDRWrapper.log(question, session, "Start", config);
        int count = sendMessageAndAttachCharge(res.reply, "Message from DH", localaddress, fromName, address, "",
                                               extras, config);
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
    public HashMap<String, String> startDialog(Map<String, String> addressNameMap,
                                               Map<String, String> addressCcNameMap,
                                               Map<String, String> addressBccNameMap, String url, String senderName,
                                               String subject, AdapterConfig config) throws Exception {

        addressNameMap = addressNameMap != null ? addressNameMap : new HashMap<String, String>();
        Map<String, String> formattedAddressNameToMap = new HashMap<String, String>();
        if (config.getAdapterType().equals("CM") ||
            config.getAdapterType().equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_SMS)) {
            for (String address : addressNameMap.keySet()) {
                try {
                    String formattedAddress = PhoneNumberUtils.formatNumber(address, null);
                    formattedAddressNameToMap.put(formattedAddress, addressNameMap.get(address));
                }
                catch (Exception e) {
                    log.warning(String.format("Address: %s is invalid for mode: %s. Ignoring it", address,
                                              config.getAdapterType()));
                }
            }
        }
        else {
            formattedAddressNameToMap = addressNameMap != null && !addressNameMap.isEmpty() ? addressNameMap : formattedAddressNameToMap;
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
        Question question = Question.fromURL(url, config.getConfigId(), loadAddress, null,
                                             Session.getSessionKey(config, loadAddress));
        if (question != null) {
            //fetch the senderName
            senderName = getSenderName(question, config, senderName);
            // store the extra information
            Map<String, Object> extras = new HashMap<String, Object>();
            if (question.getMedia_properties() != null) {
                extras.put(Question.MEDIA_PROPERTIES, question.getMedia_properties());
            }
            // add addresses in cc and bcc map
            HashMap<String, String> fullAddressMap = new HashMap<String, String>(formattedAddressNameToMap);
            if (addressCcNameMap != null) {
                fullAddressMap.putAll(addressCcNameMap);
                extras.put(MailServlet.CC_ADDRESS_LIST_KEY, addressCcNameMap);
            }
            if (addressBccNameMap != null) {
                fullAddressMap.putAll(addressBccNameMap);
                extras.put(MailServlet.BCC_ADDRESS_LIST_KEY, addressBccNameMap);
            }

            Return res = null;
            if (loadAddress != null) {

                Session session = Session.getOrCreateSession(Session.getSessionKey(config, loadAddress),
                                                             config.getKeyword());
                session.setDirection("outbound");
                session.setAccountId(config.getOwner());
                session.setLocalName(senderName);
                String preferred_language = session != null ? session.getLanguage() : null;
                if (preferred_language == null) {
                    preferred_language = config.getPreferred_language();
                }
                question.setPreferred_language(preferred_language);
                session.setQuestion(question);
                res = formQuestion(question, config.getConfigId(), loadAddress, null, session.getKey());

                if (config.getAdapterType().equalsIgnoreCase("cm") ||
                    config.getAdapterType().equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_SMS)) {
                    extras = CMStatus.storeSMSRelatedData(loadAddress, localaddress, config, question, res.reply,
                                                          session.getKey(), extras);
                }
                //check if session can be killed??
                if(res == null || res.question == null) {
                    session.setKilled(true);
                }
                session.storeSession();
                // Add key to the map (for the return)
                sessionKeyMap.put(loadAddress, session.getKey());
                sessions.add(session);
            }
            else {
                // Form the question without the responders address, because we don't know which one.
                res = formQuestion(question, config.getConfigId(), null, null, null);
                for (String address : fullAddressMap.keySet()) {
                    // store the session first
                    Session session = Session.getOrCreateSession(Session.getSessionKey(config, address),
                                                                 config.getKeyword());
                    session.setAccountId(config.getOwner());
                    session.setDirection("outbound");
                    session.setQuestion(question);
                    session.setLocalName(senderName);
                    if (config.getAdapterType().equalsIgnoreCase("cm") ||
                        config.getAdapterType().equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_SMS)) {
                        extras = CMStatus.storeSMSRelatedData(address, localaddress, config, question, res.reply,
                                                              session.getKey(), extras);
                    }
                    //check if session can be killed??
                    if(res == null || res.question == null) {
                        session.setKilled(true);
                    }
                    //save this session
                    session.storeSession();
                    // Add key to the map (for the return)
                    sessionKeyMap.put(address, session.getKey());
                    sessions.add(session);
                    DDRWrapper.log(question, session, "Start", config);
                }
            }
            
            subject = subject != null && !subject.isEmpty() ? subject : "Message from Ask-Fast";
            //play trial account audio if the account is trial
            if (config.getAccountType() != null && config.getAccountType().equals(AccountType.TRIAL)) {
                if (question.getPreferred_language() != null &&
                    (question.getPreferred_language().equals("nl") || question.getPreferred_language().equals("nl-nl"))) {
                    res.reply = "Dit is een proefaccount. Overweeg alstublieft om uw account te upgraden. \n" +
                                res.reply;
                }
                else {
                    res.reply = "This is a trial account. Please consider upgrading your account. \n" + res.reply;
                }
            }
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
            sessionKeyMap.put("Error", "Question JSON not found in url: " + url);
        }
        return sessionKeyMap;
    }
	
	public static void killSession(Session session) {
	    session.drop();
	}
	
    @Override
    public void service(HttpServletRequest req, HttpServletResponse res) throws IOException {

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
        }
        catch (Exception ex) {
            ex.printStackTrace();
            log.severe("Failed to parse received message: " + ex.getLocalizedMessage());
            return;
        }

        try {
            log.info(String.format("Inbound text: %s seen from: %s to: %s", msg.getBody(), msg.getAddress(),
                                   msg.getLocalAddress()));
            processMessage(msg);
            //set status 200 in hte response
            res.setStatus(HttpServletResponse.SC_OK);
        }
        catch (Exception e) {
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
        int count = 0;
        Map<String, Object> extras = msg.getExtras();
        AdapterConfig config;
        Session session = Session.getSession(getAdapterType() + "|" + localaddress + "|" + address);
        // If session is null it means the adapter is not found.
        if (session == null) {
            log.info("No session so retrieving config");
            config = AdapterConfig.findAdapterConfig(getAdapterType(), localaddress, keyword);
            if (config != null &&
                (config.getAdapterType().equalsIgnoreCase("cm") || config.getAdapterType()
                                                .equalsIgnoreCase(AdapterAgent.ADAPTER_TYPE_SMS))) {
                extras = CMStatus.storeSMSRelatedData(address, localaddress, config, null, getNoConfigMessage(),
                                                      getAdapterType() + "|" + localaddress + "|" + address, extras);
            }
            count = sendMessageAndAttachCharge(getNoConfigMessage(), subject, localaddress,
                                               getSenderName(null, config, null), address, toName, extras, config);
            // Create new session to store the send in the ddr.
            session = Session.getOrCreateSession(config, address);
            session.setKeyword(keyword);
            session.setDirection("inbound");
            for (int i = 0; i < count; i++) {
                DDRWrapper.log(null, null, session, "Send", config);
            }
            session.storeSession();
            return count;
        }
        config = session.getAdapterConfig();
        String fromName = session.getLocalName() != null && !session.getLocalName().isEmpty() ? session.getLocalName()
                                                                                             : getSenderName(null,
                                                                                                             config,
                                                                                                             null);
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
                                                              getNoConfigMessage(), session.getKey(), extras);
                    }
                    fromName = session.getLocalName() != null && !session.getLocalName().isEmpty() ? session
                                                    .getLocalName() : getSenderName(null, config, null);
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
            session.storeSession();
        }

        //check if the keyword matches that of the adapter keyword
        if ("inbound".equalsIgnoreCase(session.getDirection()) && config.getKeyword() != null &&
            config.getKeyword().equalsIgnoreCase(keyword)) {
            //perform a case insensitive replacement
            body = body.replaceFirst("(?i)" + keyword, "").trim();
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
        Question question = session.getQuestion();
        if (!escapeInput.skip) {
            if (escapeInput.preferred_language == null) {
                escapeInput.preferred_language = "nl";
            }

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
                Return replystr = formQuestion(question, config.getConfigId(), address, null,
                                               session != null ? session.getKey() : null);
                // fix for bug: #15 https://github.com/almende/dialog/issues/15
                escapeInput.reply = URLDecoder.decode(replystr.reply, "UTF-8");
                question = replystr.question;

                extras = CMStatus.storeSMSRelatedData(address, localaddress, config, question, escapeInput.reply,
                                                      session.getKey(), extras);
            }
            else {
                log.severe(String.format("Question is null. Couldnt fetch Question from session, nor initialAgentURL: %s nor from demoDialog",
                                         config.getURLForInboundScenario(), this.host + DEMODIALOG));
            }
        }

        try {
            session.setQuestion(question);
            session.storeSession();
            count = sendMessageAndAttachCharge(escapeInput.reply, subject, localaddress, fromName, address, toName,
                                               extras, config);
            //flush the session is no more question is there
            if (question == null) {
                //dont flush the session yet if its an sms. the DLR callback needs a session.
                //instead just mark the session that it can be killed 
                if(AdapterAgent.ADAPTER_TYPE_SMS.equalsIgnoreCase(config.getAdapterType())) {
                    //refetch session
                    session = Session.getSession(Session.getSessionKey(config, address));
                    session.setKilled(true);
                    session.storeSession();
                }
                else {
                    session.drop();
                }
                DDRWrapper.log(question, session, "Hangup", config);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
            log.severe("Message sending failed. Message: " + ex.getLocalizedMessage());
        }
        for (int i = 0; i < count; i++) {
            DDRWrapper.log(null, null, session, "Send", config);
        }
        return count;
    }
	
    /**
     * processses any escape command entered by the user
     * 
     * @return
     */
    private int processEscapeInputCommand(TextMessage msg, String fromName, AdapterConfig config,
                                          EscapeInputCommand escapeInput, Session session) throws Exception {

        log.info(String.format("escape charecter seen.. input %s", escapeInput.body));
        int result = 0;
        String cmd = escapeInput.body.toLowerCase().substring(1);
        if (cmd.startsWith("language=")) {
            escapeInput.preferred_language = cmd.substring(9);
            if (escapeInput.preferred_language.indexOf(' ') != -1)
                escapeInput.preferred_language = escapeInput.preferred_language
                                                .substring(0, escapeInput.preferred_language.indexOf(' '));

            session.setLanguage(escapeInput.preferred_language);

            escapeInput.reply = "Ok, switched preferred language to:" + escapeInput.preferred_language;
            escapeInput.body = "";

            HashMap<String, String> addressNameMap = new HashMap<String, String>(1);
            addressNameMap.put(msg.getAddress(), msg.getRecipientName());
            Map<String, Object> extras = CMStatus.storeSMSRelatedData(msg.getAddress(), msg.getLocalAddress(), config,
                                                                      null, session.getKey(), escapeInput.reply, null);
            result = broadcastMessageAndAttachCharge(escapeInput.reply, msg.getSubject(), msg.getLocalAddress(),
                                                     fromName, addressNameMap, extras, config);
        }
        else if (cmd.startsWith("reset")) {
            session.drop();
        }

        else if (cmd.startsWith("help")) {
            String[] command = cmd.split(" ");
            if (command.length == 1) {
                escapeInput.reply = "The following commands are understood:\n" + "/help <command>\n" + "/reset \n"
                                    + "/language=<lang_code>\n";
            }
            else {
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
        //attach costs
        HashMap<String, String> toAddressMap = new HashMap<String, String>();
        toAddressMap.put( to, toName );
        DDRRecord ddrRecord = createDDRForOutgoing( config, fromName, toAddressMap, message );
        //store the ddrRecord in the session
        if (ddrRecord != null) {
            Session session = Session.getSession(getAdapterType(), config.getMyAddress(), to);
            if (session != null) {
                session.setDdrRecordId(ddrRecord.getId());
                session.storeSession();
                //put sessionKey and tracking token to extras
                extras.put(Session.SESSION_KEY, session.getKey());
                extras.put(Session.TRACKING_TOKEN_KEY, session.getTrackingToken());
            }
            //save all the properties stored in the extras
            for (String extraPropertyKey : extras.keySet()) {
                ddrRecord.addAdditionalInfo(extraPropertyKey, extras.get(extraPropertyKey).toString());
            }
            ddrRecord.createOrUpdate();
            extras.put(DDRRecord.DDR_RECORD_KEY, ddrRecord.getId());
        }
        
        //send the message
        int count = sendMessage( message, subject, from, fromName, to, toName, extras, config );
        //push the cost to hte queue
        Double totalCost = DDRUtils.calculateCommunicationDDRCost( ddrRecord, true );
        //attach cost to ddr is prepaid type
        if(ddrRecord != null && AccountType.PRE_PAID.equals(ddrRecord.getAccountType())) {
            ddrRecord.setTotalCost(totalCost);
            ddrRecord.createOrUpdate();
        }
        return count;
    }
	    
    /**
     * First creates a ddr record, broadcasts a message and charges the owner of
     * the adapter for outbound communication
     * 
     * @param message
     *            message to be sent
     * @param subject
     * @param from
     * @param senderName
     * @param addressNameMap
     * @param extras
     * @param config
     * @return the number of outbound messages done
     * @throws Exception
     */
    private int broadcastMessageAndAttachCharge(String message, String subject, String from, String senderName,
                                                Map<String, String> addressNameMap, Map<String, Object> extras,
                                                AdapterConfig config) throws Exception {

        //create all the ddrRecords first
        addressNameMap = addressNameMap != null ? addressNameMap : new HashMap<String, String>();
        HashMap<String, String> copyOfAddressNameMap = new HashMap<String, String>(addressNameMap);
        //attach costs for all members (even in cc and bcc if any)
        if (extras != null) {
            copyOfAddressNameMap.putAll(addRecipientsInCCAndBCCToAddressMap(extras));
        }
        DDRRecord ddrRecord = createDDRForOutgoing(config, senderName, copyOfAddressNameMap, message);
        //store the ddrRecord in the session
        if (ddrRecord != null) {
            int addressCount = 0;
            for (String address : copyOfAddressNameMap.keySet()) {
                Session session = Session.getSession(getAdapterType(), config.getMyAddress(), address);
                if (session != null) {
                    session.setDdrRecordId(ddrRecord.getId());
                    session.storeSession();
                    //put sessionKey and tracking token to extras
                    extras.put(Session.SESSION_KEY + "_" + addressCount, session.getKey());
                    extras.put(Session.TRACKING_TOKEN_KEY + "_" + addressCount, session.getTrackingToken());
                }
                addressCount++;
            }
            //save all the properties stored in the extras
            for (String extraPropertyKey : extras.keySet()) {
                ddrRecord.addAdditionalInfo(extraPropertyKey, extras.get(extraPropertyKey).toString());
            }
            ddrRecord.createOrUpdate();
            extras.put(DDRRecord.DDR_RECORD_KEY, ddrRecord.getId());
        }
        //broadcast the message
        int count = broadcastMessage(message, subject, from, senderName, addressNameMap, extras, config);
        //push the cost to hte queue
        Double totalCost = DDRUtils.calculateCommunicationDDRCost(ddrRecord, true);
        DDRUtils.publishDDREntryToQueue(config.getOwner(), totalCost);
        //attach cost to ddr is prepaid type
        if (ddrRecord != null && AccountType.PRE_PAID.equals(ddrRecord.getAccountType())) {
            ddrRecord.setTotalCost(totalCost);
            ddrRecord.createOrUpdate();
        }
        return count;
    }
    
    private TextMessage receiveMessageAndAttachCharge(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        TextMessage receiveMessage = receiveMessage(req, resp);
        //create a session
        Session session = Session.getOrCreateSession(getAdapterType(), receiveMessage.getLocalAddress(), receiveMessage.getAddress());
        //update the current timestamp
        if (session != null) {
            session.setCreationTimestamp(String.valueOf(TimeUtils.getServerCurrentTimeInMillis()));
            session.storeSession();
        }
        log.info("Incoming message received: " + ServerUtils.serialize(receiveMessage));
        //attach charges for incoming
        AdapterConfig config = AdapterConfig.findAdapterConfig(getAdapterType(), receiveMessage.getLocalAddress());
        DDRRecord ddrRecord = createDDRForIncoming(config, receiveMessage.getAddress(), receiveMessage.getBody());
        //store the ddrRecord in the session
        if(ddrRecord != null) {
            session.setDdrRecordId(ddrRecord.getId());
            session.storeSession();
        }
        //push the cost to hte queue
        Double totalCost = DDRUtils.calculateCommunicationDDRCost(ddrRecord, true);
        //attach cost to ddr is prepaid type
        if (ddrRecord != null && AccountType.PRE_PAID.equals(ddrRecord.getAccountType())) {
            ddrRecord.setTotalCost(totalCost);
            ddrRecord.createOrUpdate();
        }
        return receiveMessage;
    }

    /**
     * collates all the addresses in the cc and bcc
     * @param extras
     * @return
     */
    private HashMap<String, String> addRecipientsInCCAndBCCToAddressMap(Map<String, Object> extras) {

        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        //add cc list
        if (extras.get(MailServlet.CC_ADDRESS_LIST_KEY) != null) {
            if (extras.get(MailServlet.CC_ADDRESS_LIST_KEY) instanceof Map) {
                TypeUtil<HashMap<String, String>> injector = new TypeUtil<HashMap<String, String>>() {
                };
                HashMap<String, String> ccAddressNameMap = injector.inject(extras.get(MailServlet.CC_ADDRESS_LIST_KEY));
                addressNameMap.putAll(ccAddressNameMap);
            }
            else {
                log.severe(String.format("CC list seen but not of Map type: %s", ServerUtils
                                                .serializeWithoutException(extras.get(MailServlet.CC_ADDRESS_LIST_KEY))));
            }
        }
        //add bcc list
        if (extras.get(MailServlet.BCC_ADDRESS_LIST_KEY) != null) {
            if (extras.get(MailServlet.BCC_ADDRESS_LIST_KEY) instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> bccAddressNameMap = (Map<String, String>) extras
                                                .get(MailServlet.BCC_ADDRESS_LIST_KEY);
                addressNameMap.putAll(bccAddressNameMap);
            }
            else {
                log.severe(String.format("BCC list seen but not of Map type: %s",
                                         ServerUtils.serializeWithoutException(extras
                                                                         .get(MailServlet.BCC_ADDRESS_LIST_KEY))));
            }
        }
        return addressNameMap;
    }
    
    /**
     * assign senderName with localAdress, if senderName is missing priority is
     * as: nickname (from Question) >> senderName (as requested from API) >>
     * myAddress (from adapter) >> "ASK-Fast" (default)
     * 
     * @param question
     * @param config
     * @return
     */
    private String getSenderName(Question question, AdapterConfig config, String senderName) {

        String nameFromQuestion = getNickname(question);
        if (nameFromQuestion != null && !nameFromQuestion.isEmpty()) {
            return nameFromQuestion;
        }
        else if (senderName != null && !senderName.isEmpty()) {
            return senderName;
        }
        else if (config != null && config.getMyAddress() != null && !config.getMyAddress().isEmpty()) {
            return config.getMyAddress();
        }
        return "ASK-Fast";
    }
}
