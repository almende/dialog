package com.almende.dialog.model;

import java.util.logging.Logger;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.model.impl.S_fields;
import com.almende.dialog.model.intf.SessionIntf;
import com.almende.dialog.state.StringStore;
import com.almende.util.ParallelInit;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;


public class Session implements SessionIntf {
	private static final long serialVersionUID = 2674975096455049670L;
	private static final Logger log = Logger
			.getLogger("DialogHandler");
	static final ObjectMapper om =ParallelInit.getObjectMapper();
	
	SessionIntf session;
	String key = "";
	
	public Session() {
		this.session = new S_fields();
	}
	@Override
	public String getSession_id() {
		return session.getSession_id();
	}
	@Override
	public String getAccount() {
		return session.getAccount();
	}
	@Override
	public void setSession_id(String session_id) {
		session.setSession_id(session_id);
	}
	@Override
	public void setAccount(String account) {
		session.setAccount(account);
	}
	
	@JsonIgnore
	public String toJSON() {
		try {
			return om.writeValueAsString(this);
		} catch (Exception e) {
			log.severe("Session toJSON: failed to serialize Session!");
		}
		return null;
	}
	@JsonIgnore
	public static Session fromJSON(String json) {
		try {
			return om.readValue(json, Session.class);
		} catch (Exception e){
			log.severe("Session fromJSON: failed to parse Session JSON!");
		}
		return null;
	}
	@JsonIgnore
	public void storeSession() {
		StringStore.storeString(key, this.toJSON());
	}
	@JsonIgnore
	public void drop() {
		StringStore.dropString(key);
	}
	@JsonIgnore
	public static Session getSession(String key) {
		Session session = null;
		String session_json = StringStore.getString(key);
		if (session_json == null || session_json.equals("")){
			String[] split = key.split("\\|");
			if (split.length == 3){
				String type = split[0];
				String localaddress = split[1];
				AdapterConfig config = AdapterConfig.findAdapterConfig(type, localaddress);
				if (config == null){
					return null;
				}
				session = new Session();
				session.setAccount(config.getAccount().toString());
				session.setRemoteAddress(split[2]);
				session.key = key;
				session.storeSession();
			} else {
				log.severe("getSession: incorrect key given:"+key);
			}
		} else {
			session = Session.fromJSON(session_json);
		}
		session.key = key;
		return session;
	}
	@Override
	public String getStartUrl() {
		return session.getStartUrl();
	}
	@Override
	public void setStartUrl(String url) {
		session.setStartUrl(url);
	}
	@Override
	public String getRemoteAddress() {
		return this.session.getRemoteAddress();
	}
	@Override
	public String getDirection() {
		return this.session.getDirection();
	}
	@Override
	public void setRemoteAddress(String remoteAddress) {
		this.session.setRemoteAddress(remoteAddress);
	}
	@Override
	public void setDirection(String direction) {
		this.session.setDirection(direction);
	}
}
