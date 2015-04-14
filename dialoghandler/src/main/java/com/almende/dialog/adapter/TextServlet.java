package com.almende.dialog.adapter;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.RequestUtil;
import com.almende.dialog.util.ServerUtils;
import com.almende.util.ParallelInit;
import com.almende.util.TypeUtil;
import com.askfast.commons.entity.AccountType;
import com.askfast.commons.entity.Language;
import com.askfast.commons.utils.PhoneNumberUtils;
import com.askfast.commons.utils.TimeUtils;

@SuppressWarnings("serial")
abstract public class TextServlet extends HttpServlet {

    protected static final Logger log = Logger.getLogger(TextServlet.class.getSimpleName());
    protected static final com.almende.dialog.Logger logger = new com.almende.dialog.Logger();
    protected static final int LOOP_DETECTION = 10;
    protected static final String DEMODIALOG = "/charlotte/";

    /**
     * @deprecated use
     *             {@link TextServlet#broadcastMessage(String,String,String, String, String, Map, AdapterConfig)
     *             broadcastMessage} instead.
     */
    @Deprecated
    protected abstract int
                                    sendMessage(String message, String subject, String from, String fromName,
                                                String to, String toName, Map<String, Object> extras,
                                                AdapterConfig config, String accountId) throws Exception;

    /**
     * Overridden by all text based communication channel servlets for
     * broadcasting the same message to multiple addresses
     * 
     * @param message
     *            The message to be sent. This can be a url also
     * @param subject
     *            This is used only by the email servlet
     * @param from
     *            This is the address from which a broadcast is sent
     * @param senderName
     *            The sendername, used only by the email servlet, SMS
     * @param addressNameMap
     *            Map with address (e.g. phonenumber or email) as Key and name
     *            as value. The name is useful for email and not used for SMS
     *            etc
     * @param extras
     *            Some extra properties that can be used for processing.
     * @param Config
     *            the adapterConfig which is used to perform this broadcast
     * @param accountId
     *            AccoundId initiating this broadcast
     * @return
     * @throws Exception
     */
    protected abstract int broadcastMessage(String message, String subject, String from, String senderName,
                                            Map<String, String> addressNameMap, Map<String, Object> extras,
                                            AdapterConfig config, String accountId) throws Exception;

    protected abstract
                                    DDRRecord
                                    createDDRForIncoming(AdapterConfig adapterConfig, String accountId,
                                                         String fromAddress, String message, String sessionKey)
                                                                                                               throws Exception;

    protected abstract DDRRecord createDDRForOutgoing(AdapterConfig adapterConfig, String accountId, String senderName,
                                                      Map<String, String> toAddress, String message,
                                                      Map<String, String> sessionKeyMap) throws Exception;

