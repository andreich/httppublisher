package de.skoobe.jenkins.plugin.httppublisher;

import hudson.FilePath;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.kohsuke.stapler.DataBoundConstructor;

public class HTTPPublisherProfile {

	private String name;
	private String server;
	private String failover;
	private PrintStream logger = null;

	public HTTPPublisherProfile() {
	}

	@DataBoundConstructor
	public HTTPPublisherProfile(String name, String server, String failover) {
		this.name = name;
		this.server = server;
		this.failover = failover;
	}

	public void setServer(String server) {
		this.server = server;
	}

	public String getServer() {
		return server;
	}

	public void setFailover(String failover) {
		this.failover = failover;
	}

	public String getFailover() {
		return failover;
	}

	public void setName(String name) {
		this.name = name;
	}

	public final String getName() {
		return name;
	}

	public void check() {
		// do nothing for now
	}

	protected void log(String message) {
		if (logger == null) {
			System.out.println(message);
		} else {
			logger.println(message);
		}
	}

	public void setLogger(PrintStream logger) {
		this.logger = logger;
	}

	public boolean upload(FilePath filePath) throws IOException,
			InterruptedException {
		if (filePath.isDirectory()) {
			throw new IOException(filePath + " is a directory");
		}

		log("uploading " + filePath);

		File file = new File(filePath.toURI());

		HttpClient client = new DefaultHttpClient();
		HttpEntity fileEntity = new FileEntity(file);

		List<String> servers = new ArrayList<String>();
		servers.add(server);
		if (failover != null && !failover.isEmpty()) {
			servers.add(failover);
		}

		boolean success = false;

		for (final String currentServer : servers) {
			success = tryUpload(currentServer, client, filePath.getName(),
					fileEntity);
			if (success)
				break;
		}

		return success;
	}

	private boolean tryUpload(String currentServer, HttpClient client,
			String fileName, HttpEntity fileEntity) {
		try {
			HttpPut put = new HttpPut(currentServer + fileName);
			put.setEntity(fileEntity);
			HttpResponse response = client.execute(put);
			log(currentServer + " " + response.getStatusLine().toString());
			if (response.getStatusLine().getStatusCode() / 100 == 2) {
				return true;
			}
		} catch (ClientProtocolException e) {
			log(currentServer + " " + e);
		} catch (IOException e) {
			log(currentServer + " " + e);
		}
		return false;
	}
}
