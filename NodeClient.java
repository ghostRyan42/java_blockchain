import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.*;
import java.net.InetAddress;
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




public class NodeClient {
    private int port;
    private String nodeId;
    private InetAddress nodeAdressIp;
    private Wallet myWallet;
    private List<NodeMainInfo> peers = new ArrayList<>();
    private List<Transaction> pendingTransactions = new ArrayList<>();

    public NodeClient(String nodeId, int port, InetAddress adressIp) throws NoSuchAlgorithmException {
        this.port = port;
        this.nodeId = nodeId;
        this.nodeAdressIp = adressIp;
        this.myWallet = new Wallet(0);
    }

    public void setWalletAmount(double balance) {
        myWallet.setBalance(balance);
    }

    public int getPort() {
        return this.port;
    }

    public void start() throws NoSuchAlgorithmException, ClassNotFoundException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log("NodeClient en écoute sur le port : " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                log("Connexion acceptée depuis : " + clientSocket.getInetAddress());

                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());

                Object receivedData = in.readObject();
                if (receivedData instanceof NodeMainInfo) {
                    NodeMainInfo serverInfo = (NodeMainInfo) receivedData;
                    log("Informations reçues du client : " + serverInfo);
                    serverInfo.setOut(out);
                    serverInfo.setIn(in);
                    peers.add(serverInfo);
                }

