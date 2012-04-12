package com.almende.dialog.proxy.agent.tools;

import java.util.ArrayList;

import com.almende.dialog.proxy.agent.tools.When;
import com.almende.dialog.proxy.agent.tools.Who;

public class Event{

	public Event(){};
	String title;
	long updated;
	ArrayList<When> when;
	ArrayList<String> where;
	String url;
	ArrayList<Who> who;
	public String getTitle() {
		return title;
	}
	public long getUpdated() {
		return updated;
	}
	public ArrayList<When> getWhen() {
		return when;
	}
	public ArrayList<String> getWhere() {
		return where;
	}
	public String getUrl() {
		return url;
	}
	public ArrayList<Who> getWho() {
		return who;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public void setUpdated(long updated) {
		this.updated = updated;
	}
	public void setWhen(ArrayList<When> when) {
		this.when = when;
	}
	public void setWhere(ArrayList<String> where) {
		this.where = where;
	}
	public void setUrl(String url) {
		this.url = url;
	}
	public void setWho(ArrayList<Who> who) {
		this.who = who;
	}
}