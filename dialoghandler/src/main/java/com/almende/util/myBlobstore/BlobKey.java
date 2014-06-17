package com.almende.util.myBlobstore;

import java.io.Serializable;

import com.almende.util.uuid.UUID;

public class BlobKey implements Serializable {

	private static final long serialVersionUID = 1L;
	private String	uuid	= null;
	
	public BlobKey() {
		setUuid(new UUID().toString());
	}
	
	public BlobKey(String uuid) {
		setUuid(uuid);
	}
	
	public String getUuid() {
		return uuid;
	}
	
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
	
}
