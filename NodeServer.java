import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;


public class NodeServer {
    private int port;
    private String nodeId;
    // private Blockchain blockchain;
    private Wallet myWallet;
    private List<NodeMainInfo> peers = new ArrayList<>();
    private List<Transaction> pendingTransactions = new ArrayList<>();
    private static final Logger logger = Logger.getLogger(NodeServer.class.getName());

    public NodeServer(String nodeId,int port) throws NoSuchAlgorithmException {
        this.port = port;
        this.nodeId = nodeId;
        // if(Blockchain.blockchain.isEmpty()){
        //     createGenesisBlock();
        // }
        this.myWallet = new Wallet(0);
        // blockchain = new Blockchain();
    }

    public void setWalletAmount(double balance){
        myWallet.setBalance(balance);
    }
    public int getPort(){
        return this.port;
    }

    private void createGenesisBlock() {
        Block genesisBlock = new Block(0,null);
        genesisBlock.mineBlock(4);
        boolean result = Blockchain.addBlock(genesisBlock);
    }

    // Démarrer le serveur pour écouter les connexions entrantes
    public void start() throws NoSuchAlgorithmException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("NodeServer en écoute sur le port : " + port);

            while (true) {
                // Accepter une connexion client
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connexion acceptée : " + clientSocket.getInetAddress()+" : "+clientSocket.getPort());
                // peers.add(new NodeServer("nodeClient"+peers.size()+1, clientSocket.getPort()));

                // Traiter la connexion dans un thread dédié
                new Thread(new ClientHandler(clientSocket,this)).start();
                System.out.println("Thread ClientHandler démarré pour : " + clientSocket.getInetAddress());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void connectToPeer(String host, int peerPort) throws NoSuchAlgorithmException {
        try {
            Socket socket = new Socket(host, peerPort);
            System.out.println(nodeId + ": Connecté au pair sur le port " + peerPort);
            // peers.add(new NodeServer("nodeClient"+peers.size()+1, peerPort));
            new Thread(new ClientHandler(socket,this)).start();
        } catch (IOException e) {
            System.out.println(nodeId + ": Impossible de se connecter au pair sur le port " + peerPort);
        }
    }

    private void broadcastTransaction(Transaction transaction) {
        for (NodeMainInfo peer : peers) {
            try (Socket socket = new Socket(peer.getIpAddress(),peer.getPort())) {
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.writeObject(transaction);
            } catch (IOException e) {
                System.out.println(nodeId + ": Échec de la diffusion de la transaction au pair " + peer.getPort());
            }
        }
    }

    private void createTransaction(NodeServer destNode, double amount, double fee) throws Exception{
        Transaction trans= this.myWallet.createTransaction(destNode.myWallet.getPublicKey(), amount, fee);
        if(trans != null){
            this.pendingTransactions.add(trans);
            broadcastTransaction(trans);

            if (pendingTransactions.size() >= 3) {
                mineNewBlock();
            }
        }else{
            System.out.println(nodeId + ": Échec de la creation de la transaction ");
        }
    }

    private void mineNewBlock() throws Exception {
        Block lastBlock = Blockchain.blockchain.get(Blockchain.blockchain.size() - 1);
        Block newBlock = new Block(Blockchain.blockchain.size()+1,lastBlock.getHash());
        
        // Ajouter les transactions en attente au nouveau bloc
        for (Transaction tx : pendingTransactions) {
            newBlock.addTransaction(tx);
        }

        // newBlock.mineBlock(4);
        if(Blockchain.addBlock(newBlock)){
            System.out.println(nodeId + ": Nouveau bloc miné - " + newBlock.getHash());
            pendingTransactions.clear();
        }
        // blockchain.add(newBlock);

        // Diffuser le nouveau bloc
        broadcastBlock(newBlock);
    }

    private void broadcastBlock(Block block) {
        for (NodeMainInfo peer : peers ) {
            try (Socket socket = new Socket(peer.getIpAddress(),peer.getPort())) {
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                out.writeObject(block);
            } catch (IOException e) {
                System.out.println(nodeId + ": Échec de la diffusion du bloc au pair " + peer.getPort());
            }
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private NodeServer mainNode;
    
        public ClientHandler(Socket socket, NodeServer node) {
            this.clientSocket = socket;
            this.mainNode = node;
        }

        private void sendBlockchain(ObjectOutputStream out) throws IOException {
            out.writeObject(Blockchain.blockchain); // Envoyer la blockchain complète
            out.flush();
            System.out.println("Blockchain envoyée au nœud connecté.");
        }
        
        
        private void handleMessage(Object message) {
            try {
                if (message instanceof Transaction) {
                    Transaction transaction = (Transaction) message;
                    System.out.println("xxxxxxxxxxxxx mywallet xxxxxxxxxxxxxxxxxx");
                    System.out.println(mainNode.myWallet.getPublicKey());
                    System.out.println("xxxxxxxxxxxxxx trans wallet xxxxxxxxxxxxxxxxx");
                    System.out.println(transaction.getRecipient());

                    if(transaction.getRecipient()==mainNode.myWallet.getPublicKey()){
                        System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
                        mainNode.myWallet.increaseBalance(transaction.getAmount());
                    }
                    System.out.println(this.mainNode.nodeId + ": Transaction reçue - " + transaction);
    
                    if (!this.mainNode.pendingTransactions.contains(transaction)) {
                        this.mainNode.pendingTransactions.add(transaction);
                        if(this.mainNode.pendingTransactions.size()==3){
                            Block lastBlock = Blockchain.blockchain.get(Blockchain.blockchain.size() - 1);
                            Block newBlock = new Block(Blockchain.blockchain.size()+1,lastBlock.getHash());
                            
                            // Ajouter les transactions en attente au nouveau bloc
                            for (Transaction tx : mainNode.pendingTransactions) {
                                newBlock.addTransaction(tx);
                            }

                            // newBlock.mineBlock(4);
                            if(Blockchain.addBlock(newBlock)){
                                System.out.println(mainNode.nodeId + ": Nouveau bloc miné - " + newBlock.getHash());
                                mainNode.pendingTransactions.clear();
                            }

                        }
                        System.out.println(this.mainNode.nodeId + ": Transaction ajoutée au pool.");
                    }
                } else if (message instanceof Block) {
                    Block block = (Block) message;
                    System.out.println(this.mainNode.nodeId + ": Bloc reçu - " + block.getHash());
    
                    if (block != null) {
                        if(Blockchain.addBlock(block)){
                            // updateWalletBalances(block);
                            this.mainNode.pendingTransactions.removeIf(tx -> block.getTransactions().contains(tx));
                            System.out.println(this.mainNode.nodeId + ": Bloc ajouté à la blockchain.");
                        };
                    }
                }else if (message instanceof NodeMainInfo) {
                    // Recevoir et stocker les informations du nœud
                    NodeMainInfo peerInfo = (NodeMainInfo) message;
                    System.out.println("Informations reçues du nœud : " + peerInfo);
                    // Vous pouvez ajouter cette information dans la liste de vos pairs (peers)
                    if (!mainNode.peers.contains(peerInfo)) {
                        mainNode.peers.add(peerInfo);
                        System.out.println("Pair ajouté à la liste des pairs.");
                    }
                }
                 else {
                    System.out.println("Commande non reconnue : " + message);
                }
            } catch (Exception e) {
                System.out.println("Erreur lors du traitement du message : " + e.getMessage());
            }
        }
    
        @Override
        public void run() {
            System.out.println("ClientHandler démarré pour : " + clientSocket.getInetAddress());
            try (
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
            ) {
                System.out.println("Noeud connecté : " + clientSocket.getInetAddress()+clientSocket.getPort());
                sendBlockchain(out);
                NodeMainInfo myInfo = new NodeMainInfo("localhost", mainNode.port, mainNode.myWallet.getPublicKey());
                out.writeObject(myInfo);
                out.flush();
                System.out.println("informations envoyées avec succès");

                while (true) {
                    try {
                        // Lire l'objet envoyé par le client
                        Object message = in.readObject();
    
                        // Gérer le message
                        System.out.println(message);
                        handleMessage(message);
    
                        // Réponse au client
                        out.writeObject("Message traité avec succès.");
                    } catch (ClassNotFoundException e) {
                        System.out.println("Objet inconnu reçu : " + e.getMessage());
                        out.writeObject("Erreur : format de message non reconnu.");
                    }
                }
            } catch (IOException e) {
                System.out.println("Erreur de communication avec le client : " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    

 
    public static void main(String[] args) throws NoSuchAlgorithmException {

        NodeServer node1 = new NodeServer("node1", 8080);
        node1.setWalletAmount(200);
        Blockchain.initialize();
        node1.start();
        }
}
