package api.practice.logic;

import java.util.Random;
import java.util.UUID;

public class Practice {
	
	public static void main(String[] args) {
	
		String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 3);
		
		System.out.println(unique);
	}

}
