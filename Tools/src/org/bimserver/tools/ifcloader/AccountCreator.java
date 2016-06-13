package org.bimserver.tools.ifcloader;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.bimserver.client.BimServerClient;
import org.bimserver.client.json.JsonBimServerClientFactory;
import org.bimserver.interfaces.objects.SUserType;
import org.bimserver.shared.ChannelConnectionException;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.shared.exceptions.BimServerClientException;
import org.bimserver.shared.exceptions.PublicInterfaceNotFoundException;
import org.bimserver.shared.exceptions.ServiceException;

public class AccountCreator {
	public static void main(String[] args) {
		Options options = new Options();
		options.addOption("email", true, "Email address (username)");
		options.addOption("name", true, "Name");
		options.addOption("type", true, "Type (USER, ADMIN)");
		options.addOption("address", true, "Address of BIMserver");
		options.addOption("username", true, "Username");
		options.addOption("password", true, "Password");
		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine cmd = parser.parse(options, args);
			new AccountCreator().start(cmd);
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private BimServerClient client;

	private void start(CommandLine commandLine) {
		try {
			String address = commandLine.getOptionValue("address");
			JsonBimServerClientFactory factory = new JsonBimServerClientFactory(address);
			client = factory.create(new UsernamePasswordAuthenticationInfo(commandLine.getOptionValue("username"), commandLine.getOptionValue("password")));

			String email = commandLine.getOptionValue("email");

			String resetUrl = address + "/apps/bimviews?page=ResetPassword";
			client.getServiceInterface().addUser(email, commandLine.getOptionValue("name"), SUserType.valueOf(commandLine.getOptionValue("type")), false, resetUrl);
			client.getAuthInterface().requestPasswordChange(email, resetUrl, false);
		} catch (BimServerClientException e) {
			e.printStackTrace();
		} catch (ServiceException e) {
			e.printStackTrace();
		} catch (ChannelConnectionException e) {
			e.printStackTrace();
		} catch (PublicInterfaceNotFoundException e) {
			e.printStackTrace();
		}
	}
}
