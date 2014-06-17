package com.almende.dialog.adapter;

import java.io.IOException;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.Notificare;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.model.ddr.DDRPrice.UnitType;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;

public  class NotificareServlet extends TextServlet {
	

	private static final long serialVersionUID = 1L;
	protected static final com.almende.dialog.Logger dialogLog =  new com.almende.dialog.Logger();
	private static final String adapterType = "push";
	private static final String servletPath = "/push/";
	
	@Override
	protected int sendMessage(String message, String subject, String from,
			String fromName, String to, String toName,
			Map<String, Object> extras, AdapterConfig config) throws Exception{
		System.out.println("notificare start");
		
		Notificare notificare = new Notificare();
		notificare.sendMessage(message, subject, from, fromName, to, toName, extras, config);
		return 0;
	}

	@Override
	protected int broadcastMessage(String message, String subject, String from,
			String senderName, Map<String, String> addressNameMap,
			Map<String, Object> extras, AdapterConfig config) throws Exception {
		
		Notificare notificare = new Notificare();
		notificare.sendMessage(message, subject, from, senderName, "", "", extras, config);
		return 0;
	}

	@Override
	protected DDRRecord createDDRForIncoming(AdapterConfig adapterConfig,
			String fromAddress) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected DDRRecord createDDRForOutgoing(AdapterConfig adapterConfig,
			Map<String, String> toAddress, String message) throws Exception {
        return DDRUtils.createDDRRecordOnOutgoingCommunication( adapterConfig, UnitType.PART, toAddress,
                1 );
	}

	@Override
	protected TextMessage receiveMessage(HttpServletRequest req,
			HttpServletResponse resp) throws Exception {
		// TODO Auto-generated method stub
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
			throws IOException {
		// TODO Auto-generated method stub
		
	}

}
