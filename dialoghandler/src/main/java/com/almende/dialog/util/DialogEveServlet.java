package com.almende.dialog.util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.almende.eve.agent.Agent;
import com.almende.eve.transport.http.DebugServlet;
import com.almende.eve.transport.http.HttpService;
import com.almende.eve.transport.http.HttpTransport;
import com.almende.util.StringUtil;


public class DialogEveServlet extends DebugServlet {
        private static final long serialVersionUID = 2523444841594323679L;
        private static Logger logger = Logger.getLogger(DialogEveServlet.class.getSimpleName());
        static Agent agent = null;
        
        @Override
        public void doPost( HttpServletRequest req, HttpServletResponse resp ) throws IOException, ServletException {
            
            if ( !handleSession( req, resp ) ) {
                if ( !resp.isCommitted() ) {
                    resp.sendError( HttpServletResponse.SC_UNAUTHORIZED );
                }
                resp.flushBuffer();
                return;
            }
    
            // retrieve the url and the request body
            final String body = StringUtil.streamToString( req.getInputStream() );
            final String id = "dialog";
            if ( id == null || id.equals( "" ) || id.equals( myUrl.toASCIIString() ) ) {
                resp.sendError( HttpServletResponse.SC_BAD_REQUEST,
                                "Couldn't parse URL, missing 'id'" );
                resp.flushBuffer();
                return;
            }
    
            String sender = req.getHeader( "X-Eve-SenderUrl" );
            if ( sender == null || sender.equals( "" ) ) {
                sender = "web://" + req.getRemoteUser() + "@" + req.getRemoteAddr();
            }
            URI senderUrl = null;
            try {
                senderUrl = new URI( sender );
            }
            catch ( final URISyntaxException e ) {
                logger.log( Level.WARNING, "Couldn't parse senderUrl:" + sender, e );
            }
            final HttpTransport transport = HttpService.get( myUrl, id );
            if ( transport != null ) {
                try {
                    final String response = transport.receive( body, senderUrl );
                    // TODO: It doesn't need to be json, should we handle mime-types
                    // better?
                    resp.addHeader( "Content-Type", "application/json" );
                    resp.getWriter().println( response );
                    resp.getWriter().close();
                }
                catch ( final IOException e ) {
                    resp.sendError( HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                    "Receiver raised exception:" + e.getMessage() );
                }
            }
            else {
                resp.sendError( HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                                "Couldn't load transport" );
            }
            resp.flushBuffer();
        }
        
}