                new Thread(new ClientHandler(clientSocket, out, in, this)).start();
            }
        } catch (IOException e) {
            logError("Erreur lors de l'écoute des connexions entrantes", e);
        }
    }

    public void connectToPeer(String host, int peerPort) throws NoSuchAlgorithmException, ClassNotFoundException {
        try {
            log("Connexion au noeud serveur en cours...");
            Socket socket = new Socket(host, peerPort);
            ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());

            Object receivedData = in.readObject();

            if (receivedData instanceof List) {
                Blockchain.blockchain = (List<Block>) receivedData;
                log("Blockchain reçue : Taille = " + Blockchain.blockchain.size());
            }

            receivedData = in.readObject();
            if (receivedData instanceof NodeMainInfo) {
                NodeMainInfo serverInfo = (NodeMainInfo) receivedData;
                log("Informations reçues du serveur : " + serverInfo);
                serverInfo.setOut(out);
                serverInfo.setIn(in);
                peers.add(serverInfo);
            }

            NodeMainInfo myInfo = new NodeMainInfo("localhost", port, myWallet.getPublicKey());
            out.writeObject(myInfo);
            out.flush();

            log(nodeId + ": Connecté au pair sur le port " + peerPort);
            new Thread(new ClientHandler(socket, out, in, this)).start();

        } catch (IOException e) {
            logError(nodeId + ": Impossible de se connecter au pair sur le port " + peerPort, e);
        }
    }

    private void broadcastTransaction(Transaction transaction) throws ClassNotFoundException {
        log("Début de la diffusion de la transaction...");
        for (NodeMainInfo peer : peers) {
            try {
                if (peer.getOutputStream() != null) {
                    peer.getOutputStream().writeObject(transaction);
                    peer.getOutputStream().flush();
                    log("Transaction envoyée avec succès au pair : " + peer.getPort());
                } else {
                    logWarning("Flux non disponible pour le pair : " + peer.getPort());
                }
            } catch (IOException e) {
                logError(nodeId + ": Échec de la diffusion de la transaction au pair " + peer.getPort(), e);
            }
        }
    }

    private void createTransaction(NodeMainInfo destNode, double amount, double fee) throws Exception {
        Transaction trans = this.myWallet.createTransaction(destNode.getWalletPublicKey(), amount, fee);
        if (trans != null) {
            log("Transaction créée avec succès.");
            this.pendingTransactions.add(trans);
            broadcastTransaction(trans);
            Thread.sleep((long) (500 + Math.random() * 1500));
            if (pendingTransactions.size() >= 3) {
                mineNewBlock();
            }
        } else {
            logWarning(nodeId + ": Échec de la création de la transaction.");
        }
    }

    private void mineNewBlock() throws Exception {
        Block lastBlock = Blockchain.blockchain.get(Blockchain.blockchain.size() - 1);
        Block newBlock = new Block(Blockchain.blockchain.size() + 1, lastBlock.getHash());

        for (Transaction tx : pendingTransactions) {
            newBlock.addTransaction(tx);
        }
        newBlock.calculateHash();

        if (Blockchain.addBlock(newBlock)) {
            log(nodeId + ": Nouveau bloc miné - " + newBlock.getHash());
            pendingTransactions.clear();
            broadcastBlock(newBlock);
        } else {
            logWarning(nodeId + ": Échec du minage du nouveau bloc.");
        }
    }

    private void broadcastBlock(Block block) {
        log("Diffusion du bloc miné en cours...");
        for (NodeMainInfo peer : peers) {
            try {
                if (peer.getOutputStream() != null) {
                    peer.getOutputStream().writeObject(block);
                    peer.getOutputStream().flush();
                    log("Bloc diffusé avec succès au pair : " + peer.getPort());
                }
            } catch (IOException e) {
                logError(nodeId + ": Échec de la diffusion du bloc au pair " + peer.getPort(), e);
            }
        }
    }

    private void log(String message) {
        System.out.println("[INFO] " + message);
    }

    private void logWarning(String message) {
        System.out.println("[WARNING] " + message);
    }

    private void logError(String message, Exception e) {
        System.err.println("[ERROR] " + message);
        e.printStackTrace();
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
    
    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private NodeClient mainNode;
        private ObjectInputStream in;
        private ObjectOutputStream out;
    
        public ClientHandler(Socket socket,ObjectOutputStream out , ObjectInputStream in, NodeClient node) {
            this.clientSocket = socket;
            this.mainNode = node;
            this.out=out;
            this.in=in;
        }        
    
        private void handleMessage(Object message) {
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
                        if(this.mainNode.pendingTransactions.size()==3){
                            Block lastBlock = Blockchain.blockchain.get(Blockchain.blockchain.size() - 1);
                            Block newBlock = new Block(Blockchain.blockchain.size()+1,lastBlock.getHash());
                            
                            // Ajouter les transactions en attente au nouveau bloc
                            for (Transaction tx : mainNode.pendingTransactions) {
                                newBlock.addTransaction(tx);
                            }
                            newBlock.calculateHash();

                            // newBlock.mineBlock(4);
                            if(Blockchain.addBlock(newBlock)){
                                System.out.println(mainNode.nodeId + ": Nouveau bloc miné - " + newBlock.getHash());
                                mainNode.broadcastBlock(newBlock);
                                mainNode.pendingTransactions.clear();
                            }

                        }else{
                            System.out.println(this.mainNode.nodeId + ": Transaction ajoutée au pool.");
                        }
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
                    Socket socket = new Socket(peerInfo.getIpAddress(),peerInfo.getPort());
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream in= new ObjectInputStream(socket.getInputStream());
                    peerInfo.setIn(in);
                    peerInfo.setOut(out);
                    System.out.println("Informations reçues du nœud : " + peerInfo.toString());

                    NodeMainInfo myInfo = new NodeMainInfo("localhost", mainNode.port, mainNode.myWallet.getPublicKey());
                    out.writeObject(myInfo);
                    out.flush();
                    // Vous pouvez ajouter cette information dans la liste de vos pairs (peers)
                    if (!mainNode.peers.contains(peerInfo)) {
                        mainNode.peers.add(peerInfo);
                        System.out.println("Pair ajouté à la liste des pairs.");
                    }
                    new Thread(new ClientHandler(socket,out,in,mainNode)).start();
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
            if(out == null || in == null){
                System.out.println("node client clienthandler en cours....");
                try (
                    ObjectOutputStream out = new ObjectOutputStream (clientSocket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream (clientSocket.getInputStream());
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
            }else{
                try {
                    System.out.println("server discussion handler : " + clientSocket.getInetAddress());
        
                    while (true) {
                        try {
                            // Lire l'objet envoyé par le client
                            Object message = in.readObject();
        
                            // Gérer le message
                            System.out.println(message);
                            handleMessage(message);
        
                            // Réponse au client
                            // out.writeObject("Message traité avec succès.");
                        } catch (ClassNotFoundException e) {
                            System.out.println("Objet inconnu reçu : " + e.getMessage());
                            // out.writeObject("Erreur : format de message non reconnu.");
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
    }
    
    // private void startHttpServer() throws IOException {
    //     HttpServer server = HttpServer.create(new InetSocketAddress(5000), 0);
    //     log("Serveur HTTP démarré sur le port 5000");

    //     // Route principale pour servir le fichier HTML
    //     server.createContext("/", exchange -> {
    //         File file = new File("index.html"); // Fichier HTML à servir
    //         if (file.exists()) {
    //             byte[] response = Files.readAllBytes(file.toPath());
    //             exchange.sendResponseHeaders(200, response.length);
    //             OutputStream os = exchange.getResponseBody();
    //             os.write(response);
    //             os.close();
    //         } else {
    //             exchange.sendResponseHeaders(404, 0);
    //             exchange.close();
    //         }
    //     });

    //     // Endpoint pour obtenir les informations du nœud
    //     server.createContext("/node/info", exchange -> {
    //         if ("GET".equals(exchange.getRequestMethod())) {
    //             String response = String.format(
    //                 "{\"nodeId\":\"%s\",\"port\":%d,\"balance\":%.2f}",
    //                 nodeId, port, myWallet.getBalance()
    //             );
    //             exchange.sendResponseHeaders(200, response.length());
    //             OutputStream os = exchange.getResponseBody();
    //             os.write(response.getBytes());
    //             os.close();
    //         }
    //     });

    //     // Endpoint pour se connecter à un pair
    //     server.createContext("/node/connect", exchange -> {
    //         if ("POST".equals(exchange.getRequestMethod())) {
    //             InputStream is = exchange.getRequestBody();
    //             BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    //             StringBuilder sb = new StringBuilder();
    //             String line;
    //             while ((line = reader.readLine()) != null) {
    //                 sb.append(line);
    //             }
    //             String[] params = sb.toString().split("&");
    //             String host = params[0].split("=")[1];
    //             int peerPort = Integer.parseInt(params[1].split("=")[1]);

    //             try {
    //                 connectToPeer(host, peerPort);
    //                 String response = "Connecté au pair avec succès.";
    //                 exchange.sendResponseHeaders(200, response.length());
    //                 exchange.getResponseBody().write(response.getBytes());
    //             } catch (Exception e) {
    //                 String response = "Erreur lors de la connexion au pair.";
    //                 exchange.sendResponseHeaders(500, response.length());
    //                 exchange.getResponseBody().write(response.getBytes());
    //             } finally {
    //                 exchange.close();
    //             }
    //         }
    //     });

    //     // Endpoint pour créer une transaction
    //     server.createContext("/transaction/create", exchange -> {
    //         if ("POST".equals(exchange.getRequestMethod())) {
    //             InputStream is = exchange.getRequestBody();
    //             BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    //             StringBuilder sb = new StringBuilder();
    //             String line;
    //             while ((line = reader.readLine()) != null) {
    //                 sb.append(line);
    //             }
    //             String[] params = sb.toString().split("&");
    //             int peerIndex = Integer.parseInt(params[0].split("=")[1]);
    //             double amount = Double.parseDouble(params[1].split("=")[1]);
    //             double fee = Double.parseDouble(params[2].split("=")[1]);

    //             try {
    //                 createTransaction(peers.get(peerIndex), amount, fee);
    //                 String response = "Transaction créée avec succès.";
    //                 exchange.sendResponseHeaders(200, response.length());
    //                 exchange.getResponseBody().write(response.getBytes());
    //             } catch (Exception e) {
    //                 String response = "Erreur lors de la création de la transaction.";
    //                 exchange.sendResponseHeaders(500, response.length());
    //                 exchange.getResponseBody().write(response.getBytes());
    //             } finally {
    //                 exchange.close();
    //             }
    //         }
    //     });

    //     // Endpoint pour obtenir la blockchain
    //     server.createContext("/blockchain", exchange -> {
    //         if ("GET".equals(exchange.getRequestMethod())) {
    //             String response = Blockchain.blockchain.toString();
    //             exchange.sendResponseHeaders(200, response.length());
    //             OutputStream os = exchange.getResponseBody();
    //             os.write(response.getBytes());
    //             os.close();
    //         }
    //     });

    //      // Route pour récupérer le nombre de pairs
    // server.createContext("/node/peers", exchange -> {
    //     String response = "Nombre de pairs connectés : " + peers.size();
    //     exchange.sendResponseHeaders(200, response.getBytes().length);
    //     OutputStream os = exchange.getResponseBody();
    //     os.write(response.getBytes());
    //     os.close();
    // });

    // // Route pour récupérer les transactions en attente
    // server.createContext("/node/pending-transactions", exchange -> {
    //     StringBuilder responseBuilder = new StringBuilder();
    //     responseBuilder.append("Transactions en attente :\n");
    //     for (Transaction tx : pendingTransactions) {
    //         responseBuilder.append(tx.toString()).append("\n");
    //     }
    //     String response = responseBuilder.toString();
    //     exchange.sendResponseHeaders(200, response.getBytes().length);
    //     OutputStream os = exchange.getResponseBody();
    //     os.write(response.getBytes());
    //     os.close();
    // });

    //     server.start();
    // }

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
        HttpServer server = HttpServer.create(new InetSocketAddress(5000), 0);
        log("Serveur HTTP démarré sur le port 5000");
    
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
                    int peerIndex = Integer.parseInt(params[0].split("=")[1]);
                    double amount = Double.parseDouble(params[1].split("=")[1]);
                    double fee = Double.parseDouble(params[2].split("=")[1]);
    
                    createTransaction(peers.get(peerIndex), amount, fee);
                    String response = "{\"message\": \"Transaction créée avec succès.\"}";
                    sendJsonResponse(exchange, response);
                } catch (Exception e) {
                    sendJsonError(exchange, 500, "Erreur lors de la création de la transaction.");
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
                String response = String.format("{\"connectedPeers\": %d}", peers.size());
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
    
 
 
    public static void main(String[] args) throws NoSuchAlgorithmException, IOException {
        // Créer le premier nœud (node1) et le démarrer dans un thread séparé
        NodeClient node1 = new NodeClient("node1", 8081, null);
        node1.setWalletAmount(100);
        new Thread(() -> {
            try {
                node1.start();
            } catch (NoSuchAlgorithmException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }).start();

        node1.startHttpServer();
    
        // Connecter le noeud à un pair
        // try {
        //     Thread.sleep(1000); // Attendre que le serveur démarre
        //     node1.connectToPeer("localhost", 8080);
        //     System.out.println(Blockchain.blockchain.size());
        // } catch (Exception e) {
        //     e.printStackTrace();
        // }

        // try {
        //     node1.createTransaction(node1.peers.get(0), 20.0, 0.1);
        // } catch (Exception e) {
        //     System.out.println("Erreur lors de la création de la transaction : " + e.getMessage());
        // }
    }
}

