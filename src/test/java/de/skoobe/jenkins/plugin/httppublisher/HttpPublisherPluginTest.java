package de.skoobe.jenkins.plugin.httppublisher;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.junit.Before;
import org.jvnet.hudson.test.HudsonTestCase;

import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;

import de.skoobe.jenkins.plugin.httppublisher.HttpPublisherPlugin.DescriptorImpl;

public class HttpPublisherPluginTest extends HudsonTestCase {
	
	private HttpPublisherPlugin.DescriptorImpl descriptor;
	
	@Override
	@Before
	protected void setUp() throws Exception {
		super.setUp();
		descriptor = HttpPublisherPlugin.DESCRIPTOR;
		descriptor.getRawProfiles().replaceBy(new HttpPublisherProfile[] {});
	}
	
	public void testBlankProjectConfiguration() throws Exception {
		descriptor.configure(null, null);
		assertEquals(0, descriptor.getRawProfiles().size());
		descriptor.configure(null, new JSONObject(true));
		assertEquals(0, descriptor.getRawProfiles().size());
	}
	
	public void testSimpleProjectConfiguration() throws Exception {
		String NAME = "testSPC";
		String HOSTNAME = "http://localhost/";
		JSONObject obj = new JSONObject();
		JSONObject profile = new JSONObject();
		JSONObject servers = new JSONObject();
		servers.put("hostname", HOSTNAME);
		profile.put("servers", servers);
		profile.put("name", NAME);
		obj.put("profile", profile);
		descriptor.configure(null, obj);
		assertEquals(1, descriptor.getRawProfiles().size());
		HttpPublisherProfile parsedProfile = descriptor.getProfiles()[0];
		assertEquals(NAME, parsedProfile.getName());
		assertEquals(1, parsedProfile.getServers().size());
		assertEquals(HOSTNAME, parsedProfile.getServers().get(0).getHostname());
	}
	
	public void testMoreComplexProjectConfiguration() throws Exception {
		String NAME = "testSPC";
		String HOSTNAME = "http://localhost/";
		JSONObject obj = new JSONObject();
		JSONArray profiles = new JSONArray();
		int nProfiles = 2;
		int nServers = 2;
		for (int i = 0; i < nProfiles; i++) {
			JSONObject profile = new JSONObject();
			JSONArray servers = new JSONArray();
			for (int j = 0; j < nServers; j++) {
				JSONObject server = new JSONObject();
				server.put("hostname", HOSTNAME + String.format("{0}-{1}/", i, j));
				servers.add(server);
			}
			profile.put("servers", servers);
			profile.put("name", NAME + String.format("-profile-{0}", i));
			profiles.add(profile);
		}
		obj.put("profile", profiles);
		descriptor.configure(null, obj);
		assertEquals(nProfiles, descriptor.getRawProfiles().size());
		for (int i = 0; i < nProfiles; i++) {
			HttpPublisherProfile parsedProfile = descriptor.getProfiles()[i];
			assertEquals(NAME + String.format("-profile-{0}", i), parsedProfile.getName());
			assertEquals(nServers, parsedProfile.getServers().size());
			for (int j = 0; j < nServers; j++) {
				assertEquals(HOSTNAME + String.format("{0}-{1}/", i, j), parsedProfile.getServers().get(0).getHostname());
			}
		}
	}
	
	public void testBlankConfigurationRoundTrip() throws Exception {
		assertEquals(0, HttpPublisherPlugin.DESCRIPTOR.getRawProfiles().size());
		configRoundtrip();
		assertEquals(0, HttpPublisherPlugin.DESCRIPTOR.getRawProfiles().size());
	}

	public void testSimpleConfigurationRoundTrip() throws Exception {
		DescriptorImpl descriptor = HttpPublisherPlugin.DESCRIPTOR;
		String HOSTNAME = "http://localhost/";
		String NAME = "testSCRT";
		Server[] servers = new Server[] { new Server(HOSTNAME) };
		HttpPublisherProfile profile = new HttpPublisherProfile(NAME, servers);
		descriptor.getRawProfiles().replaceBy(new HttpPublisherProfile[] { profile } );
		WebClient wc = createWebClient();
		submit(wc.goTo("configure").getFormByName("config"));
		assertEquals(1, descriptor.getRawProfiles().size());
		profile = descriptor.getRawProfiles().get(0);
		assertEquals(NAME, profile.getName());
		assertEquals(1, profile.getServers().size());
		assertEquals(HOSTNAME, profile.getServers().get(0).getHostname());
		HtmlForm form = wc.goTo("configure").getFormByName("config");
		HtmlInput inputName = form.getInputByName("httppublisher.name");
		HtmlInput inputHostname = form.getInputByName("httppublisher.servers.hostname");
		inputName.setValueAttribute(NAME + " changed");
		inputHostname.setValueAttribute(HOSTNAME + "changed/");
		submit(form);
		assertEquals(1, descriptor.getRawProfiles().size());
		profile = descriptor.getRawProfiles().get(0);
		assertEquals(NAME + " changed", profile.getName());
		assertEquals(1, profile.getServers().size());
		assertEquals(HOSTNAME + "changed/", profile.getServers().get(0).getHostname());
	}
	
}
