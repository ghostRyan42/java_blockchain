import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Block implements Serializable {
    private int index;
    private long timestamp;
    private List<Transaction> transactions;  // Liste des transactions financières
    private String previousHash;
    private String hash;
    private int nonce;

    // Constructeur
    public Block(int index, String previousHash) {
        this.index = index;
        this.timestamp = System.currentTimeMillis();;
        this.transactions = new ArrayList<>();
        this.previousHash = previousHash;
        this.hash = calculateHash();
    }

    // Calculer le hash du bloc
    public String calculateHash() {
        String dataToHash = index + Long.toString(timestamp) + transactions.toString() + previousHash + nonce;
        String _hash = SignatureUtil.applySha256(dataToHash);
        this.hash=_hash;
        return hash;
    }
    public int getindex(){
        return this.index;
    }

    // Ajouter une transaction valide
    public boolean addTransaction(Transaction transaction) throws Exception {
        if (transaction.verifySignature()) {
            this.transactions.add(transaction);
            Blockchain.updateBalances(transaction); // Mise à jour des soldes
            return true;
        } else {
            System.out.println("Transaction invalide.");
            return false;
        }
    }
    

    // Preuve de travail (minage)
    public void mineBlock(int difficulty) {
        String target = new String(new char[difficulty]).replace('\0', '0');
        while (!hash.substring(0, difficulty).equals(target)) {
            nonce++;
            hash = calculateHash();
        }
    }

    // Getters
    public List<Transaction> getTransactions() {
        return transactions;
    }

    public String getHash() {
        return hash;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    @Override
    public String toString() {
        return "Block [index=" + index + ", timestamp=" + timestamp + ", transactions=" + transactions + ", previousHash=" + previousHash + ", hash=" + hash + ", nonce=" + nonce + "]";
    }
}
