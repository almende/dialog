package com.almende.dialog.adapter;

import java.util.ArrayList;
import java.util.logging.Logger;

import javax.mail.MessagingException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.jivesoftware.smack.XMPPException;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.agent.AdapterAgent;

/**
 * servlet listener to start any services when the server reloads
 * @author Shravan
 * 
 */
public class ServletListner implements ServletContextListener
{
    private static final Logger log = Logger.getLogger( ServletListner.class.getSimpleName() );
    @Override
    public void contextInitialized( ServletContextEvent sce )
    {
        log.info( "registering to all inbound XMPP messages.. ");
        ArrayList<AdapterConfig> adapters = AdapterConfig.findAdapters( AdapterAgent.ADAPTER_TYPE_XMPP, null, null );
        XMPPServlet xmppServlet = new XMPPServlet();
        for ( AdapterConfig adapterConfig : adapters )
        {
            try
            {
                xmppServlet.listenForIncomingChats( adapterConfig );
            }
            catch ( XMPPException e )
            {
                log.severe( "Exception thrown while trying to register inbound XMPP service for: "
                    + adapterConfig.getMyAddress() );
            }
        }
        log.info( "registering to all inbound Email messages.. ");
        adapters = AdapterConfig.findAdapters( AdapterAgent.ADAPTER_TYPE_EMAIL, null, null );
        for ( AdapterConfig adapterConfig : adapters )
        {
            if ( !adapterConfig.getMyAddress().endsWith( "appspotmail.com" ) )
            {
                MailServlet mailServlet = new MailServlet( adapterConfig );
                try
                {
                    mailServlet.listenForIncomingEmails();
                }
                catch ( MessagingException e )
                {
                    log.severe( "Exception thrown while trying to register inbound Email service for: "
                        + adapterConfig.getMyAddress() );
                }
            }
        }
    }

    @Override
    public void contextDestroyed( ServletContextEvent sce )
    {
    }
}
