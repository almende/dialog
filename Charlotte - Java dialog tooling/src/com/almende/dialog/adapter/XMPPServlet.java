package com.almende.dialog.adapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.CharBuffer;
import java.util.HashMap;
import java.util.logging.Logger;
//import java.util.Date;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.almende.dialog.Settings;
import com.almende.dialog.accounts.Account;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.Question;
import com.almende.dialog.model.Session;
import com.almende.dialog.state.StringStore;
import com.almende.util.ParallelInit;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.taskqueue.TaskOptions.Method;
import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.PresenceShow;
import com.google.appengine.api.xmpp.PresenceType;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;

public class XMPPServlet extends HttpServlet {
	private static final long serialVersionUID = 10291032309680299L;
	private static final Logger log = Logger
			.getLogger("DialogHandler");
	private static final int LOOP_DETECTION=10;

//	private static long startTime = new Date().getTime();
	// Charlotte is the agent responsible for routing to other agents....
	private static final String DEMODIALOG = "http://"+Settings.HOST+"/charlotte/";
	private static XMPPService xmpp = XMPPServiceFactory.getXMPPService();

	private class Return {
		String reply;
		Question question;

		public Return(String reply, Question question) {
			this.reply = reply;
			this.question = question;
		}
	}
	
	public Return formQuestion(Question question,String address,JID jid, JID fromJid) {
		String reply = "";
		String preferred_language = question.getPreferred_language();
		for (int count = 0; count<=LOOP_DETECTION; count++){
			if (question == null) break;
			question.setPreferred_language(preferred_language);
			
			if (!reply.equals("")) reply+="\n";
			HashMap<String, String> id = question.getExpandedRequester();
			if (id.containsKey("nickname")) {
				xmpp.sendPresence(jid, PresenceType.AVAILABLE, PresenceShow.CHAT, "as: "+id.get("nickname"), fromJid);
				reply += "*" + id.get("nickname") + ":* ";
			}			
			String qText = question.getQuestion_expandedtext();
			if(qText!=null && !qText.equals("")) reply += qText;

			if (question.getType().equals("closed")) {
				reply += "\n[";
				for (Answer ans : question.getAnswers()) {
					reply += " "
							+ ans.getAnswer_expandedtext(question
									.getPreferred_language()) + " |";
				}
				reply = reply.substring(0, reply.length() - 1) + " ]";
				break; //Jump from forloop
			} else if (question.getType().equals("comment")) {
				question = question.answer(null, null, null);//Always returns null! So no need, but maybe in future?
			} else 	if (question.getType().equals("referral")) {
				question = Question.fromURL(question.getUrl(),address);
			} else {
				break; //Jump from forloop (open questions, etc.)
			}
		}
		return new Return(reply, question);
	}

	
	public static void startDialog(String address, Question question, Account account) {
		AdapterConfig config = AdapterConfig.findAdapterConfigForAccount("XMPP", account.getId());
		JID localJid = new JID(config.getMyJID());
		
		JID jid = new JID(address);
		xmpp.sendInvitation(jid);
		xmpp.sendPresence(jid, PresenceType.AVAILABLE, PresenceShow.CHAT, "");

		address = jid.getId().split("/")[0];
		String preferred_language = StringStore
				.getString(address + "_language");
		if (preferred_language == null){
			preferred_language = config.getPreferred_language();
		}
		question.setPreferred_language(preferred_language);

		Return res = new XMPPServlet().formQuestion(question,address,jid,localJid);
		StringStore.storeString(address, res.question.toJSON());
		Message msg = new MessageBuilder().withRecipientJids(jid).withFromJid(localJid)
				.withBody(res.reply).build();

		xmpp.sendMessage(msg);
	}

	public static void startDialog(String address, String json, Account account) {
		Question question = Question.fromJSON(json);
		XMPPServlet.startDialog(address, question, account);
	}

