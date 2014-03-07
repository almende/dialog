package com.almende.dialog.adapter;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.Roster.SubscriptionMode;
import org.jivesoftware.smack.RosterListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Message.Body;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Presence.Mode;
import org.jivesoftware.smack.packet.Presence.Type;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.util.ServerUtils;

public class XMPPServlet extends TextServlet implements MessageListener, RosterListener, ChatManagerListener 
{
    private static final long serialVersionUID = 10291032309680299L;
    public static final String XMPP_HOST_KEY = "XMPP_HOST";
    public static final String XMPP_PORT_KEY = "XMPP_PORT";
    public static final String XMPP_SERVICE_KEY = "XMPP_SERVICE";
    public static final String DEFAULT_XMPP_HOST = "talk.google.com";
    public static final int DEFAULT_XMPP_PORT = 5222;
    public static final String DEFAULT_XMPP_SERVICE = "gmail.com";
    private static ThreadLocal<XMPPConnection> xmppConnection = null;
    private static final String servletPath = "/xmpp";

    @Override
    protected int sendMessage( String message, String subject, String from, String fromName, String to, String toName,
        Map<String, Object> extras, AdapterConfig config ) throws XMPPException
    {
        try
        {
            XMPPConnection xmppConnection = getXMPPConnection( config, true );
            Roster.setDefaultSubscriptionMode( SubscriptionMode.accept_all );
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
        catch ( XMPPException ex )
        {
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
        return 1;
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
     * checks for changes in the contactList. if new users are added etc
     * @throws XMPPException 
     */
    public void listenForRosterChanges(AdapterConfig adapterConfig) throws XMPPException
    {
        XMPPConnection xmppConnection = getXMPPConnection( adapterConfig, true );
        Roster roster = xmppConnection.getRoster();
        roster.addRosterListener( this );
    }
    
    /**
     * listens on any incoming messages to this adapterConfig
     * @throws XMPPException 
     */
    public void listenForIncomingChats(AdapterConfig adapterConfig) throws XMPPException
    {
        XMPPConnection xmppConnection = getXMPPConnection( adapterConfig, true );
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
    
    private XMPPConnection getXMPPConnection(AdapterConfig adapterConfig, boolean performLogin) throws XMPPException
    {
        final String host = adapterConfig.getExtras().get( XMPP_HOST_KEY ) != null ? adapterConfig.getExtras()
            .get( XMPP_HOST_KEY ).toString() : DEFAULT_XMPP_HOST;
        final int port = adapterConfig.getExtras().get( XMPP_PORT_KEY ) != null ? Integer.parseInt( adapterConfig
            .getExtras().get( XMPP_PORT_KEY ).toString() ) : DEFAULT_XMPP_PORT;
        final String service = adapterConfig.getExtras().get( XMPP_SERVICE_KEY ) != null ? adapterConfig.getExtras()
            .get( XMPP_PORT_KEY ).toString() : DEFAULT_XMPP_SERVICE; 
        //create new xmppConnection
        xmppConnection = xmppConnection != null ? xmppConnection : new ThreadLocal<XMPPConnection>();
        if ( xmppConnection.get() == null || !xmppConnection.get().getHost().equals( host )
            || xmppConnection.get().getPort() != port || !xmppConnection.get().getServiceName().equals( service ) )
        {
            XMPPConnection connection = new XMPPConnection( new ConnectionConfiguration( host, port, service ) );
            xmppConnection.set( connection );
        }
        if ( performLogin )
        {
            if ( xmppConnection.get().getUser() == null
                || !xmppConnection.get().getUser().startsWith( adapterConfig.getXsiUser() )
                || !xmppConnection.get().isConnected() || !xmppConnection.get().isAuthenticated() )
            {
                xmppConnection.get().connect();
                xmppConnection.get().login( adapterConfig.getXsiUser(), adapterConfig.getXsiPasswd() );
            }
        }
        return xmppConnection.get();
    }

    @Override
    public void entriesAdded( Collection<String> entries )
    {
        log.info( String.format( "Following users: %s are added to account : %s", ServerUtils
            .serializeWithoutException( entries ),
            xmppConnection != null && xmppConnection.get() != null ? xmppConnection.get().getUser() : null ) );
    }

    @Override
    public void entriesDeleted( Collection<String> entries )
    {
        log.info( String.format( "Following users: %s are removed from account : %s", ServerUtils
            .serializeWithoutException( entries ),
            xmppConnection != null && xmppConnection.get() != null ? xmppConnection.get().getUser() : null ) );
    }

    @Override
    public void entriesUpdated( Collection<String> entries )
    {
    }

    @Override
    public void presenceChanged( Presence entries )
    {
    }

    @Override
    public void chatCreated( Chat chat, boolean createdLocally )
    {
        chat.addMessageListener( this );
    }


    @Override
    protected void doErrorPost( HttpServletRequest req, HttpServletResponse res ) throws IOException
    {
        
    }
}
