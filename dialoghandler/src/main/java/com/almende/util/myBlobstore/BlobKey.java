package com.almende.util.myBlobstore;

import com.almende.util.uuid.UUID;

public class BlobKey {
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