	public void doErrorPost(HttpServletRequest req, HttpServletResponse res)
			throws IOException {
		Message message = xmpp.parseMessage(req);

		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder;
		try {
			dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(new InputSource(new StringReader(
					message.getStanza())));
			Node elem = doc.getElementsByTagName("cli:error").item(0);
			log.warning("Received error stanza: "
					+ elem.getAttributes().getNamedItem("code").getNodeValue()
					+ ":"
					+ elem.getAttributes().getNamedItem("type").getNodeValue());
			log.warning(elem.getChildNodes().item(0).getNodeName());
		} catch (Exception e) {
			log.severe("XML parse error on error stanza:'"
					+ message.getStanza() + "'\n-----\n" + e.toString() + ":"
					+ e.getMessage());
		}

	}

	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse res)
			throws IOException {

//		log.warning("Starting to handle xmpp post: "+startTime+"/"+(new Date().getTime()));
		boolean loading = ParallelInit.startThreads();
		boolean skip = false;

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
			TaskOptions taskOptions = TaskOptions.Builder.withUrl("/_ah/xmpp/message/chat/")
									  .payload(bodyReader.toString()).method(Method.POST);
			queue.add(taskOptions);
		}

		Message message = xmpp.parseMessage(req);
		JID[] toJids = message.getRecipientJids();
		JID localJid = null;
		String localaddress = "";
		//Why multiple addresses? 
		if (toJids.length>0){
			localJid = toJids[0];
		} 
		if (localJid != null){
			localaddress = toJids[0].getId().split("/")[0];
		} else {
			log.severe("XMPPServlet: Can't determine local address!");
			return;
		}
		JID jid = message.getFromJid();
		xmpp.sendPresence(jid, PresenceType.AVAILABLE, PresenceShow.CHAT, "",localJid);
		xmpp.sendMessage(new MessageBuilder().withRecipientJids(jid).withFromJid(localJid)
				.asXml(true).withBody("<composing xmlns='http://jabber.org/protocol/chatstates'/>").build());

		if (loading){
			return;
		}
		
		String address = jid.getId().split("/")[0];
		Session session = Session.getSession("XMPP|"+localaddress+"|"+address);
		if (session == null){
			xmpp.sendPresence(jid, PresenceType.UNAVAILABLE, PresenceShow.CHAT, "Broken",localJid);
			xmpp.sendMessage(new MessageBuilder().withRecipientJids(jid).withFromJid(localJid)
					.withBody("Sorry, I can't find the account associated with this chat address...").build());
			return;
		}
		AdapterConfig config= AdapterConfig.findAdapterConfigForAccount("XMPP",session.getAccount());
		
		String body = message.getBody().trim();
		
		String json = "";
		String preferred_language = StringStore
				.getString(address + "_language");
		String reply = "I'm sorry, I don't know what to say. Please retry talking with me at a later time.";

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
				Message msg = new MessageBuilder().withRecipientJids(jid).withFromJid(localJid)
						.withBody(reply).build();

				xmpp.sendMessage(msg);
			}
			if (cmd.equals("reset")) {
				StringStore.dropString("question_"+address+"_"+localaddress);
				xmpp.sendPresence(jid, PresenceType.AVAILABLE, PresenceShow.CHAT, "",localJid);
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
		if (!skip) {
			if (preferred_language == null)
				preferred_language = "nl";
						
			Question question = null;
			json = StringStore.getString("question_"+address+"_"+localaddress);
			if (json == null || json.equals("")) {
				if (config.getInitialAgentURL().equals("")){
					question = Question.fromURL(DEMODIALOG,address);
				} else {
					question = Question.fromURL(config.getInitialAgentURL());
				}
			} else {
				question = Question.fromJSON(json);
			}
			if (question != null) {
				question.setPreferred_language(preferred_language);
				question = question.answer(address, null, body);
				Return replystr = formQuestion(question,address,jid,localJid);
				reply = replystr.reply;
				question = replystr.question;
				
				if (question == null) {
					StringStore.dropString("question_"+address+"_"+localaddress);
					xmpp.sendPresence(jid, PresenceType.AVAILABLE, PresenceShow.CHAT, "",localJid);
					session.drop();
				} else {
					StringStore.storeString("question_"+address+"_"+localaddress, question.toJSON());
					session.storeSession();
				}
			}
		}
		Message msg = new MessageBuilder().withRecipientJids(jid).withFromJid(localJid)
				.withBody(reply).build();

		xmpp.sendMessage(msg);
//		log.warning("Send reply to xmpp post: "+(new Date().getTime()));
		
	}
}
