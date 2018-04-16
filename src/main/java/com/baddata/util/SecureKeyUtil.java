/**
 * Copyright (c) 2016 by Baddata
 * All rights reserved.
 */
package com.baddata.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.baddata.exception.BaddataException;


/**
 * Currently only one type of license:
 * 1) User: admin, auditor, or support
 */
public class SecureKeyUtil {
    
    private static final String USER_TYPE = "-type".intern();
    private static final String VALIDATE_OPTION = "-test".intern();
    private static final String SECRET = "MA2ahSt".intern();
    private static final String SEPARATOR = "-".intern();
    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();
    private static final int NUM_OCTETS = 4;
    private static final int NUM_CHARS_PER_OCTET = 4;
    private static final int NUM_CHARS_IN_ENCRYPTED_OCTETS = (NUM_OCTETS - 1) * NUM_CHARS_PER_OCTET;
    private static final int MIN_CHARS_OF_ALL_OCTETS_SANS_SEPERATOR = NUM_OCTETS * NUM_CHARS_PER_OCTET;
    private static final String SECRET_PADDING = "000000".intern();

    private static final String HELP = "\n"
            + " Usage:  ./licensecmd [ arguments ]\n\n"
            + "    Arguments:\n"
            + "        " + USER_TYPE + " user type       -- specify the user type [admin|auditor|support]\n\n"
            + "        " + VALIDATE_OPTION + " <licensekey>     -- validates a license key\n"
            + "                            -- E.g. ./licensecmd -test ADMIN-1100-EE11-0011\n\n"
            + "    Example admin license generation:\n"
            + "        ./licensecmd -t admin\n\n"
            + "    *** License input is case-insensitive, but will be displayed in upper case. (i.e. ADMIN-1100-EE11-0011) ***\n\n";

    private static final String INVALID_LICENSE_VALIDATE_ARGS = "\n"
            + " Error!! To validate a license key, omit the user type value."
            + "\n";
    
    protected boolean isCmdLineRequest = false;
    
    public enum ErrorType {
        
        UNKNOWN_USER_TYPE_CODE("ERR-LIC-1101", "\n The user type entered is not a supported type (admin, auditor, support).\n"),
        MISSING_USER_TYPE_CODE("ERR-LIC-1102", "\n The license user type is missing.\n"),
        NONE("", "");
        
        private String errorCode = "";
        private String stdOutError = "";
        private String logError = "";
        private ErrorType(String errorCode, String stdOutError) {
            this.errorCode = errorCode;
            this.stdOutError = stdOutError;
            // replace the newline char for normal log error reporting
            this.logError = stdOutError.replaceAll( "\n", "" );
        }
        
        public String getErrorCode() {
            return errorCode;
        }

        public String getStdOutError() {
            return stdOutError;
        }

        public String getLogError() {
            return logError;
        }
        
    }
    
    public enum UserType {
        ADMIN, AUDITOR, SUPPORT;
        
        public static boolean matchesUserType(String userType) {
            for ( UserType type : UserType.values() ) {
                if ( type.name().equalsIgnoreCase( userType ) ) {
                    return true;
                }
            }
            return false;
        }
    }
    
    public static void main( String[] args ) {
        // command line will not use "getInstance()"
        SecureKeyUtil gen = new SecureKeyUtil();
        gen.isCmdLineRequest = true;
        gen.generateLicenseFromInput( args );
    }

    private void generateLicenseFromInput(String[] args) {

        // get the user type
        String userType = this.getArgValue(args, USER_TYPE);

        // get the license to test
        String licenseToTest = this.getArgValue(args, VALIDATE_OPTION);
        
        if ( StringUtils.isNotBlank(licenseToTest) ) {
            // passed in "-test"
            
            if ( StringUtils.isNotBlank( userType ) ) {
                // should only have the "-test" option, return the help message
                System.out.println(INVALID_LICENSE_VALIDATE_ARGS + HELP);
                System.exit(0);
            }
            
            // validate the license
            try {
                isValidAdminUser(licenseToTest);
                // valid license response
                System.out.println(licenseToTest + " is a valid license.\n");
            } catch ( Exception e ) {
                // invalid license response
                System.out.println("\n" + licenseToTest + " is invalid, error: " + e.toString());
            }
            
            System.exit(0);
        }
        
        ErrorType errType = validateLicenseInput( userType );
        
        if ( errType != null && errType != ErrorType.NONE ) {
            String err = errType.getStdOutError() + HELP;
            if ( err != null ) {
                System.out.println( err );
                System.exit(0);
            }
        }

        // generate the license
        try {
            this.generateLicense( userType );
        } catch (Exception e) {
            System.out.println("License generation error: " + e.getMessage());
        }
    }