    protected abstract TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp) throws Exception;

    protected abstract String getServletPath();

    protected abstract String getAdapterType();

    protected abstract String getProviderType();

    protected abstract void doErrorPost(HttpServletRequest req, HttpServletResponse res) throws IOException;

    private String host = "";

    protected class Return {

        String reply;
        Question question;

        public Return(String reply, Question question) {

            this.reply = reply;
            this.question = question;
        }
    }

    /**
     * info for generating a Return when a user enters an escape command as
     * input. E.g. /reset
     * 
     * @author Shravan
     */
    private class EscapeInputCommand {

        boolean skip;
        String body;
        String preferred_language;
        String reply;

        @Override
        public String toString() {

            return String.format("Skip: %s body: %s preferred_lang: %s reply %s", skip, body, preferred_language, reply);
        }
    }

    public Return formQuestion(Question question, String adapterID, String address, String ddrRecordId,
                               String sessionKey) {

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
            String qText = question.getQuestion_expandedtext(sessionKey);
            if (qText != null && !qText.equals(""))
                reply += qText;

            if (question.getType().equalsIgnoreCase("closed")) {
                reply += "\n[";
                for (Answer ans : question.getAnswers()) {
                    reply += " " + ans.getAnswer_expandedtext(question.getPreferred_language(), sessionKey) + " |";
                }
                reply = reply.substring(0, reply.length() - 1) + "]";
                break; // Jump from forloop
            }
            else if (question.getType().equalsIgnoreCase("comment")) {
                // Always returns null!
                // So no need, but maybe in future?
                question = question.answer(null, adapterID, null, null, null);
            }
            else if (question.getType().equalsIgnoreCase("referral")) {
                question = Question.fromURL(question.getUrl(), adapterID, address, ddrRecordId, sessionKey, null);
            }
            else if (question.getType().equalsIgnoreCase("exit")) {
                break;
            }
            else {
                break; // Jump from forloop (open questions, etc.)
            }
        }
        return new Return(reply, question);
    }

    /**
     * Just overrides the
     * {@link TextServlet#startDialog(Map, Map, Map, String, String, String, AdapterConfig, String)}
     * instead.
     * 
     * @param address
     *            destination address
     * @param dialogIdOrUrl
     *            If a String with leading "http" is found its considered as a
     *            url. Else a Dialog of this id is fetched. Any text with prefix
     *            text:// is converted to a URL endpoint automatically. A GET
     *            HTTPRequest is performed and expected a question JSON.
     * @param config
     *            AdapterConfig linked to this outbound call
     * @param accountId
     *            AccountId initiating this call
     * @return
     * @throws Exception
     */
    public String
                                    startDialog(String address, String dialogIdOrUrl, AdapterConfig config,
                                                String accountId) throws Exception {

        HashMap<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(address, "");
        HashMap<String, String> result = startDialog(addressNameMap, null, null, dialogIdOrUrl, null, null, config,
                                                     accountId);
        if (result != null && !result.isEmpty()) {
            return result.keySet().iterator().next();
        }
        return null;
    }

    /**
     * Method used to broadcast the same message to multiple addresses
     * 
     * @param addressNameMap
     *            Map with address (e.g. phonenumber or email) as Key and name
     *            as value. The name is useful for email and not used for SMS
     *            etc
     * @param addressCcNameMap
     *            Cc list of the address to which this message is broadcasted.
     *            This is only used by the email servlet.
     * @param addressBccNameMap
     *            Bcc list of the address to which this message is broadcasted.
     *            This is only used by the email servlet.
     * @param dialogIdOrUrl
     *            If a String with leading "http" is found its considered as a
     *            url. Else a Dialog of this id is fetched. Any text with prefix
     *            text:// is converted to a URL endpoint automatically. A GET
     *            HTTPRequest is performed and expected a question JSON.
     * @param senderName
     *            The sendername, used only by the email servlet, SMS
     * @param subject
     *            This is used only by the email servlet
     * @param config
     *            the adapterConfig which is used to perform this broadcast
     * @param accountId
     *            AccoundId initiating this broadcast. All costs are applied to
     *            this accountId
     * @return
     * @throws Exception
     */
    public HashMap<String, String>
                                    startDialog(Map<String, String> addressNameMap,
                                                Map<String, String> addressCcNameMap,
                                                Map<String, String> addressBccNameMap, String dialogIdOrUrl,
                                                String senderName, String subject, AdapterConfig config,
                                                String accountId) throws Exception {

        addressNameMap = addressNameMap != null ? addressNameMap : new HashMap<String, String>();
        addressCcNameMap = addressCcNameMap != null ? addressCcNameMap : new HashMap<String, String>();
        addressBccNameMap = addressBccNameMap != null ? addressBccNameMap : new HashMap<String, String>();
        String localaddress = config.getMyAddress();

        HashMap<String, String> sessionKeyMap = new HashMap<String, String>();

        String loadAddress = null;
        // If it is a broadcast don't provide the remote address because it is deceiving.
        if (addressNameMap.size() + addressCcNameMap.size() + addressBccNameMap.size() == 0) {
            log.severe("No addresses found to start a dialog");
            throw new Exception("No addresses found to start a dialog");
        }
        else if (addressNameMap.size() + addressCcNameMap.size() + addressBccNameMap.size() == 1) {

            loadAddress = addressNameMap.keySet().iterator().next();
            if (config.isSMSAdapter()) {
                loadAddress = PhoneNumberUtils.formatNumber(loadAddress, null);
            }
        }
        //create a session for the first remote address
        String firstAddressAddress = fetchFirstRemoteAddress(addressNameMap, addressCcNameMap, addressBccNameMap,
                                                             loadAddress);
        if (config.isSMSAdapter()) {
            firstAddressAddress = PhoneNumberUtils.formatNumber(firstAddressAddress, null);
        }
        Session session = null;
        if (firstAddressAddress != null) {
            session = Session.createSession(config, firstAddressAddress, true);
            session.addExtras(AdapterConfig.ADAPTER_PROVIDER_KEY, getProviderType());
            session.setAccountId(accountId);
            session.storeSession();
        }
        dialogIdOrUrl = Dialog.getDialogURL(dialogIdOrUrl, accountId, session);

        // fetch question
        // If it is a broadcast don't provide the remote address because it is deceiving.
        Question question = Question.fromURL(dialogIdOrUrl, config.getConfigId(), loadAddress, config.getMyAddress(),
                                             null, session != null ? session.getKey() : null, null);

        if (question != null) {

            //fetch the senderName
            senderName = getSenderName(question, config, senderName, session != null ? session.getKey() : null);
            // store the extra information
            Map<String, Object> extras = new HashMap<String, Object>();
            if (question.getMedia_properties() != null) {
                extras.put(Question.MEDIA_PROPERTIES, question.getMedia_properties());
            }
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
            //keep a copy of the formatted addressNameMap. dont save any cc or bcc address name maps here 
            Map<String, String> formattedAddressNameToMap = new HashMap<String, String>();
            // Form the question without the responders address, because we don't know which one.
            question.setPreferred_language(session.getLanguage());
            res = formQuestion(question, config.getConfigId(), null, null, null);

            for (String address : fullAddressMap.keySet()) {
                String formattedAddress = address; //initialize formatted address to be the original one
                if (config.isSMSAdapter()) {
                    formattedAddress = PhoneNumberUtils.formatNumber(address, null);
                }
                if (formattedAddress != null) {

                    //ignore the address for which the session is already created.
                    if (!session.getRemoteAddress().equals(formattedAddress)) {
                        // store the session first
                        session = Session.getOrCreateSession(config, formattedAddress);
                        session.setAccountId(accountId);
                        session.setDirection("outbound");
                        session.setQuestion(question);
                        session.setLocalName(senderName);
                        String preferred_language = session != null ? session.getLanguage() : null;
                        if (preferred_language == null) {
                            preferred_language = config.getPreferred_language();
                        }
                        question.setPreferred_language(preferred_language);
                        session.addExtras(AdapterConfig.ADAPTER_PROVIDER_KEY, getProviderType());
                        //check if session can be killed??
                        if (res == null || res.question == null) {
                            session.setKilled(true);
                        }
                        dialogIdOrUrl = Dialog.getDialogURL(dialogIdOrUrl, accountId, session);
                        //save this session
                        session.storeSession();
                    }
                    //put the formatted address to that a text can be broadcasted to it
                    formattedAddressNameToMap.put(formattedAddress, fullAddressMap.get(address));
                    // Add key to the map (for the return)
                    sessionKeyMap.put(formattedAddress, session.getKey());
                }
                else {
                    sessionKeyMap.put(address, "Invalid address");
                    log.severe(String.format("To address is invalid: %s. Ignoring.. ", address));
                }
            }

            subject = subject != null && !subject.isEmpty() ? subject : "Message from Ask-Fast";
            //play trial account audio if the account is trial
            if (config.getAccountType() != null && config.getAccountType().equals(AccountType.TRIAL)) {
                if (Language.DUTCH.equals(Language.getByValue(question.getPreferred_language()))) {
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
                                                        formattedAddressNameToMap, extras, config, accountId,
                                                        sessionKeyMap);
            if (count < 1) {
                log.severe("Error generating XML");
            }
        }
        else {
            sessionKeyMap.put("Error", "Question JSON not found in dialog/url: " + dialogIdOrUrl);
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
     * Processes any incoming messages (based on a Dialog) and takes actions
     * like sending, broadcasting corresponding messages.
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
        Session session = Session.getSessionByInternalKey(getAdapterType() + "|" + localaddress + "|" + address);
        // If session is null it means the adapter is not found.
        if (session == null) {
            log.info("No session so retrieving config");
            config = AdapterConfig.findAdapterConfig(getAdapterType(), localaddress, keyword);
            count = sendMessageAndAttachCharge(getNoConfigMessage(), subject, localaddress,
                                               getSenderName(null, config, null, null), address, toName, extras,
                                               config, config.getOwner(), null);
            // Create new session to store the send in the ddr.
            session = Session.createSession(config, address, true);
            session.setAccountId(config.getOwner());
            session.setKeyword(keyword);
            session.setDirection("inbound");
            session.storeSession();
            return count;
        }
        config = session.getAdapterConfig();
        String fromName = session.getLocalName() != null && !session.getLocalName().isEmpty() ? session.getLocalName()
                                                                                             : getSenderName(null,
                                                                                                             config,
                                                                                                             null, null);
        // TODO: Remove this check, this is now to support backward
        // compatibility (write patch)
        if (config == null) {
            log.info("Session doesn't contain config, so searching it again");
            config = AdapterConfig.findAdapterConfig(getAdapterType(), localaddress, keyword);
            if (config == null) {
                config = AdapterConfig.findAdapterConfig(getAdapterType(), localaddress);
                try {
                    fromName = session.getLocalName() != null && !session.getLocalName().isEmpty() ? session
                                                    .getLocalName() : getSenderName(null, config, null,
                                                                                    session.getKey());
                    count = sendMessageAndAttachCharge(getNoConfigMessage(), subject, localaddress, fromName, address,
                                                       toName, extras, config, session.getAccountId(), session.getKey());
                }
                catch (Exception ex) {
                    log.severe(ex.getLocalizedMessage());
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
            count = processEscapeInputCommand(msg, fromName, config, session.getAccountId(), escapeInput, session);
            log.info(escapeInput.toString());
        }
        Question question = session.getQuestion();
        if (!escapeInput.skip) {
            if (escapeInput.preferred_language == null) {
                escapeInput.preferred_language = "nl";
            }

            // Here we can add extra parameters in the future
            Map<String, String> extraParams = new HashMap<String, String>();

            boolean start = false;
            if (question == null) {
                if (config.getDialog() != null && "".equals(config.getDialog().getUrl())) {
                    question = Question.fromURL(this.host + DEMODIALOG, config.getConfigId(), address, localaddress,
                                                session.getDdrRecordId(), session.getKey(), extraParams);
                }
                else {
                    question = Question.fromURL(config.getURLForInboundScenario(session), config.getConfigId(),
                                                address, localaddress, session.getDdrRecordId(), session.getKey(),
                                                extraParams);
                }
                session.setDirection("inbound");
                start = true;
            }

            if (question != null) {
                question.setPreferred_language(preferred_language);
                // Do not answer a question, when it's the first and the type is
                // comment or referral anyway.
                if (!(start && (question.getType().equalsIgnoreCase("comment") || question.getType()
                                                .equalsIgnoreCase("referral")))) {
                    question = question.answer(address, config.getConfigId(), null, escapeInput.body,
                                               session != null ? session.getKey() : null);
                }
                Return replystr = formQuestion(question, config.getConfigId(), address, null,
                                               session != null ? session.getKey() : null);
                // fix for bug: #15 https://github.com/almende/dialog/issues/15
                escapeInput.reply = URLDecoder.decode(replystr.reply, "UTF-8");
                question = replystr.question;
            }
            else {
                log.severe(String.format("Question is null. Couldnt fetch Question from session, nor initialAgentURL: %s nor from demoDialog",
                                         config.getURLForInboundScenario(session), this.host + DEMODIALOG));
            }
        }

        try {
            session.setQuestion(question);
            session.storeSession();
            count = sendMessageAndAttachCharge(escapeInput.reply, subject, localaddress, fromName, address, toName,
                                               extras, config, session.getAccountId(), session.getKey());
            //flush the session is no more question is there
            if (question == null) {
                //dont flush the session yet if its an sms. the DLR callback needs a session.
                //instead just mark the session that it can be killed 
                if (AdapterAgent.ADAPTER_TYPE_SMS.equalsIgnoreCase(config.getAdapterType())) {
                    //refetch session
                    session = Session.getSessionByInternalKey(Session.getInternalSessionKey(config, address));
                    session.setKilled(true);
                    session.storeSession();
                }
                else {
                    session.drop();
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
            log.severe("Message sending failed. Message: " + ex.getLocalizedMessage());
        }
        return count;
    }

    /**
     * processses any escape command entered by the user
     * 
     * @return
     */
    private int processEscapeInputCommand(TextMessage msg, String fromName, AdapterConfig config, String accountId,
                                          EscapeInputCommand escapeInput, Session session) throws Exception {

        log.info(String.format("escape charecter seen.. input %s", escapeInput.body));
        int result = 0;
        String cmd = escapeInput.body.toLowerCase().substring(1);
        if (cmd.startsWith("language=") && session != null) {
            escapeInput.preferred_language = cmd.substring(9);
            if (escapeInput.preferred_language.indexOf(' ') != -1)
                escapeInput.preferred_language = escapeInput.preferred_language
                                                .substring(0, escapeInput.preferred_language.indexOf(' '));

            session.setLanguage(escapeInput.preferred_language);

            escapeInput.reply = "Ok, switched preferred language to:" + escapeInput.preferred_language;
            escapeInput.body = "";

            HashMap<String, String> addressNameMap = new HashMap<String, String>(1);
            addressNameMap.put(msg.getAddress(), msg.getRecipientName());
            HashMap<String, String> sessionKeyMap = new HashMap<String, String>(1);
            addressNameMap.put(msg.getAddress(), session.getKey());
            result = broadcastMessageAndAttachCharge(escapeInput.reply, msg.getSubject(), msg.getLocalAddress(),
                                                     fromName, addressNameMap, null, config, accountId, sessionKeyMap);
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

    private String getNickname(Question question, String sessionKey) {

        String nickname = null;
        if (question != null) {
            HashMap<String, String> id = question.getExpandedRequester(sessionKey);
            nickname = id.get("nickname");
        }

        return nickname;
    }

    /**
     * sends a message and charges the owner of the adapter for outbound
     * communication
     * 
     * @param message
     *            message to be sent
     * @param subject
     * @param from
     * @param fromName
     * @param to
     * @param toName
     * @param extras
     * @param config
     * @return the number of messages sent. Can be more than 1 when sending
     *         special charecters in SMS
     * @throws Exception
     */
    private int sendMessageAndAttachCharge(String message, String subject, String from, String fromName, String to,
                                           String toName, Map<String, Object> extras, AdapterConfig config,
                                           String accountId, String sessionKey) throws Exception {

        Map<String, String> addressNameMap = new HashMap<String, String>();
        addressNameMap.put(to, toName);
        HashMap<String, String> sessionKeyMap = new HashMap<String, String>();
        sessionKeyMap.put(to, sessionKey);
        return broadcastMessageAndAttachCharge(message, subject, from, fromName, addressNameMap, extras, config,
                                               accountId, sessionKeyMap);
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
                                                AdapterConfig config, String accountId,
                                                Map<String, String> sessionKeyMap) throws Exception {

        //create all the ddrRecords first
        addressNameMap = addressNameMap != null ? addressNameMap : new HashMap<String, String>();
        HashMap<String, String> copyOfAddressNameMap = new HashMap<String, String>(addressNameMap);
        //attach costs for all members (even in cc and bcc if any)
        extras = extras != null ? extras : new HashMap<String, Object>();
        copyOfAddressNameMap.putAll(addRecipientsInCCAndBCCToAddressMap(extras));

        DDRRecord ddrRecord = null;
        try {
            ddrRecord = createDDRForOutgoing(config, accountId, senderName, copyOfAddressNameMap, message,
                                             sessionKeyMap);
        }
        catch (Exception e) {
            e.printStackTrace();
            log.severe(String.format("Continuing without DDR. Error: %s", e.toString()));
        }
        //store the ddrRecord in the session
        if (ddrRecord != null) {
            for (String address : copyOfAddressNameMap.keySet()) {
                Session session = Session.getSession(sessionKeyMap.get(address));
                if (session != null) {
                    session.setDdrRecordId(ddrRecord.getId());
                    session.storeSession();
                    ddrRecord.addTrackingToken(address, session.getTrackingToken());
                }
            }
            //save all the properties stored in the extras
            for (String extraPropertyKey : extras.keySet()) {
                if (extras.get(extraPropertyKey) != null) {
                    ddrRecord.addAdditionalInfo(extraPropertyKey, extras.get(extraPropertyKey));
                }
            }
            ddrRecord.createOrUpdate();
            extras.put(DDRRecord.DDR_RECORD_KEY, ddrRecord.getId());
        }

        //update the sessions to the extras
        if (sessionKeyMap != null && !sessionKeyMap.isEmpty()) {
            extras.put(Session.SESSION_KEY, sessionKeyMap);
        }
        //broadcast the message if its not a test environment
        Integer count = broadcastMessage(message, subject, from, senderName, addressNameMap, extras, config, accountId);
        //push the cost to hte queue
        Double totalCost = DDRUtils.calculateDDRCost(ddrRecord, true);
        DDRUtils.publishDDREntryToQueue(accountId, totalCost);
        //attach cost to ddr is prepaid type
        if (ddrRecord != null && AccountType.PRE_PAID.equals(ddrRecord.getAccountType())) {
            ddrRecord.setTotalCost(totalCost);
            ddrRecord.createOrUpdate();
        }
        return count;
    }

    private TextMessage receiveMessageAndAttachCharge(HttpServletRequest req, HttpServletResponse resp)
                                                                                                       throws Exception {

        TextMessage receiveMessage = receiveMessage(req, resp);
        try {
            //create a session if it does not exist
            Session session = Session.getOrCreateSession(getAdapterType(), receiveMessage.getLocalAddress(),
                                                         receiveMessage.getAddress());
            //update the current timestamp
            if (session != null) {
                AdapterConfig adapterConfig = session.getAdapterConfig();
                //update the owner accountId if its not an existing session. 
                //So for pure inbound session. and not a reply on a closed/open question.
                if (adapterConfig != null && !session.isExistingSession()) {
                    session.setAccountId(adapterConfig.getOwner());
                }
                session.setCreationTimestamp(String.valueOf(TimeUtils.getServerCurrentTimeInMillis()));
                session.storeSession();
                logger.info(session.getAdapterConfig(), ServerUtils.serialize(receiveMessage), session);
            }
            //attach charges for incoming
            AdapterConfig config = AdapterConfig.findAdapterConfig(getAdapterType(), receiveMessage.getLocalAddress());
            DDRRecord ddrRecord = null;
            try {
                ddrRecord = createDDRForIncoming(config, receiveMessage.getAddress(), receiveMessage.getBody(),
                                                 session.getAccountId(), session.getKey());
            }
            catch (Exception e) {
                e.printStackTrace();
                log.severe(String.format("Continuing without DDR. Error: %s", e.toString()));
            }

            if (session != null) {
                receiveMessage.getExtras().put(Session.SESSION_KEY, session.getKey());
                receiveMessage.getExtras().put(Session.TRACKING_TOKEN_KEY, session.getTrackingToken());
                if (ddrRecord != null) {
                    ddrRecord.addAdditionalInfo(Session.SESSION_KEY, session.getKey());
                    ddrRecord.addAdditionalInfo(Session.TRACKING_TOKEN_KEY, session.getTrackingToken());
                    receiveMessage.getExtras().put(DDRRecord.DDR_RECORD_KEY, ddrRecord.getId());
                    ddrRecord.createOrUpdate();

                    //store the ddrRecord in the session
                    session.setDdrRecordId(ddrRecord.getId());
                    session.storeSession();
                }
            }

            //push the cost to hte queue
            Double totalCost = DDRUtils.calculateDDRCost(ddrRecord, true);
            DDRUtils.publishDDREntryToQueue(session.getAccountId(), totalCost);
            //attach cost to ddr is prepaid type
            if (ddrRecord != null && AccountType.PRE_PAID.equals(ddrRecord.getAccountType())) {
                ddrRecord.setTotalCost(totalCost);
                ddrRecord.createOrUpdate();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            log.severe("DDR processing failed for this incoming message. Message: " + e.toString());
        }
        return receiveMessage;
    }

    /**
     * collates all the addresses in the cc and bcc
     * 
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
    private String getSenderName(Question question, AdapterConfig config, String senderName, String sessionKey) {

        String nameFromQuestion = getNickname(question, sessionKey);
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

    /**
     * Returns the first remote address by looking into the addresses in to, cc
     * and bcc list
     * 
     * @param addressNameMap
     * @param addressCcNameMap
     * @param addressBccNameMap
     * @param loadAddress
     * @return
     */
    private String fetchFirstRemoteAddress(final Map<String, String> addressNameMap,
                                           final Map<String, String> addressCcNameMap,
                                           final Map<String, String> addressBccNameMap, final String loadAddress) {

        String firstAddressAddress = null;
        if (loadAddress != null) {
            firstAddressAddress = new String(loadAddress);
        }
        else if (addressNameMap != null && !addressNameMap.isEmpty()) {
            firstAddressAddress = new String(addressNameMap.keySet().iterator().next());
        }
        else if (addressCcNameMap != null && !addressCcNameMap.isEmpty()) {
            firstAddressAddress = new String(addressCcNameMap.keySet().iterator().next());
        }
        else if (addressBccNameMap != null && !addressBccNameMap.isEmpty()) {
            firstAddressAddress = new String(addressBccNameMap.keySet().iterator().next());
        }
        return firstAddressAddress;
    }
}
