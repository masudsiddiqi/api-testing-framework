package com.api.tools;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * OpenAPI 3.0 file-এর components/schemas-এর প্রতিটা schema-র জন্য একটা করে JSON Schema (draft-04) file তৈরি করে।
 * এটা দুই ধরনের schema-ই বানায়: request schema (client যে body পাঠায়)
 * এবং response schema (server যে body ফেরত দেয়)।
 * উদাহরণ: UserRegistration (request) -> userRegistrationSchema.json, Product (response) -> productSchema.json।
 *
 * কেন: Contract হাতে লেখা হয় শুধু OpenAPI file-এ।
 * Schema file গুলো সেখান থেকে তৈরি হয়, তাই কেউ সেগুলো edit করে না।
 *
 * প্রতিটা schema-র ওপর যা করে:
 *   - প্রতিটা "$ref": "#/components/schemas/X" কে X schema দিয়ে বদলে দেয় (ফলে file নিজে সম্পূর্ণ থাকে)
 *   - "nullable: true" কে type list বানায়, যেমন ["string", "null"]
 *   - draft-04 যে keyword চেনে না সেগুলো বাদ দেয় (example, deprecated, readOnly, ...)
 *   - draft-04-এর "$schema" keyword যোগ করে
 *
 * সীমা: এটা শুধু components/schemas পড়ে। যে request বা response body সরাসরি endpoint-এর ভেতরে
 * ("paths"-এর নিচে, inline) লেখা, তার জন্য কোনো file তৈরি হয় না।
 * এমন body-কে components/schemas-এ একটা নাম দাও এবং "$ref" দিয়ে সেটাকে ডাকো।
 *
 * এই class ব্যবহারের নিয়ম (pom.xml-এ দুটো argument লাগে):
 *   {@code OpenApiSchemaGenerator <openapi file (.yaml, .yml বা .json)> <output folder>}
 *
 * pom.xml-এর exec-maven-plugin-এ এভাবে লেখা থাকে:
 * <pre>{@code
 * <mainClass>com.api.tools.OpenApiSchemaGenerator</mainClass>
 * 
 * <arguments>
 * 
 *      <!-- OpenAPI contract file location -->
 *     <argument>${project.basedir}/src/test/resources/shop-openapi-spec.yaml</argument>   <!-- প্রথম argument: args[0] -->
 *     
 *     	<!-- ${project.build.testOutputDirectory} মানে target/test-classes।
 *		     তাই generated schema ফাইলগুলো target/test-classes/schemas/generated-এ তৈরি হয়। -->								
 *     <argument>${project.build.testOutputDirectory}/schemas/generated</argument>         <!-- দ্বিতীয় argument: args[1] -->
 *     
 * </arguments>
 * }</pre>
 * 
 * এই class file কোথায় থাকবে: src/test/java/com/api/tools/ (test source folder-এ)।
 *   - কেন test-এ: এটা শুধু build-এর সময় চলা একটা সরঞ্জাম। test-এর জন্য schema file বানায়।
 *     production code এটা ব্যবহার করে না, তাই এটা main source-এ রাখার দরকার নেই।
 *   - কখন চলে: pom.xml-এর exec-maven-plugin test code compile হওয়ার পরে
 *     (process-test-classes ধাপে) এটা চালায়। এই ধাপ test method চলার (test ধাপের) আগে।
 *     তাই test শুরুর আগেই schema file তৈরি হয়ে যায়।
 *     এজন্য এই class আগে compile হতে হয়, আর plugin test classpath ব্যবহার করে (classpathScope = test)।
 *   - folder আর package মিলতে হবে: file-এর path (com/api/tools) আর package লাইন (com.api.tools) এক থাকতে হয়।
 *     সরালে package লাইন আর pom.xml-এর mainClass দুটোই বদলাতে হবে।
 * 
 * এই class file-এ কোনো পরিবর্তন করা লাগে না। :
 *   - OpenAPI file-এর components/schemas-এ নতুন schema যোগ করলে এই code সেটাও পড়ে নেয়।
 *     এই class-এ কোনো বদল লাগে না।
 *   - সম্পূর্ণ নতুন OpenAPI contract file ব্যবহার করলেও এই class-এ কোনো বদল লাগে না।
 *     শুধু pom.xml-এ এই generator-এর প্রথম argument-এ নতুন contract file-এর নাম লিখে দিতে হয়।
 *   - Generated JSON schema file কোন folder-এ তৈরি হবে, তা এই code-এ লেখা নেই।
 *     সেটা pom.xml-এ ঠিক করতে হয়: প্রথম argument হলো OpenAPI file-এর path,
 *     দ্বিতীয় argument হলো output folder।
 *   - Generated JSON schema file-এর নাম আসে components/schemas-এর ভেতরের schema-র নাম থেকে।
 *     নামের প্রথম অক্ষর ছোট হাতের হয়, আর শেষে "Schema.json" যোগ হয়।
 *     উদাহরণ: Product -> productSchema.json।
 *     
 */

