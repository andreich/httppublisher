package de.skoobe.jenkins.plugin.httppublisher;

import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Launcher;
import hudson.Extension;
import hudson.Util;
import hudson.util.CopyOnWriteList;
import hudson.util.FormValidation;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Describable;
import hudson.model.Result;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

public class HttpPublisherPlugin extends Recorder implements Describable<Publisher> {

	private String profileName;
	private final List<Entry> entries = new ArrayList<Entry>();
	@Extension
	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

	@DataBoundConstructor
	public HttpPublisherPlugin() {
		super();
	}

	public HttpPublisherPlugin(String profileName) {
		super();
		if (profileName == null) {
			HttpPublisherProfile[] profiles = DESCRIPTOR.getProfiles();
			if (profiles.length > 0) {
				profileName = profiles[0].getName();
			}
		}
		this.profileName = profileName;
	}

	public List<Entry> getEntries() {
		return entries;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.STEP;
	}

	public String getName() {
		return profileName;
	}

	public void setName(String profileName) {
		this.profileName = profileName;
	}

	protected void log(final PrintStream logger, final String message) {
		logger.println(getDescriptor().getDisplayName() + " " + message);
	}
	
	public HttpPublisherProfile getProfile() {
		HttpPublisherProfile[] profiles = DESCRIPTOR.getProfiles();
		if (profileName == null && profiles.length > 0) {
			return profiles[0];
		}

		for (final HttpPublisherProfile profile : profiles) {
			if (profile.getName().equals(profileName)) {
				return profile;
			}
		}
		return null;
	}

	public static class UploadTask implements FileCallable<Boolean> {
		/**
		 * 
		 */
		private static final long serialVersionUID = -4879328981975349605L;
		private HttpPublisherProfile profile;
		private BuildListener listener;
	
		public UploadTask(final HttpPublisherProfile profile, final BuildListener listener) {
			this.profile = profile;
			this.listener = listener;
		}
		
		public Boolean invoke(File f, VirtualChannel channel)
				throws IOException, InterruptedException {
			profile.setLogger(listener.getLogger());
			return profile.upload(f);
		}
	}
	
	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException {
		if (build.getResult() == Result.FAILURE) {
			// ignore if build failed
			return true;
		}

		final HttpPublisherProfile profile = getProfile();
		if (profile == null) {
			log(listener.getLogger(), "No HTTP profile configured");
			build.setResult(Result.UNSTABLE);
			return true;
		}

		log(listener.getLogger(), "Current computer: " + Computer.currentComputer().getName());
		log(listener.getLogger(), "Using HTTP profile: " + profile.getName());
		profile.setLogger(listener.getLogger());
		try {
			Map<String, String> envVars = build.getEnvironment(listener);

			FilePath ws = build.getWorkspace();
			
			for (Entry entry : entries) {
				String expanded = Util.replaceMacro(entry.sourceFile, envVars);
				FilePath[] paths = ws.list(expanded);

				if (paths.length == 0) {
					log(listener.getLogger(), "No file(s) found: " + expanded);
					String error = ws.validateAntFileMask(expanded);
					if (error != null)
						log(listener.getLogger(), error);
				}
				
				boolean complete = true;
				for (FilePath src : paths) {
					log(listener.getLogger(), "file=" + src.getName());
					FileCallable<Boolean> task = new UploadTask(profile, listener);
					complete = complete && src.act(task);
				}
				if (!complete) {
					log(listener.getLogger(), "Could not upload all files");
					build.setResult(Result.UNSTABLE);
				}
			}
		} catch (IOException e) {
			e.printStackTrace(listener.error("Failed to upload files"));
			build.setResult(Result.UNSTABLE);
		}
		return true;
	}
	
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Publisher> {
		
		private final CopyOnWriteList<HttpPublisherProfile> profiles = new CopyOnWriteList<HttpPublisherProfile>();

		@Override
		public String getId() {
			return "HttpPublisher";
		}
		
		public DescriptorImpl(Class<? extends Publisher> klass) {
			super(klass);
			load();
		}

		public DescriptorImpl() {
			this(HttpPublisherPlugin.class);
		}
		
		public CopyOnWriteList<HttpPublisherProfile> getRawProfiles() {
			return profiles;
		}

		public HttpPublisherPlugin newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			HttpPublisherPlugin publisher = new HttpPublisherPlugin();
			req.bindParameters(publisher, "httppublisher.");
			publisher.getEntries().addAll(
					req.bindParametersToList(Entry.class, "httppublisher.entry."));
			return publisher;
		}

		private Server serverFromJSONObject(JSONObject json) {
			String hostname = json.getString("hostname");
			return new Server(hostname);
		}
		
		private HttpPublisherProfile fromJSONObject(JSONObject json) {
			String name = json.getString("name");
			Object serversObj = json.get("servers");
			List<Server> servers = new ArrayList<Server>();
			if (serversObj instanceof JSONArray) {
				JSONArray array = json.getJSONArray("servers");
				for (final Object serverObj : array) {
					if (serverObj instanceof JSONObject) {
						servers.add(serverFromJSONObject((JSONObject) serverObj));
					}
				}
			} else {
				servers.add(serverFromJSONObject(json.getJSONObject("servers")));
			}
			return new HttpPublisherProfile(name, servers);
		}
		
		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws hudson.model.Descriptor.FormException {
			if (json == null || json.isNullObject()) {
				profiles.replaceBy(new HttpPublisherProfile[] {});
			} else {
				Object profileObj = json.get("profile");
				if (profileObj instanceof JSONArray) {
					JSONArray profileArray = json.getJSONArray("profile");
					HttpPublisherProfile[] parsedProfiles = new HttpPublisherProfile[profileArray.size()]; 
					for (int i = 0; i < profileArray.size(); i++) {
						parsedProfiles[i] = fromJSONObject(profileArray.getJSONObject(i));
					}
					profiles.replaceBy(parsedProfiles);
				} else {
					HttpPublisherProfile profile = fromJSONObject(json.getJSONObject("profile"));
					profiles.replaceBy(new HttpPublisherProfile[] { profile });
				}
			}
			save();
			return true;
		}

		public HttpPublisherProfile[] getProfiles() {
			return profiles.toArray(new HttpPublisherProfile[0]);
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> jobType) {
			return true;
		}

		public FormValidation doCheckServer(final StaplerRequest req,
				StaplerResponse rsp) throws IOException, ServletException {
			String url = Util.fixEmpty(req.getParameter("url"));
			if (url == null) {
				return FormValidation.ok();
			}
			try {
				URL validUrl = new URL(url);
				if (!validUrl.getProtocol().equals("http")
						&& !validUrl.getProtocol().equals("https")) {
					return FormValidation.error("Unknown protocol used: "
							+ validUrl.getProtocol()
							+ ". Only HTTP and HTTPS are supported.");
				}
				if (!url.endsWith("/")) {
					return FormValidation.error("URL should end with '/'");
				}
			} catch (MalformedURLException e) {
				return FormValidation.error(e.getMessage());
			}
			return FormValidation.ok();
		}

		@Override
		public String getDisplayName() {
			return "Publish artifacts to HTTP";
		}

	}
}
