package de.skoobe.jenkins.plugin.httppublisher;

import java.io.File;
import java.io.IOException;

import org.apache.http.entity.FileEntity;
import org.junit.After;
import org.junit.Before;

import junit.framework.TestCase;

public class HttpPublisherProfileTest extends TestCase {

	private UploadHelper helper;
	private HttpPublisherProfile profile;
	
	@Before
	protected void setUp() throws Exception {
		super.setUp();
		helper = new UploadHelper();
		helper.startServer();
		profile = new HttpPublisherProfile("test-profile", new Server[] {
				new Server(String.format("http://localhost:%d/", helper.getServerPort()))
		});
	}
	
	@After
	protected void tearDown() throws Exception {
		helper.stopServer();
	}
	
	public void testUpload() throws Exception {
		FileEntity entity = new FileEntity(UploadHelper.fileOfLength(201));
		profile.upload("testUpload", entity);
		assertTrue(helper.contains("testUpload", entity));
	}
	
	class UploadThread extends Thread {
		private FileEntity entity;
		
		public UploadThread(String name, int length) throws IOException {
			super(name);
			entity = new FileEntity(UploadHelper.fileOfLength(length));
		}
		
		public FileEntity getEntity() {
			return entity;
		}
		
		public void run() {
			profile.upload(this.getName(), entity);
		}
	}
	
	public void testParalelUpload() throws Exception {
		int i, n = 10;
		UploadThread[] threads = new UploadThread[n];
		for (i = 0; i < n; i++) {
			threads[i] = new UploadThread(String.format("thread-%d", i), 1024 * 1024 * (n - i));
		}
		for (i = 0; i < n; i++) {
			threads[i].start();
		}
		for (i = n - 1; i >= 0; i--) {
			threads[i].join();
			// System.out.println("Joined thread-" + String.valueOf(i));
		}
		for (i = 0; i < n; i++) {
			boolean result = helper.contains(String.format("thread-%d", i), threads[i].getEntity());
			// System.out.println(String.valueOf(i) + " " + String.valueOf(result));
			assertTrue(result);
		}
	}
	
}
