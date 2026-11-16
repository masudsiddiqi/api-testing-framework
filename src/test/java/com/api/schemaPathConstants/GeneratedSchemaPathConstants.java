package com.api.schemaPathConstants;

/**
 * SCHEMA PATHS: GENERATED FILES (ডিফল্ট)
 * ============================================================================
 * এই class-এর schema file গুলো হাতে লেখা হয় না।
 * Build-এর সময় OpenAPI document থেকে নিজে নিজে (dynamically) তৈরি হয়।
 *   - Source:    OpenAPI document (উদাহরণ: src/test/resources/openapi/api.yaml)।
 *   - Generator: OpenApiSchemaGenerator। Maven এটাকে test শুরুর আগে চালায়।
 *   - Output:    target/test-classes/schemas/generated/ (generator target/test-classes-এর ভেতরে
 *                "schemas/generated" folder তৈরি করে)।
 *
 * REQUEST ও RESPONSE দুই ধরনের schema-ই এখানে আসে:
 *   - Request schema:  client যে body পাঠায়। উদাহরণ: UserRegistration -> userRegistrationSchema.json
 *   - Response schema: server যে body ফেরত দেয়। উদাহরণ: Product -> productSchema.json
 * Generator components/schemas-এর সব schema-র জন্য file বানায়।
 * কোনটা request আর কোনটা response, সেটা file দেখে বোঝা যায় না।
 * OpenAPI-তে ব্যবহার দেখে বুঝতে হয় (নিচে)।
 *
  * OpenAPI document-এ schema কীভাবে খুঁজবে:
 *
 *   ১. Generator কী বানায়:
 *      Generator "components/schemas"-এর প্রতিটা schema থেকে একটা করে file বানায়।
 *      এই schema-গুলোর কোনটা request আর কোনটা response, সেটা সেখানে লেখা থাকে না।
 *
 *   ২. কোনটা request, কোনটা response, কীভাবে বুঝবে:
 *      "paths"-এর নিচে endpoint দেখো। সেখানে "$ref" একটা schema-র নাম দেখায়।
 *      $ref কোন অংশের নিচে আছে, তার ওপর নির্ভর করে সেটা request না response:
 *        - "requestBody"-এর নিচের $ref  -> request schema-র নাম
 *        - "responses"-এর নিচের $ref    -> response schema-র নাম
 *
 *      Request schema-র পথ:
 *        {@code paths -> <endpoint> -> <method> -> requestBody -> content -> application/json -> schema -> $ref}
 *      Response schema-র পথ:
 *        {@code paths -> <endpoint> -> <method> -> responses -> <status code> -> content -> application/json -> schema -> $ref}
 *
 *   ৩. $ref থেকে schema-র নাম ও সংজ্ঞা:
 *      $ref-এর মান "components/schemas"-এর ভেতরের একটা schema দেখায়। শেষ অংশটাই schema-র নাম।
 *      উদাহরণ: "#/components/schemas/Product" -> schema-র নাম Product।
 *      Editor-এ Ctrl চেপে $ref লিংকে ক্লিক করলে সরাসরি ওই schema-র সংজ্ঞায় (field, type, নিয়ম) যাওয়া যায়।
 *      (এটা editor-এর উপর নির্ভর করে। না হলে নাম ধরে "components/schemas"-এর ভেতরে খোঁজো।)
 *      
 *   - Swagger UI-তে: endpoint খুলে "Request body" আর "Responses"-এর "Schema" ট্যাবে নাম দেখা যায়।
 *   - নামের suffix (যেমন "Request") দেখে অনুমান কোরো না। UserRegistration একটা request schema,
 *     কিন্তু নামে "Request" নেই। একই schema request ও response দুটোতেও ব্যবহার হতে পারে।
 *   - Request বা response body যদি endpoint-এর ভেতরে সরাসরি (inline) লেখা থাকে (অর্থাৎ $ref নেই),
 *     তার জন্য file তৈরি হয় না। সেটাকে components/schemas-এ নাম দিয়ে সরিয়ে $ref করতে হবে।
 *
 * কখন এই class ব্যবহার করবে: OpenAPI document-এ request বা response-এর schema আগে থেকেই আছে।
 * কেন: Contract শুধু document-এই লেখা থাকে।
 *   Contract বদলালে তুমি শুধু document বদলাও, file নিজে নতুন হয়ে যায়।
 *   দ্বিতীয় কপি আপডেট করার কথা কাউকে মনে রাখতে হয় না।
 *
 * Output folder-এর file edit কোরো না। পরের build সেগুলো overwrite করে।
 * Document যে নিয়ম লিখতে পারে না, তার জন্য ManualSchemaPathConstants দেখো।
 *
 * Folder না পেলে (উদাহরণ: Eclipse থেকে সরাসরি test চালালে) এটা চালাও:
 *   mvn process-test-classes
 * Path-এর শুরুতে '/' নেই, কারণ folder-টা classpath-এ আছে।
 */

public final class GeneratedSchemaPathConstants {

	private GeneratedSchemaPathConstants() { }

	private static final String GENERATED = "schemas/generated/";

	// SUCCESS SCHEMAS: one per response shape, so there are many.
	// WHY: each resource returns its own fields, and a list response has a different shape
	// (items plus paging data). One schema cannot describe all of them.
	// Operations that return the same shape share one constant.
	// Example: GET and POST of a resource that both return one item use the same constant.
	//
	// The file name is the schema name in the document, first letter small, plus "Schema.json".
	// Example: Product -> productSchema.json
	public static final String PRODUCT_SCHEMA      = GENERATED + "productSchema.json";
	
	public static final String PRODUCT_REQUEST_SCHEMA      = GENERATED + "ProductRequestSchema.json";
	
	public static final String PRODUCT_PAGE_SCHEMA = GENERATED + "productPageSchema.json";

	// ERROR SCHEMA: one for all error responses, so there is only one constant.
	// WHY: an error response has the same shape for every operation and every status code.
	// Only the values differ (status, title, detail). The shape does not.
	// The status code is not the job of the schema. The status assertion checks it.
	// If the contract does not define the body of an error response, skip the schema validation for it.
	// Such a response is usually made by the server or a gateway.
	// Examples: a 413 from a gateway, or a 404 and a 405 from the routing layer of the framework.
	public static final String ERROR_RESPONSE_SCHEMA        = GENERATED + "problemDetailsSchema.json";
}
