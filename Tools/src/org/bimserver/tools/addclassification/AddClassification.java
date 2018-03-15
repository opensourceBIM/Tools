package org.bimserver.tools.addclassification;

/******************************************************************************
 * Copyright (C) 2009-2018  BIMserver.org
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bimserver.LocalDevPluginLoader;
import org.bimserver.emf.IfcModelInterface;
import org.bimserver.emf.IfcModelInterfaceException;
import org.bimserver.emf.MetaDataManager;
import org.bimserver.emf.OidProvider;
import org.bimserver.emf.PackageMetaData;
import org.bimserver.emf.Schema;
import org.bimserver.ifc.step.deserializer.Ifc2x3tc1StepDeserializer;
import org.bimserver.ifc.step.serializer.Ifc2x3tc1StepSerializer;
import org.bimserver.models.ifc2x3tc1.Ifc2x3tc1Package;
import org.bimserver.models.ifc2x3tc1.IfcClassificationNotationSelect;
import org.bimserver.models.ifc2x3tc1.IfcClassificationReference;
import org.bimserver.models.ifc2x3tc1.IfcRelAssociatesClassification;
import org.bimserver.models.ifc2x3tc1.IfcSpace;
import org.bimserver.plugins.PluginConfiguration;
import org.bimserver.plugins.PluginManager;
import org.bimserver.plugins.deserializers.DeserializeException;
import org.bimserver.plugins.deserializers.Deserializer;
import org.bimserver.plugins.serializers.Serializer;
import org.bimserver.plugins.serializers.SerializerException;
import org.bimserver.shared.IncrementingOidProvider;
import org.bimserver.shared.exceptions.PluginException;
import org.bimserver.utils.IfcUtils;
import org.eclipse.emf.ecore.EClass;

public class AddClassification {
	private final Map<String, String> classifications = new HashMap<>();

	public static void main(String[] args) {
		new AddClassification().start();
	}

	private void start() {
		classifications.put("01", "13-65 23 00");
		classifications.put("02", "13-65 23 00");
		classifications.put("5", "13-65 23 00");
		classifications.put("6", "13-65 23 00");
		classifications.put("7", "13-65 23 00");
//		classifications.put("8", "13-65 23 00");
		classifications.put("9", "13-65 23 00");
		classifications.put("10", "13-65 23 00");
		classifications.put("11", "13-65 23 00");
		classifications.put("12", "13-65 23 00");
		classifications.put("13", "13-65 23 00");
//		classifications.put("15", "13-65 23 00");
		classifications.put("16", "13-65 23 00");
		classifications.put("18", "13-65 23 00");
		
		File file = new File("D:\\Dropbox\\Shared\\Singapore Code Compliance Share\\2017 04 24 01 Combined\\v3\\007_parking_v2.ifc");

		try {
			Path tmp = Paths.get("tmp");
			if (!Files.exists(tmp)) {
				Files.createDirectories(tmp);
			}
			MetaDataManager metaDataManager = new MetaDataManager(tmp);
			metaDataManager.addEPackage(Ifc2x3tc1Package.eINSTANCE, Schema.IFC2X3TC1);
			PackageMetaData packageMetaData = metaDataManager.getPackageMetaData("ifc2x3tc1");

			Deserializer deserializer = new Ifc2x3tc1StepDeserializer();
			deserializer.init(packageMetaData);
			IfcModelInterface model = deserializer.read(new FileInputStream(file), file.getName(), file.length(), null);

			addClassifications(model);

			Serializer serializer = new Ifc2x3tc1StepSerializer(new PluginConfiguration());
			serializer.init(model, null, false);

			model.generateMinimalExpressIds();
			
			File outputFile = new File("out.ifc");
			serializer.writeToOutputStream(new FileOutputStream(outputFile), null);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (DeserializeException e) {
			e.printStackTrace();
		} catch (SerializerException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (IfcModelInterfaceException e) {
			e.printStackTrace();
		}
	}

	private void addClassifications(IfcModelInterface model) throws IfcModelInterfaceException {
		OidProvider oidProvider = new IncrementingOidProvider(model.getHighestOid() + 1);
		for (IfcSpace ifcSpace : model.getAll(IfcSpace.class)) {
			List<IfcClassificationNotationSelect> classifications = IfcUtils.getClassifications(ifcSpace, model);
			if (classifications.isEmpty()) {
				System.out.println("Adding classification to " + ifcSpace.getName());
				
				EClass ifcRelAssociatesClassification2 = Ifc2x3tc1Package.eINSTANCE.getIfcRelAssociatesClassification();
				EClass refClass = Ifc2x3tc1Package.eINSTANCE.getIfcClassificationReference();
				IfcRelAssociatesClassification ifcRelAssociatesClassification = model.createAndAdd(ifcRelAssociatesClassification2, oidProvider.newOid(ifcRelAssociatesClassification2));
				ifcRelAssociatesClassification.getRelatedObjects().add(ifcSpace);
				
				IfcClassificationReference ifcClassificationNotationSelect = model.createAndAdd(refClass, oidProvider.newOid(refClass));
				ifcClassificationNotationSelect.setItemReference(this.classifications.get(ifcSpace.getName()));
				ifcRelAssociatesClassification.setRelatingClassification(ifcClassificationNotationSelect);
			}
		}
	}
}
