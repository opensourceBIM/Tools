package org.bimserver.tools.ifcloader;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.bimserver.client.BimServerClient;
import org.bimserver.client.json.JsonBimServerClientFactory;
import org.bimserver.interfaces.objects.SDeserializerPluginConfiguration;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.plugins.services.Flow;
import org.bimserver.shared.ChannelConnectionException;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.shared.exceptions.BimServerClientException;
import org.bimserver.shared.exceptions.PublicInterfaceNotFoundException;
import org.bimserver.shared.exceptions.ServerException;
import org.bimserver.shared.exceptions.ServiceException;
import org.bimserver.shared.exceptions.UserException;

public class IfcLoader {
	private BimServerClient client;

	public static void main(String[] args) {
		Options options = new Options();
		options.addOption("path", true, "Directory with IFC files (can have subdirectories)");
		options.addOption("address", true, "Address of BIMserver");
		options.addOption("username", true, "Username");
		options.addOption("password", true, "Password");
		options.addOption("mainproject", true, "Main project name");
		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine cmd = parser.parse(options, args);
			new IfcLoader().start(cmd);
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	private void start(CommandLine commandLine) {
		try {
			Path baseDirectory = Paths.get(commandLine.getOptionValue("path"));

			JsonBimServerClientFactory factory = new JsonBimServerClientFactory(commandLine.getOptionValue("address"));
			client = factory.create(new UsernamePasswordAuthenticationInfo(commandLine.getOptionValue("username"), commandLine.getOptionValue("password")));

			String mainProjectName = commandLine.getOptionValue("mainproject");
			
			if (Files.exists(baseDirectory)) {
				createRecursive(baseDirectory, null, mainProjectName, 0);
			}
		} catch (BimServerClientException e) {
			e.printStackTrace();
		} catch (ServiceException e) {
			e.printStackTrace();
		} catch (ChannelConnectionException e) {
			e.printStackTrace();
		}
	}

	private void createRecursive(Path baseDirectory, SProject parent, String mainProjectName, int nrChildren) {
		try {
			if (Files.isDirectory(baseDirectory)) {
				SProject project = null;
				if (parent == null) {
					project = client.getServiceInterface().addProject(mainProjectName, "ifc2x3tc1");
				} else {
					project = client.getServiceInterface().addProjectAsSubProject(baseDirectory.getFileName().toString(), parent.getOid(), "ifc2x3tc1");
				}
				List<Path> paths = new ArrayList<>();
				try (DirectoryStream<Path> stream = Files.newDirectoryStream(baseDirectory)) {
					for (Path entry : stream) {
						paths.add(entry);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				for (Path path : paths) {
					createRecursive(path, project, null, paths.size());
				}
			} else {
				SDeserializerPluginConfiguration deserializer = client.getServiceInterface().getSuggestedDeserializerForExtension("ifc", parent.getOid());
				if (nrChildren == 1) {
					try {
						client.checkin(parent.getOid(), "Initial", deserializer.getOid(), false, Flow.SYNC, baseDirectory);
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					String folderName = baseDirectory.getFileName().toString();
					folderName = folderName.substring(0, folderName.lastIndexOf(".") - 1);
					SProject project = client.getServiceInterface().addProjectAsSubProject(folderName, parent.getOid(), "ifc2x3tc1");
					try {
						client.checkin(project.getOid(), "Initial", deserializer.getOid(), false, Flow.SYNC, baseDirectory);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} catch (ServerException e) {
			e.printStackTrace();
		} catch (UserException e) {
			e.printStackTrace();
		} catch (PublicInterfaceNotFoundException e) {
			e.printStackTrace();
		}
	}
}
