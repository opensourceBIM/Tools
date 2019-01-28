package org.bimserver.tools.findinspace;

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

import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.bimserver.client.BimServerClient;
import org.bimserver.client.ClientIfcModel;
import org.bimserver.client.json.JsonBimServerClientFactory;
import org.bimserver.geometry.Matrix;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.models.geometry.GeometryData;
import org.bimserver.models.geometry.GeometryInfo;
import org.bimserver.models.ifc2x3tc1.IfcArbitraryClosedProfileDef;
import org.bimserver.models.ifc2x3tc1.IfcArbitraryProfileDefWithVoids;
import org.bimserver.models.ifc2x3tc1.IfcAxis2Placement3D;
import org.bimserver.models.ifc2x3tc1.IfcCartesianPoint;
import org.bimserver.models.ifc2x3tc1.IfcCurve;
import org.bimserver.models.ifc2x3tc1.IfcExtrudedAreaSolid;
import org.bimserver.models.ifc2x3tc1.IfcPolyline;
import org.bimserver.models.ifc2x3tc1.IfcProduct;
import org.bimserver.models.ifc2x3tc1.IfcProductRepresentation;
import org.bimserver.models.ifc2x3tc1.IfcProfileDef;
import org.bimserver.models.ifc2x3tc1.IfcRectangleProfileDef;
import org.bimserver.models.ifc2x3tc1.IfcRepresentation;
import org.bimserver.models.ifc2x3tc1.IfcRepresentationItem;
import org.bimserver.models.ifc2x3tc1.IfcShapeRepresentation;
import org.bimserver.models.ifc2x3tc1.IfcSpace;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.shared.exceptions.BimServerClientException;
import org.bimserver.utils.GeometryUtils;
import org.bimserver.utils.IfcUtils;
import org.eclipse.emf.common.util.EList;

import com.opencsv.CSVWriter;

public class FindInSpaceV2 {
	private final Set<String> foundGuids = new HashSet<>();

	public static void main(String[] args) {
		new FindInSpaceV2().start();
	}

	private void addObject(IfcProduct ifcProduct, CSVWriter csvWriter) {
		String guid = ifcProduct.getGlobalId();
		if (foundGuids.contains(guid)) {
			System.out.println("Already added GUID " + guid);
		}
		foundGuids.add(guid);
		if (ifcProduct.getName().equals("Firesensor")) {
			Set<String> values = new HashSet<>();
			for (String propertyName : IfcUtils.listPropertyNames(ifcProduct)) {
				values.add(propertyName + "=" + IfcUtils.getStringProperty(ifcProduct, propertyName));
			}
			String[] line = new String[values.size() + 4];
			line[0] = "";
			line[1] = ifcProduct.eClass().getName();
			line[2] = ifcProduct.getGlobalId();
			line[3] = ifcProduct.getName();
			Iterator<String> iterator = values.iterator();
			for (int i=4; i<line.length; i++) {
				line[i] = iterator.next();
			}
			csvWriter.writeNext(line);
		}
	}

