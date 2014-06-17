package com.almende.dialog.util;

import java.util.UUID;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

import com.almende.util.myBlobstore.MyBlobStore;

@Path("upload")
public class UploadUtil {
	
	@GET
	public Response upload() {		
		return uploadWithFile(null);
	}

	@GET
	@Path("{file}")
	public Response uploadWithFile(@PathParam("file") String filename) {
		
		if(filename==null || filename.isEmpty())
			filename = UUID.randomUUID().toString()+".wav";
		
		MyBlobStore store = new MyBlobStore();
		String html = "<!DOCTYPE html>" + "<html>" + "<head>"
				+ "<meta charset=\"UTF-8\" />" + "<title> Upload file </title>" + "</head>" + "<body>" + "<form action=\""
				+ store.createUploadUrl(filename, "/dialoghandler/rest/download/"+filename)
				+ "\" method=\"post\" enctype=\"multipart/form-data\">"
				+ "<input type=\"file\" name=\""+filename+"\" />" + "<hr />"
				+ "<input type=\"submit\" value=\"submit\" />" + "</form>"
				+ "</body>" + "</html>";
		return Response.ok( html ).build() ;
	}
}