public class OpenApiSchemaGenerator {

	private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";
	private static final String DRAFT_04 = "http://json-schema.org/draft-04/schema#";

	// OpenAPI keywords that are not part of draft-04. They are removed.
	private static final Set<String> REMOVED_KEYWORDS = Set.of(
			"example", "examples", "deprecated", "readOnly", "writeOnly",
			"discriminator", "xml", "externalDocs", "nullable");

	private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

	private final JsonNode allSchemas;

	private OpenApiSchemaGenerator(JsonNode allSchemas) {
		this.allSchemas = allSchemas;
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			throw new IllegalArgumentException("Usage: OpenApiSchemaGenerator <openapi file> <output folder>");
		}
		Path specFile = Path.of(args[0]);
		Path outputFolder = Path.of(args[1]);

		JsonNode spec = mapperFor(specFile).readTree(specFile.toFile());
		JsonNode schemas = spec.path("components").path("schemas");
		if (!schemas.isObject() || schemas.isEmpty()) {
			throw new IllegalStateException("No components/schemas found in " + specFile);
		}

		Files.createDirectories(outputFolder);
		OpenApiSchemaGenerator generator = new OpenApiSchemaGenerator(schemas);
		ObjectMapper writer = new ObjectMapper();

		Iterator<String> names = schemas.fieldNames();
		int count = 0;
		while (names.hasNext()) {
			String name = names.next();
			ObjectNode schema = generator.build(name);
			File target = outputFolder.resolve(fileNameFor(name)).toFile();
			writer.writerWithDefaultPrettyPrinter().writeValue(target, schema);
			count++;
		}
		System.out.println("Generated " + count + " schema files in " + outputFolder);
	}

	// Product -> productSchema.json
	static String fileNameFor(String schemaName) {
		return Character.toLowerCase(schemaName.charAt(0)) + schemaName.substring(1) + "Schema.json";
	}

	private static ObjectMapper mapperFor(Path file) {
		String name = file.getFileName().toString().toLowerCase();
		return (name.endsWith(".yaml") || name.endsWith(".yml"))
				? new ObjectMapper(new YAMLFactory())
				: new ObjectMapper();
	}

	private ObjectNode build(String name) {
		Deque<String> path = new ArrayDeque<>();
		path.push(name);
		ObjectNode result = (ObjectNode) convert(allSchemas.get(name), path);
		path.pop();
		// "$schema" goes first in the file
		ObjectNode withHeader = NODES.objectNode();
		withHeader.put("$schema", DRAFT_04);
		withHeader.setAll(result);
		return withHeader;
	}

	// Copies a schema node and fixes it, level by level.
	private JsonNode convert(JsonNode node, Deque<String> path) {
		if (node.isArray()) {
			ArrayNode copy = NODES.arrayNode();
			node.forEach(item -> copy.add(convert(item, path)));
			return copy;
		}
		if (!node.isObject()) {
			return node.deepCopy();
		}

		// A reference: replace it with the schema it points to.
		if (node.has("$ref")) {
			String ref = node.get("$ref").asText();
			if (!ref.startsWith(SCHEMA_REF_PREFIX)) {
				throw new IllegalStateException("Only references to components/schemas are supported: " + ref);
			}
			String target = ref.substring(SCHEMA_REF_PREFIX.length());
			if (!allSchemas.has(target)) {
				throw new IllegalStateException("Reference to a missing schema: " + ref);
			}
			if (path.contains(target)) {
				throw new IllegalStateException("A schema that refers to itself is not supported: "
						+ String.join(" -> ", path) + " -> " + target);
			}
			path.push(target);
			JsonNode resolved = convert(allSchemas.get(target), path);
			path.pop();
			return resolved;
		}

		ObjectNode copy = NODES.objectNode();
		boolean nullable = node.path("nullable").asBoolean(false);
		for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext();) {
			Map.Entry<String, JsonNode> field = it.next();
			String key = field.getKey();
			if (REMOVED_KEYWORDS.contains(key)) {
				continue;
			}
			// "properties" and "enum" hold names and values, not schema keywords: do not remove inside them
			if ("properties".equals(key) && field.getValue().isObject()) {
				ObjectNode props = NODES.objectNode();
				field.getValue().fields().forEachRemaining(p -> props.set(p.getKey(), convert(p.getValue(), path)));
				copy.set(key, props);
			} else if ("enum".equals(key) || "required".equals(key) || "default".equals(key)) {
				copy.set(key, field.getValue().deepCopy());
			} else {
				copy.set(key, convert(field.getValue(), path));
			}
		}
		if (nullable && copy.has("type") && copy.get("type").isTextual()) {
			ArrayNode types = NODES.arrayNode();
			types.add(copy.get("type").asText());
			types.add("null");
			copy.set("type", types);
		}
		return copy;
	}
}