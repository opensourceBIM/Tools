package org.bimserver.examinegeometry;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.bimserver.client.BimServerClient;
import org.bimserver.client.json.JsonBimServerClientFactory;
import org.bimserver.interfaces.objects.SExtendedData;
import org.bimserver.interfaces.objects.SExtendedDataSchema;
import org.bimserver.interfaces.objects.SProject;
import org.bimserver.interfaces.objects.SProjectSmall;
import org.bimserver.shared.UsernamePasswordAuthenticationInfo;
import org.bimserver.shared.exceptions.BimServerClientException;
import org.bimserver.utils.Formatters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class GeometryExaminar {
	public GeometryExaminar(String address, String username, String password, String projectName) {
		try (JsonBimServerClientFactory factory = new JsonBimServerClientFactory(address)) {
			try (BimServerClient client = factory.create(new UsernamePasswordAuthenticationInfo(username, password))) {
				SProject project = client.getServiceInterface().getTopLevelProjectByName(projectName);
				SExtendedDataSchema extendedDataSchema = client.getServiceInterface().getExtendedDataSchemaByName("GEOMETRY_GENERATION_REPORT_JSON_1_1");
				for (SProjectSmall sProjectSmall : client.getServiceInterface().getAllRelatedProjects(project.getOid())) {
					if (sProjectSmall.getLastRevisionId() != -1) {
						for (SExtendedData sExtendedData : client.getServiceInterface().getAllExtendedDataOfRevisionAndSchema(sProjectSmall.getLastRevisionId(), extendedDataSchema.getOid())) {
							ByteArrayOutputStream baos = new ByteArrayOutputStream();
							client.downloadExtendedData(sExtendedData.getOid(), baos);
							ObjectNode objectNode = new ObjectMapper().readValue(baos.toByteArray(), ObjectNode.class);
							System.out.println(objectNode.get("ifcModel").get("filename").asText());
							ArrayNode jobsNode = (ArrayNode)objectNode.get("jobs");
							for (JsonNode jobNode : jobsNode) {
								long nanos = jobNode.get("totalTimeNanos").asLong();
								long ms = nanos / 1000000;
								if (ms > 60000) {
									System.out.println("\t" + jobNode.get("mainType").asText() + " (" + Formatters.formatNanoSeconds(nanos) + ")");
								}
							}
						}
					}
				}
			}
		} catch (BimServerClientException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		new GeometryExaminar(args[0], args[1], args[2], args[3]);
	}
}
