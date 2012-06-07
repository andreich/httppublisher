package de.skoobe.jenkins.plugin.httppublisher;

import java.io.Serializable;

import org.kohsuke.stapler.DataBoundConstructor;

public final class Server implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = -4619767225792670321L;

	@DataBoundConstructor
	public Server(String hostname) {
		this.hostname = hostname;
	}

	private String hostname;
	
	public String getHostname() {
		return hostname;
	}
	
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

}
