package com.almende.dialog.model;

import java.util.logging.Logger;

import com.almende.dialog.accounts.AdapterConfig;
import com.almende.dialog.adapter.TextServlet;
import com.almende.dialog.adapter.VoiceXMLRESTProxy;
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
	public boolean killed = false;
	
	public Session() {
		this.session = new S_fields();
	}
	@Override
	public String getSession_id() {
		return session.getSession_id();
	}

	@Override
	public void setSession_id(String session_id) {
		session.setSession_id(session_id);
	}
	
	@JsonIgnore
	public void kill(){
		this.killed=true;
		this.storeSession();
		if ("XMPP".equals(this.getType()) || "MAIL".equals(this.getType()) || "SMS".equals(this.getType())){
			TextServlet.killSession(this);
		} else {
			VoiceXMLRESTProxy.killSession(this);
		}
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
	public static Session getSession(String key) {
		return getSession(key, null);
	}
	
	@JsonIgnore
	public static Session getSession(String key, String keyword) {
		Session session = null;
		String session_json = StringStore.getString(key);
		if (session_json == null || session_json.equals("")){
			String[] split = key.split("\\|");
			if (split.length == 3){
				String type = split[0];
				String localaddress = split[1];
				AdapterConfig config = AdapterConfig.findAdapterConfig(type, localaddress, keyword);
				if (config == null){
					return null;
				}
				//TODO: check account/pubkey usage here
				session = new Session();
				session.setAdapterID(config.getConfigId());
				session.setPubKey(config.getPublicKey().toString());
				session.setRemoteAddress(split[2]);
				session.setLocalAddress(localaddress);
				session.setType(type);
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
	@JsonIgnore
	public AdapterConfig getAdapterConfig() {
		if(session.getAdapterID()!=null)
			return AdapterConfig.getAdapterConfig(session.getAdapterID());
		
		return null;
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
	@Override
	public String getLocalAddress() {
		return this.session.getLocalAddress();
	}
	@Override
	public void setLocalAddress(String localAddress) {
		this.session.setLocalAddress(localAddress);
	}
	@Override
	public String getType() {
		return this.session.getType();
	}
	@Override
	public void setType(String type) {
		this.session.setType(type);
	}
	@Override
	public String getPubKey() {
		
		return this.session.getPubKey();
	}
	@Override
	public String getPrivKey() {
		return this.session.getPrivKey();
	}
	@Override
	public void setPubKey(String pubKey) {
		this.session.setPubKey(pubKey);
	}
	@Override
	public void setPrivKey(String privKey) {
		this.session.setPrivKey(privKey);
	}
	
	@Override
	public String getExternalSession() {
		return this.session.getExternalSession();
	}
	
	@Override
	public void setExternalSession(String externalSession) {
		this.session.setExternalSession(externalSession);
	}
	
	@Override
	public String getAdapterID() {
		return this.session.getAdapterID();
	}
	
	@Override
	public void setAdapterID(String adapterID) {
		this.session.setAdapterID(adapterID);
	}
}
