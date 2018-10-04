package org.bimserver.tools.ifcloader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.bimserver.client.BimServerClient;
import org.bimserver.client.json.JsonBimServerClientFactory;
import org.bimserver.interfaces.objects.SDeserializerPluginConfiguration;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.plugins.services.Flow;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.shared.exceptions.BimServerClientException;
import org.bimserver.shared.exceptions.PublicInterfaceNotFoundException;
import org.bimserver.shared.exceptions.ServerException;
import org.bimserver.shared.exceptions.ServiceException;
import org.bimserver.shared.exceptions.UserException;

import com.google.common.base.Charsets;

public class BulkLoader {
	public static void main(String[] args) {
		new BulkLoader().start();
	}

	private byte[] extractHead(Path path) {
		try (InputStream inputStream = Files.newInputStream(path)) {
			byte[] buffer = new byte[(int) Math.min(2048, Files.size(path))];
			IOUtils.readFully(inputStream, buffer);
			return buffer;
		} catch (IOException e) {
			return null;
		}
	}
	
	private void start() {
		Path basePath = Paths.get("E:\\org");
		Path bulkPath = basePath.resolve("bulk");
		Path regularPath = basePath.resolve("single");
		try (JsonBimServerClientFactory factory = new JsonBimServerClientFactory("http://localhost:8080")) {
			ExecutorService executorService = new ThreadPoolExecutor(16, 16, 1, TimeUnit.HOURS, new ArrayBlockingQueue<>(10000));
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
				if (Files.exists(regularPath)) {
					DirectoryStream<Path> regularStream = Files.newDirectoryStream(regularPath);
					for (Path regularFile : regularStream) {
						executorService.submit(new Runnable(){
							@Override
							public void run() {
								try {
									String filename = regularFile.getFileName().toString();
									String schema = client.getServiceInterface().determineIfcVersion(extractHead(regularFile), filename.toLowerCase().endsWith(".ifczip"));
									SProject project = client.getServiceInterface().addProject(filename, schema);
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
								} catch (ServiceException e) {
									e.printStackTrace();
								}
							}
						});
					}					
				}
				executorService.shutdown();
				executorService.awaitTermination(24, TimeUnit.HOURS);
			}
		} catch (BimServerClientException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
