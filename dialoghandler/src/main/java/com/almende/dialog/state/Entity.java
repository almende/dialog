package com.almende.dialog.state;

import com.almende.util.twigmongo.annotations.Id;

public class Entity {
	@Id
	private String	id		= null;
	private String	string	= null;
	
	public Entity(){}
	
	public Entity(String id) {
		this.id = id;
	}
	
	public String getId() {
		return id;
	}
	
	public void setId(String id) {
		this.id = id;
	}
	
	public String getString() {
		return string;
	}
	
	public void setString(String string) {
		this.string = string;
	}
}
