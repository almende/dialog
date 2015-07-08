package com.almende.dialog.adapter;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.tools.Notificare;
import com.almende.dialog.agent.AdapterAgent;
import com.almende.dialog.agent.tools.TextMessage;
import com.almende.dialog.model.Session;
import com.almende.dialog.model.ddr.DDRRecord;
import com.almende.dialog.util.DDRUtils;
import com.almende.dialog.util.ServerUtils;
import com.askfast.commons.entity.AdapterProviders;
import com.google.common.io.CharStreams;

public class NotificareServlet extends TextServlet {

    private static final long serialVersionUID = 1L;
    protected static final com.almende.dialog.Logger dialogLog = new com.almende.dialog.Logger();
    public static final String servletPath = "/push/";

    @Override
    protected int sendMessage(String message, String subject, String from, String fromName, String to, String toName,
        Map<String, Object> extras, AdapterConfig config, String accountId, DDRRecord ddrRecord) throws Exception {

        Session ses = Session.getSessionByInternalKey(getAdapterType(), config.getMyAddress(), to);
        if (!ServerUtils.isInUnitTestingEnvironment()) {
            Notificare notificare = new Notificare();
            notificare.sendMessage(message, subject, from, fromName, to, toName, config, ses);
        }
        return 1;
    }

    @Override
    protected int broadcastMessage(String message, String subject, String from, String senderName,
        Map<String, String> addressNameMap, Map<String, Object> extras, AdapterConfig config, String accountId,
        DDRRecord ddrRecord) throws Exception {

        int count = 0;
        for (String address : addressNameMap.keySet()) {
            count += sendMessage(message, subject, from, senderName, address, addressNameMap.get(address), extras,
                                 config, accountId, ddrRecord);
        }
        return count;
    }

    @Override
    protected DDRRecord createDDRForIncoming(AdapterConfig adapterConfig, String accountId, String fromAddress,
        String message, Session session) throws Exception {

        return DDRUtils.createDDRRecordOnIncomingCommunication(adapterConfig, accountId, fromAddress, message, session);
    }

    @Override
    protected DDRRecord createDDRForOutgoing(AdapterConfig adapterConfig, String accountId, String senderName,
        Map<String, String> toAddress, String message, Map<String, Session> sessionKeyMap) throws Exception {

        return DDRUtils.createDDRRecordOnOutgoingCommunication(adapterConfig, accountId, null, toAddress, 1, message,
                                                               sessionKeyMap);
    }

    @Override
    protected TextMessage receiveMessage(HttpServletRequest req, HttpServletResponse resp) throws Exception {

        TextMessage message = new TextMessage();
        String responseBody = "";
        if ("POST".equalsIgnoreCase(req.getMethod())) {
            responseBody = URLDecoder.decode(CharStreams.toString(req.getReader()), "UTF-8");
        }
        message = parseQuestion(responseBody);
        resp.getWriter().println(getHtmlResponse("Thank you for you response"));
        return message;
    }

    private String getHtmlResponse(String message) {

        String html = "<html> <head> " +
                      "<link rel=\"stylesheet\" type=\"text/css\" href=\"http://static.ask-cs.com/askfast/base.css\"> " +
                      "<link rel=\"stylesheet\" type=\"text/css\" href=\"http://static.ask-cs.com/askfast/layout.css\"> " +
                      "<link rel=\"stylesheet\" type=\"text/css\"  href=\"http://static.ask-cs.com/askfast/skeleton.css\"> " +
                      "</head> <div class=\"container\" >" + "<div class=\"sixteen columns\">" + "<h4>" + message +
                      "</h4><hr>" + "</div></div></body></html>";
        return html;
    }

    private TextMessage parseQuestion(String postBody) throws Exception {

        TextMessage message = new TextMessage();
        Map<String, String> map = new HashMap<String, String>();
        String[] pairs = postBody.split("\\&");
        for (int i = 0; i < pairs.length; i++) {
            String[] fields = pairs[i].split("=");
            String name = URLDecoder.decode(fields[0], "UTF-8");
            String value = URLDecoder.decode(fields[1], "UTF-8");
            map.put(name, value);
        }
        Session ses = Session.getSession(map.get("sessionkey"));
        if (ses != null) {
            AdapterConfig adapterConfig = ses.getAdapterConfig();
            //update the owner accountId if its not an existing session. 
            //So for pure inbound session. and not a reply on a closed/open question.
            if(adapterConfig != null && !ses.isExistingSession()) {
                ses.setAccountId(adapterConfig.getOwner());
                ses.storeSession();
            }
            message.setBody(map.get("answer"));
            message.setAddress(ses.getRemoteAddress());
            message.setLocalAddress(ses.getLocalAddress());
        }
        return message;
    }

    @Override
    protected String getServletPath() {

        return servletPath;
    }

    @Override
    protected String getAdapterType() {

        return AdapterAgent.ADAPTER_TYPE_PUSH;
    }

    @Override
    protected void doErrorPost(HttpServletRequest req, HttpServletResponse res) throws IOException {

        // TODO Auto-generated method stub

    }

    @Override
    protected String getProviderType() {

        return AdapterProviders.NOTIFICARE.toString();
    }

}
