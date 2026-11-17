package com.api.utils;


import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


/**
 * Single source of truth for environment configuration.
 *
 * Resolution order for every key:
 *   1. JVM system variable/property   (e.g. mvn test -DbaseUri=http://localhost:8081)
 *   2. config.properties              (src/test/resources)
 *   3. default value passed by the caller
 *   
 * Key/property value passed from Command line/JVM system variable 
 * overrides both config.properties and default value, 
 * whereas config.properties overrides default value.
 * 
 * Every value that identifies the server under test (the base URI) or an account
 * (user name, password) must be configured. There are no placeholder defaults, so a
 * missing value fails at once with a message that names the key, instead of
 * surfacing later as a confusing 401 or connection error.
 */

public class ConfigUtils {

	private static final Properties PROPERTIES = new Properties();
	 
	// Load config.properties via ClassLoader instead of a file path based on the current/working directory (user.dir).
	// user.dir works fine when running a project from an IDE or the command line (mvn test), 
	// but it's unreliable in CI/CD and Docker, where the working directory isn't guaranteed.
	// ClassLoader resolves resources/files from the classpath instead of current directory, 
	// so this works consistently across environments.
	//
	// Requirement: Resources/Files must live 
	// under src/test/resources or src/main/resources, not under the project root.
	// Any leading slash (slash at the beginning of the path) must be avoided

    static {
        try (InputStream input = ConfigUtils.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("config.properties not found on classpath");
            }
            PROPERTIES.load(input);
            
         // PROPERTIES.load(input) may throw checked IOException and "catch(IOException e)" catches that exception.
		 // Catch block rethrows a RuntimeException in place of IOException 
		 // because a static block cannot declare or throw
		// checked exceptions like IOException directly.
	        } catch (IOException e) {
	            throw new RuntimeException("Failed to load config.properties", e);
	        }
    }
 
    
    private ConfigUtils() {
    }

     // This method looks up the key's value in 3 sources based on priority: 
    // - firstly, JVM System Variable i.e. Command Line, 
    // - Secondly, config.properties, 
    // - finally, default value
    //
    // System property (-Dkey=value) wins over config.properties, which wins over the default.
    public static String getProperty(String key, String defaultValue) {
    	// System.getProperty(key) reads values passed via JVM System Properties / Command Line (e.g., -DbaseUri=http://localhost:8081).
    	// PROPERTIES.getProperty(key, defaultValue) CANNOT read values passed via Command Line/JVM System Variables
    	String override = System.getProperty(key);
        if (override != null && !override.isBlank()) {
            return override.trim();
        }else {
            return PROPERTIES.getProperty(key, defaultValue); 
        }
    }
 
   

    // Value of a key that must be configured; fails fast and names the key when it is missing.
    private static String required(String key) {
        String value = getProperty(key, ""); // default value is empty
        if (value.isBlank()) {
            throw new IllegalStateException(
                    "'" + key + "' is not configured. Set it in config.properties or pass -D" + key + "=<value>");
        }
        return value.trim();
    }

    // Base URI of the server under test, read from config.properties ("baseUri").
    // Without editing config.properties, you can override it at runtime from the
    // command line:  -DbaseUri=http://localhost:8081
    // Throws IllegalStateException if it is not configured.
	public static String getBaseUri() {
		return required("baseUri");
	}
    
	// --- Authentication credentials ---
	// Set the real values in config.properties or override them with
	// -Dauth.validUser=... etc. Never hardcode real secrets directly in this file.

	public static String getValidUsername() {
		return required("auth.validUser");
	}

	public static String getValidPassword() {
		return required("auth.validPassword");
	}

	// --- Role-based test accounts ---
	// Looks up credentials for any named role, e.g. getRoleUsername("noCreateBookingPermissionRole").
	// New roles are added purely via config.properties: no new Java method is needed per role,
	// just a new "auth.role.<roleName>.username" / "...password" entry.
	public static String getRoleUsername(String roleName) {
		return required("auth.account." + roleName + ".username");
	}

	public static String getRolePassword(String roleName) {
		return required("auth.account." + roleName + ".password");
	}
}
