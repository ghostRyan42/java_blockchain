import java.io.Serializable;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.UUID;

public class Transaction implements Serializable {
    private String transactionId;    // ID unique
    private PublicKey sender;        // Clé publique de l'expéditeur
    private PublicKey recipient;     // Clé publique du destinataire
    private double amount;           // Montant à transférer
    private double transactionFee;   // Frais de transaction
    private long timestamp;          // Horodatage
    private byte[] signature;        // Signature pour valider la transaction

    // Constructeur
    public Transaction(PublicKey sender, PublicKey recipient, double amount, double transactionFee) {
        this.transactionId = UUID.randomUUID().toString();
        this.sender = sender;
        this.recipient = recipient;
        this.amount = amount;
        this.transactionFee = transactionFee;
        this.timestamp = Instant.now().toEpochMilli(); // Horodatage actuel
    }

    // Générer la signature de la transaction
    public void generateSignature(PrivateKey privateKey) throws Exception {
        String data = getData();
        this.signature = SignatureUtil.signData(data,privateKey);
    }

    // Vérifier la signature de la transaction
    public boolean verifySignature() throws Exception {
        String data = getData();
        return SignatureUtil.verifySignature( data, signature,sender);
    }

    // Obtenir les données à signer
    private String getData() {
        return sender.toString() + recipient.toString() + Double.toString(amount) + Double.toString(transactionFee) + Long.toString(timestamp);
    }

    // Getter pour le montant total (inclut les frais)
    public double getTotalAmount() {
        return amount + transactionFee;
    }

    // Getter et Setter (pour chaque attribut)
    public String getTransactionId() {
        return transactionId;
    }

    public PublicKey getSender() {
        return sender;
    }

    public PublicKey getRecipient() {
        return recipient;
    }

    public double getAmount() {
        return amount;
    }

    public double getTransactionFee() {
        return transactionFee;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
