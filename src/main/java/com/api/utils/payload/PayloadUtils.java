package com.api.utils.payload;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;


/**
 * Test payload বানানোর builder।
 *
 * <p>Lambda expression দুই ধাপে কাজ করে।
 * Evaluation: lambda expression evaluate হলে একটা object পাওয়া যায় (lambda object)।
 * শুধু object তৈরি হয়, lambda body চলে না।
 * Execution: functional interface-এর একমাত্র abstract method (SAM, যেমন get(), accept())
 * lambda object-এর ওপর call করলে lambda body চলে।
 * Lambda expression-এর কাজ lambda object তৈরিতেই সীমাবদ্ধ।
 * SAM call lambda expression-কে আবার evaluate করে না।
 * Evaluation-এর পর প্রতিটা SAM call-এ lambda body একবার করে execute হয়।
 *
 * <p>Lambda object কেমন:
 * কোন interface implement হবে (target type), তা compiler compile-এর সময় ঠিক করে।
 * বাকিটা প্রচলিত JVM-এর implementation, ভাষার নিয়ম নয়:
 * Lambda expression প্রথমবার evaluate হলে JVM সেই interface implement করে একটা hidden class বানায়
 * (এটা Java-র anonymous class নয়: compile-এর সময় আলাদা .class ফাইল হয় না)।
 * সেই evaluate-ই ওই class-এর একটা object-ও বানায়। একই lambda expression-এর পরের evaluate-গুলো সেই
 * একই class ব্যবহার করে।
 * Class-এর SAM method lambda body চালায়।
 * (ভেতরে compiler body-র code enclosing class-এ একটা লুকোনো method-এ রাখে, যার নাম lambda$...।
 * SAM method সেটাকে call করে, আর captured field-এর মান বাড়তি argument হিসেবে দেয়।
 * stack trace-এ ওই নাম দেখা যায়।)
 *
 * <p>Captured variable: Lambda expression যে method-এর ভেতরে লেখা, সেই method-কে বলে enclosing method।
 * Lambda body ওই enclosing method-এর parameter বা local variable ব্যবহার করতে পারে।
 * (Local variable মানে lambda-র আগে ওই method-এ declare করা variable।)
 * Body যে এমন variable বা parameter ব্যবহার করে, তাকে captured variable বলে।
 * Captured variable-কে final বা effectively final হতে হয়: মান পাওয়ার পর তাকে আর assign করা যায় না,
 * lambda-র আগে, পরে বা ভেতরে কোথাও নয়। নইলে compile error হয়।
 * প্রতিটা captured variable-এর জন্য lambda object-এ একটা final instance field (captured field) তৈরি হয়।
 * তাতে evaluate-এর সময়কার মানের কপি জমা হয়।
 * Lambda কিছু capture না করলে object-এ কোনো field থাকে না। তখন JVM একই object পুনর্ব্যবহারও করতে
 * পারে (গ্যারান্টি নয়)।
 *
 * <p>Object আর কিছু রাখে না। নিচের জিনিসগুলো object-এ থাকে না, থাকে method call-এর frame-এ।
 * Call শুরু হলে এগুলো তৈরি হয়, আর call return করলে মুছে যায়। প্রতিটা call নিজের নতুন variable পায়।
 * (Captured field এর উল্টো: object যতদিন আছে ততদিন থাকে, আর সব SAM call-এ একই মান দেয়।)
 * <ul>
 *   <li>lambda-র নিজের parameter (Example: {@code tree -> ...}-এর tree;
 *       এটা accept()-এর argument থেকে মান পায়)। থাকে SAM call-এর frame-এ</li>
 *   <li>lambda body-র ভেতরে declare করা variable। থাকে SAM call-এর frame-এ</li>
 *   <li>lambda body যে অন্য method call করে, সেই method-এর নিজের parameter
 *       (Example: body-তে replaceAt(tree, pointer, value) call হলে replaceAt-এর parameter)।
 *       থাকে সেই method call-এর frame-এ</li>
 * </ul>
 *
 * <p>একটা lambda object-এর captured field একবার সেট হয়, object তৈরির সময়, আর কখনো বদলায় না।
 * কিন্তু execution যতবার lambda expression-এ পৌঁছায়, ততবার সেটা আবার evaluate হয়।
 * Lambda কিছু capture করলে বাস্তবে প্রতিবার নতুন lambda object তৈরি হয়, নিজের captured field সহ।
 * পুরনো object বদলায় না।
 * Example: enclosing method দুবার random মান দিয়ে call করলে দুটো lambda object হয়, প্রতিটা নিজের মান রাখে।
 *
 * <p>কপি মানে variable-এর মানের কপি। Variable-এর type ঠিক করে মানটা কী।
 * Primitive হলে (Example: int) মানটাই কপি হয়।
 * Reference type হলে (Example: String, Map) মান হল একটা object-এর ঠিকানা। ঠিকানার কপি হয়, object
 * নিজে কপি হয় না।
 * তাই variable আর field একই object-কে point করে।
 * Object mutable হলে (Example: একটা Map) পরে কেউ তাকে বদলালে lambda সেই বদল দেখে।
 * String-এর মতো immutable object-এ এমন বদল সম্ভব নয়।
 *
 * <p>SAM call: SAM (get() বা accept(x)) call করে caller, মানে code-এর যে লাইনে reference.get() বা
 * reference.accept(x) লেখা
 * (reference হল lambda object-কে point করা variable)। get()-এর কোনো argument নেই।
 * accept(x)-এ argument x দেয় caller, JVM নয়।
 * SAM call যায় lambda object-এর SAM method-এ, তাই প্রতিটা SAM call-এ lambda body execute হয়।
 * Body-র কাছে দুই ধরনের মান থাকে: SAM-এর argument (body-র নিজের parameter-এ),
 * আর captured মান (object-এর captured field থেকে; source code-এ body এগুলো নাম ধরে সরাসরি ব্যবহার করে)।
 * Body এই মানগুলো অন্য method-এ argument হিসেবে দিতে পারে (Example: replaceAt(tree, pointer, value))।
 * তখনই সেগুলো সেই method-এর argument হয়।
 */


