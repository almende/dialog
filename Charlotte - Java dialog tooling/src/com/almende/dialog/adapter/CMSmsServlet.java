package com.almende.dialog.adapter;

import java.io.IOException;
import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.CM;
import com.almende.dialog.agent.tools.TextMessage;

public class CMSmsServlet extends TextServlet {

	private static final long serialVersionUID = 408503132941968804L;
	
	private static final String servletPath = "/_ah/sms/";
	private static final String adapterType = "CM";
	
	@Override
	protected int sendMessage(String message, String subject, String from,
			String fromName, String to, String toName, AdapterConfig config) throws Exception {
    	        String[] tokens = config.getAccessToken().split("\\|");
                
                CM cm = new CM(tokens[0], tokens[1], config.getAccessTokenSecret());
		return cm.sendMessage(message, subject, from, fromName, to, toName, config);
	}
	
        @Override
        protected int broadcastMessage( String message, String subject, String from, String fromName,
            ArrayList<String> toList, ArrayList<String> toNames, AdapterConfig config ) throws Exception
        {
            String[] tokens = config.getAccessToken().split( "\\|" );
    
            CM cm = new CM( tokens[0], tokens[1], config.getAccessTokenSecret() );
            return cm.broadcastMessage( message, subject, from, fromName, toList, toNames, config );
        }

	@Override
	protected TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp)
			throws Exception {
		// TODO: Needs implementation, but service not available at CM
		return null;
	}

	@Override
	protected String getServletPath() {
		return servletPath;
	}

	@Override
	protected String getAdapterType() {
		return adapterType;
	}

	@Override
	protected void doErrorPost(HttpServletRequest req, HttpServletResponse res)
			throws IOException {}

	
}
