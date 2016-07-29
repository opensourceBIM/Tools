package org.bimserver.tools.massservice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import org.bimserver.client.BimServerClient;
import org.bimserver.client.json.JsonBimServerClientFactory;
import org.bimserver.interfaces.objects.SDeserializerPluginConfiguration;
import org.bimserver.interfaces.objects.SExtendedData;
import org.bimserver.interfaces.objects.SInternalServicePluginConfiguration;
import org.bimserver.interfaces.objects.SProfileDescriptor;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.interfaces.objects.SService;
import org.bimserver.interfaces.objects.SServiceDescriptor;
import org.bimserver.plugins.services.Flow;
import org.bimserver.shared.ChannelConnectionException;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.shared.exceptions.BimServerClientException;
import org.bimserver.shared.exceptions.PublicInterfaceNotFoundException;
import org.bimserver.shared.exceptions.ServerException;
import org.bimserver.shared.exceptions.ServiceException;
import org.bimserver.shared.exceptions.UserException;
import org.bimserver.utils.PathUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jxl.CellView;
import jxl.Workbook;
import jxl.WorkbookSettings;
import jxl.format.Colour;
import jxl.write.Label;
import jxl.write.WritableCellFormat;
import jxl.write.WritableFont;
import jxl.write.WritableSheet;
import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import jxl.write.biff.RowsExceededException;

public class MassServiceCaller {
	private BimServerClient client;
	private WritableCellFormat times;
	private WritableCellFormat timesbold;
	private WritableCellFormat error;
	private WritableCellFormat ok;
	private ByteArrayOutputStream byteArrayOutputStream;
	private WritableWorkbook workbook;
	private WritableSheet sheet;
	private int row;

	private String[] checks = new String[]{
		"PROJECT___ONLY_ONE_IFC_PROJECT",
		"SITE___ONLY_ONE_SITE",
		"UNITS___LENGTH",
		"BUILDINGSTOREYS___ALL_OBJECTS_IN_BUILDING_STOREY",
		"BUILDING___AT_LEAST_ONE_BUILDING",
		"BUILDING_STOREY___AT_LEAST_ONE_BUILDING_STOREY",
		"BUILDINGSTOREYS___BUILDING_STOREY_NAMES_AND_Z_ORDER",
		"UNITS___LENGTH",
		"UNITS___AREA",
		"UNITS___VOLUME",
		"REPRESENTATION___HAS_TRUE_NORTH_SET",
		"SITE___ELEVATION",
		"SITE___LATITUDE",
		"SITE___LONGITUDE",
		"SITE___KADASTRALE_AANDUIDING",
	};
	
	public static void main(String[] args) {
		new MassServiceCaller().start();
	}
	
