package de.skoobe.jenkins.plugin.httppublisher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.entity.FileEntity;
import org.apache.http.util.ByteArrayBuffer;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;

import java.util.Random;

public class UploadHelper extends AbstractHandler {
	
	public static int minSleep = 2;
	private Server currentServer;
	private List<UploadedFile> files = new ArrayList<UploadedFile>();
	
	private class UploadedFile {
		public String filename;
		public int length;
		public ByteArrayBuffer content;
		
		private void readTo(ByteArrayBuffer content, InputStream from) throws IOException {
			byte[] data = new byte[4 * 1024];
			int len;
			while ( (len = from.read(data, 0, 4 * 1024)) != -1 ) {
				content.append(data, 0, len);
			}
		}
		
		public UploadedFile(HttpServletRequest request) throws IOException {
			filename = new String(request.getPathInfo().substring(1));
			length = request.getContentLength();
			content = new ByteArrayBuffer(length);
			readTo(content, request.getInputStream());
		}
		
		public boolean equalsEntity(FileEntity entity) throws IOException {
			if (length != entity.getContentLength()) {
				return false;
			}
			ByteArrayBuffer entityContent = new ByteArrayBuffer(length);
			readTo(entityContent, entity.getContent());
			byte[] bufferA, bufferB;
			bufferA = content.buffer();
			bufferB = entityContent.buffer();
			for (int i = 0; i < bufferA.length; i++) {
				if (bufferA[i] != bufferB[i]) {
					return false;
				}
			}
			return true;
		}
		
	    @Override
	    public String toString() {
	    	return String.format("UploadFile(%s, %d)", filename, length);
	    }
	}
	
	public void handle(String target, HttpServletRequest request,
			HttpServletResponse response, int dispatch) throws IOException,
			ServletException {
		String msg = "Putting " + request.getPathInfo() + " with request " + Thread.currentThread().getId() + "\n";
		response.getWriter().append(msg);
		// System.err.print(msg);
		response.getWriter().flush();
		UploadedFile file = new UploadedFile(request);
		synchronized (this) {
			// System.out.println("Add file " + file);
			files.add(file);
		}
		try {
			Thread.sleep((minSleep + request.getContentLength() % minSleep) * 1000);
		} catch (InterruptedException e) {
		}
		msg = "Thread " + Thread.currentThread().getId() + " finished\n";
		response.getWriter().append(msg);
		// System.err.print(msg);
		((Request)request).setHandled(true);
	}
	
	public boolean contains(String filename, FileEntity entity) throws IOException {
		for (final UploadedFile u : files) {
			// System.out.println("Compare " + filename + " with " + u.filename);
			if (u.filename.equals(filename) && u.equalsEntity(entity)) {
				return true;
			}
		}
		return false;
	}
	
	public void startServer() throws Exception {
		currentServer = new Server(0);
		currentServer.addHandler(this);
		currentServer.start();
		while (!currentServer.isStarted()) {
			Thread.sleep(100);
		}
	}

	public int getServerPort() {
		return currentServer.getConnectors()[0].getLocalPort();
	}
	
	public void stopServer() throws Exception {
		currentServer.stop();
		currentServer.join();
	}
	
	public static File fileOfLength(int length) throws IOException {
		File tmp = File.createTempFile("tUp", "htp");
		Random random = new Random();
		byte[] bytes = new byte[length];
		random.nextBytes(bytes);
		FileOutputStream fos = new FileOutputStream(tmp);
		fos.write(bytes);
		fos.close();
		return tmp;
	}
	
	public static void main(String args[]) throws Exception {
		UploadHelper helper = new UploadHelper();
		helper.startServer();
	}
}
