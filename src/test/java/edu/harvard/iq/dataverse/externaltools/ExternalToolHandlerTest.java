package edu.harvard.iq.dataverse.externaltools;

import edu.harvard.iq.dataverse.DataFile;
import edu.harvard.iq.dataverse.authorization.users.ApiToken;
import javax.json.Json;
import javax.json.JsonObject;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ExternalToolHandlerTest {

    @Test
    public void testToJson() {
        System.out.println("toJson");
        ExternalTool externalTool = new ExternalTool("displayName", "description", "toolUrl", "{}");
        externalTool.setId(42l);
        DataFile dataFile = new DataFile();
        ApiToken apiToken = new ApiToken();
        ExternalToolHandler externalToolHandler = new ExternalToolHandler(externalTool, dataFile, apiToken);
        JsonObject json = externalToolHandler.toJson().build();
        System.out.println("JSON: " + json);
        assertEquals("displayName", json.getString("displayName"));

    }

    // TODO: It would probably be better to split these into individual tests.
    @Test
    public void testGetToolUrlWithOptionalQueryParameters() {
        String toolUrl = "http://example.com";
        ExternalTool externalTool = new ExternalTool("displayName", "description", toolUrl, "{}");

        // One query parameter.
        externalTool.setToolParameters(Json.createObjectBuilder()
                .add("queryParameters", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("key1", "value1")
                        )
                )
                .build().toString());
        DataFile nullDataFile = null;
        ApiToken nullApiToken = null;
        ExternalToolHandler externalToolHandler1 = new ExternalToolHandler(externalTool, nullDataFile, nullApiToken);
        String result1 = externalToolHandler1.getQueryParametersForUrl();
        System.out.println("result1: " + result1);
        assertEquals("?key1=value1", result1);

        // Two query parameters.
        externalTool.setToolParameters(Json.createObjectBuilder()
                .add("queryParameters", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("key1", "value1")
                        )
                        .add(Json.createObjectBuilder()
                                .add("key2", "value2")
                        )
                )
                .build().toString());
        ExternalToolHandler externalToolHandler2 = new ExternalToolHandler(externalTool, nullDataFile, nullApiToken);
        String result2 = externalToolHandler2.getQueryParametersForUrl();
        System.out.println("result2: " + result2);
        assertEquals("?key1=value1&key2=value2", result2);

        // Two query parameters, both reserved words
        externalTool.setToolParameters(Json.createObjectBuilder()
                .add("queryParameters", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("key1", "{fileId}")
                        )
                        .add(Json.createObjectBuilder()
                                .add("key2", "{apiToken}")
                        )
                )
                .build().toString());
        DataFile dataFile = new DataFile();
        dataFile.setId(42l);
        ApiToken apiToken = new ApiToken();
        apiToken.setTokenString("7196b5ce-f200-4286-8809-03ffdbc255d7");
        ExternalToolHandler externalToolHandler3 = new ExternalToolHandler(externalTool, dataFile, apiToken);
        String result3 = externalToolHandler3.getQueryParametersForUrl();
        System.out.println("result3: " + result3);
        assertEquals("?key1=42&key2=7196b5ce-f200-4286-8809-03ffdbc255d7", result3);

        // Two query parameters, both reserved words, no apiToken
        externalTool.setToolParameters(Json.createObjectBuilder()
                .add("queryParameters", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("key1", "{fileId}")
                        )
                        .add(Json.createObjectBuilder()
                                .add("key2", "{apiToken}")
                        )
                )
                .build().toString());
        ExternalToolHandler externalToolHandler4 = new ExternalToolHandler(externalTool, dataFile, nullApiToken);
        String result4 = externalToolHandler4.getQueryParametersForUrl();
        System.out.println("result4: " + result4);
        assertEquals("?key1=42&key2=null", result4);

        // Two query parameters, attempt to use a reserved word that doesn't exist.
        externalTool.setToolParameters(Json.createObjectBuilder()
                .add("queryParameters", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("key1", "{siteUrl}")
                        )
                        .add(Json.createObjectBuilder()
                                .add("key2", "{apiToken}")
                        )
                )
                .build().toString());
        ExternalToolHandler externalToolHandler5 = new ExternalToolHandler(externalTool, dataFile, nullApiToken);
        String result5 = externalToolHandler5.getQueryParametersForUrl();
        System.out.println("result5: " + result5);
        assertEquals("?key1={siteUrl}&key2=null", result5);

    }

}