    /**
     * Retrieve the arg values from the command line input
     *
     * @param args
     * @param option
     * @return
     */
    private String getArgValue( String[] args, String option ) {
        if ( args == null || args.length == 0 ) {
            // args are empty or null, return null
            return null;
        }

        for ( int i = 0; i < args.length; i++ ) {
            String arg = args[i].trim();
            if ( arg.equalsIgnoreCase(option) ) {
                ++i;
                if ( args.length > i ) {
                    return args[i].trim();
                }
                // doesn't have a value for this option, return null
                return null;
            }
        }

        // unable to find a matching option
        return null;
    }

    /**
     * Generate a License
     * 
     * It's a protected method to allow unit tests to use a testable class to invoke this method,
     * but otherwise one can only generate a license via the command line input.
     *
     * @param userType
     * @return String is the license generated by userType
     * @throws Exception
     */
    protected String generateLicense( String userType ) throws Exception {
        
        if ( !UserType.matchesUserType( userType ) ) {
            if ( this.isCmdLineRequest ) {
                // ref being null means the command line used the "public static main" function
                // to use the protected LicenseUtil constructor.
                throw new Exception( ErrorType.UNKNOWN_USER_TYPE_CODE.getStdOutError() + HELP );
            }
            // else this was invoked by the testable.  there's no other way generate license
            // will be invoked other than the command line
            throw new Exception( ErrorType.UNKNOWN_USER_TYPE_CODE.getLogError() );
        }

        // upper case it since it matches a user type
        userType = userType.toUpperCase();

        String licenseStr = "";

        // get the random hex
        String randHex = createThreeByteRandomHex();
        String rawLicense = createRawLicense(userType, randHex);

        // generate a SHA1 hash
        try {
            MessageDigest md = MessageDigest.getInstance(AlgoUtil.SHA_2_ALGO);
            byte[] shaBytes = md.digest(rawLicense.getBytes());

            // copy the 1st 3 bytes into our signature
            byte[] signatureBytes = new byte[3];
            System.arraycopy(shaBytes, 0, signatureBytes, 0, signatureBytes.length);

            String signatureHex = bytesToHex(signatureBytes, HEX_CHARS);

            licenseStr = formatLicense(userType, randHex, signatureHex);
        } catch ( NoSuchAlgorithmException e ) {
            throw new Exception("Encryption algorithm not found, unable to generate license.");
        }

        // print key
        System.out.println(licenseStr + "\n");

        return licenseStr;
    }


    /**
     * Format the input values into license octets
     *
     * @param userType
     * @param randHex
     * @param signatureHex
     * @return String is a formatted license
     */
    private static String formatLicense( String userType, String randHex, String signatureHex ) {
        // format the random hex to fit in the license format
        randHex = randHex.substring(0, randHex.length() - 2) + SEPARATOR + randHex.substring(randHex.length() - 2);

        // format the signature to fit in the license format
        signatureHex = signatureHex.substring(0, 2) + SEPARATOR + signatureHex.substring(2);

        // create the license
        String license = userType + SEPARATOR + randHex + signatureHex;

        return license;
    }
    
    /**
     * Validate the license
     * @param license
     * @return A string of possible errors or null if the license is valid
     */
    public static boolean isValidAdminUser(String passwordKey) throws BaddataException {

        List<String> errors = new ArrayList<String>();
        
        String userTypePart = null;
        try {
            // this will validate the license format and extract the user type part
            passwordKey = validateFormatAndRemoveSeparators( passwordKey );
            
            // extract the user type
            userTypePart = verifyFormatAndExtractUserType( passwordKey );
            if ( userTypePart == null ) {
                // throw new BaddataException( "Unable to verify user license format" );
                return false;
            }
        } catch (Exception e) {
            // throw new BaddataException(e.getMessage());
            return false;
        }

        // take out the signature to compare (end of the string)
        String currSignature = passwordKey.substring(passwordKey.length() - 6);

        //
        // Take out the final 6 chars from the incoming license and create the test license.
        // Start at the encrypted part of the license until 6 from the end.
        //
        String secretAndPartSignature = passwordKey.substring(passwordKey.length() - NUM_CHARS_IN_ENCRYPTED_OCTETS, passwordKey.length() - SECRET_PADDING.length());
        String rawLicense = createRawLicense(userTypePart, secretAndPartSignature);

        try {
            // generate SHA1 hash out of the new test license
            MessageDigest md = MessageDigest.getInstance(AlgoUtil.SHA_2_ALGO);
            byte[] shaBytes = md.digest(rawLicense.getBytes());

            // copy the 1st 3 bytes into our signature
            byte[] signatureBytes = new byte[3];
            System.arraycopy(shaBytes, 0, signatureBytes, 0, signatureBytes.length);

            String testSignatureHex = bytesToHex( signatureBytes, HEX_CHARS );

            // test the equality of the signature
            if ( !currSignature.equals( testSignatureHex ) ) {
                // the signature pulled from the computed license does not match
                errors.add("license key is invalid.");
            }

        } catch ( NoSuchAlgorithmException e ) {
            errors.add(e.getMessage());
        }

        // create a string result of any errors, if any
        if ( CollectionUtils.isNotEmpty( errors ) ) {
            String validationResult = StringUtils.join( errors, ", " );

            throw new BaddataException(validationResult);
        }
        
        // no errors, return true
        return true;
    }
    
