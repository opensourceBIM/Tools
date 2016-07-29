package org.bimserver.tools.findinspace;

import java.io.File;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;

import org.bimserver.client.BimServerClient;
import org.bimserver.client.ClientIfcModel;
import org.bimserver.client.json.JsonBimServerClientFactory;
import org.bimserver.database.queries.om.InBoundingBox;
import org.bimserver.database.queries.om.Query;
import org.bimserver.database.queries.om.QueryPart;
import org.bimserver.emf.IdEObject;
import org.bimserver.geometry.AxisAlignedBoundingBox;
import org.bimserver.ifc.IfcModelChangeListener;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.models.geometry.GeometryInfo;
import org.bimserver.models.ifc2x3tc1.Ifc2x3tc1Package;
import org.bimserver.models.ifc2x3tc1.IfcProduct;
import org.bimserver.models.ifc2x3tc1.IfcSpace;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.shared.exceptions.BimServerClientException;

import com.opencsv.CSVWriter;

public class FindInSpaceV1 {
	public static void main(String[] args) {
		new FindInSpaceV1().start();
	}

	private void start() {
		try (JsonBimServerClientFactory factory = new JsonBimServerClientFactory("http://localhost:8080")) {
			try (BimServerClient client = factory.create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"))) {
				try (CSVWriter csvWriter = new CSVWriter(new FileWriter(new File("spacecontent.csv")))) {
					csvWriter.writeNext(new String[]{"", "Type", "GUID", "Name"});
					csvWriter.writeNext(new String[]{"", "", "", ""});

					SProject spacesProject = client.getServiceInterface().getTopLevelProjectByName("Spaces");
					SProject objectsProject = client.getServiceInterface().getTopLevelProjectByName("Installations");

					ClientIfcModel spacesModel = client.getModel(spacesProject, spacesProject.getLastRevisionId(), true, false, true);
					ClientIfcModel objectsModel = client.getModel(objectsProject, objectsProject.getLastRevisionId(), false, false, true);

					Set<String> foundGuids = new HashSet<>();
					
					for (IfcSpace ifcSpace : spacesModel.getAll(IfcSpace.class)) {
						csvWriter.writeNext(new String[]{"Container", "IfcSpace", ifcSpace.getGlobalId(), ifcSpace.getName()});
						
						GeometryInfo geometryInfo = ifcSpace.getGeometry();
						if (geometryInfo != null) {
							AxisAlignedBoundingBox boundingBox = new AxisAlignedBoundingBox(geometryInfo.getMinBounds(), geometryInfo.getMaxBounds());
//							boundingBox.enlarge(0.1f);

							Query query = new Query(objectsModel.getPackageMetaData());
							QueryPart part = query.createQueryPart();
							part.addType(Ifc2x3tc1Package.eINSTANCE.getIfcProduct(), true);
							part.setInBoundingBox(new InBoundingBox(boundingBox));
							
							objectsModel.queryNew(query, new IfcModelChangeListener() {
								@Override
								public void objectAdded(IdEObject idEObject) {
									if (idEObject instanceof IfcProduct) {
										IfcProduct ifcProduct = (IfcProduct)idEObject;
										csvWriter.writeNext(new String[]{"", ifcProduct.eClass().getName(), ifcProduct.getGlobalId(), ifcProduct.getName()});
										if (foundGuids.contains(ifcProduct.getGlobalId())) {
											System.out.println("Double GUID: " + ifcProduct.getGlobalId());
										}
										foundGuids.add(ifcProduct.getGlobalId());
									}
								}
							});
						} else {
							System.out.println("Missing geometry for IfcSpace " + ifcSpace.getGlobalId());
						}
						csvWriter.writeNext(new String[]{"", "", "", ""});
					}
				}
			}
		} catch (BimServerClientException e) {
			e.printStackTrace();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}
}
