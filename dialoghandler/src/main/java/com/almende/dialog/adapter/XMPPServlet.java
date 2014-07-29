package com.almende.dialog.adapter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jivesoftware.smack.AccountManager;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.Roster.SubscriptionMode;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Message.Body;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Mode;
import org.jivesoftware.smack.packet.Presence.Type;
import com.almende.dialog.Logger;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;

public class XMPPServlet extends TextServlet implements MessageListener, ChatManagerListener
{
    private static final long serialVersionUID = 10291032309680299L;
    public static final String XMPP_HOST_KEY = "XMPP_HOST";
    public static final String XMPP_PORT_KEY = "XMPP_PORT";
    public static final String XMPP_SERVICE_KEY = "XMPP_SERVICE";
    public static final String GTALK_XMPP_HOST = "talk.google.com";
    public static final String DEFAULT_XMPP_HOST = "xmpp.ask-fast.com";
    public static final int DEFAULT_XMPP_PORT = 5222;
    private static ThreadLocal<XMPPConnection> xmppConnection = null;
    private static final String servletPath = "/xmpp";
    private static final Logger dialogLog = new Logger();

    @Override
    protected int sendMessage( String message, String subject, String from, String fromName, String to, String toName,
        Map<String, Object> extras, AdapterConfig config ) throws Exception
    {
        XMPPConnection xmppConnection = null;
        try
        {
            xmppConnection = getXMPPConnection( config, true );
            Roster xmppRooster = xmppConnection.getRoster();
            Roster.setDefaultSubscriptionMode( SubscriptionMode.accept_all );
            if( !xmppRooster.contains( to ))
            {
                xmppRooster.createEntry( to, toName, null );
                String sessionKey = extras.containsKey(Session.SESSION_KEY) ? extras.get(Session.SESSION_KEY).toString() : null;
                String ddrRecordId =  extras.containsKey(DDRRecord.DDR_RECORD_KEY) ? extras.get(DDRRecord.DDR_RECORD_KEY).toString() : null; 
                dialogLog.warning(config, String.format("Sending xmpp chat: %s to: %s might be incomplete. Contact just added in AddressBook",
                                                        message, to), ddrRecordId, sessionKey);
            }
            if ( fromName != null && !fromName.isEmpty() )
            {
                Presence presence = new Presence( Type.available );
                presence.setMode( Mode.chat );
                presence.setStatus( "as: "+ fromName );
                xmppConnection.sendPacket( presence );
                message = "*" + fromName + ":* "+message;
            }
            Chat chat = xmppConnection.getChatManager().createChat( to, this );
            chat.sendMessage( message );
            return 1;
        }
        catch ( Exception ex )
        {
            if(xmppConnection != null)
            {
                xmppConnection.disconnect();
            }
            log.severe( "XMPP send message failed. Error:" + ex.getLocalizedMessage() );
            throw ex;
        }
    }
    

    @Override
    protected int broadcastMessage( String message, String subject, String from, String senderName,
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config ) throws Exception
    {
        for ( String address : addressNameMap.keySet() )
        {
            sendMessage( message, subject, from, senderName, address, addressNameMap.get( address ), extras, config );
        }
        return addressNameMap.size();
    }
        
    @Override
    protected TextMessage receiveMessage( HttpServletRequest req, HttpServletResponse resp ) throws Exception
    {
        log.warning( "Receive Message is moved to processsMessage()" );
        return null;
    }

    @Override
    protected String getServletPath() {
        return servletPath;
    }


    @Override
    protected String getAdapterType() {
        return AdapterAgent.ADAPTER_TYPE_XMPP;
    }
    
    /**
     * listens on any incoming messages to this adapterConfig
     * @throws XMPPException 
     */
    public void listenForIncomingChats(AdapterConfig adapterConfig) throws XMPPException
    {
        XMPPConnection xmppConnection = getXMPPConnection( adapterConfig, true );
        Roster.setDefaultSubscriptionMode( SubscriptionMode.accept_all );
        xmppConnection.getChatManager().addChatListener( this );
    }

    @Override
    public void processMessage( Chat chat, Message message )
    {
        for ( Body messageBody : message.getBodies() )
        {
            TextMessage textMessage = new TextMessage();
            textMessage.setAddress( message.getFrom().split( "/" )[0] );
            textMessage.setLocalAddress( message.getTo().split( "/" )[0] );
            textMessage.setBody( messageBody.getMessage() );
            textMessage.setRecipientName( message.getFrom() );
            textMessage.setSubject( message.getSubject() );
            try
            {
                processMessage( textMessage );
            }
            catch ( Exception e )
            {
                log.warning( "Processing XMPP message failed. Error: " + e.getLocalizedMessage() );
            }
        }
    }
    
