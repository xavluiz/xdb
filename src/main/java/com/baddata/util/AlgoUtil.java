/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.util;

import java.io.IOException;
import java.math.BigInteger;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;

import com.baddata.log.Logger;
import com.baddata.util.FBUserUtil.DecodedSignedRequest;
import com.google.gson.Gson;

import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;

public class AlgoUtil {

    private static Logger logger = Logger.getLogger(AlgoUtil.class.getName());
    
    public static final String AES_ALGO = "AES".intern();
    public static final String MD5_ALGO = "MD5".intern();
    public static final String HMAC_SHA256_ALGO = "HMACSHA256".intern();
    public static final String HMAC_SHA256_ALGO_NAME = "HMAC-SHA256".intern();
    public static final String ISO_8859_1_STR = "ISO-8859-1".intern();
    public static final String SHA_2_ALGO = "SHA-1".intern();
    
    private static final byte[] keyValue = new byte[] {
        'T', 'h', 'e',
        'B', 'e', 's', 't',
        'S', 'e', 'c', 'r', 'e', 't',
        'K', 'e', 'y' };

    public static String encryptAES(String Data) throws Exception {
        Key key = generateAESkey();
        Cipher c = Cipher.getInstance(AES_ALGO);
        c.init(Cipher.ENCRYPT_MODE, key);
        byte[] encVal = c.doFinal(Data.getBytes());
        String encryptedValue = new BASE64Encoder().encode(encVal);
        return encryptedValue;
    }
    

    public static String decryptAES(String encryptedData) throws Exception {
        Key key = generateAESkey();
        Cipher c = Cipher.getInstance(AES_ALGO);
        c.init(Cipher.DECRYPT_MODE, key);
        byte[] decordedValue = new BASE64Decoder().decodeBuffer(encryptedData);
        byte[] decValue = c.doFinal(decordedValue);
        String decryptedValue = new String(decValue);
        return decryptedValue;
    }
    

    private static Key generateAESkey() throws Exception {
        Key key = new SecretKeySpec(keyValue, AES_ALGO);
        return key;
    }

    public static String hashToMD5(String value) {
        String hashword = null;
        try {
            MessageDigest md5 = MessageDigest.getInstance(MD5_ALGO);
            md5.update(value.getBytes());
            BigInteger hash = new BigInteger(1, md5.digest());
            hashword = hash.toString(16);
        } catch (NoSuchAlgorithmException e) {
            // defaulting to hashcode
            value = String.valueOf(value.hashCode());
            logger.error("Algorithm " + MD5_ALGO + " not supported.", e);
        }
        return pad(hashword, 32, '0');
    }

    private static String pad(String s, int length, char pad) {
        StringBuffer buffer = new StringBuffer(s);
        while (buffer.length() < length) {
            buffer.insert(0, pad);
        }
        return buffer.toString();
    }
    
    public static byte[] base64_url_decode(String input) throws IOException {
        return new Base64(true).decode(input);
    }

    public static DecodedSignedRequest parse_signed_request(String input, String secret) throws Exception {
        return parse_signed_request(input, secret, 3600);
    }
    
    public static DecodedSignedRequest parse_signed_request(String input, String secret, int max_age) throws Exception {
        String[] split = input.split("[.]", 2);

        String encoded_sig = split[0];
        String encoded_envelope = split[1];
        
        String jsonPayload = new String(new Base64(true).decode(encoded_envelope));
        DecodedSignedRequest decodedSignedReq = new Gson().fromJson(jsonPayload, DecodedSignedRequest.class);

        String algorithm = decodedSignedReq.getAlgorithm();

        if ( !algorithm.equalsIgnoreCase( HMAC_SHA256_ALGO_NAME ) ) {
            throw new Exception("Invalid request. (Unsupported algorithm.)");
        }

        byte[] key = secret.getBytes();
        SecretKey hmacKey = new SecretKeySpec(key, HMAC_SHA256_ALGO);
        Mac mac = Mac.getInstance(HMAC_SHA256_ALGO);
        mac.init(hmacKey);
        byte[] digest = mac.doFinal(encoded_envelope.getBytes());
        
        byte[] sigBytes = base64_url_decode(encoded_sig);

        // compare the encoded signature bytes and the expected signature
        if ( !Arrays.equals(sigBytes, digest) ) {
            throw new Exception("Invalid request. (Invalid signature.)");
        }

        return decodedSignedReq;
    }

}
