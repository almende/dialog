package com.almende.dialog.adapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.CharBuffer;
import java.util.HashMap;
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
import com.almende.dialog.util.KeyServerLib;
import com.almende.dialog.util.RequestUtil;
import com.almende.util.ParallelInit;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

@SuppressWarnings("serial")
abstract public class TextServlet extends HttpServlet {
	protected static final Logger log = Logger
			.getLogger("DialogHandler");
	protected static final int LOOP_DETECTION=10;
	protected static final String DEMODIALOG = "/charlotte/";
	
	protected abstract int sendMessage(String message, String subject, String from, String fromName, 
										String to, String toName, AdapterConfig config) throws Exception;
	protected abstract TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp) throws Exception; 
	protected abstract String getServletPath();
	protected abstract String getAdapterType();
	protected abstract void doErrorPost(HttpServletRequest req, HttpServletResponse res) throws IOException;
	
	private String host="";
	
	protected class Return {
		String reply;
		Question question;

		public Return(String reply, Question question) {
			this.reply = reply;
			this.question = question;
		}
	}
	
	public Return formQuestion(Question question,String address) {
		String reply = "";
		
		String preferred_language = "nl"; // TODO: Change to null??
		if(question!=null)
			preferred_language = question.getPreferred_language();
		
		for (int count = 0; count<=LOOP_DETECTION; count++){
			if (question == null) break;
			question.setPreferred_language(preferred_language);
			
			if (!reply.equals("")) reply+="\n";	
			String qText = question.getQuestion_expandedtext();
			if(qText!=null && !qText.equals("")) reply += qText;

			if (question.getType().equalsIgnoreCase("closed")) {
				reply += "\n[";
				for (Answer ans : question.getAnswers()) {
					reply += " "
							+ ans.getAnswer_expandedtext(question
									.getPreferred_language()) + " |";
				}
				reply = reply.substring(0, reply.length() - 1) + " ]";
				break; //Jump from forloop
			} else if (question.getType().equalsIgnoreCase("comment")) {
				question = question.answer(null, null, null);//Always returns null! So no need, but maybe in future?
			} else 	if (question.getType().equalsIgnoreCase("referral")) {
				question = Question.fromURL(question.getUrl(),address);
			} else {
				break; //Jump from forloop (open questions, etc.)
			}
		}
		return new Return(reply, question);
	}
	
	public String startDialog(String address, String url, AdapterConfig config) throws Exception {
		if(config.getAdapterType().equals("CM") || config.getAdapterType().equals("SMS")) {
			address = formatNumber(address);
		}
		String localaddress = config.getMyAddress();
		String sessionKey =getAdapterType()+"|"+localaddress+"|"+address;
		Session session = Session.getSession(sessionKey, config.getKeyword());
		if (session == null){
			log.severe("XMPPServlet couldn't start new outbound Dialog, adapterConfig not found? "+sessionKey);
			return "";
		}
		session.setPubKey(config.getPublicKey());
		session.setDirection("outbound");
		session.setTrackingToken(UUID.randomUUID().toString());
		session.storeSession();
		
		url = encodeURLParams(url);
		
		Question question = Question.fromURL(url, address);
		String preferred_language = StringStore
				.getString(address + "_language");
		if (preferred_language == null){
			preferred_language = config.getPreferred_language();
		}
		question.setPreferred_language(preferred_language);
		Return res = formQuestion(question,address);
		String fromName = getNickname(res.question);
		if(res.question!=null) {
			StringStore.storeString("question_"+address+"_"+localaddress, res.question.toJSON());
		}
		
		DDRWrapper.log(question,session,"Start",config);
		int count = sendMessage(res.reply, "Message from DH", localaddress, fromName, address, "", config);
		for(int i=0;i<count;i++) { 
			DDRWrapper.log(question, session, "Send", config);
		}
		return sessionKey;
	}
	
	public static void killSession(Session session){
		StringStore.dropString("question_"+session.getRemoteAddress()+"_"+session.getLocalAddress());
	}
	
	@Override
	public void service(HttpServletRequest req, HttpServletResponse res)
			throws IOException {
		this.host = RequestUtil.getHost(req);
//		log.warning("Starting to handle xmpp post: "+startTime+"/"+(new Date().getTime()));
		boolean loading = ParallelInit.startThreads();
		
		if (req.getServletPath().endsWith("/error/")) {
			doErrorPost(req, res);
			return;
		}

		if (loading){
			CharBuffer bodyReader = CharBuffer.allocate(req.getContentLength());
			BufferedReader reader = req.getReader();
			reader.read(bodyReader);
			
			bodyReader.rewind();
			Queue queue = QueueFactory.getDefaultQueue();
			TaskOptions taskOptions = TaskOptions.Builder.withUrl(getServletPath())
									  .payload(bodyReader.toString()).method(Method.POST);
			queue.add(taskOptions);
			return;
		}	 
        
		TextMessage msg;
        try {
        	msg = receiveMessage(req, res);
		} catch(Exception ex) {
			log.severe("Failed to parse received message: "+ex.getLocalizedMessage());
			return;
		}
		
		processMessage(msg);
	}
	
	protected void processMessage(TextMessage msg) {
		
		boolean skip = false;
		String localaddress = msg.getLocalAddress();
		String address = msg.getAddress();
		String subject = msg.getSubject();
		String body = msg.getBody();
		String toName = msg.getRecipientName();
		String keyword = msg.getKeyword();
		String fromName="DH";
		int count=0;
		
		
		AdapterConfig config;
		Session session = Session.getSession(getAdapterType()+"|"+localaddress+"|"+address, keyword);
		// If session is null it means the adapter is not found.
		if (session == null){
			log.info("No session so retrieving config");
			config = AdapterConfig.findAdapterConfig(getAdapterType(),localaddress);
			try {
				count = sendMessage(getNoConfigMessage(), subject, localaddress, fromName, address, toName, config);
				// Create new session to store the send in the ddr.
				session = new Session();
				session.setDirection("inbound");
				session.setRemoteAddress(address);
				session.setTrackingToken(UUID.randomUUID().toString());
			} catch(Exception ex) {
			}
			for(int i=0;i<count;i++) { 
				DDRWrapper.log(null,  null, session, "Send", config);
			}
			return;
		}
		
		config = session.getAdapterConfig();
		//TODO: Remove this check, this is now to support backward compatibility
		if(config==null) {
			log.info("Session doesn't contain config, so searching it again");
			config = AdapterConfig.findAdapterConfig(getAdapterType(),localaddress, keyword);
			if (config == null){
				config = AdapterConfig.findAdapterConfig(getAdapterType(),localaddress);
				try {
					count = sendMessage(getNoConfigMessage(), subject, localaddress, fromName, address, toName, config);
				} catch(Exception ex) {
				}
				for(int i=0;i<count;i++) { 
					DDRWrapper.log(null,  null, session, "Send", config);
				}
				return;
			}
			session.setAdapterID(config.getConfigId());
		}
		
		String json = "";
		String preferred_language = StringStore
				.getString(address + "_language");
		String reply = "I'm sorry, I don't know what to say. Please retry talking with me at a later time.";
		
		if(KeyServerLib.checkCredits(config.getPublicKey())) {

			if (body.toLowerCase().charAt(0) == '/') {
				String cmd = body.toLowerCase().substring(1);
				if (cmd.startsWith("language=")) {
					preferred_language = cmd.substring(9);
					if (preferred_language.indexOf(' ') != -1)
						preferred_language = preferred_language.substring(0,
								preferred_language.indexOf(' '));
					StringStore.storeString(address + "_language",
							preferred_language);
					reply = "Ok, switched preferred language to:"
							+ preferred_language;
					body="";
					try{
					 count=sendMessage(reply, subject, localaddress, fromName, address, toName, config);
					} catch(Exception ex) {
					}
				}
				if (cmd.startsWith("reset")) {
					StringStore.dropString("question_"+address+"_"+localaddress);
					// Send something else??
				}
				if (cmd.startsWith("help")) {
					String[] command = cmd.split(" ");
					if (command.length == 1) {
						reply = "The following commands are understood:\n"
								+ "/help <command>\n" + "/reset \n"
								+ "/language=<lang_code>\n";
					} else {
						if (command[1].equals("reset")) {
							reply = "/reset will return you to Charlotte's initial question.";
						}
						if (command[1].equals("language")) {
							reply = "/language=<lang_code>, switches the preferred language to the provided lang_code. (e.g. /language=nl)";
						}
						if (command[1].equals("help")) {
							reply = "/help <command>, provides a help text about the provided command (e.g. /help reset)";
						}
					}
	
					skip = true;
				}
			}
		} else {
			reply = "Not enough credits to return an answer";
			skip=true;
		}
		
		if (!skip) {
			if (preferred_language == null)
				preferred_language = "nl";
						
			Question question = null;
			boolean start = false;
			json = StringStore.getString("question_"+address+"_"+localaddress);
			if (json == null || json.equals("")) {
				body=null; // Remove the body, because it is to start the question
				if (config.getInitialAgentURL().equals("")){
					question = Question.fromURL(this.host+DEMODIALOG,address,localaddress);
				} else {
					question = Question.fromURL(config.getInitialAgentURL(),address,localaddress);
				}
				session.setDirection("inbound");
				DDRWrapper.log(question,session,"Start",config);
				start = true;
			} else {
				question = Question.fromJSON(json);
			}
			
			if (question != null) {
				question.setPreferred_language(preferred_language);
				// Do not answer a question, when it's the first and the type is comment or referral anyway.
				if(!(start && (question.getType().equalsIgnoreCase("comment") || question.getType().equalsIgnoreCase("referral"))))
					question = question.answer(address, null, body);
				Return replystr = formQuestion(question,address);
				reply = replystr.reply;
				question = replystr.question;
				fromName = getNickname(question);
				
				if (question == null) {
					StringStore.dropString("question_"+address+"_"+localaddress);
					session.drop();
					DDRWrapper.log(question,session,"Hangup",config);
				} else {
					StringStore.storeString("question_"+address+"_"+localaddress, question.toJSON());
					session.storeSession();
					DDRWrapper.log(question,session,"Answer",config);
				}
			}
		}

		try{
			count=sendMessage(reply, subject, localaddress, fromName, address, toName, config);
		} catch(Exception ex) {
		}
		
		for(int i=0;i<count;i++) { 
			DDRWrapper.log(null,  null, session, "Send", config);
		}
	}
	
	protected String getNoConfigMessage() {
		return "Sorry, I can't find the account associated with this chat address...";
	}
	
	private String getNickname(Question question) {
		
		String nickname="";
		if(question!=null) {
			HashMap<String, String> id = question.getExpandedRequester();
			nickname = id.get("nickname");
		}
		
		return nickname; 
	}
	
	private String encodeURLParams(String url) {
		try {
			URL remoteURL = new URL(url);
			return new URI(remoteURL.getProtocol(), remoteURL.getUserInfo(), remoteURL.getHost(), remoteURL.getPort(), remoteURL.getPath(), remoteURL.getQuery(), remoteURL.getRef()).toString();
	        
		} catch (Exception e){
			e.printStackTrace();
		}
		return url;
	}
	
	protected String formatNumber(String phone) {
		//TODO: Change this so that it will also work with international numbers and providers
		PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
		try {
			PhoneNumber numberProto = phoneUtil.parse(phone,"NL");
			//TODO: Change to E164 as soon as portal is fixed
			return phoneUtil.format(numberProto,PhoneNumberFormat.NATIONAL).replace(" ","");
		} catch (NumberParseException e) {
		  log.severe("NumberParseException was thrown: " + e.toString());
		}
		return null;	
	}
}
