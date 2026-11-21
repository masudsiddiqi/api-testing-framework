package com.api.schemaPathConstants;


//=======================================================================================================
//SCHEMA PATHS: MANUAL FILES
//=======================================================================================================
//The schema files behind these paths are written by hand (in src/test/resources).
//The generator does not touch them.
//Use a schema from the OpenAPI-generated class (GeneratedSchemaPathConstants) when the document has it.
//Use this class only when the document has no schema or is too loose.
//=======================================================================================================

public class ManualSchemaPathConstants {

	private ManualSchemaPathConstants() {}

	// The folder of the success schemas. Written once, so a folder change is made in one place.
	// The path has no leading slash because the folder is on the classpath.
	private static final String SUCCESS_SCHEMA_DIR = "manualSchemas/responseSchemas/successSchema/";
	
	private static final String ERROR_SCHEMA_DIR ="manualSchemas/responseSchemas/errorSchema/";

	// Error schema: one shared schema, the same for all operations.
	// So there is ONE constant, not one per operation.
	// A response whose body is not defined in the contract skips the schema validation.
	// Such a response is usually made by the server or by a gateway.
	// Examples: a 413 from a gateway, or a 404 and a 405 from the routing layer of the framework
	// (in Spring, the DispatcherServlet).
	public static final String ERROR_RESPONSE_SCHEMA = ERROR_SCHEMA_DIR + "error-response-schema.json";

	// Success schemas: one per response shape. Operations with the same response shape share one constant.
	// Example: GET and POST of a resource that both return one item use the same constant.

	public static final String AUTH_SUCCESS_SCHEMA = SUCCESS_SCHEMA_DIR + "authSuccessSchema.json";

	public static final String USER_SUCCESS_SCHEMA = SUCCESS_SCHEMA_DIR + "userSuccessSchema.json";

	public static final String USER_PAGE_SUCCESS_SCHEMA = SUCCESS_SCHEMA_DIR + "userPageSuccessSchema.json";

	public static final String PRODUCT_SUCCESS_SCHEMA = SUCCESS_SCHEMA_DIR + "productSuccessSchema.json";

	public static final String PRODUCT_PAGE_SUCCESS_SCHEMA = SUCCESS_SCHEMA_DIR + "productPageSuccessSchema.json";

	public static final String ORDER_SUCCESS_SCHEMA = SUCCESS_SCHEMA_DIR + "orderSuccessSchema.json";

	public static final String ORDER_PAGE_SUCCESS_SCHEMA = SUCCESS_SCHEMA_DIR + "orderPageSuccessSchema.json";

	public static final String CART_SUCCESS_SCHEMA = SUCCESS_SCHEMA_DIR + "cartSuccessSchema.json";
}
