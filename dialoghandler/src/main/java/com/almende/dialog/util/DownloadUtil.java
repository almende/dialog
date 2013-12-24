package com.almende.dialog.util;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.almende.util.myBlobstore.BlobKey;
import com.almende.util.myBlobstore.MyBlobStore;

@Path("/download")
public class DownloadUtil {

	@POST
	@Path("/{file}")
	public Response download(@PathParam("file") String filename, @Context HttpServletResponse res, String data) {
			
		if(filename.equals("audio.vxml")) {
			String resp = "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
					+ "<vxml xmlns=\"http://www.w3.org/2001/vxml\" version=\"2.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.w3.org/2001/vxml http://www.w3.org/TR/2007/REC-voicexml21-20070619/vxml.xsd\">"
					+ "<form>" + "<block>"
					+ "<var name=\"response\" expr=\"'SUCCESS'\"/>"
					+ "<return namelist=\"response\"/>" + "</block>"
					+ "</form>" + "</vxml>";
			return Response.ok(resp).build();
		}
		
		MyBlobStore store = new MyBlobStore();
		BlobKey key = store.getUploadedBlob(filename);
		if(key==null)
			return Response.status(Status.NOT_FOUND).entity("No such file").build();
		store.serve(key, res);
		
		return null;
	}
	
	@GET
	@Path("/{file}")
	public Response serve(@PathParam("file") String filename, @Context HttpServletResponse res) {
		
		if(filename.equals("audio.vxml")) {
			String resp = "<?xml version=\"1.0\" encoding=\"UTF-8\"?> "
					+ "<vxml xmlns=\"http://www.w3.org/2001/vxml\" version=\"2.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.w3.org/2001/vxml http://www.w3.org/TR/2007/REC-voicexml21-20070619/vxml.xsd\">"
					+ "<form>" + "<block>"
					+ "<var name=\"response\" expr=\"'SUCCESS'\"/>"
					+ "<return namelist=\"response\"/>" + "</block>"
					+ "</form>" + "</vxml>";
			return Response.ok(resp).build();
		}
		
		MyBlobStore store = new MyBlobStore();
		BlobKey key = store.getUploadedBlob(filename);
		if(key==null)
			return Response.status(Status.NOT_FOUND).entity("No such file").build();
		store.serve(key, res);
		
		return null;
	}
}
