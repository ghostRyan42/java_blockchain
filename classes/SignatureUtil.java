import java.security.*;
import java.util.Base64;

public class SignatureUtil {

    // Générer une paire de clés (clé privée et clé publique)
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC"); // Algorithme ECDSA
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        keyGen.initialize(256, random); // Taille de la clé
        return keyGen.generateKeyPair();
    }

    // Signer des données avec une clé privée
    public static byte[] signData(String data, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(data.getBytes());
        return signature.sign(); // Retourner directement les bytes
    }
    

    // Vérifier la signature avec une clé publique
    public static boolean verifySignature(String data, byte[] signatureBytes, PublicKey publicKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initVerify(publicKey);
        signature.update(data.getBytes());
        // byte[] signatureBytes = Base64.getDecoder().decode(signatureStr); // Décoder la signature en Base64
        return signature.verify(signatureBytes);
    }

    public static String applySha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
