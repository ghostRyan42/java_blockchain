import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

public class Wallet {
    private PrivateKey privateKey;   // Clé privée
    private PublicKey publicKey;     // Clé publique
    private double balance;          // Solde du portefeuille
    public String name;

    public Wallet(double balance) throws NoSuchAlgorithmException {
        KeyPair keyPair = SignatureUtil.generateKeyPair();
        this.privateKey = keyPair.getPrivate();
        this.publicKey = keyPair.getPublic();
        this.balance = balance; // Solde initial
        this.name="";
    }

    // Créer une transaction
    public Transaction createTransaction(PublicKey recipient, double amount, double fee) {
        // double senderBalance = Blockchain.getBalance(this.publicKey);
    
        if (balance < amount + fee) {
            System.out.println("Fonds insuffisants pour effectuer cette transaction.");
            return null;
        }
        Transaction transaction = new Transaction(publicKey, recipient, amount, fee);
        try {
            transaction.generateSignature(privateKey);
            balance=balance-(amount + fee);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return transaction;
    }
    

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public void increaseBalance(double balance) {
        this.balance +=balance;
    }

    public double getBalance() {
        return balance;
    }
}
