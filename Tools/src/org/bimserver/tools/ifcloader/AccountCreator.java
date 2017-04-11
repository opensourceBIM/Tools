package org.bimserver.tools.ifcloader;

/******************************************************************************
 * Copyright (C) 2009-2015  BIMserver.org
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see {@literal<http://www.gnu.org/licenses/>}.
 *****************************************************************************/

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
