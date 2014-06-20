package com.almende.dialog.agent.tools;

import java.util.ArrayList;

import com.almende.dialog.agent.tools.Event;

public class Result{
	/*
	 * {"id":1,"result":[{"title":"Travel from ASK to Almende","updated":1333694266000,"when":[{"start":1333400637000,"end":1333711800000}],"where":[],"url":"https://www.google.com/calendar/feeds/ludo%40almende.org/private/full/eaq67oa8v0qe8lbei19rgk71ss","who":[{"email":"ludo@almende.org","name":""}]},{"title":"Travel from Almende to ASK","updated":1333694264000,"when":[{"start":1333396026000,"end":1333706400000}],"where":[],"url":"https://www.google.com/calendar/feeds/ludo%40almende.org/private/full/ri7utv5lq86t85m3d6qplu6rd4","who":[{"email":"ludo@almende.org","name":""}]},{"title":"Dialog Status","updated":1333634816000,"when":[{"start":1333702800000,"end":1333704600000}],"where":["Almende"],"url":"https://www.google.com/calendar/feeds/ludo%40almende.org/private/full/ttqpodscq6jab4tmra2uo287fs","who":[{"email":"ludo@almende.org","name":""},{"email":"andries@almende.org","name":""}]},{"title":"BRIDGE Almende team meeting","updated":1333623244000,"when":[{"start":1333711800000,"end":1333715400000}],"where":["Almende"],"url":"https://www.google.com/calendar/feeds/ludo%40almende.org/private/full/ci4fujvceqmpseobog0ntk68o4_20120406T080000Z","who":[{"email":"scott@almende.org","name":""},{"email":"alfons@almende.org","name":""},{"email":"dferro@ask-cs.com","name":""},{"email":"ludo@almende.org","name":""},{"email":"duco@almende.org","name":""},{"email":"alfons1@almende.org","name":""},{"email":"radu@almende.org","name":""}]},{"title":"@ASK, Dialoog overdracht","updated":1333449059000,"when":[{"start":1333706400000,"end":1333724400000}],"where":["ASK"],"url":"https://www.google.com/calendar/feeds/ludo%40almende.org/private/full/nvirlbm9lsdv8g70ts69klhhbk","who":[{"email":"ludo@almende.org","name":""}]},{"title":"BRIDGE Almende team meeting","recurrence":"DTSTART;TZID=Europe/Amsterdam:20111118T100000\nDTEND;TZID=Europe/Amsterdam:20111118T110000\nRRULE:FREQ=WEEKLY;BYDAY=FR\nBEGIN:VTIMEZONE\nTZID:Europe/Amsterdam\nX-LIC-LOCATION:Europe/Amsterdam\nBEGIN:DAYLIGHT\nTZOFFSETFROM:+0100\nTZOFFSETTO:+0200\nTZNAME:CEST\nDTSTART:19700329T020000\nRRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU\nEND:DAYLIGHT\nBEGIN:STANDARD\nTZOFFSETFROM:+0200\nTZOFFSETTO:+0100\nTZNAME:CET\nDTSTART:19701025T030000\nRRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU\nEND:STANDARD\nEND:VTIMEZONE","updated":1331545627000,"when":[{"start":1333711800000,"end":1333715400000}],"where":["Almende"],"url":"https://www.google.com/calendar/feeds/ludo%40almende.org/private/full/ci4fujvceqmpseobog0ntk68o4","who":[{"email":"scott@almende.org","name":""},{"email":"alfons@almende.org","name":""},{"email":"dferro@ask-cs.com","name":""},{"email":"ludo@almende.org","name":""},{"email":"duco@almende.org","name":""},{"email":"alfons1@almende.org","name":""},{"email":"radu@almende.org","name":""}]}],"error":null}
	 */
	int id;
	ArrayList<Event> result;
	String error;
	public Result(){};
	
	public int getId() {
		return id;
	}
	public ArrayList<Event> getResult() {
		return result;
	}
	public String getError() {
		return error;
	}
	public void setId(int id) {
		this.id = id;
	}
	public void setResult(ArrayList<Event> result) {
		this.result = result;
	}
	public void setError(String error) {
		this.error = error;
	}
}