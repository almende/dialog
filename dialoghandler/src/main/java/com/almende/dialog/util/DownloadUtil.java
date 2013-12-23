package com.almende.dialog.util;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.almende.util.myBlobstore.BlobKey;
import com.almende.util.myBlobstore.MyBlobStore;

@Path("/download")
public class DownloadUtil {

	@GET
	@Path("/{file}")
	public Response serve(@PathParam("file") String filename, @Context HttpServletResponse res) {
		
		MyBlobStore store = new MyBlobStore();
		BlobKey key = store.getUploadedBlob(filename);
		if(key==null)
			return Response.status(Status.NOT_FOUND).entity("No such file").build();
		store.serve(key, res);
		
		return null;
	}
}
