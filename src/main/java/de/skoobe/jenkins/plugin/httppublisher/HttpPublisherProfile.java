package de.skoobe.jenkins.plugin.httppublisher;

import hudson.FilePath;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.DataBoundConstructor;

public class HttpPublisherProfile implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3407333590032467265L;
	private String name;
	private List<Server> servers;

	private transient PrintStream logger;
	
	
	public HttpPublisherProfile() {
	}

	public HttpPublisherProfile(String name, List<Server> servers) {
		this.name = name;
		this.servers = servers;
	}

	@DataBoundConstructor
	public HttpPublisherProfile(String name, Server[] servers) {
		this.name = name;
		this.servers = new ArrayList<Server>();
		for (final Server server : servers) {
			this.servers.add(server);
		}
	}
	
	public void setLogger(PrintStream logger) {
		this.logger = logger;
	}
	
	public void setServers(List<Server> servers) {
		this.servers = servers;
	}
	
	public List<Server> getServers() {
		return servers;
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
			System.err.println(message);
		} else {
			logger.println(message);
		}
	}

	public boolean upload(FilePath filePath) throws IOException,
			InterruptedException {
		if (filePath.isDirectory()) {
			throw new IOException(filePath + " is a directory");
		}

		log("uploading " + filePath);

		File file = new File(filePath.toURI());
		return upload(file);
	}
	
	public boolean upload(File file) {
		HttpEntity fileEntity = new FileEntity(file);
		return upload(file.getName(), fileEntity);
	}
	
	public boolean upload(String fileName, HttpEntity fileEntity) {
		HttpClient client = new DefaultHttpClient();
		
		boolean success = false;

		for (final Server currentServer : servers) {
			success = tryUpload(currentServer.getHostname(), client, fileName,
					fileEntity, logger);
			if (success)
				break;
		}

		return success;
	}

	private boolean tryUpload(String currentServer, HttpClient client,
			String fileName, HttpEntity fileEntity, PrintStream logger) {
		try {
			HttpPut put = new HttpPut(currentServer + fileName);
			put.setEntity(fileEntity);
			HttpResponse response = client.execute(put);
			log(currentServer + " " + response.getStatusLine().toString() + " (uploaded size: " +fileEntity.getContentLength() + ")");
			EntityUtils.consume(response.getEntity());
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