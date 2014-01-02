package com.almende.util.myBlobstore;

import java.io.Serializable;

import com.almende.util.twigmongo.annotations.Id;
import com.almende.util.twigmongo.annotations.Index;

public class FileContentType implements Serializable {
	private static final long	serialVersionUID	= 3786461126487686318L;
	
	@Id
	public String				uuid;
	public String				contentType;
	@Index
	public String				fileName;
	
	public FileContentType(String uuid, String contentType, String fileName) {
		this.uuid = uuid;
		this.contentType = contentType;
		this.fileName = fileName;
	}
	
	public FileContentType() {
		this(null, null, null);
	}
	
	public String getUuid() {
		return uuid;
	}
	
	public void setUuid(String uuid) {
		this.uuid = uuid;
	}
	
	public String getContentType() {
		return contentType;
	}
	
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}
	
	public String getFileName() {
		return fileName;
	}
	
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
}
