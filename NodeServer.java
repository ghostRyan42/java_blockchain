import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        // for (NodeMainInfo peer : peers) {
        //     try (Socket socket = new Socket(peer.getIpAddress(),peer.getPort())) {
        //         ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        //         out.writeObject(transaction);
        //     } catch (IOException e) {
        //         System.out.println(nodeId + ": Échec de la diffusion de la transaction au pair " + peer.getPort());
        //     }
        // }
        for (NodeMainInfo peer : peers) {
            try {
                if (peer.getOutputStream() != null) {
                    peer.getOutputStream().writeObject(transaction);
                    peer.getOutputStream().flush();

                } else {
                    System.out.println("Flux non disponible pour le pair : " + peer.getPort());
                }
            } catch (IOException e) {
                System.out.println(nodeId + ": Échec de la diffusion de la transaction au pair " + peer.getPort() + " : " + e.getMessage());
            }
        }
    }

    private void createTransaction(NodeMainInfo destNode, double amount, double fee) throws Exception {
        Transaction trans = this.myWallet.createTransaction(destNode.getWalletPublicKey(), amount, fee);
        if (trans != null) {
            System.out.println("Transaction créée avec succès.");
            this.pendingTransactions.add(trans);
            broadcastTransaction(trans);
            // Thread.sleep((long) (500 + Math.random() * 1500));
            if (pendingTransactions.size() >= 3) {
                mineNewBlock();
            }
        } else {
            System.out.println(nodeId + ": Échec de la création de la transaction.");
            throw new Exception("fondsInsuffisant");
        }
    }

    private void mineNewBlock() throws Exception {
        Block lastBlock = Blockchain.blockchain.get(Blockchain.blockchain.size() - 1);
        Block newBlock = new Block(Blockchain.blockchain.size(),lastBlock.getHash());
        
        // Ajouter les transactions en attente au nouveau bloc
        for (Transaction tx : pendingTransactions) {
            newBlock.addTransaction(tx);
        }

        newBlock.calculateHash();

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
        System.out.println("Diffusion du bloc miné en cours...");
        for (NodeMainInfo peer : peers) {
            try {
                if (peer.getOutputStream() != null) {
                    peer.getOutputStream().writeObject(block);
                    peer.getOutputStream().flush();
                    System.out.println("Bloc diffusé avec succès au pair : " + peer.getPort());
                }
            } catch (IOException e) {
                System.out.println(nodeId + ": Échec de la diffusion du bloc au pair " + peer.getPort() + " \n " + e);
            }
        }
    }

    public void notifyPeers(NodeMainInfo newPeerInfo) {
        for (NodeMainInfo peer : peers) {
            // Vérifier que le peer n'est pas le nouveau nœud connecté
            if (!peer.equals(newPeerInfo)) {
                try{ 
                    if(peer.getOutputStream() != null){
                        peer.getOutputStream().writeObject(newPeerInfo);
                        peer.getOutputStream().flush();
                    }
                    System.out.println(nodeId + ": Notifié le pair " + peer.getPort() + " du nouveau nœud.");
                } catch (IOException e) {
                    System.out.println(nodeId + ": Échec de notification du pair " + peer.getPort());
                }
            }
        }
    }

    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private NodeServer mainNode;
        private NodeMainInfo peerInfo;

    
        public ClientHandler(Socket socket, NodeServer node) {
            this.clientSocket = socket;
            this.mainNode = node;
        }

        private void sendBlockchain(ObjectOutputStream out) throws IOException {
            out.writeObject(Blockchain.blockchain); // Envoyer la blockchain complète
            out.flush();
            System.out.println("Blockchain envoyée au nœud connecté.");
        }
        
        
        private void handleMessage(Object message, ObjectInputStream in, ObjectOutputStream out) {
            try {
                if (message instanceof Transaction) {
                    Transaction transaction = (Transaction) message;
                    System.out.println(transaction.getRecipient().equals(mainNode.myWallet.getPublicKey()));
                    // System.out.println("xxxxxxxxxxxxxx trans wallet xxxxxxxxxxxxxxxxx");
                    // System.out.println(transaction.getRecipient());


                    if(transaction.getRecipient().equals(mainNode.myWallet.getPublicKey())){
                        System.out.println("xxxxxxxxxxxxx mywallet xxxxxxxxxxxxxxxxxx");
                        System.err.println(mainNode.myWallet.getBalance());
                        mainNode.myWallet.increaseBalance(transaction.getAmount());
                        System.err.println(mainNode.myWallet.getBalance());
                    }
                    System.out.println(this.mainNode.nodeId + ": Transaction reçue - " + transaction);
    
                    if (!this.mainNode.pendingTransactions.contains(transaction)) {
                        this.mainNode.pendingTransactions.add(transaction);
                        Thread.sleep((long) (500 + Math.random() * 1500));
                        // if(this.mainNode.pendingTransactions.size()==3){
                        //     Block lastBlock = Blockchain.blockchain.get(Blockchain.blockchain.size() - 1);
                        //     Block newBlock = new Block(Blockchain.blockchain.size(),lastBlock.getHash());
                            
                        //     // Ajouter les transactions en attente au nouveau bloc
                        //     for (Transaction tx : mainNode.pendingTransactions) {
                        //         newBlock.addTransaction(tx);
                        //     }
                        //     newBlock.calculateHash();

                        //     // newBlock.mineBlock(4);
                        //     if(Blockchain.addBlock(newBlock)){
                        //         System.out.println(mainNode.nodeId + ": Nouveau bloc miné - " + newBlock.getHash());
                        //         mainNode.broadcastBlock(newBlock);
                        //         mainNode.pendingTransactions.clear();
                        //     }

                        // }
                        System.out.println(this.mainNode.nodeId + ": Transaction ajoutée au pool.");
                    }
                } else if (message instanceof Block) {
                    Block block = (Block) message;
                    System.out.println(this.mainNode.nodeId + ": Bloc reçu - " + block.getHash());
    
                    if (block != null) {
                        if(Blockchain.addBlock(block)){
                            // updateWalletBalances(block);
                            this.mainNode.pendingTransactions.removeIf(tx -> block.getTransactions().contains(tx));
                            System.err.println("taille de la liste des transactions en attente après retrait : "+this.mainNode.pendingTransactions.size());
                            System.out.println(this.mainNode.nodeId + ": Bloc ajouté à la blockchain.");
                        };
                    }
                }else if (message instanceof NodeMainInfo) {
                    // Recevoir et stocker les informations du nœud
                    NodeMainInfo peerInfo = (NodeMainInfo) message;
                    peerInfo.setIpAddress(clientSocket.getInetAddress().getHostAddress());
                    peerInfo.setIn(in);
                    peerInfo.setOut(out);
                    System.out.println("Informations reçues du nœud : " + peerInfo.toString());
                    // Vous pouvez ajouter cette information dans la liste de vos pairs (peers)
                    if (!mainNode.peers.contains(peerInfo)) {
                        this.peerInfo = peerInfo;
                        mainNode.peers.add(peerInfo);
                        mainNode.notifyPeers(peerInfo);
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
                NodeMainInfo myInfo = new NodeMainInfo("localhost", mainNode.port, mainNode.myWallet.getPublicKey(), mainNode.myWallet.name);
                out.writeObject(myInfo);
                out.flush();
                System.out.println("informations envoyées avec succès");

                while (true) {
                    try {
                        // Lire l'objet envoyé par le client
                        Object message = in.readObject();
    
                        // Gérer le message
                        // System.out.println(message);
                        handleMessage(message, in, out);
    
                        // Réponse au client
                        // out.writeObject(new Message("transaction reçu avec succès", "success"));
                    } catch (ClassNotFoundException e) {
                        System.out.println("Objet inconnu reçu : " + e.getMessage());
                        out.writeObject(new Message("format de message non reconnu", "error"));
                    }
                }
            } catch (IOException e) {
                System.out.println("Erreur de communication avec le client : " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                    System.out.println("Connexion fermée pour : " + clientSocket.getInetAddress());
                    mainNode.peers.removeIf(peer ->  peer.getWalletPublicKey() == this.peerInfo.getWalletPublicKey());
                    System.out.println("Nœud supprimé de la liste des pairs : "+mainNode.peers.size());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    

    private List<Map<String, Object>> serializeTransactions(List<Transaction> transactions) {
        List<Map<String, Object>> serializedTransactions = new ArrayList<>();
        for (Transaction tx : transactions) {
            Map<String, Object> transactionMap = new HashMap<>();
            transactionMap.put("id", tx.getTransactionId());
            transactionMap.put("timestamp", tx.getFormattedTimestamp());
            transactionMap.put("sender", tx.getSender().toString());      // Clé publique convertie en chaîne
            transactionMap.put("recipient", tx.getRecipient().toString()); // Clé publique convertie en chaîne
            transactionMap.put("amount", tx.getAmount());
            transactionMap.put("transactionFee", tx.getTransactionFee());
            serializedTransactions.add(transactionMap);
        }
        return serializedTransactions;
    }
    
    
    private void startHttpServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8083), 0);
        System.out.println("Serveur HTTP démarré sur le port 8083");
    
        // Route principale pour servir le fichier HTML
        server.createContext("/", exchange -> {
            File file = new File("index.html"); // Fichier HTML à servir
            if (file.exists()) {
                byte[] response = Files.readAllBytes(file.toPath());
                exchange.sendResponseHeaders(200, response.length);
                OutputStream os = exchange.getResponseBody();
                os.write(response);
                os.close();
            } else {
                sendJsonError(exchange, 404, "Page non trouvée");
            }
        });
    
        // Endpoint pour obtenir les informations du nœud
        server.createContext("/node/info", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                String response = new Gson().toJson(new NodeInfoResponse(nodeId, port, myWallet.getBalance()));
                sendJsonResponse(exchange, response);
            }
        });
    
        // Endpoint pour se connecter à un pair
        server.createContext("/node/connect", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                try (InputStream is = exchange.getRequestBody();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    String[] params = sb.toString().split("&");
                    String host = params[0].split("=")[1];
                    int peerPort = Integer.parseInt(params[1].split("=")[1]);
    
                    connectToPeer(host, peerPort);
                    String response = "{\"message\": \"Connecté au pair avec succès.\"}";
                    sendJsonResponse(exchange, response);
                } catch (Exception e) {
                    sendJsonError(exchange, 500, "Erreur lors de la connexion au pair.");
                }
            }
        });
    
        // Endpoint pour créer une transaction
        server.createContext("/transaction/create", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                try (InputStream is = exchange.getRequestBody();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    String[] params = sb.toString().split("&");
                    String peerWalletKey = params[0].split("=")[1];
                    double amount = Double.parseDouble(params[1].split("=")[1]);
                    double fee = Double.parseDouble(params[2].split("=")[1]);
    
                    NodeMainInfo destNode = peers.stream()
                        .filter(peer -> new Gson().toJson(peer.getWalletPublicKey().toString().replace("\n", "").trim()).equals(new Gson().toJson(peerWalletKey)))
                        .findFirst()
                        .orElseThrow(() -> new Exception("Peer not found"));
                    createTransaction(destNode, amount, fee);
                    String response = "{\"message\": \"Transaction créée avec succès.\"}";
                    sendJsonResponse(exchange, response);
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    if(e.getMessage().equals("fondsInsuffisant")){
                        sendJsonError(exchange, 405, "Fonds insuffisants pour effectuer cette transaction.");
                    }else{
                        sendJsonError(exchange, 500, "Erreur lors de la création de la transaction.");
                    }
                }
            }
        });
    
        // Endpoint pour obtenir la blockchain
        server.createContext("/blockchain", exchange -> {
            try {
                if ("GET".equals(exchange.getRequestMethod())) {
                    // Transformation des blocs en une structure sérialisable
                    List<Map<String, Object>> serializedBlockchain = new ArrayList<>();
        
                    for (Block block : Blockchain.blockchain) {
                        Map<String, Object> blockMap = new HashMap<>();
                        blockMap.put("index", block.getindex());
                        blockMap.put("timestamp", block.getTimestamp());
                        blockMap.put("transactions", serializeTransactions(block.getTransactions())); // Sérialiser les transactions
                        blockMap.put("previousHash", block.getPreviousHash());
                        blockMap.put("hash", block.getHash());
                        blockMap.put("nonce", block.getNonce());
        
                        serializedBlockchain.add(blockMap);
                    }
        
                    // Sérialisation en JSON
                    String response = new Gson().toJson(serializedBlockchain);
                    sendJsonResponse(exchange, response);
                }
            } catch (Exception e) {
                System.err.println("Erreur lors de la récupération de la blockchain: " + e.getMessage());
                e.printStackTrace();
                sendJsonError(exchange, 500, "Erreur interne du serveur");
            }
        });
    
        // Route pour récupérer le nombre de pairs
        server.createContext("/node/peers", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
            Map<String, Object> responseMap = new HashMap<>();
            responseMap.put("connectedPeers", peers.size());
            
            List<Map<String, Object>> peersInfo = new ArrayList<>();
            for (NodeMainInfo peer : peers) {
                Map<String, Object> peerMap = new HashMap<>();
                peerMap.put("ipAddress", peer.getIpAddress());
                peerMap.put("port", peer.getPort());
                peerMap.put("walletPublicKey", peer.getWalletPublicKey().toString());
                peerMap.put("name", peer.walletName);
                peersInfo.add(peerMap);
            }
            responseMap.put("peers", peersInfo);
            
            String response = new Gson().toJson(responseMap);
            sendJsonResponse(exchange, response);
            }
        });
    
        // Route pour récupérer les transactions en attente
        server.createContext("/node/pending-transactions", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                try {
                    // Vérification de null
                    if (pendingTransactions == null) {
                        pendingTransactions = new ArrayList<>(); // ou la structure appropriée
                    }
                    ArrayList<Map<String, String>> tempTrans = new ArrayList<>();
                    for (Transaction tx : pendingTransactions) {
                        Map<String, String> tempTx = new HashMap<>();
                        tempTx.put("id", tx.getTransactionId());
                        tempTx.put("timestamp", tx.getFormattedTimestamp());
                        tempTx.put("sender", tx.getSender().toString());
                        tempTx.put("recipient", tx.getRecipient().toString());
                        tempTx.put("amount", String.valueOf(tx.getAmount()));
                        tempTx.put("transactionFee", String.valueOf(tx.getTransactionFee()));
                        tempTrans.add(tempTx);
                    }
                    Gson gson = new Gson();
                    String response = gson.toJson(tempTrans);
                    sendJsonResponse(exchange, response);
                } catch (Exception e) {
                    System.err.println("Erreur lors de la récupération des transactions en attente: " + e.getMessage());
                    e.printStackTrace();
                    sendJsonError(exchange, 500, "Erreur interne du serveur");
                }
            }
        });
    
        server.start();
    }
    
    private void sendJsonResponse(HttpExchange exchange, String jsonResponse) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonResponse.getBytes());
        }
    }
    
    private void sendJsonError(HttpExchange exchange, int statusCode, String errorMessage) throws IOException {
        String response = String.format("{\"error\": \"%s\"}", errorMessage);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
    
    // Classe de réponse pour les informations de noeud
    private static class NodeInfoResponse {
        private String nodeId;
        private int port;
        private double balance;
    
        public NodeInfoResponse(String nodeId, int port, double balance) {
            this.nodeId = nodeId;
            this.port = port;
            this.balance = balance;
        }
    }

    public class PublicKeyAdapter extends TypeAdapter<PublicKey> {
        @Override
        public void write(JsonWriter out, PublicKey value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            // On convertit la clé en format Base64
            out.value(Base64.getEncoder().encodeToString(value.getEncoded()));
        }

        @Override
        public PublicKey read(JsonReader in) throws IOException {
            // La désérialisation n'est pas nécessaire pour votre cas,
            // mais il faut l'implémenter pour le TypeAdapter
            return null;
        }
    }
    
    
 
    public static void main(String[] args) throws NoSuchAlgorithmException, IOException {
            NodeServer node1 = new NodeServer("node1", 8080);
            node1.setWalletAmount(200);
            node1.myWallet.name="DAVID";
            Blockchain.initialize();
            new Thread(() -> {
                try {
                    node1.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            node1.startHttpServer();

        }
}