	private void start() {
		try (JsonBimServerClientFactory factory = new JsonBimServerClientFactory("http://localhost:8080")) {
			try (BimServerClient client = factory.create(new UsernamePasswordAuthenticationInfo("admin@bimserver.org", "admin"))) {
				try (CSVWriter csvWriter = new CSVWriter(new FileWriter(new File("spacecontent.csv")))) {
					csvWriter.writeNext(new String[] { "", "Type", "GUID", "Name" });
					csvWriter.writeNext(new String[] { "", "", "", "" });

					SProject spacesProject = client.getServiceInterface().getTopLevelProjectByName("Spaces");
					SProject objectsProject = client.getServiceInterface().getTopLevelProjectByName("Installations");

					ClientIfcModel spacesModel = client.getModel(spacesProject, spacesProject.getLastRevisionId(), true, false, true);
					ClientIfcModel objectsModel = client.getModel(objectsProject, objectsProject.getLastRevisionId(), true, false, true);

					for (IfcSpace ifcSpace : spacesModel.getAll(IfcSpace.class)) {
						csvWriter.writeNext(new String[] { "Container", "IfcSpace", ifcSpace.getGlobalId(), ifcSpace.getName(), getGeometryType(ifcSpace) });

						if (ifcSpace.getRepresentation() != null) {
							System.out.println(ifcSpace.getGlobalId());
							GeometryInfo spaceGeometry = ifcSpace.getGeometry();
//							double[] spaceMatrix = GeometryUtils.toDoubleArray(spaceGeometry.getTransformation());
							IfcProductRepresentation representation = ifcSpace.getRepresentation();
							for (IfcRepresentation ifcRepresentation : representation.getRepresentations()) {
								if (ifcRepresentation instanceof IfcShapeRepresentation) {
									IfcShapeRepresentation ifcShapeRepresentation = (IfcShapeRepresentation) ifcRepresentation;
									for (IfcRepresentationItem ifcRepresentationItem : ifcShapeRepresentation.getItems()) {
										if (ifcRepresentationItem instanceof IfcExtrudedAreaSolid) {
											IfcExtrudedAreaSolid ifcExtrudedAreaSolid = (IfcExtrudedAreaSolid) ifcRepresentationItem;
											IfcAxis2Placement3D position = ifcExtrudedAreaSolid.getPosition();
											IfcCartesianPoint location = position.getLocation();

											double[] minBoundsSemantic = new double[] { location.getCoordinates().get(0), location.getCoordinates().get(1) };

											IfcProfileDef ifcProfileDef = ifcExtrudedAreaSolid.getSweptArea();
											if (ifcProfileDef instanceof IfcArbitraryProfileDefWithVoids) {
												IfcArbitraryProfileDefWithVoids ifcArbitraryProfileDefWithVoids = (IfcArbitraryProfileDefWithVoids) ifcProfileDef;
												Path2D path2d = new Path2D.Float();
												IfcCurve outerCurve = ifcArbitraryProfileDefWithVoids.getOuterCurve();
												if (outerCurve instanceof IfcPolyline) {
													IfcPolyline ifcPolyline = (IfcPolyline) outerCurve;
													IfcCartesianPoint first = ifcPolyline.getPoints().get(0);
													path2d.moveTo(first.getCoordinates().get(0) + minBoundsSemantic[0], first.getCoordinates().get(1) + minBoundsSemantic[1]);
													for (IfcCartesianPoint cartesianPoint : ifcPolyline.getPoints()) {
														EList<Double> coords = cartesianPoint.getCoordinates();

														// This does nothing in
														// this model, identity
														// matrix
//														double[] result = new double[4];
//														Matrix.multiplyMV(result, 0, spaceMatrix, 0, new double[] { coords.get(0), coords.get(1), 0, 1 }, 0);

														path2d.lineTo(coords.get(0) + minBoundsSemantic[0], coords.get(1) + minBoundsSemantic[1]);
													}
													path2d.closePath();
												}
												Area area = new Area(path2d);

												checkWithPath(csvWriter, objectsModel, area, "IfcArbitraryProfileDefWithVoids");
											} else if (ifcProfileDef instanceof IfcRectangleProfileDef) {
												IfcRectangleProfileDef ifcRectangleProfileDef = (IfcRectangleProfileDef) ifcProfileDef;

												double[] offset = new double[] { minBoundsSemantic[0] + ifcRectangleProfileDef.getPosition().getLocation().getCoordinates().get(0),
														minBoundsSemantic[1] + ifcRectangleProfileDef.getPosition().getLocation().getCoordinates().get(1) };

												// System.out.println(minBounds.getX()
												// + "," + minBounds.getY() + ",
												// " + minBounds.getZ() + " " +
												// maxBounds.getX() + ", " +
												// maxBounds.getY() + ", " +
												// maxBounds.getZ());
												// System.out.println(minBoundsSemantic[0]
												// + "," + minBoundsSemantic[1]
												// + ", " + " " +
												// maxBoundsSemantic[0] + ", " +
												// maxBoundsSemantic[1]);
												// System.out.println();

												for (IfcProduct ifcProduct : objectsModel.getAllWithSubTypes(IfcProduct.class)) {
													if (ifcProduct.getGeometry() != null) {
														GeometryInfo geometry = ifcProduct.getGeometry();
														GeometryData data = geometry.getData();
														float[] vertices = GeometryUtils.toFloatArray(data.getVertices().getData());
														boolean allInside = true;
														double[] matrix = GeometryUtils.toDoubleArray(geometry.getTransformation());
														double[] result = new double[4];
														for (int i = 0; i < vertices.length; i += 3) {
															Matrix.multiplyMV(result, 0, matrix, 0, new double[] { vertices[i], vertices[i + 1], vertices[i + 2], 1 }, 0);
															if (result[0] > offset[0] && result[1] > offset[1] && result[0] < offset[1] + ifcRectangleProfileDef.getXDim()
																	&& result[1] < offset[1] + ifcRectangleProfileDef.getYDim()) {
																// Inside
															} else {
																allInside = false;
															}
														}
														if (allInside) {
															addObject(ifcProduct, csvWriter);
														}
													}
												}
											} else if (ifcProfileDef instanceof IfcArbitraryClosedProfileDef) {
												IfcArbitraryClosedProfileDef ifcArbitraryClosedProfileDef = (IfcArbitraryClosedProfileDef) ifcProfileDef;
												Path2D path2d = new Path2D.Float();
												IfcCurve outerCurve = ifcArbitraryClosedProfileDef.getOuterCurve();
												if (outerCurve instanceof IfcPolyline) {
													IfcPolyline ifcPolyline = (IfcPolyline) outerCurve;
													IfcCartesianPoint first = ifcPolyline.getPoints().get(0);
													path2d.moveTo(first.getCoordinates().get(0) + minBoundsSemantic[0], first.getCoordinates().get(1) + minBoundsSemantic[1]);
													for (IfcCartesianPoint cartesianPoint : ifcPolyline.getPoints()) {
														EList<Double> coords = cartesianPoint.getCoordinates();

//														double[] result = new double[4];
//														Matrix.multiplyMV(result, 0, spaceMatrix, 0, new double[] { coords.get(0), coords.get(1), 0, 1 }, 0);

														path2d.lineTo(coords.get(0) + minBoundsSemantic[0], coords.get(1) + minBoundsSemantic[1]);
													}
													path2d.closePath();
												}
												Area area = new Area(path2d);

												checkWithPath(csvWriter, objectsModel, area, "IfcArbitraryClosedProfileDef");
											} else {
												System.out.println("Unimplemented: " + ifcProfileDef);
											}
										}
									}
								}
							}
						}

						csvWriter.writeNext(new String[] { "", "", "", "" });
					}

					for (IfcProduct ifcProduct : objectsModel.getAllWithSubTypes(IfcProduct.class)) {
						if (!foundGuids.contains(ifcProduct.getGlobalId()) && ifcProduct.getName().equals("Firesensor")) {
							System.out.println("GUID not found: " + ifcProduct.getGlobalId());
						}
					}
				}
			}
		} catch (BimServerClientException e) {
			e.printStackTrace();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}

	private String getGeometryType(IfcSpace ifcSpace) {
		if (ifcSpace.getRepresentation() != null) {
			GeometryInfo spaceGeometry = ifcSpace.getGeometry();
			IfcProductRepresentation representation = ifcSpace.getRepresentation();
			for (IfcRepresentation ifcRepresentation : representation.getRepresentations()) {
				if (ifcRepresentation instanceof IfcShapeRepresentation) {
					IfcShapeRepresentation ifcShapeRepresentation = (IfcShapeRepresentation) ifcRepresentation;
					for (IfcRepresentationItem ifcRepresentationItem : ifcShapeRepresentation.getItems()) {
						if (ifcRepresentationItem instanceof IfcExtrudedAreaSolid) {
							IfcExtrudedAreaSolid ifcExtrudedAreaSolid = (IfcExtrudedAreaSolid)ifcRepresentationItem;
							IfcProfileDef ifcProfileDef = ifcExtrudedAreaSolid.getSweptArea();
							return "IfcExtrudedAreaSolid, " + ifcProfileDef.eClass().getName();
						}
					}
				}
			}
		}
		return "Unimplemented type";
	}

	private void checkWithPath(CSVWriter csvWriter, ClientIfcModel objectsModel, Area area, String geometryType) {
		for (IfcProduct ifcProduct : objectsModel.getAllWithSubTypes(IfcProduct.class)) {
			if (ifcProduct.getGeometry() != null) {
				GeometryInfo geometry = ifcProduct.getGeometry();
				GeometryData data = geometry.getData();
				float[] vertices = GeometryUtils.toFloatArray(data.getVertices().getData());
				boolean allInside = true;
				for (int i = 0; i < vertices.length; i += 3) {
					double[] result = new double[4];
					double[] matrix = GeometryUtils.toDoubleArray(geometry.getTransformation());
					Matrix.multiplyMV(result, 0, matrix, 0, new double[] { vertices[i], vertices[i + 1], vertices[i + 2], 1 }, 0);
					if (!area.contains(new Point2D.Double(result[0], result[1]))) {
						allInside = false;
						break;
					}
				}
				if (allInside) {
					addObject(ifcProduct, csvWriter);
				}
			}
		}
	}
}
