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
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.state.StringStore;
import com.almende.dialog.util.PhoneNumberUtils;
import com.almende.dialog.util.RequestUtil;
import com.almende.util.ParallelInit;

@SuppressWarnings("serial")
abstract public class TextServlet extends HttpServlet {
	protected static final Logger	log				= Logger.getLogger(TextServlet.class
															.getSimpleName());
	protected static final int		LOOP_DETECTION	= 10;
	protected static final String	DEMODIALOG		= "/charlotte/";
	protected String				sessionKey		= null;
	
	/**
	 * @deprecated use
	 *             {@link TextServlet#broadcastMessage(String,String,String, String, String, Map, AdapterConfig)
	 *             broadcastMessage} instead.
	 */
	@Deprecated
	protected abstract int sendMessage(String message, String subject,
			String from, String fromName, String to, String toName,
			AdapterConfig config) throws Exception;
	
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
			AdapterConfig config) throws Exception;
	
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
	
	public Return formQuestion(Question question, String adapterID,
			String address) {
		String reply = "";
		
		String preferred_language = "nl"; // TODO: Change to null??
		if (question != null) preferred_language = question
				.getPreferred_language();
		
		for (int count = 0; count <= LOOP_DETECTION; count++) {
			if (question == null) break;
			question.setPreferred_language(preferred_language);
			
			if (!reply.equals("")) reply += "\n";
			String qText = question.getQuestion_expandedtext();
			if (qText != null && !qText.equals("")) reply += qText;
			
			if (question.getType().equalsIgnoreCase("closed")) {
				reply += "\n[";
				for (Answer ans : question.getAnswers()) {
					reply += " "
							+ ans.getAnswer_expandedtext(question
									.getPreferred_language()) + " |";
				}
				reply = reply.substring(0, reply.length() - 1) + " ]";
				break; // Jump from forloop
			} else if (question.getType().equalsIgnoreCase("comment")) {
				question = question.answer(null, adapterID, null, null, null);// Always
																				// returns
																				// null!
																				// So
																				// no
																				// need,
																				// but
																				// maybe
																				// in
																				// future?
			} else if (question.getType().equalsIgnoreCase("referral")) {
				question = Question.fromURL(question.getUrl(), adapterID,
						address);
			} else {
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
		if (config.getAdapterType().equals("CM")
				|| config.getAdapterType().equals("SMS")) {
			address = PhoneNumberUtils.formatNumber(address, null);
		}
		String localaddress = config.getMyAddress();
		sessionKey = getAdapterType() + "|" + localaddress + "|" + address;
		Session session = Session.getSession(sessionKey, config.getKeyword());
		if (session == null) {
			log.severe("TextServlet couldn't start new outbound Dialog, adapterConfig not found? "
					+ sessionKey);
			return "";
		}
		session.setPubKey(config.getPublicKey());
		session.setDirection("outbound");
		session.setTrackingToken(UUID.randomUUID().toString());
		session.storeSession();
		
		url = encodeURLParams(url);
		
		Question question = Question
				.fromURL(url, config.getConfigId(), address);
		String preferred_language = StringStore
				.getString(address + "_language");
		if (preferred_language == null) {
			preferred_language = config.getPreferred_language();
		}
		question.setPreferred_language(preferred_language);
		Return res = formQuestion(question, config.getConfigId(), address);
		String fromName = getNickname(res.question);
		if (res.question != null) {
			StringStore.storeString("question_" + address + "_" + localaddress,
					res.question.toJSON());
		}
		
		DDRWrapper.log(question, session, "Start", config);
		int count = sendMessage(res.reply, "Message from DH", localaddress,
				fromName, address, "", config);
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
			Map<String, String> addressNameMap, String url, String senderName,
			String subject, AdapterConfig config) throws Exception {
		Map<String, String> formattedAddressNameMap = new HashMap<String, String>();
		if (config.getAdapterType().equals("CM")
				|| config.getAdapterType().equals("SMS")) {
			for (String address : addressNameMap.keySet()) {
				String formattedAddress = PhoneNumberUtils.formatNumber(
						address, null);
				formattedAddressNameMap.put(formattedAddress,
						addressNameMap.get(address));
			}
		} else {
			formattedAddressNameMap = addressNameMap;
		}
		String localaddress = config.getMyAddress();
		url = encodeURLParams(url);
		
		HashMap<String, String> sessionKeyMap = new HashMap<String, String>();
		ArrayList<Session> sessions = new ArrayList<Session>();
		
		// If it is a broadcast don't provide the remote address because it is
		// deceiving.
		String loadAddress = null;
		if (formattedAddressNameMap.size() == 1) loadAddress = formattedAddressNameMap
				.keySet().iterator().next();
		
		// fetch question
		Question question = Question.fromURL(url, config.getConfigId(), loadAddress);
		String preferred_language = StringStore.getString(loadAddress
				+ "_language");
		if (preferred_language == null) {
			preferred_language = config.getPreferred_language();
		}
		question.setPreferred_language(preferred_language);
		Return res = formQuestion(question, config.getConfigId(), loadAddress);
		for (String address : formattedAddressNameMap.keySet()) {
			// store the session first
			String sessionKey = getAdapterType() + "|" + localaddress + "|"
					+ address;
			Session session = Session.getSession(sessionKey,
					config.getKeyword());
			if (session == null) {
				log.severe("TextServlet couldn't start new outbound Dialog, adapterConfig not found? "
						+ sessionKey);
				return null;
			}
			session.setPubKey(config.getPublicKey());
			session.setDirection("outbound");
			session.storeSession();
			
			// Add key to the map (for the return)
			sessionKeyMap.put(address, sessionKey);
			sessions.add(session);
			
			if (res.question != null) {
				StringStore.storeString("question_" + address + "_"
						+ localaddress, res.question.toJSON());
			}
			DDRWrapper.log(question, session, "Start", config);
		}
		String fromName = getNickname(res.question);
		log.info(String.format("fromName: %s senderName %s", fromName,
				senderName));
		// assign senderName with localAdress, if senderName is missing
		// priority is as: nickname >> senderName >> myAddress
		if (fromName == null || fromName.isEmpty()) {
			senderName = senderName != null && !senderName.isEmpty() ? senderName
					: localaddress;
		} else {
			senderName = fromName;
		}

		subject = subject != null && !subject.isEmpty() ? subject
				: "Message from DH";
		int count = broadcastMessage(res.reply, subject, localaddress,
				senderName, formattedAddressNameMap, config);
		
		for (Session session : sessions) {
			for (int i = 0; i < count; i++) {
				DDRWrapper.log(question, session, "Send", config);
			}
		}
		if (count < 1) {
			log.severe("Error generating XML");
			
		}
		return sessionKeyMap;
	}
	
	public static void killSession(Session session) {
		StringStore.dropString("question_" + session.getRemoteAddress() + "_"
				+ session.getLocalAddress());
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
			msg = receiveMessage(req, res);
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
	
	protected int processMessage(TextMessage msg) throws Exception {
		String localaddress = msg.getLocalAddress();
		String address = msg.getAddress();
		String subject = msg.getSubject();
		String body = msg.getBody();
		String toName = msg.getRecipientName();
		String keyword = msg.getKeyword();
		String fromName = "DH";
		int count = 0;
		
		AdapterConfig config;
		Session session = Session.getSession(getAdapterType() + "|"
				+ localaddress + "|" + address, keyword);
		// If session is null it means the adapter is not found.
		if (session == null) {
			log.info("No session so retrieving config");
			config = AdapterConfig.findAdapterConfig(getAdapterType(),
					localaddress);
			count = sendMessage(getNoConfigMessage(), subject, localaddress,
					fromName, address, toName, config);
			// Create new session to store the send in the ddr.
			session = new Session();
			session.setDirection("inbound");
			session.setRemoteAddress(address);
			session.setTrackingToken(UUID.randomUUID().toString());
			for (int i = 0; i < count; i++) {
				DDRWrapper.log(null, null, session, "Send", config);
			}
			return count;
		}
		
		config = session.getAdapterConfig();
		// TODO: Remove this check, this is now to support backward
		// compatibility (write patch)
		if (config == null) {
			log.info("Session doesn't contain config, so searching it again");
			config = AdapterConfig.findAdapterConfig(getAdapterType(),
					localaddress, keyword);
			if (config == null) {
				config = AdapterConfig.findAdapterConfig(getAdapterType(),
						localaddress);
				try {
					count = sendMessage(getNoConfigMessage(), subject,
							localaddress, fromName, address, toName, config);
				} catch (Exception ex) {
				}
				for (int i = 0; i < count; i++) {
					DDRWrapper.log(null, null, session, "Send", config);
				}
				return count;
			}
			session.setAdapterID(config.getConfigId());
		}
		
		String json = "";
		String preferred_language = StringStore
				.getString(address + "_language");
		
		EscapeInputCommand escapeInput = new EscapeInputCommand();
		escapeInput.skip = false;
		escapeInput.body = body;
		escapeInput.preferred_language = preferred_language;
		escapeInput.reply = "I'm sorry, I don't know what to say. Please retry talking with me at a later time.";
		
		/*
		 * if (!KeyServerLib.checkCredits( config.getPublicKey() ) ){
		 * escapeInput.reply = "Not enough credits to return an answer";
		 * escapeInput.skip = true;
		 * }
		 */
		if (!escapeInput.skip
				&& escapeInput.body.toLowerCase().trim().charAt(0) == '/') {
			count = processEscapeInputCommand(msg, fromName, config,
					escapeInput);
			log.info(escapeInput.toString());
		}
		if (!escapeInput.skip) {
			
			if (escapeInput.preferred_language == null) {
				escapeInput.preferred_language = "nl";
			}
			
			Question question = null;
			boolean start = false;
			json = StringStore.getString("question_" + address + "_"
					+ localaddress);
			if (json == null || json.equals("")) {
				escapeInput.body = null; // Remove the body, because it is to
											// start the question
				if (config.getInitialAgentURL().equals("")) {
					question = Question.fromURL(this.host + DEMODIALOG,
							config.getConfigId(), address, localaddress);
				} else {
					question = Question.fromURL(config.getInitialAgentURL(),
							config.getConfigId(), address, localaddress);
				}
				session.setDirection("inbound");
				DDRWrapper.log(question, session, "Start", config);
				start = true;
			} else {
				question = Question.fromJSON(json, config.getConfigId());
			}
			
			if (question != null) {
				question.setPreferred_language(preferred_language);
				// Do not answer a question, when it's the first and the type is
				// comment or referral anyway.
				if (!(start && (question.getType().equalsIgnoreCase("comment") || question
						.getType().equalsIgnoreCase("referral")))) {
					question = question.answer(address, config.getConfigId(),
							null, escapeInput.body, null);
				}
				Return replystr = formQuestion(question, config.getConfigId(),
						address);
				// fix for bug: #15 https://github.com/almende/dialog/issues/15
				escapeInput.reply = URLDecoder.decode(replystr.reply, "UTF-8");
				question = replystr.question;
				fromName = getNickname(question);
				
				if (question == null) {
					StringStore.dropString("question_" + address + "_"
							+ localaddress);
					session.drop();
					DDRWrapper.log(question, session, "Hangup", config);
				} else {
					StringStore.storeString("question_" + address + "_"
							+ localaddress, question.toJSON());
					session.storeSession();
					DDRWrapper.log(question, session, "Answer", config);
				}
			}
		}
		try {
			count = sendMessage(escapeInput.reply, subject, localaddress,
					fromName, address, toName, config);
		} catch (Exception ex) {
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
	private int processEscapeInputCommand(TextMessage msg, String fromName,
			AdapterConfig config, EscapeInputCommand escapeInput)
			throws Exception {
		log.info(String.format("escape charecter seen.. input %s",
				escapeInput.body));
		int result = 0;
		String cmd = escapeInput.body.toLowerCase().substring(1);
		if (cmd.startsWith("language=")) {
			escapeInput.preferred_language = cmd.substring(9);
			if (escapeInput.preferred_language.indexOf(' ') != -1) escapeInput.preferred_language = escapeInput.preferred_language
					.substring(0, escapeInput.preferred_language.indexOf(' '));
			
			StringStore.storeString(msg.getAddress() + "_language",
					escapeInput.preferred_language);
			
			escapeInput.reply = "Ok, switched preferred language to:"
					+ escapeInput.preferred_language;
			escapeInput.body = "";
			
			HashMap<String, String> addressNameMap = new HashMap<String, String>(
					1);
			addressNameMap.put(msg.getAddress(), msg.getRecipientName());
			result = broadcastMessage(escapeInput.reply, msg.getSubject(),
					msg.getLocalAddress(), fromName, addressNameMap, config);
		} else if (cmd.startsWith("reset")) {
			StringStore.dropString("question_" + msg.getAddress() + "_"
					+ msg.getLocalAddress());
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
}
