package com.almende.dialog;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.almende.dialog.model.Answer;
import com.almende.dialog.model.Question;
import com.almende.dialog.state.StringStore;
import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.PresenceShow;
import com.google.appengine.api.xmpp.PresenceType;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;

public class XMPPReceiverServlet extends HttpServlet {
	private static final long serialVersionUID = 10291032309680299L;
	private static final Logger log = Logger.getLogger(com.almende.dialog.XMPPReceiverServlet.class.getName()); 	
	//TODO: Add presence info
	
	//Charlotte is the agent responsible for routing to other agents....
	private static final String DEMODIALOG = "http://char-a-lot.appspot.com/charlotte/";
	private static XMPPService xmpp = XMPPServiceFactory.getXMPPService();
	
    private class Return{
    	String reply;
    	Question question;
    	public Return(String reply,Question question){
    		this.reply=reply;
    		this.question=question;
    	}
    }
	public Return formQuestion(Question question){
		String reply="";
		if (question != null){
			HashMap<String,String> id =question.getExpandedRequester();
			if (id.containsKey("nickname")){
				reply += "*"+id.get("nickname")+":* ";
			}
	    	reply += question.getQuestion_expandedtext();
    		if (question.getType().equals("referral")){
    			String preferred_language=question.getPreferred_language();
        		question = Question.fromURL(question.getUrl());
        		question.setPreferred_language(preferred_language);
    			id =question.getExpandedRequester();
    			reply+="\n";
    			if (id.containsKey("nickname")){
    				reply += "*"+id.get("nickname")+":* ";
    			}
        		reply+=question.getQuestion_expandedtext();
        	}
    		if (question.getType().equals("closed")){
    			reply+="\n[";
    			for (Answer ans: question.getAnswers()){
    				reply+=" "+ans.getAnswer_expandedtext(question.getPreferred_language())+" |";
    			}
    			reply=reply.substring(0, reply.length()-1)+" ]";
    		}
    		while (question.getType().equals("comment")){
    			question = question.answer( null, null, null);
    			if (question == null) break;
    			reply+="\n"+question.getQuestion_expandedtext();
    		}
    	}
    	return new Return(reply,question);
	}
	
	
	public static void startDialog(String address, String json){
		JID jid = new JID(address);
        xmpp.sendInvitation(jid);
        xmpp.sendPresence(jid,PresenceType.AVAILABLE,PresenceShow.CHAT,""); 
		
		address = jid.getId().split("/")[0];
		String preferred_language = StringStore.getString(address+"_language");
		if (preferred_language == null) preferred_language = "nl";
		
		Question question = Question.fromJSON(json);
		question.setPreferred_language(preferred_language);

		Return res = new XMPPReceiverServlet().formQuestion(question);        
		StringStore.storeString(address, res.question.toJSON());
        Message msg = new MessageBuilder()
        .withRecipientJids(jid)
        .withBody(res.reply)
        .build();

        xmpp.sendMessage(msg);	
	}
	
	public void doErrorPost(HttpServletRequest req, HttpServletResponse res)
          throws IOException {
		Message message = xmpp.parseMessage(req);
		log.warning(message.getStanza());
		
		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder;
		try {
			dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(new InputSource(new StringReader(message.getStanza())));
			Node elem = doc.getElementsByTagName("cli:error").item(0);
			log.warning("Received error stanza: "+elem.getAttributes().getNamedItem("code").getNodeValue()+":"+elem.getAttributes().getNamedItem("type").getNodeValue());
			log.warning(elem.getChildNodes().item(0).getNodeName());
		} catch (Exception e) {
			log.severe("XML parse error on error stanza:'"+message.getStanza()+"'\n-----\n"+e.toString()+":"+e.getMessage());
		}
		
	}
	
	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse res)
          throws IOException {
		
        boolean skip=false;
        
        if (req.getServletPath().endsWith("/error/")){
        	doErrorPost(req,res);
        	return;
        }
        
        Message message = xmpp.parseMessage(req);
        JID jid = message.getFromJid();
        
        xmpp.sendPresence(jid,PresenceType.AVAILABLE,PresenceShow.CHAT,"");        
        
        String address = jid.getId().split("/")[0];
        String body = message.getBody().trim();
        
        String json = "";
        String preferred_language = StringStore.getString(address+"_language");
        String reply="I'm sorry, I don't know what to say. Please retry talking with me at a later time.";
        
        if (body.toLowerCase().charAt(0) == '/'){
        	String cmd = body.toLowerCase().substring(1);
        	if (cmd.startsWith("language=")){
        		preferred_language = cmd.substring(9);
        		if (preferred_language.indexOf(' ')!=-1) preferred_language = preferred_language.substring(0, preferred_language.indexOf(' '));
        		StringStore.storeString(address+"_language",preferred_language);
        		reply="Ok, switched preferred language to:"+preferred_language;
        		skip=true;
        	} 
            if (cmd.equals("reset")){
            	StringStore.dropString(address);
            }
            if (cmd.startsWith("help")){
            	String[] command = cmd.split(" ");
            	if (command.length == 1){
            		reply="The following commands are understood:\n"+
            		  "/help <command>\n"+
              		  "/reset \n"+
           			  "/language=<lang_code>\n";
            	} else {
            		if (command[1].equals("reset")){
            			reply="/reset will return you to Charlotte's initial question.";
            		}
            		if (command[1].equals("language")){
            			reply="/language=<lang_code>, switches the preferred language to the provided lang_code. (e.g. /language=nl)";
            		}
            		if (command[1].equals("help")){
            			reply="/help <command>, provides a help text about the provided command (e.g. /help reset)";
            		}
            	}
            	
            	skip=true;
            }
        }
        if (!skip){
        	if (preferred_language == null) preferred_language = "nl";
        	Question question=null;
        	json=StringStore.getString(address);
        	if (json == null || json == ""){
        		question=Question.fromURL(DEMODIALOG);
        	} else {
        		question=Question.fromJSON(json);
        	}
        	if (question != null){
        	question.setPreferred_language(preferred_language);
        	question = question.answer( address, null, body);
       		Return replystr=formQuestion(question);
       		reply=replystr.reply;
       		question=replystr.question;
        	if (question == null){
        		StringStore.dropString(address);
        	} else {
        		StringStore.storeString(address, question.toJSON());
        	}
        }
        }
        Message msg = new MessageBuilder()
                .withRecipientJids(jid)
                .withBody(reply)
                .build();
        
        xmpp.sendMessage(msg);

    }
}
