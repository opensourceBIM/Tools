package org.bimserver.tools.ifcloader;

/******************************************************************************
 * Copyright (C) 2009-2019  BIMserver.org
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
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.shared.exceptions.BimServerClientException;
import org.bimserver.shared.exceptions.PublicInterfaceNotFoundException;
import org.bimserver.shared.exceptions.ServerException;
import org.bimserver.shared.exceptions.UserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BulkLoader {
	private static final Logger LOGGER = LoggerFactory.getLogger(BulkLoader.class);
	
	public static void main(String[] args) {
		new BulkLoader().start();
	}

	private byte[] extractHead(Path path) {
		try (InputStream inputStream = Files.newInputStream(path)) {
			byte[] buffer = new byte[(int) Math.min(4096, Files.size(path))];
			IOUtils.readFully(inputStream, buffer);
			return buffer;
		} catch (IOException e) {
			return null;
		}
	}
	
	private void start() {
		Path basePath = Paths.get("/home/ruben/backup/ifcfilesorganized");
		Path bulkPath = basePath.resolve("bulk");
		Path regularPath = basePath.resolve("single");
		try (JsonBimServerClientFactory factory = new JsonBimServerClientFactory("http://localhost:8080")) {
			ExecutorService executorService = new ThreadPoolExecutor(1, 1, 1, TimeUnit.HOURS, new ArrayBlockingQueue<>(10000));
			try (BimServerClient client = factory.create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"))) {
				if (Files.exists(bulkPath)) {
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
				}
				if (Files.exists(regularPath)) {
					DirectoryStream<Path> regularStream = Files.newDirectoryStream(regularPath);
					for (Path regularFile : regularStream) {
						executorService.submit(new Runnable(){
							@Override
							public void run() {
								String filename = regularFile.getFileName().toString().toLowerCase();
								try {
									if (filename.endsWith(".ifc") || filename.endsWith(".ifczip")) {
										String schema = client.getServiceInterface().determineIfcVersion(extractHead(regularFile), filename.toLowerCase().endsWith(".ifczip"));
										SProject project = client.getServiceInterface().addProject(filename, schema);
										SDeserializerPluginConfiguration deserializer = client.getServiceInterface().getSuggestedDeserializerForExtension("ifc", project.getOid());
										client.checkinSync(project.getOid(), "Automatic checkin", deserializer.getOid(), false, regularFile);
									} else {
										LOGGER.info("Skipping " + filename);
									}
								} catch (Exception e) {
									LOGGER.error(filename, e);
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
