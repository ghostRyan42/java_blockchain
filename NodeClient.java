import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;



public class NodeClient {
    private int port;
    private String nodeId;
    private InetAddress nodeAdressIp;
    // private Blockchain blockchain;
    private Wallet myWallet;
    private List<NodeMainInfo> peers = new ArrayList<>();
    private List<Transaction> pendingTransactions = new ArrayList<>();

    public NodeClient(String nodeId,int port, InetAddress adressIp) throws NoSuchAlgorithmException {
        this.port = port;
        this.nodeId = nodeId;
        this.nodeAdressIp = adressIp;
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

    // Démarrer le serveur pour écouter les connexions entrantes
    public void start() throws NoSuchAlgorithmException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("NodeClient en écoute sur le port : " + port);

            while (true) {
                // Accepter une connexion client
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connexion acceptée : " + clientSocket.getInetAddress());

                // Traiter la connexion dans un thread dédié
                new Thread(new ClientHandler(clientSocket,this)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void connectToPeer(String host, int peerPort) throws NoSuchAlgorithmException, ClassNotFoundException {
        try {
            System.out.println("connection au noeud server avec succès ...");
            Socket socket = new Socket(host, peerPort);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            Object receivedData = in.readObject();

            if (receivedData instanceof List) {
                Blockchain.blockchain = (List<Block>) receivedData;
                System.out.println("Blockchain reçue : Taille = " + Blockchain.blockchain.size());
            }

            receivedData = in.readObject();

            if (receivedData instanceof NodeMainInfo) {
                NodeMainInfo serverInfo = (NodeMainInfo) receivedData;
                System.out.println("Informations reçues du serveur : " + serverInfo);
                
                // Ajouter le serveur à la liste des pairs
                serverInfo.setOut(out);
                serverInfo.setIn(in);

                peers.add(serverInfo);
            }

            NodeMainInfo myInfo = new NodeMainInfo("localhost", port, myWallet.getPublicKey());
            out.writeObject(myInfo);
            out.flush();

            System.out.println(nodeId + ": Connecté au pair sur le port " + peerPort);
            new Thread(new ClientHandler(socket,this)).start();

        } catch (IOException e) {
            System.out.println(nodeId + ": Impossible de se connecter au pair sur le port " + peerPort + " : " + e.getMessage());
        }
    }

    private void broadcastTransaction(Transaction transaction) throws ClassNotFoundException {
        System.out.println("debut du broadcast...");
        for (NodeMainInfo peer : peers) {
            try {
                if (peer.getOutputStream() != null) {
                    peer.getOutputStream().writeObject(transaction);
                    peer.getOutputStream().flush();

                    // Object receivedData = peer.getInputStream().readObject();
                    // if(receivedData instanceof Message){
                    //     Message message = (Message) receivedData;
                    //     if(message.getCode().equals("success")){
                    //         System.out.println("Transaction envoyée avec succès au pair : " + peer.getPort());
                    //     }else{
                    //         System.out.println("Échec de la transaction au pair : " + peer.getPort());
                    //     }
                    // }
                } else {
                    System.out.println("Flux non disponible pour le pair : " + peer.getPort());
                }
            } catch (IOException e) {
                System.out.println(nodeId + ": Échec de la diffusion de la transaction au pair " + peer.getPort() + " : " + e.getMessage());
            }
        }
        
    }

    private void createTransaction(NodeMainInfo destNode, double amount, double fee) throws Exception{
        Transaction trans= this.myWallet.createTransaction(destNode.getWalletPublicKey(), amount, fee);
        System.out.println("transaction créé avec succès");
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
        private NodeClient mainNode;
    
        public ClientHandler(Socket socket, NodeClient node) {
            this.clientSocket = socket;
            this.mainNode = node;
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
                } else {
                    System.out.println("Commande non reconnue : " + message);
                }
            } catch (Exception e) {
                System.out.println("Erreur lors du traitement du message : " + e.getMessage());
            }
        }
    
        @Override
        public void run() {
            try (
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())
            ) {
                System.out.println("Noeud connecté : " + clientSocket.getInetAddress());
    
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
        // Créer le premier nœud (node1) et le démarrer dans un thread séparé
        NodeClient node1 = new NodeClient("node1", 8081, null);
        node1.setWalletAmount(100);
        new Thread(() -> {
            try {
                node1.start();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }).start();
    
        // Connecter le noeud à un pair
        try {
            Thread.sleep(1000); // Attendre que le serveur démarre
            node1.connectToPeer("localhost", 8080);
            System.out.println(Blockchain.blockchain.size());
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            node1.createTransaction(node1.peers.get(0), 20.0, 0.1);
        } catch (Exception e) {
            System.out.println("Erreur lors de la création de la transaction : " + e.getMessage());
        }
    }
}

