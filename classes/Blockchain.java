import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Blockchain {
    public static List<Block> blockchain = new ArrayList<>();
    private static HashMap<PublicKey, Double> balances = new HashMap<>();

    public static synchronized void initialize() {
        if (blockchain.isEmpty()) {
            Block genesisBlock = new Block(0, null);
            genesisBlock.mineBlock(4);
            blockchain.add(genesisBlock);
            System.out.println("Bloc Genesis créé : " + genesisBlock.getHash());
            System.err.println(blockchain.size());
        }
    }

    public static boolean isChainValid() {
        Block currentBlock;
        Block previousBlock;

        for (int i = 1; i < blockchain.size(); i++) {
            currentBlock = blockchain.get(i);
            previousBlock = blockchain.get(i - 1);

            if (!currentBlock.getHash().equals(currentBlock.calculateHash())) {
                return false;
            }

            if (!currentBlock.getPreviousHash().equals(previousBlock.getHash())) {
                return false;
            }
        }
        return true;
    }

    public static boolean addBlock(Block newBlock) {
        if (blockchain.isEmpty()) {
            // Vérifier que le bloc Genesis a un index égal à 0 et aucun précédent
            if (newBlock.getindex() != 0 || newBlock.getPreviousHash() != null) {
                System.out.println("Erreur : Le bloc Genesis est invalide.");
                return false;
            }
            blockchain.add(newBlock);
            System.out.println("Bloc Genesis ajouté avec succès !");
            return true;
        }
        // Obtenir le dernier bloc actuel
        Block lastBlock = blockchain.get(blockchain.size() - 1);
    
        // Vérifier que le hash précédent correspond
        if (!newBlock.getPreviousHash().equals(lastBlock.getHash())) {
            System.out.println("Erreur : Le hash précédent ne correspond pas.");
            return false;
        }
    
        // Vérifier que le hash du bloc est valide
        if (!newBlock.getHash().equals(newBlock.calculateHash())) {
            System.out.println("Erreur : Le hash du bloc est invalide.");
            return false;
        }
    
        // Ajouter le bloc valide à la blockchain
        blockchain.add(newBlock);
        System.out.println("Bloc ajouté avec succès !");
        return true;
    }
    

    // Mettre à jour les soldes après chaque transaction
    public static void updateBalances(Transaction transaction) {
        PublicKey sender = transaction.getSender();
        PublicKey recipient = transaction.getRecipient();
        double totalAmount = transaction.getTotalAmount();
    
        // Vérifier si les soldes existent, sinon les initialiser
        balances.putIfAbsent(sender, 0.0);
        balances.putIfAbsent(recipient, 0.0);
    
        // Mettre à jour les soldes
        balances.put(sender, balances.get(sender) - totalAmount);
        balances.put(recipient, balances.get(recipient) + transaction.getAmount());
    }

    public static double getBalance(PublicKey key) {
        return balances.getOrDefault(key, 0.0);
    }
}
