package de.skoobe.jenkins.plugin.httppublisher;

import hudson.FilePath;
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
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;

public class HttpPublisher extends Recorder implements Describable<Publisher> {

	private String profileName;
	private final List<Entry> entries = new ArrayList<Entry>();
	@Extension
	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

	@DataBoundConstructor
	public HttpPublisher() {
		super();
	}

	public HttpPublisher(String profileName) {
		super();
		if (profileName == null) {
			// @TODO: exactly like s3, take the first one
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

	public HTTPPublisherProfile getProfile() {
		HTTPPublisherProfile[] profiles = DESCRIPTOR.getProfiles();
		if (profileName == null && profiles.length > 0) {
			return profiles[0];
		}

		for (final HTTPPublisherProfile profile : profiles) {
			if (profile.getName().equals(profileName)) {
				return profile;
			}
		}
		return null;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException {

		if (build.getResult() == Result.FAILURE) {
			// ignore if build failed
			return true;
		}

		HTTPPublisherProfile profile = getProfile();
		if (profile == null) {
			log(listener.getLogger(), "No HTTP profile configured");
			build.setResult(Result.UNSTABLE);
			return true;
		}

		log(listener.getLogger(), "Using HTTP profile: " + profile.getName());
		try {
			Map<String, String> envVars = build.getEnvironment(listener);

			for (Entry entry : entries) {
				String expanded = Util.replaceMacro(entry.sourceFile, envVars);
				FilePath ws = build.getWorkspace();
				FilePath[] paths = ws.list(expanded);

				if (paths.length == 0) {
					log(listener.getLogger(), "No file(s) found: " + expanded);
					String error = ws.validateAntFileMask(expanded);
					if (error != null)
						log(listener.getLogger(), error);
				}
				profile.setLogger(listener.getLogger());
				boolean complete = true;
				for (FilePath src : paths) {
					log(listener.getLogger(), "file=" + src.getName());
					complete = complete && profile.upload(src);
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

		private final CopyOnWriteList<HTTPPublisherProfile> profiles = new CopyOnWriteList<HTTPPublisherProfile>();

		public DescriptorImpl(Class<? extends Publisher> klass) {
			super(klass);
			load();
		}

		public DescriptorImpl() {
			this(HttpPublisher.class);
		}

		public HttpPublisher newInstance(StaplerRequest req,
				net.sf.json.JSONObject formData) throws FormException {
			HttpPublisher publisher = new HttpPublisher();
			req.bindParameters(publisher, "pth.");
			publisher.getEntries().addAll(
					req.bindParametersToList(Entry.class, "pth.entry."));
			return publisher;
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws hudson.model.Descriptor.FormException {
			profiles.replaceBy(req.bindParametersToList(
					HTTPPublisherProfile.class, "pth."));
			save();
			return true;
		}

		public HTTPPublisherProfile[] getProfiles() {
			return profiles.toArray(new HTTPPublisherProfile[0]);
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
