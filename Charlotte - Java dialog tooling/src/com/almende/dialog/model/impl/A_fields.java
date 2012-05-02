package com.almende.dialog.model.impl;

import java.util.logging.Logger;

import com.almende.dialog.model.intf.AnswerIntf;
import com.almende.tools.ParallelInit;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;

public class A_fields implements AnswerIntf {
	private static final long serialVersionUID = -2673880321244315796L;
	private static final Logger log = Logger
			.getLogger("DialogHandler");
	String answer_id;
	String answer_text;
	String callback;
	
	public A_fields() {}

	@Override
	public String getAnswer_id() {
		return answer_id;
	}

	@Override
	public String getAnswer_text() {
		return answer_text;
	}

	@Override
	public String getCallback() {
		return callback;
	}

	@Override
	public void setAnswer_id(String answer_id) {
		this.answer_id = answer_id;
	}

	@Override
	public void setAnswer_text(String answer_text) {
		this.answer_text = answer_text;
	}

	@Override
	public void setCallback(String callback) {
		this.callback = callback;
	}

	@Override
	public String getAnswer_expandedtext(String language) {
		Client client = ParallelInit.getClient();
		String url = this.getAnswer_text();
		if (language != null && !language.equals("")) url+="?preferred_language="+language;
		WebResource webResource = client.resource(url);
		String text = "";
		try {
			text = webResource.type("text/plain").get(String.class);
		} catch (Exception e){
			log.severe(e.toString());
		}
		return text;
	}
	@Override
	public String getAnswer_expandedtext() {
		return getAnswer_expandedtext(null);
	}

}