	private void start() {
		try {
			JsonBimServerClientFactory factory = new JsonBimServerClientFactory("http://localhost:8080");
			client = factory.create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"));
			
	    	WorkbookSettings wbSettings = new WorkbookSettings();
	    	
	    	wbSettings.setLocale(new Locale("en", "EN"));
	    	
	    	WritableFont times10pt = new WritableFont(WritableFont.ARIAL, 10);
	    	times = new WritableCellFormat(times10pt);
	    	
	    	WritableFont times10ptbold = new WritableFont(WritableFont.ARIAL, 10);
			times10ptbold.setBoldStyle(WritableFont.BOLD);
			timesbold = new WritableCellFormat(times10ptbold);
			
			error = new WritableCellFormat(times10pt);
			error.setBackground(Colour.RED);

			ok = new WritableCellFormat(times10pt);
			ok.setBackground(Colour.LIGHT_GREEN);
			
			byteArrayOutputStream = new ByteArrayOutputStream();
			workbook = Workbook.createWorkbook(byteArrayOutputStream, wbSettings);
			
			Path[] paths = new Path[]{
				Paths.get("D:\\Dropbox\\Shared\\IFC files\\Hendriks en partners aspectmodellen\\Leveranciers"),
				Paths.get("D:\\Dropbox\\Shared\\IFC files\\HHS woning met toeleveranciers"),
				Paths.get("D:\\Dropbox\\Shared\\IFC files\\ZEEP en partners aspectmodellen"),
				Paths.get("D:\\Dropbox\\Shared\\IFC files\\de Nijs (pontsteiger amsterdam)"),
				Paths.get("D:\\Dropbox\\Shared\\IFC files\\HAGA ziekenhuis"),
				Paths.get("D:\\Dropbox\\Shared\\IFC files public\\Dataset Schependomlaan\\Coordination model and subcontractors models\\BIMsight Projectdata1")
			};
			
			for (Path path : paths) {
				System.out.println(path.getFileName().toString());
				if (sheet != null) {
					for (int x = 0; x < 6; x++) {
						CellView cell = sheet.getColumnView(x);
						cell.setAutosize(true);
						sheet.setColumnView(x, cell);
					}
				}
				sheet = workbook.createSheet(path.getFileName().toString(), 0);
				
				int col = 0;
				for (String check : checks) {
					sheet.addCell(new Label(col++, 0, check, timesbold));
				}
				
				row = 2;
				for (Path file : PathUtils.list(path)) {
					String projectName = file.getFileName().toString();
					if (projectName.contains(".")) {
						String extension = projectName.substring(projectName.lastIndexOf(".") + 1);
						if (extension.toUpperCase().endsWith("IFC") || extension.toUpperCase().endsWith("IFCZIP") || extension.toUpperCase().endsWith("IFCXML")) {
							System.out.println("\t" + file.toAbsolutePath().toString());
							SProject project = null;
							List<SProject> projects = client.getServiceInterface().getProjectsByName(projectName);
							if (projects.size() == 1) {
								project = projects.get(0);
								
								List<Long> services = project.getServices();
								if (services.size() == 1) {
									Long serviceOid = project.getServices().get(0);
									if (project.getLastRevisionId() == -1) {
										System.out.println("Probably invalid IFC");
//										checkin(project, extension, file);
									} else {
										client.getServiceInterface().triggerNewRevision(project.getLastRevisionId(), serviceOid);
										waitForResults(file, project.getLastRevisionId());
									}
								} else {
									addService(project, file, extension);
								}
							} else {
								createProject(projectName, file, extension);
							}
						}
					}
				}
			}
			
			workbook.write();
			try {
				workbook.close();
			} catch (WriteException e) {
				throw new IOException(e);
			}
			
			Files.write(Paths.get("excel.xlsx"), byteArrayOutputStream.toByteArray());
			
			client.close();
		} catch (BimServerClientException e) {
			e.printStackTrace();
		} catch (ServiceException e) {
			e.printStackTrace();
		} catch (ChannelConnectionException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (PublicInterfaceNotFoundException e) {
			e.printStackTrace();
		} catch (WriteException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}

	private void checkin(SProject project, String extension, Path file) throws UserException, ServerException, IOException, PublicInterfaceNotFoundException {
		SDeserializerPluginConfiguration deserializer = client.getServiceInterface().getSuggestedDeserializerForExtension(extension, project.getOid());
		client.checkin(project.getOid(), "Test", deserializer.getOid(), false, Flow.SYNC, file);
		project = client.getServiceInterface().getProjectByPoid(project.getOid());
		
		if (project.getLastRevisionId() == -1) {
			System.out.println("Probably invalid IFC");
		} else {
			waitForResults(file, project.getLastRevisionId());
		}
	}

	public void waitForResults(Path file, long roid) throws ServerException, UserException, PublicInterfaceNotFoundException, IOException {
		try {
			for (int i = 0; i < 25; i++) {
				List<SExtendedData> allExtendedDataOfRevision = client.getServiceInterface().getAllExtendedDataOfRevision(roid);
				if (allExtendedDataOfRevision.size() > 0) {
					SExtendedData extendedData = allExtendedDataOfRevision.get(0);
					// Path outputFile =
					// file.getParent().resolve(file.getFileName().toString() +
					// ".xlsx");
					// System.out.println("Writing to " +
					// outputFile.getFileName().toString());
					ByteArrayOutputStream bytes = new ByteArrayOutputStream();
					client.downloadExtendedData(extendedData.getOid(), bytes);
					ObjectMapper objectMapper = new ObjectMapper();
					ObjectNode results = objectMapper.readValue(bytes.toByteArray(), ObjectNode.class);
					
					ObjectNode checks = (ObjectNode) results.get("checks");

					sheet.addCell(new Label(0, row, file.getFileName().toString(), times));
					
					int col = 1;
					for (String check : this.checks) {
						if (checks.has(check)) {
							boolean value = checks.get(check).asBoolean();
							sheet.addCell(new Label(col++, row, value ? "1" : "0", value ? ok : error));
						} else {
							sheet.addCell(new Label(col++, row, "?", times));
						}
					}

					row++;
					return;
				}
				Thread.sleep(2000);
			}
			System.out.println("Waited too long, stopping");
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (RowsExceededException e) {
			e.printStackTrace();
		} catch (WriteException e) {
			e.printStackTrace();
		}
	}
	
	public void createProject(String projectName, Path file, String extension) throws ServerException, UserException, PublicInterfaceNotFoundException, IOException {
		SProject project = client.getServiceInterface().addProject(projectName, "ifc2x3tc1");
		
		addService(project, file, extension);
	}

	private void addService(SProject project, Path file, String extension) throws ServerException, UserException, PublicInterfaceNotFoundException, IOException {
		for (SInternalServicePluginConfiguration internalServicePluginConfiguration : client.getPluginInterface().getAllInternalServices(true)) {
			if (internalServicePluginConfiguration.getName().equals("IFC Validator")) {
				String serviceIdentifier = "" + internalServicePluginConfiguration.getOid();
				SServiceDescriptor serviceDescriptor = client.getServiceInterface().getServiceDescriptor("http://localhost:8080", serviceIdentifier);
				List<SProfileDescriptor> profiles = client.getServiceInterface().getAllLocalProfiles(serviceIdentifier);
				SProfileDescriptor profile = profiles.get(0);
				
				SService sService = new SService();
				sService.setName(serviceDescriptor.getName());
				sService.setProviderName(serviceDescriptor.getProviderName());
				sService.setServiceIdentifier(serviceDescriptor.getIdentifier());
				sService.setServiceName(serviceDescriptor.getName());
				sService.setUrl(serviceDescriptor.getUrl());
				sService.setToken(client.getToken());
				sService.setNotificationProtocol(serviceDescriptor.getNotificationProtocol());
				sService.setDescription(serviceDescriptor.getDescription());
				sService.setTrigger(serviceDescriptor.getTrigger());
				sService.setProfileIdentifier(profile.getIdentifier());
				sService.setProfileName(profile.getName());
				sService.setProfileDescription(profile.getDescription());
				sService.setProfilePublic(profile.isPublicProfile());
				sService.setReadRevision(serviceDescriptor.isReadRevision());
				sService.setReadExtendedDataId(-1);
				sService.setWriteRevisionId(-1);
				sService.setWriteExtendedDataId(client.getServiceInterface().getExtendedDataSchemaByNamespace(serviceDescriptor.getWriteExtendedData()).getOid());
				
				client.getServiceInterface().addServiceToProject(project.getOid(), sService);
				
				SDeserializerPluginConfiguration deserializer = client.getServiceInterface().getSuggestedDeserializerForExtension(extension, project.getOid());
				client.checkin(project.getOid(), "Test", deserializer.getOid(), false, Flow.SYNC, file);
				project = client.getServiceInterface().getProjectByPoid(project.getOid());
				
				waitForResults(file, project.getLastRevisionId());
			}
		}
	}
}