// Tools that every payload factory needs. They do not know any resource.
// A resource factory (for example the factory of one resource) calls them.
//
//   1. Convert an object to a JSON tree, a JSON text or an XML text, and read a file.
//   2. Build the BODIES of the negative scenarios: a valid payload with ONE thing made wrong.
public final class PayloadUtils {

	private PayloadUtils() {} // only static members: no instance is needed

	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
	private static final XmlMapper XML_MAPPER = new XmlMapper();

	// ==============================================================================================
	// 1. CONVERT AND READ
	// ==============================================================================================

	// Converts an object into a Jackson ObjectNode: a JSON tree that you can change field by field.
	// The object must become a JSON object (a POJO or a Map).
	// A List gives an ArrayNode and fails with a ClassCastException.
	//
	// Why an ObjectNode and not the POJO: a negative scenario needs a body that a POJO cannot hold.
	//   - wrong type: a number field that holds the text "not-a-number" (in the POJO it is an Integer)
	//   - unknown key: a key that the POJO does not have
	//   A missing field could also be done with the POJO (null + @JsonInclude(NON_NULL)),
	//   but it is done with the ObjectNode too, so that all negative scenarios are written the same way.
	//
	// Why an ObjectNode and not a Map:
	//   - Nested values are easy to reach and change.
	//     With a Map, get("key") returns an Object. Object has no put(),
	//     so you must cast it to a Map before you change an inner value.
	//   - The field order is the same in both: valueToTree() keeps the order of the POJO,
	//     and convertValue(pojo, Map.class) gives a LinkedHashMap, which keeps it too.
	//     (The order does not matter to the server. It only helps when you read logs.)
	//
	// Why an ObjectNode and not a JSON String (for example from writeValueAsString()):
	//   - A String has no structure: it is only a sequence of characters.
	//     Java does not know that it has keys and values.
	//     To change a field you must find the characters and replace them (like Find and Replace in a text editor).
	//     A typo finds nothing and nobody is told. The same name in two places is changed twice.
	//     If you put a value into the String by hand, a quote inside the value breaks the JSON.
	//   - An ObjectNode knows the structure. A field is changed by its path, and Jackson writes the quotes and the escapes.
	//   - A String is still needed for a body that is broken as TEXT (example: a missing bracket or a duplicate key).
	//     A tree cannot hold such a body. Read it from a file with readClasspathFile().
	public static ObjectNode toObjectNode(Object pojo) {
		return JSON_MAPPER.valueToTree(pojo);
	}

