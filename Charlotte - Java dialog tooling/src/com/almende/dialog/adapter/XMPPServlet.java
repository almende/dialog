package com.almende.dialog.adapter;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.almende.dialog.TestFramework;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.util.ServerUtils;
import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.PresenceShow;
import com.google.appengine.api.xmpp.PresenceType;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;

public class XMPPServlet extends TextServlet {
	private static final long serialVersionUID = 10291032309680299L;

	private static XMPPService xmpp = XMPPServiceFactory.getXMPPService();

	private static final String servletPath = "/_ah/xmpp/message/chat/";
	private static final String adapterType = "XMPP";

	@Override
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
	protected int sendMessage(String message, String subject, String from,
			String fromName, String to, String toName, AdapterConfig config) {

		JID localJid = new JID(from);
		JID jid = new JID(to);

		if(fromName!=null) {
			xmpp.sendPresence(jid, PresenceType.AVAILABLE, PresenceShow.CHAT, "as: "+fromName, localJid);
			message = "*" + fromName + ":* "+message;
		}

		Message msg = new MessageBuilder().withRecipientJids(jid).withFromJid(localJid)
				.withBody(message).build();

		xmpp.sendMessage(msg);
        if(ServerUtils.isInUnitTestingEnvironment())
        {
            TestFramework.log(msg);
        }
		
		return 1;
	}
	

    @Override
    protected int broadcastMessage( String message, String subject, String from, String senderName,
        Map<String, String> addressNameMap, AdapterConfig config ) throws Exception
    {
        JID localJid = new JID( from );
        ArrayList<JID> jids = new ArrayList<JID>();

        for ( String address : addressNameMap.keySet() )
        {
            JID jid = new JID( address );
            jids.add( jid );
            if ( senderName != null )
            {
                xmpp.sendPresence( jid, PresenceType.AVAILABLE, PresenceShow.CHAT, "as: " + senderName, localJid );
                message = "*" + senderName + ":* " + message;
            }
        }
        Message msg = new MessageBuilder().withRecipientJids( jids.toArray( new JID[addressNameMap.size()] ) )
            .withFromJid( localJid ).withBody( message ).build();
        xmpp.sendMessage( msg );
        if ( ServerUtils.isInUnitTestingEnvironment() )
        {
            TestFramework.log( msg );
        }
        return 1;
    }
	    
	@Override
	protected TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp)
	throws Exception {
		
		Message message = xmpp.parseMessage(req);
		return receiveMessage( message );
	}

        /**
         * @param message
         * @return
         * @throws Exception
         */
        private TextMessage receiveMessage( Message message ) throws Exception
        {
            TextMessage msg = new TextMessage(false);
    		JID[] toJids = message.getRecipientJids();
    		JID localJid = null;
    
    		//Why multiple addresses? 
    		if (toJids.length>0){
    			localJid = toJids[0];
    		} 
    		if (localJid != null){
    			msg.setLocalAddress(localJid.getId().split("/")[0]);
    		} else {
    			throw new Exception("XMPPServlet: Can't determine local address!");
    		}
    		JID jid = message.getFromJid();
    		
    		msg.setAddress(jid.getId().split("/")[0]);
    		
    		msg.setBody(message.getBody().trim());
    		
    		xmpp.sendPresence(jid, PresenceType.AVAILABLE, PresenceShow.CHAT, "",localJid);
    		xmpp.sendMessage(new MessageBuilder().withRecipientJids(jid).withFromJid(localJid)
    				.asXml(true).withBody("<composing xmlns='http://jabber.org/protocol/chatstates'/>").build());
    		
    		return msg;
        }

	@Override
	protected String getServletPath() {
		return servletPath;
	}


	@Override
	protected String getAdapterType() {
		return adapterType;
	}
}