    private static String validateFormatAndRemoveSeparators( String license ) throws Exception {
        // check if the license string is null, empty or whitespace
        if (StringUtils.isBlank(license)) {
            throw new Exception("License is empty or null.");
        } else {
            //
            // upper case the license string since it's not empty
            //
            license = license.trim().toUpperCase();

            // !important!
            // remove the separators
            //
            if (license.indexOf(SEPARATOR) != -1) {
                
                // check if we have the correct # of octets
                String[] licenseParts = license.split(SEPARATOR);
                if ( licenseParts.length != NUM_OCTETS ) {
                    throw new Exception("Incorrect encryption format, license is invalid.");
                }
                
                license = license.replaceAll(SEPARATOR, "");
            }

            // we should have 4 (userType is a variable length) octets,
            // with a min length of the 4 octets and the length of each of octet.
            if (license.length() < MIN_CHARS_OF_ALL_OCTETS_SANS_SEPERATOR ) {
                throw new Exception("Incorrect key format, license is invalid.");
            }
        }
        
        return license;
    }
    
    /**
     * Tests the license string for format issues, then extracts the user type of the license string.
     * 
     * @param license
     * @return
     * @throws Exception
     */
    public static String verifyFormatAndExtractUserType( String license ) throws Exception {
        license = validateFormatAndRemoveSeparators( license );

        //
        // get and return the user type part.
        // it will be the first part of the encrypted admin license (i.e. ADMIN-1100-EE11-0011)
        //

        String userTypePart = license.substring(0, license.length() - NUM_CHARS_IN_ENCRYPTED_OCTETS);
        
        // validate the user type
        ErrorType errType = validateLicenseInput( userTypePart );
        
        if ( errType != null && errType != ErrorType.NONE ) {
            String err = errType.getLogError();
            if ( err != null ) {
                throw new Exception( err );
            }
        }
        
        return userTypePart;
    }
    
    /**
     * Validate the model and feature input
     * @param model
     * @param feature
     * @return
     */
    protected static ErrorType validateLicenseInput( String userType ) {
        
        if ( StringUtils.isBlank( userType ) ) {
            return ErrorType.MISSING_USER_TYPE_CODE;
        }

        if ( !UserType.matchesUserType( userType ) ) {
            return ErrorType.UNKNOWN_USER_TYPE_CODE;
        }
        
        return ErrorType.NONE;
    }
    
    /**
     * Convert bytes to hex format
     * @param bytes
     * @return
     */
    private static String bytesToHex(byte[] bytes, char[] hexChars) {
        char[] chars = new char[2 * bytes.length];
        for (int i = 0; i < bytes.length; ++i) {
            chars[2 * i] = hexChars[(bytes[i] & 0xF0) >>> 4];
            chars[2 * i + 1] = hexChars[bytes[i] & 0x0F];
        }
        return new String(chars);
    }

    /**
     * Create 3 bytes of random data
     *
     * @return
     */
    public static String createThreeByteRandomHex() {
        SecureRandom sr = new SecureRandom();
        byte[] rndBytes = new byte[3];
        sr.nextBytes(rndBytes);
        String randNumStr = bytesToHex(rndBytes, HEX_CHARS);
        return randNumStr;
    }
    
    public static String createTwentyByteRandomHex() {
        SecureRandom sr = new SecureRandom();
        byte[] rndBytes = new byte[20];
        sr.nextBytes(rndBytes);
        String randNumStr = bytesToHex(rndBytes, HEX_CHARS);
        return randNumStr;
    }
    
    private static String createRawLicense(String userType, String randHex) {
        String rawLicense = SECRET + userType + randHex + SECRET_PADDING;
        
        return rawLicense;
    }
    
    
}