    public static XMPPConnection getXMPPConnection(AdapterConfig adapterConfig, boolean performLogin) throws XMPPException
    {
        final String host = adapterConfig.getProperties().get( XMPP_HOST_KEY ) != null ? adapterConfig.getProperties()
            .get( XMPP_HOST_KEY ).toString() : GTALK_XMPP_HOST;
        final int port = adapterConfig.getProperties().get( XMPP_PORT_KEY ) != null ? Integer.parseInt( adapterConfig
            .getProperties().get( XMPP_PORT_KEY ).toString() ) : DEFAULT_XMPP_PORT;
        final String service = adapterConfig.getProperties().get( XMPP_SERVICE_KEY ) != null ? adapterConfig.getProperties()
            .get( XMPP_SERVICE_KEY ).toString() : null; 
        //create new xmppConnection
        xmppConnection = xmppConnection != null ? xmppConnection : new ThreadLocal<XMPPConnection>();
        if ( xmppConnection.get() == null || !xmppConnection.get().getHost().equals( host )
            || xmppConnection.get().getPort() != port || !xmppConnection.get().getServiceName().equals( service ) )
        {
            ConnectionConfiguration connectionConfiguration = null;
            if(service != null)
            {
                connectionConfiguration = new ConnectionConfiguration(host, port, service);
            }
            else
            {
                connectionConfiguration = new ConnectionConfiguration(host);
            }
            connectionConfiguration.setSASLAuthenticationEnabled(false);
            XMPPConnection connection = new XMPPConnection( connectionConfiguration );
            xmppConnection.set( connection );
        }
        if ( performLogin )
        {
            if ( xmppConnection.get().getUser() == null
                || !xmppConnection.get().getUser().startsWith( adapterConfig.getXsiUser() )
                || !xmppConnection.get().isConnected() || !xmppConnection.get().isAuthenticated() )
            {
                SASLAuthentication.supportSASLMechanism("PLAIN", 0);
                xmppConnection.get().connect();
                if ( xmppConnection.get().isConnected() )
                {
                    xmppConnection.get().login( adapterConfig.getXsiUser(), adapterConfig.getXsiPasswd(), "SMACK" );
                }
            }

        }
        return xmppConnection.get();
    }
    
    @Override
    public void chatCreated( Chat chat, boolean createdLocally )
    {
        log.info( "chatCreated" );
        chat.addMessageListener( this );
    }


    @Override
    protected void doErrorPost( HttpServletRequest req, HttpServletResponse res ) throws IOException
    {
        log.info( "doErrorPost" );
    }
    
    @Override
    protected DDRRecord createDDRForIncoming(AdapterConfig adapterConfig, String fromAddress, String message) throws Exception {

        return DDRUtils.createDDRRecordOnIncomingCommunication(adapterConfig, fromAddress, message);
    }


    @Override
    protected DDRRecord createDDRForOutgoing(AdapterConfig adapterConfig, String senderName,
                                             Map<String, String> toAddress, String message) throws Exception {

        return DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig, toAddress, message);
    }
    
    public static void registerASKFastXMPPAccount( String username, String password, String name, String email )
    throws Exception
    {
        if ( username != null && username.endsWith( "@xmpp.ask-fast.com" ) )
        {
            try
            {
                XMPPConnection xmppConnection = new XMPPConnection( "xmpp.ask-fast.com" );
                xmppConnection.connect();
                AccountManager accountManager = new AccountManager( xmppConnection );
                Map<String, String> attributes = new HashMap<String, String>();
                attributes.put("username", username.replace( "@xmpp.ask-fast.com", "" ));
                attributes.put("password", password);
                attributes.put("email", email);
                attributes.put("name", name);
                accountManager.createAccount( username, password, attributes );
            }
            catch ( XMPPException e )
            {
                log.severe( "XMPP account creation failed. Error: " + e.getLocalizedMessage() );
                throw e;
            }
        }
        else
        {
            throw new Exception( "Invalid XMPP address for ASK-Fast" );
        }
    }
    
    public static void deregisterASKFastXMPPAccount( AdapterConfig adapterConfig )
    throws Exception
    {
        if ( adapterConfig != null && adapterConfig.getMyAddress() != null && adapterConfig.getMyAddress().endsWith( "@xmpp.ask-fast.com" ) )
        {
            try
            {
                XMPPConnection xmppConnection = getXMPPConnection( adapterConfig, true );
                AccountManager accountManager = xmppConnection.getAccountManager();
                accountManager.deleteAccount();
            }
            catch ( XMPPException e )
            {
                if ( !e.getLocalizedMessage().equals( "No response from server." ) ) //TODO: ugly. the server should respond with proper status
                {
                    log.severe( "XMPP account deletion failed. Error: " + e.getLocalizedMessage() );
                    throw e;
                }
            }
        }
        throw new Exception( "Invalid XMPP address for ASK-Fast" );
    }
}