	public static String toJson(Object pojo) {
		try {
			return JSON_MAPPER.writeValueAsString(pojo);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize the payload to JSON", e);
		}
	}

	public static String toXml(Object pojo) {
		try {
			return XML_MAPPER.writeValueAsString(pojo);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize the payload to XML", e);
		}
	}

	// Reads a text file from the classpath, for example a broken JSON that cannot be built from a POJO.
	// The file must be under src/test/resources or src/main/resources, not in the project root.
	// Do not start the path with a slash.
	public static String readClasspathFile(String resourcePath) {
		try (InputStream input = PayloadUtils.class.getClassLoader().getResourceAsStream(resourcePath)) {
			if (input == null) {
				throw new RuntimeException("File not found on classpath: " + resourcePath);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read resource: " + resourcePath, e);
		}
	}

	// ==============================================================================================
	// 2. THE BODIES OF THE NEGATIVE SCENARIOS
	//
	// The methods below do not know any resource. The resource factory says which field to break and how.
	// Every method returns a Supplier<ObjectNode>. Call get() to receive a new body.
	// A field is named by a JSON pointer. Example: "/price", or "/address/city" for a nested field.
	//
	// SAFE AGAINST TYPOS: the methods that change or remove a field need the field to EXIST in the valid payload.
	// If the pointer has a typo (example: "/adress/city" instead of "/address/city"), they fail with an IllegalArgumentException.
	// Jackson's withObject() would not fail: it would add a NEW field, the real field would stay valid,
	// and the server would answer 400 (unknown key), not the error that the scenario wants to test.
	//
	// ----------------------------------------------------------------------------------------------
	// HOW IT WORKS: Consumer, Supplier and lambda
	//
	// Consumer<T> is a functional interface. Its only abstract method is: void accept(T t).
	// Here T is ObjectNode, so accept() takes an ObjectNode and returns nothing.
	// Supplier<T> is a functional interface. Its only abstract method is: T get().
	// get() takes nothing and returns a value.
	//
	// A lambda expression evaluates to an object. That object implements a functional interface.
	// Which one? The type of the variable or parameter that receives the lambda (the "target type").
	// The body of the lambda becomes the body of the abstract method of that interface.
	// So we can call accept(), get() and so on on that object.
	//
	// If the parameter of a method is a functional interface (like mutation() here), the argument must be
	// an object that implements it. It can be:
	//   - a lambda expression (the most common)
	//   - a method reference (for example MyClass::someMethod)
	//   - an anonymous class, or a named class that implements the interface
	//   - an existing variable of that type
	//
	// mutation() takes a Consumer<ObjectNode>. The caller passes a lambda like:  p -> p.put(...)
	//   - left of the arrow (p): the parameter of accept()
	//   - right of the arrow: the body of accept()
	// mutation() is reusable:
	//   - the lambda of the caller decides WHAT to change.
	//   - mutation() decides ON WHAT (the new payloadTree that it passes to accept())
	//     and WHEN (after building it, before returning it).
	// p.put(...) returns a value, but accept() is void. Java allows this and ignores the returned value.
	//
	// WHY A SUPPLIER AND NOT A PLAIN OBJECT
	//   - Lazy: the payload is built only when get() is called (for example in a data provider).
	//     Before that, no payload exists in memory.
	//   - Fresh: every get() builds a new, independent payload. No two tests share one object,
	//     so parallel tests stay safe.
	//   - Memory: a big payload (for example 12 MB) is not kept in memory all the time.
	//     After the test is done with it, it can be garbage collected.
	//
	// Why not a plain static field like  static final ObjectNode X = mutate(...)?
	// A plain field is built early, when the class is initialized, and the static field keeps it
	// until the class is unloaded (usually when the JVM exits). That is a problem for large payloads.
	// With a Supplier, the payload can be garbage collected when the test is done.
	// ----------------------------------------------------------------------------------------------
	// ==============================================================================================

	// 12 million characters are about 12 MB. That is bigger than a size limit of 10 MB.
	// The limit of the API under test decides the number: the body must be BIGGER than the limit,
	// so that the server rejects it (for example 413 Payload Too Large).
	// A body of 10 MB or less is accepted and may get a success status (for example 200).
	public static final int OVER_10_MB = 12_000_000;

	// The general builder. Use it when no ready-made method below fits.
	//   validPayloadSupplier  gives a new valid payload (example: a method reference such as SomeFactory::getValidSomePayloadPOJO)
	//   change                the change that makes it wrong
	public static Supplier<ObjectNode> mutation(Supplier<?> validPayloadSupplier, Consumer<ObjectNode> change) {
		return () -> {
			// 1. A new JSON tree from a new valid payload. Each call has its own copy.
			//    toObjectNode() calls valueToTree(). valueToTree() gives the node based on its argument/input:
			//    - For a POJO or a Map input valueToTree() returns an ObjectNode
			//    - For any other input value valueToTree() returns another kind of node.
			//      Example: a String gives a TextNode, a number gives an IntNode, a boolean gives a BooleanNode,
			//      a List gives an ArrayNode.
			//    All outputs of valueToTree() are JsonNodes (values), and an ObjectNode is a JsonNode too.
			//
			//    But payloadTree here is an ObjectNode. So the valid payload must be a POJO or a Map.
			//    Any other value fails with a ClassCastException.
			//
			//    A node stands for a VALUE, not for a key.
			//    The keys are not nodes: an ObjectNode keeps its keys as plain Strings, and each key points to a value node.
			ObjectNode payloadTree = toObjectNode(validPayloadSupplier.get());
			// 2. The caller's lambda runs on this same object and changes it in place.
			change.accept(payloadTree);
			// 3. The changed payloadTree.
			return payloadTree;
		};
	}

	// Replaces the value of an EXISTING field. Example: a number field gets a text (wrong type),
	// or a number below the minimum (range breach), or a date in the wrong format or a field gets a huge value.
	public static Supplier<ObjectNode> withValue(Supplier<?> validPayloadSupplier, String pointer, Object value) {
	    return mutation(validPayloadSupplier, payloadTree -> {
	        if (value instanceof Supplier) {
	            throw new IllegalArgumentException("withValue() puts a value that is already calculated. "
	                    + "It does not call get() on a Supplier. "
	                    + "For a value that must be new every time a body is made, use mutation().");
	        }
	        replaceAt(payloadTree, pointer, value);
	    });
	}

	// Replaces several EXISTING fields at once. Example: a start date after an end date (cross-field rule).
	public static Supplier<ObjectNode> withValues(Supplier<?> validPayloadSupplier, Map<String, Object> changes) {
		return mutation(validPayloadSupplier, payloadTree ->
				changes.forEach((pointer, value) -> replaceAt(payloadTree, pointer, value)));
	}

	// Removes an EXISTING field. Example: a required field is missing.
	public static Supplier<ObjectNode> without(Supplier<?> validPayloadSupplier, String pointer) {
		return mutation(validPayloadSupplier, payloadTree -> {
			ParentAndName target = find(payloadTree, pointer);
			target.parent().remove(target.name());
		});
	}

	// Adds a key that the contract does not know. You choose the key and the value.
	//   - A harmless name tests that the server rejects ANY unknown key.
	//     Example: withUnknownKey(valid, "unknown_field", "x")
	//   - A name that looks like a real internal field tests Mass Assignment (Over-Posting).
	//     Example: withUnknownKey(valid, "isAdmin", true)
	//
	// An attacker might send sensitive fields as unknown keys, such as "isAdmin": true or "role": "SUPER_ADMIN".
	// If the backend accepts them, it could change roles, permissions or balances without anybody asking for it.
	//
	// The key must be UNKNOWN: it must not be in the valid payload.
	// A field that is in the valid payload (example: "price") would only be REPLACED.
	// The body would then have no unknown key, and it could even be a valid body.
	// So a key that exists fails with an IllegalArgumentException. To change a real field, use withValue(...).
	public static Supplier<ObjectNode> withUnknownKey(Supplier<?> validPayloadSupplier, String key, Object value) {
		return mutation(validPayloadSupplier, payloadTree -> {
			if (payloadTree.has(key)) {
				throw new IllegalArgumentException("The key '" + key + "' is already in the valid payload, so it is not "
						+ "an unknown key. To change a real field, use withValue(...).");
			}
			// valueToTree() returns the node (field value) based on its argument/input passed to it:
			//   - For a POJO or a Map value it returns an ObjectNode (the new field holds a nested object).
			//   - For any other value it returns another kind of node.
			//     Example: a String gives a TextNode, a number gives an IntNode, a boolean gives a BooleanNode,
			//     a List gives an ArrayNode, null gives a NullNode.
			// set() takes any JsonNode, and all of these are JsonNodes. So every kind of value is fine here.
			//
			// toObjectNode() is different. It calls the same valueToTree(), but it returns an ObjectNode.
			// So the result of toObjectNode() must be an ObjectNode.
			// This means toObjectNode() works only for a POJO or a Map.
			// Any other value fails with a ClassCastException when toObjectNode() returns.
			payloadTree.set(key, JSON_MAPPER.valueToTree(value));
		});
	}


	// Replaces an EXISTING text field with a very long text.
	// What breaks depends on the number of characters:
	//   - Just over the length rule of the field (example: 501 characters for a field with a limit of 500):
	//     a CONTENT error. The server answers 422 (or 400, as its contract says).
	//   - Over the size limit of the whole body (see OVER_10_MB): a SIZE error. The server answers 413.
	//     It stops the request before the content is checked, so the length rule of the field is never tested.
	public static Supplier<ObjectNode> withHugeText(Supplier<?> validPayloadSupplier, String pointer, int characters) {
		return withValue(validPayloadSupplier, pointer, "A".repeat(characters));
	}

	private record ParentAndName(ObjectNode parent, String name) {
	}

	// Finds the object that holds the field, and the name of the field. Both must exist.
	private static ParentAndName find(ObjectNode payloadTree, String pointerText) {
		// A JSON pointer is a path to a field. Example: "/address/city".
		// compile() turns the text into a JsonPointer. The text must start with a slash, or it fails.
		// The pointer must name a field. An empty pointer is the root itself: it has no parent and no name,
		// so head() and last() give null.
		JsonPointer pointer = JsonPointer.compile(pointerText);

		// head() is the path without its last field/step. 
		// It is the path of the PARENT: the object that holds the field. 
		// Example: the head of "/address/city" is "/address".
		// For a field on the top level (example: "/price") the head is empty,
		// and at("") gives the root object itself: the outermost object i.e. outermost {}, the one that holds all the fields.
		// So the root is the parent of a top-level field.
		// at() only looks. It does not change the tree. A path that does not exist gives a MissingNode.
		JsonNode parent = payloadTree.at(pointer.head());

		// last() is the last field of the path, and getMatchingProperty() is its name.
		// Example: the name of "/address/city" is "city".
		String name = pointer.last().getMatchingProperty();
		
		if (!(parent instanceof ObjectNode)) {
			throw new IllegalArgumentException("The path '" + pointerText + "' is wrong: '" + pointer.head()
					+ "' is not an object in the valid payload. Check the spelling.");
		}
		if (!parent.has(name)) {
			throw new IllegalArgumentException(
					"The field '" + pointerText + "' does not exist in the valid payload. Check the spelling.");
		}
		return new ParentAndName((ObjectNode) parent, name);
	}

	private static void replaceAt(ObjectNode payloadTree, String pointer, Object value) {
		ParentAndName target = find(payloadTree, pointer);
		if (value == null) {
			target.parent().putNull(target.name());
		} else {
			// valueToTree() gives the node that fits the type of the value, and set() takes any JsonNode.
			// See withUnknownKey() for the details.
			target.parent().set(target.name(), JSON_MAPPER.valueToTree(value));
		}
	}
}