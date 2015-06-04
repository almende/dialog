package com.almende.dialog.model.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;
import com.almende.dialog.accounts.Dialog;
import com.almende.dialog.model.Answer;
import com.almende.dialog.model.EventCallback;
import com.almende.dialog.model.intf.QuestionIntf;
import com.almende.dialog.util.AFHttpClient;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Q_fields implements QuestionIntf {
	private static final long serialVersionUID = 748817624285821262L;
	private static final Logger log = Logger
			.getLogger("DialogHandler");
	static final ObjectMapper om =ParallelInit.getObjectMapper();
	
	String question_id;
	String question_text;
	String type;
	ArrayList<String> url;
	String requester;
	String data;
	String token;

	ArrayList<Answer> answers;
	ArrayList<EventCallback> event_callbacks;
	
	public Q_fields(){}
	
	@Override
	public String getQuestion_id() {
		return question_id;
	}
	@Override
	public String getQuestion_text() {
		return question_text;
	}
	@Override
	public String getType() {
		return type;
	}
	@Override
	public List<String> getUrl() {
		return url;
	}
	@Override
	public String getRequester() {
		return requester;
	}
    
        @Override
        public HashMap<String, String> getExpandedRequester(String language, String sessionKey) {
    
            HashMap<String, String> result = new HashMap<String, String>(0);
            String url = this.getRequester();
            if (url == null || url.equals(""))
                return result;
            if (language != null && !language.equals("")) {
                url += url.indexOf("?") > 0 ? "&" : "?";
                url += "preferred_language=" + language;
            }
            try {
                AFHttpClient client = ParallelInit.getAFHttpClient();
                String text = "";
                try {
                    String credentialsFromSession = Dialog.getCredentialsFromSession(sessionKey);
                    if (credentialsFromSession != null) {
                        client.addBasicAuthorizationHeader(credentialsFromSession);
                    }
                    text = client.get(url).getResponseBody();
                }
                catch (Exception e) {
                    log.severe(e.toString());
                }
                result = om.readValue(text, new TypeReference<HashMap<String, String>>() {
                });
            }
            catch (Exception e) {
                log.severe(e.toString());
                log.severe(e.getMessage());
            }
            return result;
        }
        
        @Override
        public HashMap<String, String> getExpandedRequester(String sessionKey) {
    
            return getExpandedRequester(null, sessionKey);
        }
	@Override
	public ArrayList<Answer> getAnswers() {
		return answers;
	}
	@Override
	public ArrayList<EventCallback> getEvent_callbacks() {
		return event_callbacks;
	}
	@Override
	public void setQuestion_id(String question_id) {
		this.question_id = question_id;
	}
	@Override
	public void setQuestion_text(String question_text) {
		this.question_text = question_text;
	}
	@Override
	public void setType(String type) {
		this.type = type;
	}
	@SuppressWarnings( "unchecked" )
	@Override
	public void setUrl(Object url) {
	    if(url instanceof String) {
	        this.url = new ArrayList<>();
	        this.url.add((String) url); 
	    } else if (url instanceof List) {
	        this.url = (ArrayList<String>) url;
	    }
		
	}
	@Override
	public void setAnswers(ArrayList<Answer> answers) {
		this.answers = answers;
	}
	@Override
	public void setEvent_callbacks(ArrayList<EventCallback> event_callbacks) {
		this.event_callbacks = event_callbacks;
	}
	@Override
	public void setRequester(String requester) {
		this.requester = requester;
	}

    @Override
    public String getQuestion_expandedtext(String language, String sessionKey) {

        String url = this.getQuestion_text();
        if (url == null || url.equals("")) {
            return "";
        }
        if (url.startsWith("text://")) {
            return url.replace("text://", "");
        }
        else if (url.startsWith("http") && url.endsWith(".wav")) {
            return url;
        }

        if (language != null && !language.equals("")) {
            url += url.indexOf("?") > 0 ? "&" : "?";
            url += "preferred_language=" + language;
        }
        String text = "";
        AFHttpClient client = ParallelInit.getAFHttpClient();
        try {
            String credentialsFromSession = Dialog.getCredentialsFromSession(sessionKey);
            if (credentialsFromSession != null) {
                client.addBasicAuthorizationHeader(credentialsFromSession);
            }
            text = client.get(url).getResponseBody();
        }
        catch (Exception e) {
            log.severe(e.toString());
        }
        return text;
    }
	
    @Override
    public String getQuestion_expandedtext(String sessionKey) {

        return getQuestion_expandedtext(null, sessionKey);
    }

	@Override
	public String getData() {
		return this.data;
	}

	@Override
	public String getTrackingToken() {
		return token;
	}

	@Override
	public void setData(String data) {
		this.data=data;
	}

	@Override
	public void setTrackingToken(String token) {
		this.token = token;
	}

}
