package org.ganjp.api.auth.util;

/**
 * Demo class to show how to use the PasswordGenerator class.
 */
public class PasswordGeneratorDemo {
    
    public static void main(String[] args) {
        // Create a password generator with default settings
        PasswordGenerator generator = new PasswordGenerator();
        
        System.out.println("===== Password Generator Demo =====\n");
        
        // Generate a password with default length (12 characters)
        String defaultPassword = generator.generatePassword();
        System.out.println("Default password (12 chars): " + defaultPassword);
        
        // Generate a longer password (16 characters)
        String longerPassword = generator.generatePassword(16);
        System.out.println("Longer password (16 chars): " + longerPassword);
        
        // Generate a password and show its BCrypt encoding
        String[] pwdWithEncoding = generator.generatePasswordWithEncoding();
        System.out.println("\nGenerated password: " + pwdWithEncoding[0]);
        System.out.println("BCrypt encoded: " + pwdWithEncoding[1]);
        
        // Verify a password against its encoded version
        boolean matches = generator.matches(pwdWithEncoding[0], pwdWithEncoding[1]);
        System.out.println("Password match verification: " + matches);
        
        // Example for encoding a known password (like 'admin123')
        String knownPassword = "123456";
        String encodedKnown = generator.encodePassword(knownPassword);
        System.out.println("\nKnown password: " + knownPassword);
        System.out.println("BCrypt encoded: " + encodedKnown);
    }
}
