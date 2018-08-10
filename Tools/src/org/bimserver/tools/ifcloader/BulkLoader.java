package org.bimserver.tools.ifcloader;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.bimserver.client.BimServerClient;
import org.bimserver.client.json.JsonBimServerClientFactory;
import org.bimserver.interfaces.objects.SDeserializerPluginConfiguration;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.plugins.services.Flow;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.shared.exceptions.BimServerClientException;
import org.bimserver.shared.exceptions.PublicInterfaceNotFoundException;
import org.bimserver.shared.exceptions.ServerException;
import org.bimserver.shared.exceptions.UserException;

public class BulkLoader {
	public static void main(String[] args) {
		new BulkLoader().start();
	}

	private void start() {
		Path basePath = Paths.get("C:\\IFC");
		Path bulkPath = basePath.resolve("bulk");
		Path regularPath = basePath.resolve("single");
		Path ifc4path = regularPath.resolve("ifc4");
		Path ifc2x3tc1path = regularPath.resolve("ifc2x3tc1");
		try (JsonBimServerClientFactory factory = new JsonBimServerClientFactory("http://localhost:8080")) {
			ExecutorService executorService = new ThreadPoolExecutor(8, 8, 1, TimeUnit.HOURS, new ArrayBlockingQueue<>(10000));
			try (BimServerClient client = factory.create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"))) {
				DirectoryStream<Path> stream = Files.newDirectoryStream(bulkPath);
				for (Path path : stream) {
					executorService.submit(new Runnable(){
						@Override
						public void run() {
							try {
								SProject project = client.getServiceInterface().addProject(path.getFileName().toString(), "ifc2x3tc1");
								client.bulkCheckin(project.getOid(), path, "Automatic bulk checkin");
							} catch (ServerException e) {
								e.printStackTrace();
							} catch (UserException e) {
								e.printStackTrace();
							} catch (PublicInterfaceNotFoundException e) {
								e.printStackTrace();
							}
						}});
				}
				DirectoryStream<Path> regularStream = Files.newDirectoryStream(ifc4path);
				for (Path regularFile : regularStream) {
					executorService.submit(new Runnable(){
						@Override
						public void run() {
							try {
								SProject project = client.getServiceInterface().addProject(regularFile.getFileName().toString(), "ifc4");
								SDeserializerPluginConfiguration deserializer = client.getServiceInterface().getSuggestedDeserializerForExtension("ifc", project.getOid());
								client.checkin(project.getOid(), "Automatic checkin", deserializer.getOid(), false, Flow.SYNC, regularFile);
							} catch (ServerException e) {
								e.printStackTrace();
							} catch (UserException e) {
								e.printStackTrace();
							} catch (PublicInterfaceNotFoundException e) {
								e.printStackTrace();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					});
				}
				regularStream = Files.newDirectoryStream(ifc2x3tc1path);
				for (Path regularFile : regularStream) {
					executorService.submit(new Runnable(){
						@Override
						public void run() {
							try {
								SProject project = client.getServiceInterface().addProject(regularFile.getFileName().toString(), "ifc2x3tc1");
								SDeserializerPluginConfiguration deserializer = client.getServiceInterface().getSuggestedDeserializerForExtension("ifc", project.getOid());
								client.checkin(project.getOid(), "Automatic checkin", deserializer.getOid(), false, Flow.SYNC, regularFile);
							} catch (ServerException e) {
								e.printStackTrace();
							} catch (UserException e) {
								e.printStackTrace();
							} catch (PublicInterfaceNotFoundException e) {
								e.printStackTrace();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					});
				}
				executorService.shutdown();
				executorService.awaitTermination(1, TimeUnit.HOURS);
			}
		} catch (BimServerClientException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
