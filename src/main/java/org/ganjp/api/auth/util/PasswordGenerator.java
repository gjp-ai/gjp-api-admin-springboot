package org.ganjp.api.auth.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for generating secure passwords and encoding them with BCrypt.
 */
public class PasswordGenerator {

    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()_-+=<>?";
    
    private static final int DEFAULT_PASSWORD_LENGTH = 12;
    private static final int DEFAULT_BCRYPT_STRENGTH = 10; // Standard strength
    
    private final SecureRandom random;
    private final BCryptPasswordEncoder passwordEncoder;
    
    /**
     * Creates a new password generator with default BCrypt strength.
     */
    public PasswordGenerator() {
        this(DEFAULT_BCRYPT_STRENGTH);
    }
    
    /**
     * Creates a new password generator with specified BCrypt strength.
     * 
     * @param bcryptStrength The strength parameter for BCrypt (4-31, higher is stronger but slower)
     */
    public PasswordGenerator(int bcryptStrength) {
        this.random = new SecureRandom();
        this.passwordEncoder = new BCryptPasswordEncoder(bcryptStrength);
    }
    
    /**
     * Generates a random password of default length (12 characters).
     * 
     * @return A random secure password
     */
    public String generatePassword() {
        return generatePassword(DEFAULT_PASSWORD_LENGTH);
    }
    
    /**
     * Generates a random password of specified length.
     * The password will contain at least one uppercase letter, one lowercase letter,
     * one digit, and one special character.
     * 
     * @param length The length of the password to generate
     * @return A random secure password
     */
    public String generatePassword(int length) {
        if (length < 8) {
            throw new IllegalArgumentException("Password length must be at least 8 characters");
        }
        
        StringBuilder password = new StringBuilder(length);
        
        // Ensure at least one character from each required type
        password.append(getRandomChar(LOWERCASE));
        password.append(getRandomChar(UPPERCASE));
        password.append(getRandomChar(DIGITS));
        password.append(getRandomChar(SPECIAL));
        
        // Fill the rest with random characters from all types
        String allChars = LOWERCASE + UPPERCASE + DIGITS + SPECIAL;
        for (int i = 4; i < length; i++) {
            password.append(getRandomChar(allChars));
        }
        
        // Shuffle to avoid predictable pattern
        List<Character> passwordChars = password.chars()
                .mapToObj(c -> (char) c)
                .collect(Collectors.toList());
        Collections.shuffle(passwordChars, random);
        
        return passwordChars.stream()
                .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append)
                .toString();
    }
    
    /**
     * Encodes a raw password using BCrypt.
     * 
     * @param rawPassword The password to encode
     * @return BCrypt encoded password hash
     */
    public String encodePassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }
    
    /**
     * Checks if the raw password matches the encoded password.
     * 
     * @param rawPassword The raw password to check
     * @param encodedPassword The encoded password to check against
     * @return true if the passwords match
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
    
    /**
     * Generates a password and returns both the raw and encoded versions.
     * 
     * @return Array containing [raw password, encoded password]
     */
    public String[] generatePasswordWithEncoding() {
        String rawPassword = generatePassword();
        String encodedPassword = encodePassword(rawPassword);
        return new String[]{rawPassword, encodedPassword};
    }
    
    /**
     * Generates a password of specified length and returns both the raw and encoded versions.
     * 
     * @param length The length of the password to generate
     * @return Array containing [raw password, encoded password]
     */
    public String[] generatePasswordWithEncoding(int length) {
        String rawPassword = generatePassword(length);
        String encodedPassword = encodePassword(rawPassword);
        return new String[]{rawPassword, encodedPassword};
    }
    
    private char getRandomChar(String characterSet) {
        int randomIndex = random.nextInt(characterSet.length());
        return characterSet.charAt(randomIndex);
    }
    
    /**
     * Main method for testing and generating passwords via command line.
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        PasswordGenerator generator = new PasswordGenerator();
        
        // Parse command line arguments
        int length = DEFAULT_PASSWORD_LENGTH;
        boolean showEncoded = false;
        String rawPasswordToEncode = null;
        
        for (int i = 0; i < args.length; i++) {
            if ("-l".equals(args[i]) || "--length".equals(args[i])) {
                if (i + 1 < args.length) {
                    try {
                        length = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid password length: " + args[i]);
                        printUsage();
                        return;
                    }
                }
            } else if ("-e".equals(args[i]) || "--encode".equals(args[i])) {
                showEncoded = true;
            } else if ("-p".equals(args[i]) || "--password".equals(args[i])) {
                if (i + 1 < args.length) {
                    rawPasswordToEncode = args[++i];
                }
            } else if ("-h".equals(args[i]) || "--help".equals(args[i])) {
                printUsage();
                return;
            }
        }
        
        // If a specific password was provided, just encode it
        if (rawPasswordToEncode != null) {
            String encoded = generator.encodePassword(rawPasswordToEncode);
            System.out.println("Raw password: " + rawPasswordToEncode);
            System.out.println("Encoded password: " + encoded);
            return;
        }
        
        // Generate password(s)
        if (showEncoded) {
            String[] passwordWithEncoding = generator.generatePasswordWithEncoding(length);
            System.out.println("Generated password: " + passwordWithEncoding[0]);
            System.out.println("BCrypt encoded: " + passwordWithEncoding[1]);
        } else {
            String password = generator.generatePassword(length);
            System.out.println("Generated password: " + password);
        }
    }
    
    private static void printUsage() {
        System.out.println("Password Generator Usage:");
        System.out.println("  -l, --length <num>    Set the password length (default: 12)");
        System.out.println("  -e, --encode          Show BCrypt encoded password as well");
        System.out.println("  -p, --password <pwd>  Encode a specific password instead of generating one");
        System.out.println("  -h, --help            Show this help message");
    }
}
